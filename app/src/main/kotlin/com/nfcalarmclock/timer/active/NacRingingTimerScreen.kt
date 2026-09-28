package com.nfcalarmclock.timer.active

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavDeepLinkBuilder
import com.nfcalarmclock.R
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.main.NacMainActivity
import com.nfcalarmclock.system.permission.fullscreenintent.NacFullScreenIntentPermission
import com.nfcalarmclock.system.toBundle
import com.nfcalarmclock.timer.db.NacTimer

/**
 * Brings a ringing timer to the screen, the phone locked included.
 *
 * A timer has no screen of its own: it is shown in the main screen of the app, which
 * does not come up over the lock screen, and its notification had nothing to open
 * a screen with. It rang, and nothing could be seen (28 Sept, 1.92).
 *
 * Two things change while a timer rings:
 * - a notification of its own is posted, on a channel of high importance, with a
 *   full screen intent. Only such a channel may carry one, and a full screen intent
 *   that arrives as an update of a notification already on screen may never fire,
 *   which is why this is not added to the countdown notification;
 * - the main screen is allowed over the lock screen, for as long as a timer rings
 *   and no longer, so that the rest of the app is not left open on a locked phone.
 */
object NacRingingTimerScreen
{

	/**
	 * Channel of the ringing timer. Silent: the timer plays its own sound.
	 */
	private const val CHANNEL_ID = "NacNotiChannelTimerRinging"

	/**
	 * Kept away from the ids of the countdown notifications, which start at 1069.
	 */
	private const val BASE_ID = 5069

	/**
	 * Ids of the timers that are ringing.
	 */
	private val ringingIds: MutableSet<Long> = mutableSetOf()

	/**
	 * Called when a timer starts or stops ringing. The main screen listens, to come
	 * over the lock screen or to leave it.
	 */
	var onRingingChanged: (() -> Unit)? = null

	/**
	 * Whether a timer is ringing.
	 */
	val isAnyRinging: Boolean
		get() = ringingIds.isNotEmpty()

	/**
	 * Notification id of a ringing timer.
	 */
	private fun calcId(timer: NacTimer): Int = BASE_ID + timer.id.toInt()

	/**
	 * What opens the screen of the timer.
	 */
	private fun buildPendingIntent(context: Context, timer: NacTimer): PendingIntent
	{
		return NavDeepLinkBuilder(context.applicationContext)
			.setComponentName(NacMainActivity::class.java)
			.setGraph(R.navigation.nav_main_fragments)
			.addDestination(R.id.nacShowTimersFragment)
			.addDestination(R.id.nacActiveTimerFragment)
			.setArguments(timer.toBundle())
			.createPendingIntent()
	}

	/**
	 * A timer started ringing.
	 */
	@SuppressLint("MissingPermission", "FullScreenIntentPolicy")
	fun show(context: Context, timer: NacTimer)
	{
		ringingIds.add(timer.id)
		onRingingChanged?.invoke()

		val manager = NotificationManagerCompat.from(context)

		// The channel. Its importance cannot be changed once it exists, which is why it
		// is one of its own
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID,
				NotificationManagerCompat.IMPORTANCE_HIGH)
			.setName(context.getString(R.string.title_ringing_timer_channel))
			.setDescription(context.getString(R.string.description_ringing_timer_channel))
			.setSound(null, null)
			.setVibrationEnabled(false)
			.setLightsEnabled(false)
			.setShowBadge(false)
			.build()

		manager.createNotificationChannel(channel)

		val pendingIntent = buildPendingIntent(context, timer)
		val title = timer.name.ifEmpty { context.getString(R.string.word_timer) }

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.hourglass_empty_32)
			.setContentTitle(title)
			.setContentText(context.getString(R.string.message_timer_ringing))
			.setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
			.setCategory(NotificationCompat.CATEGORY_ALARM)
			.setPriority(NotificationCompat.PRIORITY_MAX)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setContentIntent(pendingIntent)
			.setFullScreenIntent(pendingIntent, true)
			.setOngoing(true)
			.setAutoCancel(false)
			.build()

		// Not setSilent(): it files the notification in a group that only alerts
		// through its summary, and the system then holds back the full screen intent
		// with every other alert. That is why the screen stayed locked in 1.92. The
		// channel has no sound and no vibration, which is all the silence needed

		NacLog.i("Timer ${timer.id} rings: posting its full screen notification. " +
			"Full screen allowed=${NacFullScreenIntentPermission.hasPermission(context)} " +
			"overlay=${Settings.canDrawOverlays(context)}")

		try
		{
			manager.notify(calcId(timer), notification)
		}
		catch (e: Exception)
		{
			NacLog.e("Unable to post the ringing timer notification", throwable = e)
		}

		// With the permission to draw over other apps the screen can also be opened
		// straight away, as the alarm does. Without it, the full screen intent does it
		if (Settings.canDrawOverlays(context))
		{
			try
			{
				pendingIntent.send()
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to open the screen of the ringing timer", throwable = e)
			}
		}
	}

	/**
	 * A timer stopped ringing, or was put away.
	 */
	fun cancel(context: Context, timer: NacTimer)
	{
		NotificationManagerCompat.from(context).cancel(calcId(timer))

		if (ringingIds.remove(timer.id))
		{
			onRingingChanged?.invoke()
		}
	}

}
