package com.nfcalarmclock.system.scheduler

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nfcalarmclock.alarm.activealarm.NacActiveAlarmBroadcastReceiver
import com.nfcalarmclock.alarm.activealarm.NacActiveAlarmService
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.alarm.options.dismissoptions.NacDismissEarlyService
import com.nfcalarmclock.alarm.options.upcomingreminder.NacUpcomingReminderService
import com.nfcalarmclock.main.NacMainActivity
import com.nfcalarmclock.system.NacCalendar
import java.util.Calendar
import kotlin.random.Random

/**
 * The alarm scheduler.
 */
object NacScheduler
{

	/**
	 * Add all alarm days to the scheduler.
	 */
	fun add(context: Context, alarm: NacAlarm?)
	{
		// Check if the alarm is null or not enabled
		if (alarm?.isEnabled != true)
		{
			return
		}

		// Get the calendar for the next alarm
		val nextAlarmCal = NacCalendar.getNextAlarmDay(alarm, ignoreSkip = true)!!
		//NacLog.i("NacScheduler : Next alarm : ${calendarToString(nextAlarmCal, "EEE MMM dd HH:mm:ss z yyyy")}")

		// Add the alarm
		addAlarm(context, alarm, nextAlarmCal)

		// Check if should show an upcoming reminder
		if (alarm.shouldShowReminder && !alarm.shouldSkipNextAlarm)
		{
			// Get the calendar for the first upcoming reminder
			val firstReminderCal = NacCalendar.getFirstAlarmUpcomingReminder(alarm, nextAlarmCal)

			// Add the upcoming reminder
			addUpcomingReminder(context, alarm, firstReminderCal)
		}

		// Check if should schedule a dismiss early notification
		if (alarm.canDismissEarly && alarm.shouldShowDismissEarlyNotification && !alarm.shouldSkipNextAlarm)
		{
			addDismissEarly(context, alarm)
		}

		// Check if the dawn simulation should be scheduled
		if (alarm.shouldUseDawn && !alarm.shouldSkipNextAlarm)
		{
			addDawn(context, alarm, nextAlarmCal)
		}
	}

	/**
	 * Add the dawn simulation to the scheduler.
	 *
	 * The dawn starts the given number of minutes before the alarm goes off.
	 */
	fun addDawn(
		context: Context,
		alarm: NacAlarm,
		atCal: Calendar? = null,
		isSnoozedDuringDawn: Boolean = false
	)
	{
		// Get the calendar for when the next alarm will run. It is given when the
		// alarm has been snoozed, since the snooze time is not the usual next time
		val nextCal = atCal ?: NacCalendar.getNextAlarmDay(alarm) ?: return
		val now = Calendar.getInstance()

		// Get the time at which the dawn should start
		val nextMillis = nextCal.timeInMillis
		val dawnMillis = nextMillis - alarm.dawnDuration*60*1000L

		// The dawn would have started in the past
		var startMillis = dawnMillis

		if (dawnMillis < now.timeInMillis)
		{
			// The alarm has been snoozed. Light the screen back up over whatever time
			// is left, so that the dawn is not lost for the rest of the morning
			if (alarm.snoozeCount > 0)
			{
				// Nothing left to light up
				if ((nextMillis - now.timeInMillis) < 30000)
				{
					return
				}

				// Snoozed during the dawn: the screen stays dark for the first half of
				// the time left, then the dawn lights up over the second half. Starting
				// it at once would undo the snooze that was just asked for (2.13)
				startMillis = if (isSnoozedDuringDawn)
				{
					now.timeInMillis + (nextMillis - now.timeInMillis) / 2
				}
				else
				{
					now.timeInMillis
				}

				NacLog.i("Alarm was snoozed, running a shortened dawn")
			}
			// The alarm was just set or changed. Do not light the screen up out of
			// the blue: the next occurrence gets its full dawn
			else
			{
				NacLog.i("The alarm is too close, skipping the dawn for this occurrence")
				return
			}
		}

		// Create the intent. The dawn is the alarm service itself, started ahead of
		// time: it shows the alarm screen, in silence, and lets it light up. When the
		// alarm goes off, the very same service is started again, this time to ring,
		// and the screen does not move
		val intent = NacActiveAlarmService.getDawnIntent(context, alarm, nextMillis)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_CANCEL_CURRENT)!!

		// Schedule the dawn as an alarm clock, like the alarm itself. This matters:
		// an alarm clock grants the app a temporary exemption when it fires, which is
		// what allows the dawn service to start in the foreground and the dawn screen
		// to come up. A plain exact alarm does not, and the dawn would silently fail
		// to appear
		val showPendingIntent = buildMainActivityPendingIntent(context)
		val clockInfo = AlarmClockInfo(startMillis, showPendingIntent)
		val manager = getAlarmManager(context)

		manager.setAlarmClock(clockInfo, pendingIntent)
	}

	/**
	 * Add an alarm to the scheduler.
	 */
	private fun addAlarm(
		context: Context,
		alarm: NacAlarm,
		cal: Calendar)
	{
		// Operation to perform when the alarm goes off
		val pendingIntent = buildAddAlarmPendingIntent(context, alarm)

		// Check if the next alarm should be skipped
		if (alarm.shouldSkipNextAlarm)
		{
			// Schedule the notification, but not as an alarm so it does not show up in
			// the status bar tile
			val manager = getAlarmManager(context)
			manager.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pendingIntent)
		}
		// Normal alarm
		else
		{
			// Schedule the alarm clock
			addToAlarmManager(context, cal, pendingIntent)
		}
	}

	/**
	 * Add a dismiss early notification to the scheduler.
	 */
	fun addDismissEarly(context: Context, alarm: NacAlarm)
	{
		// Get the current calendar and the calendar when the next alarm will run
		val nextCal = NacCalendar.getNextAlarmDay(alarm)
		val now = Calendar.getInstance()

		// Get the time when the next alarm can be dismissed early
		val nextMillis = nextCal!!.timeInMillis
		var dismissMillis = nextMillis - alarm.dismissEarlyTime*60*1000
		val diffMillis = dismissMillis - now.timeInMillis

		// Check the time to make sure it is valid and not imminent
		if (diffMillis < 0)
		{
			// Imminent so do not schedule anything
			if ((nextMillis-now.timeInMillis) < 60000)
			{
				return
			}
			// Use the current time
			else
			{
				dismissMillis = now.timeInMillis
			}
		}

		// Create the intent
		val intent = NacDismissEarlyService.getStartIntent(context, alarm)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_CANCEL_CURRENT)!!

		// Schedule the notification, but not as an alarm so it does not show up in
		// the status bar tile
		val manager = getAlarmManager(context)
		manager.setExactAndAllowWhileIdle(AlarmManager.RTC, dismissMillis, pendingIntent)
	}

	/**
	 * Add an alarm calendar to the scheduler.
	 */
	private fun addToAlarmManager(
		context: Context,
		cal: Calendar,
		operationPendingIntent: PendingIntent)
	{
		// Time at which the alarm should go off
		val millis = cal.timeInMillis

		// Show the main activity
		val showPendingIntent = buildMainActivityPendingIntent(context)

		// Get the clock info and manager
		val clockInfo = AlarmClockInfo(millis, showPendingIntent)
		val manager = getAlarmManager(context)

		// Set the alarm
		manager.setAlarmClock(clockInfo, operationPendingIntent)
	}

	/**
	 * Add an upcoming reminder to the scheduler.
	 */
	fun addUpcomingReminder(
		context: Context,
		alarm: NacAlarm,
		reminderCal: Calendar)
	{
		// Get the current calendar and make a copy of the alarm calendar
		val now = Calendar.getInstance()

		// Check if the calendar for the upcoming reminder has already passed
		if (reminderCal.before(now))
		{
			// Do not schedule the upcoming reminder
			return
		}

		// Create the intent
		val intent = NacUpcomingReminderService.getStartIntent(context, alarm)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_CANCEL_CURRENT)!!

		// Schedule the notification, but not as an alarm so it does not show up in
		// the status bar tile
		val manager = getAlarmManager(context)
		manager.setExactAndAllowWhileIdle(AlarmManager.RTC, reminderCal.timeInMillis, pendingIntent)
	}

	/**
	 * Build the pending intent for adding an alarm.
	 *
	 * @return The pending intent for adding an alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildAddAlarmPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent
	{
		// Create the intent
		val intent = if (alarm.shouldSkipNextAlarm)
		{
			NacActiveAlarmService.getSkipIntent(context, alarm)
		}
		else
		{
			NacActiveAlarmService.getStartIntent(context, alarm)
		}

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_CANCEL_CURRENT)!!
	}

	/**
	 * Build the pending intent for canceling an alarm.
	 *
	 * @return The pending intent for canceling an alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildCancelAlarmPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent?
	{
		// Create the intent
		val intent = NacActiveAlarmService.getStartIntent(context, null)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_NO_CREATE)
	}

	/**
	 * Build the pending intent for canceling a skipped alarm.
	 *
	 * @return The pending intent for canceling a skipped alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildCancelSkipPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent?
	{
		// Create the intent
		val intent = NacActiveAlarmService.getSkipIntent(context, null)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_NO_CREATE)
	}

	/**
	 * Build the pending intent to launch the main activity.
	 *
	 * @return The pending intent to launch the main activity.
	 */
	private fun buildMainActivityPendingIntent(context: Context): PendingIntent?
	{
		// Get a random number to use for the ID
		val id = Random.nextInt(1, 500)

		// Get the flags
		val flags = PendingIntent.FLAG_IMMUTABLE

		// Create the intent
		val intent = Intent(context, NacMainActivity::class.java)

		// Build the pending intent
		return PendingIntent.getActivity(context, id, intent, flags)
	}

	/**
	 * Build the pending intent for an alarm.
	 *
	 * @return The pending intent for an alarm.
	 */
	private fun buildServicePendingIntent(
		context: Context,
		alarm: NacAlarm,
		intent: Intent,
		flags: Int
	): PendingIntent?
	{
		// Get the alarm ID
		val id = alarm.id.toInt()

		// Prepare the flags
		val intentFlags = flags or PendingIntent.FLAG_IMMUTABLE

		// Create the pending intent
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			PendingIntent.getForegroundService(context, id, intent, intentFlags)
		}
		else
		{
			PendingIntent.getService(context, id, intent, intentFlags)
		}
	}

	/**
	 * @see NacScheduler.cancel
	 */
	fun cancel(context: Context, alarm: NacAlarm?)
	{
		// Check if the alarm is null
		if (alarm == null)
		{
			return
		}

		// Cancel the alarm
		cancelAlarm(context, alarm)

		// Cancel the upcoming reminder
		cancelUpcomingReminder(context, alarm)

		// Cancel the dismiss early
		cancelDismissEarly(context, alarm)

		// Cancel the dawn simulation
		cancelDawn(context, alarm)
	}

	/**
	 * Cancel the dawn simulation.
	 */
	fun cancelDawn(context: Context, alarm: NacAlarm)
	{
		// Create the intent
		val intent = NacActiveAlarmService.getDawnIntent(context, null)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_NO_CREATE)

		// Check if the pending intent for the dawn is not null
		if (pendingIntent != null)
		{
			// Cancel the dawn
			getAlarmManager(context).cancel(pendingIntent)
		}

		// Note: the service is deliberately left alone. The dawn is a phase of the
		// alarm service, so stopping it here would also stop an alarm that is
		// currently ringing, and this is called every time an alarm is rescheduled
	}

	/**
	 * Cancel an alarm.
	 */
	private fun cancelAlarm(context: Context, alarm: NacAlarm)
	{
		// Build the pending intent for the alarm
		val alarmPendingIntent = buildCancelAlarmPendingIntent(context, alarm)
		val skipPendingIntent = buildCancelSkipPendingIntent(context, alarm)

		// Check if the alarm pending intent can be canceled
		if (alarmPendingIntent != null)
		{
			// Cancel the alarm
			getAlarmManager(context).cancel(alarmPendingIntent)
		}

		// Check if the skipped alarm pending intent can be canceled
		if (skipPendingIntent != null)
		{
			// Cancel the skipped alarm
			getAlarmManager(context).cancel(skipPendingIntent)
		}
	}

	/**
	 * Cancel dismiss early.
	 */
	fun cancelDismissEarly(context: Context, alarm: NacAlarm)
	{
		// Create the intent
		val intent = NacDismissEarlyService.getStartIntent(context, null)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_NO_CREATE)

		// Check if the pending intent for the upcoming reminder is not null
		if (pendingIntent != null)
		{
			// Cancel the alarm
			getAlarmManager(context).cancel(pendingIntent)
		}
	}

	/**
	 * Cancel the old alarm type with a given ID.
	 *
	 * @param  context  Context.
	 * @param  id  Alarm ID.
	 */
	fun cancelOld(context: Context, id: Int)
	{
		// Prepare the flags
		val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE

		// Create the pending intent for the old type
		val intent = Intent(context, NacActiveAlarmBroadcastReceiver::class.java)
		val pending = PendingIntent.getBroadcast(context, id, intent, flags)

		// Cancel the alarm
		if (pending != null)
		{
			getAlarmManager(context).cancel(pending)
		}
	}

	/**
	 * Cancel the older alarm type with a given ID.
	 *
	 * @param  context  Context.
	 * @param  id  Alarm ID.
	 */
	private fun cancelOlder(context: Context, id: Int)
	{
		// Prepare the flags
		var flags = PendingIntent.FLAG_NO_CREATE

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
		{
			flags = flags or PendingIntent.FLAG_MUTABLE
		}

		// Create the pending intent for the old type
		val intent = Intent(context, NacActiveAlarmBroadcastReceiver::class.java)
		val pending = PendingIntent.getBroadcast(context, id, intent, flags)

		// Cancel the alarm
		if (pending != null)
		{
			getAlarmManager(context).cancel(pending)
		}
	}

	/**
	 * Cancel an upcoming reminder.
	 */
	fun cancelUpcomingReminder(context: Context, alarm: NacAlarm)
	{
		// Create the intent
		val intent = NacUpcomingReminderService.getStartIntent(context, null)

		// Build the pending intent
		val pendingIntent = buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_NO_CREATE)

		// Check if the pending intent for the upcoming reminder is not null
		if (pendingIntent != null)
		{
			// Cancel the alarm
			getAlarmManager(context).cancel(pendingIntent)
		}
	}

	/**
	 * Get the AlarmManager.
	 *
	 * @return The AlarmManager.
	 */
	private fun getAlarmManager(context: Context): AlarmManager
	{
		return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
	}

	/**
	 * Refresh all alarms.
	 */
	fun refreshAll(context: Context, alarms: List<NacAlarm>)
	{
		// Iterate over each alarm
		for (a in alarms)
		{
			val id = a.id.toInt()

			// Clear out the older alarms (Do not use IMMUTABLE flag)
			cancelOlder(context, id)

			// Clear out the old alarms (Use IMMUTABLE flag)
			cancelOld(context, id)

			// Clear out any new alarms, just in case
			cancel(context, a)

			// Add each alarm
			add(context, a)
		}
	}

	/**
	 * Update all days in a given alarm.
	 */
	fun update(context: Context, alarm: NacAlarm?)
	{
		// Cancel the alarm
		cancel(context, alarm)

		// Add the alarm
		add(context, alarm)
	}

	/**
	 * Update a single calendar in a given alarm.
	 */
	fun update(
		context: Context,
		alarm: NacAlarm,
		cal: Calendar,
		isSnoozedDuringDawn: Boolean = false
	)
	{
		// Cancel the alarm
		cancel(context, alarm)

		// Add the alarm. This will not add the reminder, but that is OK
		addAlarm(context, alarm, cal)

		// Schedule the dawn for this time as well. Without this, snoozing loses the
		// dawn: the alarm comes back on the plain screen, as if it had never been
		// asked for
		if (alarm.shouldUseDawn && !alarm.shouldSkipNextAlarm)
		{
			addDawn(context, alarm, cal, isSnoozedDuringDawn)
		}
	}

	/**
	 * Update a list of alarms.
	 */
	fun updateAll(context: Context, alarms: List<NacAlarm>)
	{
		// Iterate over each alarm
		for (a in alarms)
		{
			// Update the alarm
			update(context, a)
		}
	}

}
