package com.nfcalarmclock.alarm.options.tts

import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nfcalarmclock.R
import com.nfcalarmclock.main.NacMainActivity
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.NacCalendar
import com.nfcalarmclock.view.notification.NacBaseNotificationBuilder

/**
 * The notification the shake service has to hold while it listens.
 *
 * Android does not let a foreground service run without one, and a sensor is only
 * listened to while a process is alive. It carries no button: one was tried and did
 * not work, so the tile and the shake are what say the time now.
 *
 * Which makes it the sign that the phone is listening: it is there for exactly as long
 * as a shake will say the time, and not a minute longer. Its text names the hour it
 * stops at, so that one look at the shade answers the question.
 *
 * @param context Context.
 */
class NacSayTimeNotification(
	context: Context
) : NacBaseNotificationBuilder(context, "NacNotiChannelSayTime")
{

	/**
	 * @see NacBaseNotificationBuilder.id
	 */
	override val id: Int = 53

	/**
	 * @see NacBaseNotificationBuilder.channelName
	 */
	override val channelName: String
		get() = context.getString(R.string.title_say_time)

	/**
	 * @see NacBaseNotificationBuilder.channelDescription
	 */
	override val channelDescription: String
		get() = context.getString(R.string.description_say_time_notification_channel)

	/**
	 * @see NacBaseNotificationBuilder.channelImportance
	 */
	override val channelImportance: Int = NotificationManagerCompat.IMPORTANCE_LOW

	/**
	 * @see NacBaseNotificationBuilder.priorityLevel
	 */
	override val priorityLevel: Int = NotificationCompat.PRIORITY_LOW

	/**
	 * @see NacBaseNotificationBuilder.category
	 */
	override val category: String = NotificationCompat.CATEGORY_SERVICE

	/**
	 * @see NacBaseNotificationBuilder.group
	 */
	override val group: String = "NacNotiGroupSayTime"

	/**
	 * @see NacBaseNotificationBuilder.contentText
	 */
	override val contentText: String
		get()
		{
			val shared = NacSharedPreferences(context)
			val from = shared.sayTimeFromHour
			val to = shared.sayTimeToHour

			// The whole day, so there is no hour to name
			if (from == to)
			{
				return context.getString(R.string.description_say_time_listening_always)
			}

			// Both ends, not only the far one. The near one is what says whether the
			// span has already begun, which is the question a look at the shade asks
			return context.getString(R.string.description_say_time_listening_between,
				NacCalendar.getClockTime(context, from, 0),
				NacCalendar.getClockTime(context, to, 0))
		}

	/**
	 * @see NacBaseNotificationBuilder.contentPendingIntent
	 */
	override val contentPendingIntent: PendingIntent
		get() = NacMainActivity.getStartPendingIntent(context)

	/**
	 * Constructor.
	 */
	init
	{
		// Create the channel
		setupChannel()

		// Build the notification
		this.setPriority(priorityLevel)
			.setCategory(category)
			.setGroup(group)
			.setContentTitle(context.getString(R.string.title_say_time))
			.setContentText(contentText)
			.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
			.setContentIntent(contentPendingIntent)
			.setSmallIcon(smallIcon)
			.setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
			.setAutoCancel(false)
			.setOngoing(true)
			.setShowWhen(false)
			.setSound(null)
	}

	/**
	 * @see NacBaseNotificationBuilder.createChannel
	 */
	@RequiresApi(Build.VERSION_CODES.O)
	override fun createChannel(): NotificationChannel
	{
		// Create the channel
		val channel = super.createChannel()

		// Setup the channel. Nothing about it should ever make a sound or a light, it
		// is only there to hold a button
		channel.setShowBadge(false)
		channel.enableLights(false)
		channel.enableVibration(false)
		channel.setSound(null, null)

		return channel
	}

}
