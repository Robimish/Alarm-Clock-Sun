package com.nfcalarmclock.alarm.options.tts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.NacLifecycleService
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Listens to the sensors so that shaking the phone, or passing a hand over it, says
 * the time out loud.
 *
 * A sensor is only listened to while something of the app is alive, and Android kills
 * a plain background process, so this has to be a foreground service. That is also why
 * the notification is always there while it listens: Android requires one.
 *
 * Nothing is answered outside the hours that were chosen for the spoken time. Without
 * that, a phone in a pocket would talk on its own all day.
 */
class NacSayTimeService
	: NacLifecycleService(),
	SensorEventListener
{

	/**
	 * Sensor manager, kept so that the listener can be taken off again.
	 */
	private var sensorManager: SensorManager? = null

	/**
	 * Proximity sensor, kept for the farthest it can see, which is what a reading is
	 * compared against.
	 */
	private var proximitySensor: Sensor? = null

	/**
	 * How many readings in a row were above the threshold.
	 */
	private var shakeCount: Int = 0

	/**
	 * When the first of those readings came in. [Units: ms]
	 */
	private var firstShakeMillis: Long = 0

	/**
	 * When the time was last said, so that one shake does not say it five times.
	 * [Units: ms]
	 */
	private var lastSpokeMillis: Long = 0

	/**
	 * How hard the phone has to be moved, worked out from the setting.
	 */
	private var shakeThreshold: Float = SOFT_THRESHOLD

	/**
	 * Whether something is in front of the proximity sensor right now.
	 */
	private var isCovered: Boolean = false

	/**
	 * When it arrived in front of the sensor, so that a hand passing over can be told
	 * apart from a phone lying face down. [Units: ms]
	 */
	private var coverStartMillis: Long = 0

	/**
	 * How many hands have passed over so far.
	 */
	private var passCount: Int = 0

	/**
	 * When the first of them passed. [Units: ms]
	 */
	private var firstPassMillis: Long = 0

	/**
	 * How many passes make the gesture, worked out from the setting.
	 */
	private var passesNeeded: Int = 2

	/**
	 * Called when the service is bound.
	 */
	override fun onBind(intent: Intent): IBinder?
	{
		super.onBind(intent)

		return null
	}

	/**
	 * Called when the service is started.
	 */
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
	{
		super.onStartCommand(intent, flags, startId)

		val shared = NacSharedPreferences(this)
		val isOn = shared.shouldSayTime
		val shouldShake = isOn && shared.shouldShakeToSayTime
		val shouldWave = isOn && shared.shouldWaveToSayTime

		// Both were turned off while the service was running, or the switch that
		// holds back the two of them was
		if (!shouldShake && !shouldWave)
		{
			NacLog.i("Nothing is left to listen for. Stopping the service")
			cancelBoundaryAlarm(this)
			stopThisService()

			return START_NOT_STICKY
		}

		// Outside the hours that were chosen there is nothing to listen for, so the
		// service stands down and its notification goes with it. Android does not let a
		// foreground service run without a notification, so stopping is the only way to
		// take it off the screen. An exact alarm brings it back at the hour
		val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]

		if (!shared.isWithinSayTimeHours(hour))
		{
			NacLog.i("Outside the hours for the spoken time. Standing down")
			scheduleBoundaryAlarm(this, shared, isInside = false)
			stopThisService()

			return START_NOT_STICKY
		}

		// How hard a shake has to be
		shakeThreshold = when (shared.shakeSensitivity)
		{
			0 -> FINEST_THRESHOLD
			1 -> FINER_THRESHOLD
			3 -> NORMAL_THRESHOLD
			else -> SOFT_THRESHOLD
		}

		// How many hands passing over make the gesture
		passesNeeded = if (shared.wavePasses == 0) 1 else 2

		// Android asks for a notification before it lets a service stay alive
		val notification = NacSayTimeNotification(this)

		showForegroundNotification {
			startForeground(notification.id, notification.build())
		}

		// The settings may have changed since the last time this ran, so what is being
		// listened to is settled from scratch
		stopListening()
		startListening(shouldShake, shouldWave)

		// Stand down again when the hours run out
		scheduleBoundaryAlarm(this, shared, isInside = true)

		return START_STICKY
	}

	/**
	 * Called when the service is destroyed.
	 */
	override fun onDestroy()
	{
		stopListening()

		super.onDestroy()
	}

	/**
	 * Start listening to the sensors that were asked for.
	 */
	private fun startListening(shouldShake: Boolean, shouldWave: Boolean)
	{
		val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

		// The phone has no sensors at all, which would be surprising but is possible
		if (manager == null)
		{
			NacLog.w("No sensors on this phone. Nothing can say the time by itself")
			return
		}

		// The accelerometer, for a shake
		if (shouldShake)
		{
			val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

			if (sensor == null)
			{
				NacLog.w("No accelerometer on this phone. Shaking cannot say the time")
			}
			else
			{
				// SENSOR_DELAY_UI rather than a faster rate: a shake lasts a good fraction
				// of a second, and every extra reading is battery spent all night for
				// nothing
				manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
				sensorManager = manager
			}
		}

		// The proximity sensor, for a hand passing over
		if (shouldWave)
		{
			val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

			if (sensor == null)
			{
				NacLog.w("No proximity sensor on this phone. A hand cannot say the time")
			}
			else
			{
				// SENSOR_DELAY_NORMAL: this sensor only speaks up when what sits in front
				// of it changes, so asking for a faster rate buys nothing
				manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)

				proximitySensor = sensor
				sensorManager = manager
				isCovered = false
				passCount = 0
			}
		}
	}

	/**
	 * Stop listening to the sensors.
	 */
	private fun stopListening()
	{
		sensorManager?.unregisterListener(this)
		sensorManager = null
		proximitySensor = null
		isCovered = false
		passCount = 0
		shakeCount = 0
	}

	/**
	 * The accuracy of a sensor changed, which is of no interest here.
	 */
	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
	{
	}

	/**
	 * A reading came in from one of the sensors.
	 */
	override fun onSensorChanged(event: SensorEvent?)
	{
		val values = event?.values ?: return

		when (event.sensor?.type)
		{
			Sensor.TYPE_ACCELEROMETER -> onAccelerometerReading(values)
			Sensor.TYPE_PROXIMITY -> onProximityReading(values)
		}
	}

	/**
	 * A reading came in from the accelerometer.
	 */
	private fun onAccelerometerReading(values: FloatArray)
	{
		if (values.size < 3)
		{
			return
		}

		// How much the phone is being moved, as a multiple of gravity. At rest this is
		// 1, since gravity itself is always being felt
		val x = values[0] / SensorManager.GRAVITY_EARTH
		val y = values[1] / SensorManager.GRAVITY_EARTH
		val z = values[2] / SensorManager.GRAVITY_EARTH
		val force = sqrt((x*x) + (y*y) + (z*z))

		// Not moved hard enough
		if (force < shakeThreshold)
		{
			return
		}

		val now = System.currentTimeMillis()

		// The readings that came before are too old to be part of the same shake
		if ((now - firstShakeMillis) > SHAKE_WINDOW_MILLIS)
		{
			firstShakeMillis = now
			shakeCount = 0
		}

		shakeCount += 1

		// One good jolt is not a shake. Several in a row are
		if (shakeCount < SHAKE_COUNT)
		{
			return
		}

		shakeCount = 0

		sayTheTime("Phone shaken")
	}

	/**
	 * A reading came in from the proximity sensor.
	 */
	private fun onProximityReading(values: FloatArray)
	{
		if (values.isEmpty())
		{
			return
		}

		// Most of these sensors only say near or far, so a reading is either zero or the
		// farthest they can see. A few give centimetres, which is what the second test
		// is for
		val farthest = proximitySensor?.maximumRange ?: return
		val nowCovered = (values[0] < farthest) && (values[0] < PROXIMITY_NEAR_CM)
		val now = System.currentTimeMillis()

		// Nothing changed
		if (nowCovered == isCovered)
		{
			return
		}

		isCovered = nowCovered

		// Something has just arrived in front of the sensor
		if (nowCovered)
		{
			coverStartMillis = now
			return
		}

		// It has just left. A hand passing over is in front of the sensor for a moment;
		// a phone lying face down, in a pocket or against an ear stays there far longer,
		// and is not a pass
		if ((now - coverStartMillis) > PASS_MAX_COVER_MILLIS)
		{
			passCount = 0
			return
		}

		// The pass before is too old to belong to the same gesture
		if ((now - firstPassMillis) > PASS_WINDOW_MILLIS)
		{
			firstPassMillis = now
			passCount = 0
		}

		passCount += 1

		// One pass is asked for, or two
		if (passCount < passesNeeded)
		{
			return
		}

		passCount = 0

		sayTheTime("Hand passed over the phone")
	}

	/**
	 * Say the time out loud, unless it was just said or the hours have run out.
	 */
	private fun sayTheTime(reason: String)
	{
		val now = System.currentTimeMillis()

		// It was said a moment ago
		if ((now - lastSpokeMillis) < SPEAK_COOLDOWN_MILLIS)
		{
			return
		}

		// The service is only alive within the hours, so this should never be false.
		// It is kept for the minutes around the boundary, where the alarm that stands
		// the service down may not have fired yet
		val shared = NacSharedPreferences(this)
		val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]

		if (!shared.isWithinSayTimeHours(hour))
		{
			return
		}

		lastSpokeMillis = now

		NacLog.i("$reason. Saying the time")

		// The receiver does the talking, the same one the notification button uses
		sendBroadcast(Intent(this, NacSayTimeReceiver::class.java)
			.setAction(NacSayTimeReceiver.ACTION_SAY_TIME))
	}

	companion object
	{

		/**
		 * How hard the phone has to be moved, as a multiple of gravity. At rest the
		 * reading is 1.
		 */
		private const val FINEST_THRESHOLD = 1.15f
		private const val FINER_THRESHOLD = 1.35f
		private const val SOFT_THRESHOLD = 1.7f
		private const val NORMAL_THRESHOLD = 2.3f

		/**
		 * How many readings above the threshold make a shake, and how long they have to
		 * arrive within. [Units: ms for the window]
		 */
		private const val SHAKE_COUNT = 3
		private const val SHAKE_WINDOW_MILLIS = 1000L

		/**
		 * How close something has to be to count as in front of the proximity sensor,
		 * for the few sensors that give a distance rather than near or far. [Units: cm]
		 */
		private const val PROXIMITY_NEAR_CM = 5f

		/**
		 * How long something may sit in front of the proximity sensor and still count as
		 * a hand passing over, and how long the two passes of the gesture have to arrive
		 * within. [Units: ms]
		 */
		private const val PASS_MAX_COVER_MILLIS = 1500L
		private const val PASS_WINDOW_MILLIS = 2000L

		/**
		 * How long before the time can be said again. [Units: ms]
		 */
		private const val SPEAK_COOLDOWN_MILLIS = 5000L

		/**
		 * Request code of the alarm that stands the service up and down at the hours.
		 */
		private const val BOUNDARY_REQUEST_CODE = 5301

		/**
		 * The broadcast that asks the service to look at the clock again.
		 */
		private fun boundaryPendingIntent(context: Context): PendingIntent
		{
			val intent = Intent(context, NacSayTimeReceiver::class.java)
				.setAction(NacSayTimeReceiver.ACTION_REFRESH_SHAKE)

			return PendingIntent.getBroadcast(context, BOUNDARY_REQUEST_CODE, intent,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		}

		/**
		 * Ask to be woken at the next end of the hours that were chosen.
		 *
		 * An exact alarm on purpose: Android lets an app start a foreground service
		 * from the background when one of its own exact alarms fires, and an inexact
		 * one would leave the notification hanging around for a while after the hour.
		 */
		private fun scheduleBoundaryAlarm(
			context: Context,
			shared: NacSharedPreferences,
			isInside: Boolean
		)
		{
			val from = shared.sayTimeFromHour
			val to = shared.sayTimeToHour

			// The whole day counts, so there is no hour to wait for
			if (from == to)
			{
				cancelBoundaryAlarm(context)
				return
			}

			// Inside the hours, wait for them to run out. Outside, wait for them to start
			val targetHour = if (isInside) to else from
			val calendar = Calendar.getInstance()

			calendar[Calendar.HOUR_OF_DAY] = targetHour
			calendar[Calendar.MINUTE] = 0
			calendar[Calendar.SECOND] = 0
			calendar[Calendar.MILLISECOND] = 0

			// That hour is behind us today, so it is tomorrow
			if (calendar.timeInMillis <= System.currentTimeMillis())
			{
				calendar.add(Calendar.DAY_OF_YEAR, 1)
			}

			val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
				?: return
			val pendingIntent = boundaryPendingIntent(context)

			try
			{
				manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
					calendar.timeInMillis, pendingIntent)
			}
			catch (e: Exception)
			{
				// Exact alarms were refused. An approximate one still does the job, only
				// the notification lingers a little past the hour
				NacLog.e("Unable to set an exact alarm for the spoken time hours", throwable = e)
				manager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
			}
		}

		/**
		 * Forget the alarm that stands the service up and down.
		 */
		private fun cancelBoundaryAlarm(context: Context)
		{
			val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
				?: return

			manager.cancel(boundaryPendingIntent(context))
		}

		/**
		 * Start the service, or stop it, following the settings.
		 *
		 * Starting a foreground service is not allowed from just anywhere on Android 12
		 * and later, so this never throws: if the moment was wrong, the next time the
		 * app is opened will do it.
		 */
		fun refresh(context: Context)
		{
			val shared = NacSharedPreferences(context)
			val intent = Intent(context, NacSayTimeService::class.java)

			// The clock is looked at here rather than letting the service start and stop
			// itself: Android expects a service started this way to go into the
			// foreground within a few seconds, and starting one only to stand it down is
			// asking for trouble
			val hour = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
			val isWanted = shared.shouldSayTime
				&& (shared.shouldShakeToSayTime || shared.shouldWaveToSayTime)
			val shouldListen = isWanted && shared.isWithinSayTimeHours(hour)

			try
			{
				if (shouldListen)
				{
					// ContextCompat rather than startForegroundService(), which only
					// exists from API 26 and this app goes back to 24
					ContextCompat.startForegroundService(context, intent)
				}
				else
				{
					context.stopService(intent)

					// Still wanted, only not at this hour, so come back when it starts
					if (isWanted)
					{
						scheduleBoundaryAlarm(context, shared, isInside = false)
					}
					else
					{
						cancelBoundaryAlarm(context)
					}
				}
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to start or stop the spoken time service", throwable = e)
			}
		}

	}

}
