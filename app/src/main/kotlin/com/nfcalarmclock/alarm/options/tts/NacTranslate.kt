package com.nfcalarmclock.alarm.options.tts

import android.content.Context
import android.text.format.DateFormat
import com.nfcalarmclock.R
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.NacCalendar
import java.util.Calendar

/**
 * How to translate certain phrases to other languages.
 */
object NacTranslate
{

	/**
	 * The clock is said the way the phone shows it.
	 */
	const val TIME_FORMAT_FOLLOW_PHONE = 0

	/**
	 * The clock is said from 1 to 12, with AM or PM after it.
	 */
	const val TIME_FORMAT_12_HOUR = 1

	/**
	 * The clock is said from 0 to 23, with no AM or PM.
	 */
	const val TIME_FORMAT_24_HOUR = 2

	/**
	 * Whether the clock should be said from 0 to 23 or from 1 to 12.
	 */
	private fun isSaid24Hour(context: Context): Boolean
	{
		return when (NacSharedPreferences(context).sayTimeFormat)
		{
			TIME_FORMAT_12_HOUR -> false
			TIME_FORMAT_24_HOUR -> true
			else -> DateFormat.is24HourFormat(context)
		}
	}

	/**
	 * Get how to say the current time in any language.
	 */
	private fun getSayCurrentTime(context: Context): String
	{
		// Get the current hour and minute
		val calendar = Calendar.getInstance()
		val hour = calendar[Calendar.HOUR_OF_DAY]
		val minute = calendar[Calendar.MINUTE]

		// Whether to say "sixteen hours" or "four PM"
		val is24Hour = isSaid24Hour(context)

		// Only a 12 hour clock has a meridian. This is worked out here rather than
		// through NacCalendar.getMeridian(), which only ever looks at the setting of
		// the phone and would return nothing when the phone is on a 24 hour clock but
		// a 12 hour clock was asked for here
		val meridian = if (is24Hour)
		{
			""
		}
		// French and Spanish say the part of the day: "du matin", "de la tarde"
		else if (context.resources.getBoolean(R.bool.tts_use_day_parts))
		{
			context.resources.getString(when
			{
				hour < 12 -> R.string.tts_morning
				hour < 18 -> R.string.tts_afternoon
				else      -> R.string.tts_evening
			})
		}
		else
		{
			context.resources.getString(if (hour < 12) R.string.am else R.string.pm)
		}

		// Get the hour how it should be said. Note that upstream had these two the
		// wrong way round, which is why a 24 hour phone said "four" at 16:00
		val showHour = if (is24Hour)
		{
			hour.toString()
		}
		else
		{
			NacCalendar.to12HourFormat(hour).toString()
		}

		// Some languages read the clock face, where "4:05" needs its leading zero, and
		// others say the minutes as a plain number, where "05" comes out as "zero five"
		val showMinute = if (context.resources.getBoolean(R.bool.tts_should_pad_minutes))
		{
			minute.toString().padStart(2, '0')
		}
		// On the hour, those languages say nothing at all rather than "zero"
		else if (minute == 0)
		{
			""
		}
		else
		{
			minute.toString()
		}

		// One o'clock is said apart in several languages: "Es la una" and never
		// "Son las una", "Il est 1 heure" and not "heures". Where a language draws no
		// such distinction, the two strings hold the same sentence
		// On the hour, the languages that say nothing for the minutes have a sentence
		// of their own, so that "Son las siete y" does not hang on a lonely "y"
		val isOnTheHour = showMinute.isEmpty()

		// Midnight and noon are said by their names where the part of the day is said:
		// "minuit", "medianoche", "midi", "mediodía", and never "12 heures du matin"
		val isNamedHour = !is24Hour
			&& context.resources.getBoolean(R.bool.tts_use_day_parts)
		val isMidnight = isNamedHour && (hour == 0)
		val isNoon = isNamedHour && (hour == 12)

		val formatId = when
		{
			isMidnight && isOnTheHour        -> R.string.tts_say_time_midnight_on_the_hour
			isMidnight                       -> R.string.tts_say_time_midnight
			isNoon && isOnTheHour            -> R.string.tts_say_time_noon_on_the_hour
			isNoon                           -> R.string.tts_say_time_noon
			isOnTheHour && (showHour == "1") -> R.string.tts_say_time_one_on_the_hour
			isOnTheHour                      -> R.string.tts_say_time_on_the_hour
			showHour == "1"                  -> R.string.tts_say_time_one_oclock
			else                             -> R.string.tts_say_time
		}

		// Return the TTS phrase, without the space left before the full stop when
		// there is no meridian
		return context.resources.getString(formatId, showHour, showMinute, meridian)
			.replace(Regex("\\s+"), " ")
			.replace(" .", ".")
	}

	/**
	 * Get how to say the alarm reminder in any language.
	 */
	fun getSayReminder(
		context: Context,
		name: String,
		minute: Int
	): String
	{
		// Get the alarm name if it is set, but if it is empty, then get the
		// generic name for an alarm
		val reminder = name.ifEmpty { context.resources.getString(R.string.word_alarm) }

		// Return the statement that should be said
		return context.resources.getQuantityString(R.plurals.tts_say_reminder, minute,
			reminder, minute)
	}

	/**
	 * The text-to-speech phrase to say.
	 */
	fun getTtsPhrase(
		context: Context,
		shouldSayCurrentTime: Boolean,
		shouldSayAlarmName: Boolean,
		alarmName: String
	): String
	{
		// Initialize the phrase
		var phrase = ""

		// Say the current time
		if (shouldSayCurrentTime)
		{
			phrase += getSayCurrentTime(context)
		}

		// Say the alarm name
		if (shouldSayAlarmName)
		{
			phrase += " "
			phrase += alarmName
		}

		return phrase
	}

}