package com.nfcalarmclock.shared

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.Gravity
import androidx.preference.PreferenceManager
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.options.NacAlarmButton
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.timer.db.NacTimer
import com.nfcalarmclock.system.NacCalendar
import com.nfcalarmclock.system.getDeviceProtectedStorageContext
import com.nfcalarmclock.system.media.NacMedia
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Calendar
import androidx.core.content.edit
import com.nfcalarmclock.system.daysToValue
import com.nfcalarmclock.system.getPhoneFirstDayOfWeek

/**
 * Index of the "Automatic" choice of the start of the week, which follows the
 * locale of the phone.
 */
private const val START_WEEK_ON_AUTOMATIC = 2

/**
 * Container for the values of each preference.
 */
class NacSharedPreferences(context: Context)
{

	/**
	 * Shared preferences instance.
	 */
	val instance: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
		getDeviceProtectedStorageContext(context))

	/**
	 * Resources.
	 */
	val resources: Resources = context.resources

	/**
	 * Application context, for what the phone itself says (the country of the SIM
	 * card for the start of the week).
	 */
	private val appContext: Context = context.applicationContext ?: context

	/**
	 * AM color.
	 */
	val amColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_am)
			val defaultValue = resources.getInteger(R.integer.default_am_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * App's first run value.
	 */
	var appFirstRun: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_first_run)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_first_run)

			saveBoolean(key, value)
		}

	/**
	 * App's next alarm time in milliseconds.
	 */
	var appNextAlarmTimeMillis: Long
		get()
		{
			val key = resources.getString(R.string.key_app_next_alarm_time_millis)
			val defaultValue = 0L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_next_alarm_time_millis)

			saveLong(key, value)
		}

	/**
	 * App's next alarm timezone ID.
	 */
	var appNextAlarmTimezoneId: String
		get()
		{
			val key = resources.getString(R.string.key_app_next_alarm_timezone_id)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_next_alarm_timezone_id)

			saveString(key, value)
		}

	/**
	 * App's next alarm time in milliseconds.
	 */
	var appShouldSaveNextAlarm: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_next_alarm_should_save_app_alarm)

			return instance.getBoolean(key, true)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_next_alarm_should_save_app_alarm)

			saveBoolean(key, value)
		}

	/**
	 * Whether statistics should start to be collected or not.
	 */
	var appStartStatistics: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_start_statistics)
			val defaultValue = resources.getBoolean(R.bool.default_app_start_statistics)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_start_statistics)

			saveBoolean(key, value)
		}

	/**
	 * Audio source of an alarm.
	 */
	var audioSource: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_audio_source)
			val audioSources = resources.getStringArray(R.array.audio_source_keys)
			val defaultValue = audioSources[0]

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_audio_source)

			saveString(key, value)
		}

	/**
	 * Audio source of a timer.
	 */
	var audioSourceTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_audio_source)
			val audioSources = resources.getStringArray(R.array.audio_source_keys)
			val defaultValue = audioSources[0]

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_audio_source)

			saveString(key, value)
		}

	/**
	 * Auto dismiss time of an alarm.
	 */
	var autoDismissTime: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_auto_dismiss_time)
			val defaultValue = 900

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_auto_dismiss_time)

			saveInt(key, value)
		}

	/**
	 * Auto dismiss time of a timer.
	 */
	var autoDismissTimeTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_auto_dismiss_time)
			val defaultValue = 900

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_auto_dismiss_time)

			saveInt(key, value)
		}

	/**
	 * Old auto dismiss index.
	 */
	private val oldAutoDismissIndex: Int
		get()
		{
			val key = resources.getString(R.string.old_auto_dismiss_key)
			val defaultValue = resources.getInteger(R.integer.default_auto_dismiss_index)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Old auto dismiss time.
	 *
	 * This is used when updating database versions.
	 *
	 * @see .getAutoDismissTime
	 */
	val oldAutoDismissTime: Int
		get()
		{
			return if (oldAutoDismissIndex < 5)
			{
				oldAutoDismissIndex
			}
			else
			{
				(oldAutoDismissIndex - 4) * 5
			}
		}

	/**
	 * Auto snooze time.
	 */
	var autoSnoozeTime: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_auto_snooze_time)
			val defaultValue = 300

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_auto_snooze_time)

			saveInt(key, value)
		}

	/**
	 * Whether an alarm can be dismissed early or not.
	 */
	var canDismissEarly: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_can_dismiss_early)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_can_dismiss_early)

			saveBoolean(key, value)
		}

	/**
	 * Alarm card height when it is collapsed.
	 */
	var cardHeightCollapsed: Int
		get()
		{
			val key = resources.getString(R.string.key_main_card_height_collapsed)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_height_collapsed)

			saveInt(key, value)
		}

	/**
	 * Alarm card height when it is collapsed, with dismiss showing.
	 */
	var cardHeightCollapsedDismiss: Int
		get()
		{
			val key = resources.getString(R.string.key_main_card_height_collapsed_dismiss)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_height_collapsed_dismiss)

			saveInt(key, value)
		}

	/**
	 * Alarm card height when it is expanded.
	 */
	var cardHeightExpanded: Int
		get()
		{
			val key = resources.getString(R.string.key_main_card_height_expanded)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_height_expanded)

			saveInt(key, value)
		}

	/**
	 * Alarm card height when it is expanded, with the dismiss row showing.
	 */
	var cardHeightExpandedDismiss: Int
		get()
		{
			val key = resources.getString(R.string.key_main_card_height_expanded_dismiss)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_height_expanded_dismiss)

			saveInt(key, value)
		}

	/**
	 * Which version of the alarm card layout the heights were measured for.
	 *
	 * The heights of the card are measured once and kept. When the layout changes,
	 * the old heights no longer fit, so this says whether they can still be trusted.
	 */
	var cardLayoutVersion: Int
		get()
		{
			val key = resources.getString(R.string.key_main_card_layout_version)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_layout_version)

			saveInt(key, value)
		}

	/**
	 * Check if the alarm card has been measured.
	 */
	var cardIsMeasured: Boolean
		get()
		{
			val key = resources.getString(R.string.key_main_card_is_measured)
			val defaultValue = resources.getBoolean(R.bool.default_card_is_measured)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_card_is_measured)

			saveBoolean(key, value)
		}

	/**
	 * Alarm icon color in the clock widget.
	 */
	var clockWidgetAlarmIconColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_alarm_icon)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_alarm_icon)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_alarm_icon)

			saveInt(key, value)
		}

	/**
	 * Alarm time color in the clock widget.
	 */
	var clockWidgetAlarmTimeColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_alarm_time)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_alarm_time)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_alarm_time)

			saveInt(key, value)
		}

	/**
	 * Position of the alarm time above the date in the clock widget.
	 */
	var clockWidgetAlarmTimePositionAboveDate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_above_date)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_above_date)

			saveBoolean(key, value)
		}

	/**
	 * Position of the alarm time below the date in the clock widget.
	 */
	var clockWidgetAlarmTimePositionBelowDate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_below_date)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_below_date)

			saveBoolean(key, value)
		}

	/**
	 * Position of the alarm time same line as the date in the clock widget.
	 */
	var clockWidgetAlarmTimePositionSameLineAsDate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_same_line_as_date)

			return instance.getBoolean(key, true)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_position_alarm_time_same_line_as_date)

			saveBoolean(key, value)
		}

	/**
	 * Text size of the alarm time in the clock widget.
	 */
	var clockWidgetAlarmTimeTextSize: Float
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_alarm_time)
			val defaultValue = 14f

			return instance.getFloat(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_alarm_time)

			saveFloat(key, value)
		}

	/**
	 * Color of AM/PM in the clock widget.
	 */
	var clockWidgetAmPmColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_am_pm)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_am_pm)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_am_pm)

			saveInt(key, value)
		}

	/**
	 * Text size of AM/PM in the clock widget.
	 */
	var clockWidgetAmPmTextSize: Float
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_am_pm)
			val defaultValue = 18f

			return instance.getFloat(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_am_pm)

			saveFloat(key, value)
		}

	/**
	 * Background color of the clock widget.
	 */
	var clockWidgetBackgroundColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_background_color)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_background)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_background_color)

			saveInt(key, value)
		}

	/**
	 * Background transparency of the clock widget.
	 */
	var clockWidgetBackgroundTransparency: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_background_transparency)
			val defaultValue = 100

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_background_transparency)

			saveInt(key, value)
		}

	/**
	 * Color of the date in the clock widget.
	 */
	var clockWidgetDateColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_date)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_date)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_date)

			saveInt(key, value)
		}

	/**
	 * Text size of the date in the clock widget.
	 */
	var clockWidgetDateTextSize: Float
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_date)
			val defaultValue = 14f

			return instance.getFloat(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_date)

			saveFloat(key, value)
		}

	/**
	 * General alignment of the views in the clock widget.
	 */
	var clockWidgetGeneralAlignment: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_general_alignment)

			return instance.getInt(key, Gravity.CENTER_HORIZONTAL)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_general_alignment)

			saveInt(key, value)
		}

	/**
	 * Color of the hour in the clock widget.
	 */
	var clockWidgetHourColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_hour)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_hour)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_hour)

			saveInt(key, value)
		}

	/**
	 * Color of the minutes in the clock widget.
	 */
	var clockWidgetMinuteColor: Int
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_color_minute)
			val defaultValue = resources.getInteger(R.integer.default_clock_widget_color_minute)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_color_minute)

			saveInt(key, value)
		}

	/**
	 * Text size of the time in the clock widget.
	 */
	var clockWidgetTimeTextSize: Float
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_time)
			val defaultValue = 78f

			return instance.getFloat(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_text_size_time)

			saveFloat(key, value)
		}

	/**
	 * Whether an alarm should be auto dismissed or not.
	 */
	var shouldAutoDismiss: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_auto_dismiss)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_auto_dismiss)

			saveBoolean(key, value)
		}

	/**
	 * Whether a timer should be auto dismissed or not.
	 */
	var shouldAutoDismissTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_should_auto_dismiss)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_should_auto_dismiss)

			saveBoolean(key, value)
		}

	/**
	 * Whether an alarm should be auto snoozed or not.
	 */
	var shouldAutoSnooze: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_auto_snooze)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_auto_snooze)

			saveBoolean(key, value)
		}

	/**
	 * Whether the alarm time should be bold or not in the clock widget.
	 */
	var shouldClockWidgetBoldAlarmTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_bold_alarm_time)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_bold_alarm_time)

			saveBoolean(key, value)
		}

	/**
	 * Whether AM/PM should be bold or not in the clock widget.
	 */
	var shouldClockWidgetBoldAmPm: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_bold_am_pm)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_bold_am_pm)

			saveBoolean(key, value)
		}

	/**
	 * Whether the date should be bold or not in the clock widget.
	 */
	var shouldClockWidgetBoldDate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_bold_date)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_bold_date)

			saveBoolean(key, value)
		}

	/**
	 * Whether the hour should be bold or not in the clock widget.
	 */
	var shouldClockWidgetBoldHour: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_bold_hour)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_bold_hour)

			saveBoolean(key, value)
		}

	/**
	 * Whether the minutes should be bold or not in the clock widget.
	 */
	var shouldClockWidgetBoldMinute: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_bold_minute)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_bold_minute)

			saveBoolean(key, value)
		}

	/**
	 * Whether the alarm icon and time should be shown or not in the clock widget.
	 */
	var shouldClockWidgetShowAlarm: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_show_alarm)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_show_alarm)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show app specific alarms in the clock widget.
	 */
	var shouldClockWidgetShowAppSpecificAlarms: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_show_app_specific_alarms)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_show_app_specific_alarms)

			saveBoolean(key, value)
		}

	/**
	 * Whether the date should be shown or not in the clock widget.
	 */
	var shouldClockWidgetShowDate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_show_date)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_show_date)

			saveBoolean(key, value)
		}

	/**
	 * Whether the time should be shown or not in the clock widget.
	 */
	var shouldClockWidgetShowTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_clock_widget_show_time)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_clock_widget_show_time)

			saveBoolean(key, value)
		}

	/**
	 * Get the current playing alarm media.
	 */
	var currentPlayingAlarmMedia: String
		get()
		{
			val key = resources.getString(R.string.key_media_current_playing_alarm)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_media_current_playing_alarm)

			saveString(key, value)
		}

	/**
	 * Which style to use for the day buttons.
	 *
	 * 1: Represents using the filled-in buttons (Default)
	 * 2: Represents the outlined button style
	 */
	val dayButtonStyle: Int
		get()
		{
			val key = resources.getString(R.string.key_style_day_button)
			val defaultValue = resources.getInteger(R.integer.default_day_button_style)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Alarm days.
	 */
	var days: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_days)
			val defaultValue = resources.getInteger(R.integer.default_days)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_days)

			saveInt(key, value)
		}

	/**
	 * Days color.
	 */
	val daysColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_days)
			val defaultValue = resources.getInteger(R.integer.default_days_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Counter to delay showing the What's New dialog.
	 */
	var delayShowingWhatsNewDialogCounter: Int
		get()
		{
			val key = resources.getString(R.string.key_main_delay_showing_whats_new_dialog_counter)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_delay_showing_whats_new_dialog_counter)

			saveInt(key, value)
		}

	/**
	 * Delete after dismissed color.
	 */
	val deleteAfterDismissedColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_delete_after_dismissed)
			val defaultValue = resources.getInteger(R.integer.default_delete_alarm_after_dismissed_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * The time before an alarm goes off to start showing the dismiss early button by.
	 */
	var dismissEarlyTime: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_early_time)
			val defaultValue = 30

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_early_time)

			saveInt(key, value)
		}

	/**
	 * Event to fix any auto dismiss, auto snooze, or snooze duration values that are set
	 * to 0 in alarms.
	 */
	var eventFixZeroAutoDismissAndSnooze: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_fix_zero_auto_dismiss_and_snooze)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_fix_zero_auto_dismiss_and_snooze)

			saveBoolean(key, value)
		}

	/**
	 * Event to write down the audio source of every alarm by a name that does not
	 * change with the language.
	 */
	var eventAudioSourceToKeys: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_audio_source_to_keys)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_audio_source_to_keys)

			saveBoolean(key, value)
		}

	/**
	 * Whether the ready-made 5 minute timer of 1.94 and 1.95 has been turned into
	 * the Eggs timer of 10 minutes (1.97).
	 */
	var eventEggTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_egg_timer)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_egg_timer)

			saveBoolean(key, value)
		}

	/**
	 * Whether the ready-made 5 minute timer has been added.
	 */
	var eventAddFiveMinuteTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_add_five_minute_timer)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_add_five_minute_timer)

			saveBoolean(key, value)
		}

	/**
	 * Whether the audio source of the timers has been written down as a name that
	 * does not change with the language.
	 */
	var eventTimerAudioSourceToKeys: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_timer_audio_source_to_keys)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_timer_audio_source_to_keys)

			saveBoolean(key, value)
		}

	/**
	 * Event to write down where each alarm sits in the list, so that a list that is
	 * no longer rearranged keeps the order it had.
	 */
	var eventFreezeAlarmOrder: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_freeze_alarm_order)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_freeze_alarm_order)

			saveBoolean(key, value)
		}

	/**
	 * Event to move the shake sensitivity onto the list that gained two finer
	 * entries and lost the firmest one.
	 */
	var eventShakeSensitivityFiner: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_shake_sensitivity_finer)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_shake_sensitivity_finer)

			saveBoolean(key, value)
		}

	/**
	 * Event to update and backup media information in alarms, starting at database
	 * version 31.
	 */
	var eventUpdateAndBackupMediaInfoInAlarmsDbV31: Boolean
		get()
		{
			val key = resources.getString(R.string.key_event_update_and_backup_media_info_in_alarms_db_v31)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_event_update_and_backup_media_info_in_alarms_db_v31)

			saveBoolean(key, value)
		}

	/**
	 * Whether a new alarm card should be expanded or not.
	 */
	val expandNewAlarm: Boolean
		get()
		{
			val key = resources.getString(R.string.key_tweak_expand_new_alarm)
			val defaultValue = resources.getBoolean(R.bool.default_expand_new_alarm)

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Number of seconds to turn off the flashlight for an alarm.
	 */
	var flashlightOffDuration: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_off_duration)
			val defaultValue = "1"

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_off_duration)

			saveString(key, value)
		}

	/**
	 * Number of seconds to turn off the flashlight for a timer.
	 */
	var flashlightOffDurationTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_off_duration)
			val defaultValue = "1"

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_off_duration)

			saveString(key, value)
		}

	/**
	 * Number of seconds to turn on the flashlight for an alarm.
	 */
	var flashlightOnDuration: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_on_duration)
			val defaultValue = "1"

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_on_duration)

			saveString(key, value)
		}

	/**
	 * Number of seconds to turn on the flashlight for a timer.
	 */
	var flashlightOnDurationTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_on_duration)
			val defaultValue = "1"

			return instance.getString(key, defaultValue) ?: defaultValue
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_on_duration)

			saveString(key, value)
		}

	/**
	 * Strength level of the flashlight of an alarm.
	 */
	var flashlightStrengthLevel: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_strength_level)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_strength_level)

			saveInt(key, value)
		}

	/**
	 * Strength level of the flashlight of a timer.
	 */
	var flashlightStrengthLevelTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_strength_level)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_strength_level)

			saveInt(key, value)
		}

	/**
	 * Amount of time to wait before gradually increasing the flashlight strength level
	 * another step for an alarm.
	 */
	var graduallyIncreaseFlashlightStrengthLevelWaitTime: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_gradually_increase_flashlight_strength_level_wait_time)
			val defaultValue = 5

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_gradually_increase_flashlight_strength_level_wait_time)

			saveInt(key, value)
		}

	/**
	 * Amount of time to wait before gradually increasing the flashlight strength level
	 * another step for a timer.
	 */
	var graduallyIncreaseFlashlightStrengthLevelWaitTimeTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_gradually_increase_flashlight_strength_level_wait_time)
			val defaultValue = 5

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_gradually_increase_flashlight_strength_level_wait_time)

			saveInt(key, value)
		}

	/**
	 * Amount of time to wait before gradually increasing the volume another step for an
	 * alarm.
	 */
	var graduallyIncreaseVolumeWaitTime: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_volume_gradually_increase_volume_wait_time)
			val defaultValue = 5

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_volume_gradually_increase_volume_wait_time)

			saveInt(key, value)
		}

	/**
	 * Amount of time to wait before gradually increasing the volume another step for a
	 * timer.
	 */
	var graduallyIncreaseVolumeWaitTimeTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_volume_gradually_increase_volume_wait_time)
			val defaultValue = 5

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_volume_gradually_increase_volume_wait_time)

			saveInt(key, value)
		}

	/**
	 * Check if the selected media for an alarm is not available.
	 */
	var isSelectedMediaForAlarmNotAvailable: Boolean
		get()
		{
			val key = resources.getString(R.string.key_media_is_selected_for_alarm_not_available)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_media_is_selected_for_alarm_not_available)

			saveBoolean(key, value)
		}

	/**
	 * Check if the app has reached the counter limit.
	 *
	 * Note: This is used in the Google Play version of NacRateMyApp.
	 */
	@Suppress("unused")
	val isRateMyAppLimit: Boolean
		get()
		{
			return rateMyAppCounter >= 50
		}

	/**
	 * Check if the app has been rated.
	 *
	 * Note: This is used in the Google Play version of NacRateMyApp.
	 */
	@Suppress("unused")
	val isRateMyAppRated: Boolean
		get()
		{
			val rated = resources.getInteger(R.integer.default_rate_my_app_rated)

			return rateMyAppCounter == rated
		}

	/**
	 * Max number of snoozes.
	 */
	var maxSnooze: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_max_snooze)
			// Three snoozes, then the alarm has to be dismissed. Negative means unlimited
			val defaultValue = 3

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_max_snooze)

			saveInt(key, value)
		}

	/**
	 * Old index for the max number of snoozes.
	 */
	private val oldMaxSnoozeIndex: Int
		get()
		{
			val key = resources.getString(R.string.old_max_snooze_key)
			val defaultValue = resources.getInteger(R.integer.default_max_snooze_index)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Old max number of snoozes.
	 *
	 * This is used when updating database versions.
	 */
	val oldMaxSnoozeValue: Int
		get()
		{
			return if (oldMaxSnoozeIndex == 11)
			{
				-1
			}
			else
			{
				oldMaxSnoozeIndex
			}
		}

	/**
	 * Local media path for an alarm.
	 */
	var localMediaPath: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_alarm_local_media_path)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_alarm_local_media_path)

			saveString(key, value)
		}

	/**
	 * Local media path for a timer.
	 */
	var localMediaPathTimer: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_timer_local_media_path)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_timer_local_media_path)

			saveString(key, value)
		}

	/**
	 * Media artist for an alarm.
	 */
	var mediaArtist: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_artist)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_artist)

			saveString(key, value)
		}

	/**
	 * Media artist for a timer.
	 */
	var mediaArtistTimer: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_timer_media_artist)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_timer_media_artist)

			saveString(key, value)
		}

	/**
	 * Media path for an alarm.
	 */
	var mediaPath: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_path)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_path)

			saveString(key, value)
		}

	/**
	 * Media path for a timer.
	 */
	var mediaPathTimer: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_timer_media_path)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_timer_media_path)

			saveString(key, value)
		}

	/**
	 * Media title for an alarm.
	 */
	var mediaTitle: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_title)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_title)

			saveString(key, value)
		}

	/**
	 * Media title for a timer.
	 */
	var mediaTitleTimer: String
		get()
		{
			val key = resources.getString(R.string.key_general_default_timer_media_title)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_timer_media_title)

			saveString(key, value)
		}

	/**
	 * Media type for an alarm.
	 */
	var mediaType: Int
		get()
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_type)
			val defaultValue = NacMedia.TYPE_NONE

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_alarm_media_type)

			saveInt(key, value)
		}

	/**
	 * Media type for a timer.
	 */
	var mediaTypeTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_general_default_timer_media_type)
			val defaultValue = NacMedia.TYPE_RINGTONE

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_general_default_timer_media_type)

			saveInt(key, value)
		}

	/**
	 * Whether the missed alarm notifications should be displayed.
	 */
	val missedAlarmNotification: Boolean
		get()
		{
			val key = resources.getString(R.string.key_missed_alarm)
			val defaultValue = resources.getBoolean(R.bool.default_missed_alarm)

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Name of the alarm.
	 */
	var name: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_name)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_name)

			saveString(key, value)
		}

	/**
	 * Name of the timer.
	 */
	var nameTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_name)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_name)

			saveString(key, value)
		}

	/**
	 * Name color.
	 */
	val nameColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_name)
			val defaultValue = resources.getInteger(R.integer.default_name_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Whether the display next alarm should show time remaining for the next alarm.
	 */
	val nextAlarmFormat: Int
		get()
		{
			val key = resources.getString(R.string.key_tweak_next_alarm_format)
			val defaultValue = resources.getInteger(R.integer.default_next_alarm_format_index)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Order in which to dismiss NFC tags when multiple are selected for an alarm.
	 */
	var nfcTagDismissOrder: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_tag_dismiss_order)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_tag_dismiss_order)

			saveInt(key, value)
		}

	/**
	 * Order in which to dismiss NFC tags when multiple are selected for a timer.
	 */
	var nfcTagDismissOrderTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_nfc_tag_dismiss_order)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_nfc_tag_dismiss_order)

			saveInt(key, value)
		}

	/**
	 * ID of the NFC tag that needs to be used to dismiss the alarm.
	 */
	var nfcTagId: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_tag_id)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_tag_id)

			saveString(key, value)
		}

	/**
	 * ID of the NFC tag that needs to be used to dismiss the timer.
	 */
	var nfcTagIdTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_nfc_tag_id)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_nfc_tag_id)

			saveString(key, value)
		}

	/**
	 * PM color.
	 */
	val pmColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_pm)
			val defaultValue = resources.getInteger(R.integer.default_pm_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * The previous version of the app.
	 *
	 * Normally, this should be the same as the current version, but when an
	 * install occurs, these values will differ.
	 */
	var previousAppVersion: String
		get()
		{
			val key = resources.getString(R.string.key_app_previous_version)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_previous_version)

			saveString(key, value)
		}

	/**
	 * The previous system volume being used by bluetooth, before an alarm goes off.
	 */
	var previousBluetoothVolume: Int
		get()
		{
			val key = resources.getString(R.string.sys_previous_bluetooth_volume)
			val defaultValue = -1

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.sys_previous_bluetooth_volume)

			saveInt(key, value)
		}

	/**
	 * The previous system volume, before an alarm goes off.
	 */
	var previousVolume: Int
		get()
		{
			val key = resources.getString(R.string.sys_previous_volume)
			val defaultValue = -1

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.sys_previous_volume)

			saveInt(key, value)
		}

	/**
	 * The app's rating counter.
	 *
	 * Note: This is used in the Google Play version of NacRateMyApp.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var rateMyAppCounter: Int
		get()
		{
			val key = resources.getString(R.string.key_app_rating_counter)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_rating_counter)

			saveInt(key, value)
		}

	/**
	 * Whether to recursively play the media in a directory for an alarm.
	 */
	var recursivelyPlayMedia: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_media_should_recursively_play_media)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_media_should_recursively_play_media)

			saveBoolean(key, value)
		}

	/**
	 * Whether to recursively play the media in a directory for a timer.
	 */
	var recursivelyPlayMediaTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_media_should_recursively_play_media)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_media_should_recursively_play_media)

			saveBoolean(key, value)
		}

	/**
	 * Frequency at which to show the reminder, in units of minutes.
	 */
	var reminderFrequency: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_frequency)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_frequency)

			saveInt(key,  value)
		}

	/**
	 * Frequency at which to repeat the alarm.
	 */
	var repeatFrequency: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency)
			val defaultValue = 1

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency)

			saveInt(key,  value)
		}

	/**
	 * Days to run before starting the frequency at which to repeat the alarm.
	 */
	var repeatFrequencyDaysToRunBeforeStarting: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency_days_to_run_before_starting)
			val defaultValue = NacCalendar.Day.WEEK.daysToValue()

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency_days_to_run_before_starting)

			saveInt(key,  value)
		}

	/**
	 * Units for the frequency at which to repeat the alarm.
	 */
	var repeatFrequencyUnits: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency_units)
			val defaultValue = 4

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_frequency_units)

			saveInt(key,  value)
		}

	/**
	 * Whether the flashlight should be blinked or not for an alarm.
	 */
	var shouldBlinkFlashlight: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_should_blink)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_should_blink)

			saveBoolean(key, value)
		}

	/**
	 * Whether the flashlight should be blinked or not for a timer.
	 */
	var shouldBlinkFlashlightTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_should_blink)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_should_blink)

			saveBoolean(key, value)
		}

	/**
	 * Whether to use delete the alarm after it is dismissed or not.
	 */
	var shouldDeleteAfterDismissed: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_delete_alarm_after_dismissed)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_delete_alarm_after_dismissed)

			saveBoolean(key, value)
		}

	/**
	 * Whether to use delete the timer after it is dismissed or not.
	 */
	var shouldDeleteAfterDismissedTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_should_delete_alarm_after_dismissed)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_dismiss_should_delete_alarm_after_dismissed)

			saveBoolean(key, value)
		}

	/**
	 * Whether to toggle alarms with airplane mode.
	 */
	var shouldToggleAlarmsWithAirplaneMode: Boolean
		get()
		{
			val key = resources.getString(R.string.key_misc_should_toggle_alarms_with_airplane_mode)
			val defaultValue = resources.getBoolean(R.bool.default_misc_should_toggle_alarms_with_airplane_mode)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_misc_should_toggle_alarms_with_airplane_mode)

			saveBoolean(key, value)
		}

	/**
	 * Whether easy snooze is enabled or not.
	 */
	var shouldEasySnooze: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_use_easy_snooze)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_use_easy_snooze)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume should be gradually increased or not for an alarm.
	 */
	var shouldGraduallyIncreaseVolume: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_volume_should_gradually_increase_volume)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_volume_should_gradually_increase_volume)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume should be gradually increased or not for a timer.
	 */
	var shouldGraduallyIncreaseVolumeTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_volume_should_gradually_increase_volume)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_volume_should_gradually_increase_volume)

			saveBoolean(key, value)
		}

	/**
	 * Whether audio should be played through speakers and bluetooth or not.
	 */
	var shouldPlayAudioThroughSpeakersAndBluetooth: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_should_play_audio_through_speakers_and_bluetooth)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_should_play_audio_through_speakers_and_bluetooth)

			saveBoolean(key, value)
		}

	/**
	 * Whether the main activity should be refreshed or not.
	 */
	var shouldRefreshMainActivity: Boolean
		get()
		{
			val key = resources.getString(R.string.key_main_should_refresh_activity)
			val defaultValue = resources.getBoolean(R.bool.default_app_should_refresh_main_activity)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_should_refresh_activity)

			saveBoolean(key, value)
		}

	/**
	 * Whether the alarm should be repeated or not.
	 */
	var shouldRepeat: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_should_repeat)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_repeat_should_repeat)

			saveBoolean(key, value)
		}

	/**
	 * Whether the timer should be repeated or not.
	 */
	var shouldRepeatTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_repeat_should_repeat)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_repeat_should_repeat)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume should be restricted or not for an alarm.
	 */
	var shouldRestrictVolume: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_volume_should_restrict_volume)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_volume_should_restrict_volume)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume should be restricted or not for a timer.
	 */
	var shouldRestrictVolumeTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_volume_should_restrict_volume)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_volume_should_restrict_volume)

			saveBoolean(key, value)
		}

	/**
	 * Whether a new alarm shows a clock with hands rather than the digits.
	 */
	var shouldUseAnalogClock: Boolean
		get()
		{
			val key = resources.getString(R.string.key_analog_clock)
			val defaultValue = resources.getBoolean(R.bool.default_analog_clock)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_analog_clock), value)
		}

	/**
	 * Whether the light comes back on its own after a tap during a dawn.
	 */
	var shouldDawnRevealReturn: Boolean
		get()
		{
			val key = resources.getString(R.string.key_dawn_reveal_return)
			val defaultValue = resources.getBoolean(R.bool.default_dawn_reveal_return)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_dawn_reveal_return)

			saveBoolean(key, value)
		}

	/**
	 * Whether alarms dismissed, snoozed, missed, created and deleted are written to
	 * the statistics. Nothing already written is removed when it is turned off.
	 */
	var shouldRecordStatistics: Boolean
		get()
		{
			val key = resources.getString(R.string.key_record_statistics)
			val defaultValue = resources.getBoolean(R.bool.default_record_statistics)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_record_statistics), value)
		}

	/**
	 * Phone button that snoozes an alarm whose snooze options allow it
	 * (NacAlarmButton). Chosen in Settings, General (2.01).
	 */
	var snoozeButton: String
		get()
		{
			val key = resources.getString(R.string.key_snooze_button)

			return instance.getString(key, NacAlarmButton.DEFAULT_SNOOZE) ?: NacAlarmButton.DEFAULT_SNOOZE
		}
		set(value)
		{
			saveString(resources.getString(R.string.key_snooze_button), value)
		}

	/**
	 * Phone button that dismisses an alarm whose dismiss options allow it
	 * (NacAlarmButton). Chosen in Settings, General (2.01).
	 */
	var dismissButton: String
		get()
		{
			val key = resources.getString(R.string.key_dismiss_button)

			return instance.getString(key, NacAlarmButton.DEFAULT_DISMISS) ?: NacAlarmButton.DEFAULT_DISMISS
		}
		set(value)
		{
			saveString(resources.getString(R.string.key_dismiss_button), value)
		}

	/**
	 * Whether the old power button setting, a single switch for every alarm, has been
	 * turned into the dismiss button of each alarm (2.01).
	 */
	var eventPowerDismissToAlarms: Boolean
		get()
		{
			return instance.getBoolean(resources.getString(R.string.key_event_power_dismiss_to_alarms), false)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_event_power_dismiss_to_alarms), value)
		}

	/**
	 * Whether the buttons that snooze and dismiss, chosen for each alarm in 2.01, have
	 * been carried over to Settings, General, which decides for every alarm (2.02).
	 */
	var eventButtonsToGeneral: Boolean
		get()
		{
			return instance.getBoolean(resources.getString(R.string.key_event_buttons_to_general), false)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_event_buttons_to_general), value)
		}

	/**
	 * Whether a button that snoozes was ever chosen in Settings, General.
	 */
	val hasChosenSnoozeButton: Boolean
		get() = instance.contains(resources.getString(R.string.key_snooze_button))

	/**
	 * Whether a button that dismisses was ever chosen in Settings, General.
	 */
	val hasChosenDismissButton: Boolean
		get() = instance.contains(resources.getString(R.string.key_dismiss_button))

	/**
	 * Whether the power button, through the screen going out, dismisses the alarm.
	 *
	 * Only read once, to carry it over to the alarms (2.01). The power button is now
	 * one of the buttons chosen in Settings, General.
	 */
	var shouldPowerDismiss: Boolean
		get()
		{
			val key = resources.getString(R.string.key_power_dismiss)
			val defaultValue = resources.getBoolean(R.bool.default_power_dismiss)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_power_dismiss)

			saveBoolean(key, value)
		}

	/**
	 * How long a tap shows the screen as normal during a dawn. [Units: sec]
	 */
	var dawnRevealDuration: Int
		get()
		{
			val key = resources.getString(R.string.key_dawn_reveal_duration)
			val defaultValue = resources.getInteger(R.integer.default_dawn_reveal_duration)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_dawn_reveal_duration)

			saveInt(key, value)
		}

	/**
	 * How long a dawn preview runs. [Units: sec]
	 */
	var dawnPreviewDuration: Int
		get()
		{
			val key = resources.getString(R.string.key_dawn_preview_duration)
			val defaultValue = resources.getInteger(R.integer.default_dawn_preview_duration)

			// The allowed range has shrunk since this setting first shipped, so an
			// older value is brought back inside it
			return instance.getInt(key, defaultValue).coerceIn(5, 30)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_dawn_preview_duration)

			saveInt(key, value)
		}

	/**
	 * Image chosen for the dawn but not yet applied to the alarm.
	 *
	 * Android can close the options dialog while the file picker is in front, which
	 * would throw the choice away before OK is ever pressed. Keeping it here means
	 * the choice survives, and it is picked back up when the dialog is reopened.
	 */
	var dawnPendingImagePath: String
		get() = instance.getString("dawnPendingImagePath", "") ?: ""
		set(value)
		{
			saveString("dawnPendingImagePath", value)
		}

	/**
	 * Id of the alarm the pending dawn image belongs to.
	 */
	var dawnPendingImageAlarmId: Long
		get() = instance.getLong("dawnPendingImageAlarmId", 0L)
		set(value)
		{
			instance.edit { putLong("dawnPendingImageAlarmId", value) }
		}

	/**
	 * How far the slider of the alarm screen must go before it snoozes or dismisses,
	 * from 80 to 100. [Units: %]
	 */
	var swipeThreshold: Int
		get()
		{
			val key = resources.getString(R.string.key_swipe_threshold)
			val defaultValue = resources.getInteger(R.integer.default_swipe_threshold)

			return instance.getInt(key, defaultValue).coerceIn(80, 100)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_swipe_threshold), value.coerceIn(80, 100))
		}

	/**
	 * First hour at which the time can be spoken. [Units: h]
	 */
	var sayTimeFromHour: Int
		get()
		{
			val key = resources.getString(R.string.key_say_time_from_hour)
			val defaultValue = resources.getInteger(R.integer.default_say_time_from_hour)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_from_hour), value)
		}

	/**
	 * Hour after which the time is no longer spoken. [Units: h]
	 */
	var sayTimeToHour: Int
		get()
		{
			val key = resources.getString(R.string.key_say_time_to_hour)
			val defaultValue = resources.getInteger(R.integer.default_say_time_to_hour)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_to_hour), value)
		}

	/**
	 * Minute of [sayTimeFromHour] at which the time can start being spoken. [Units: min]
	 */
	var sayTimeFromMinute: Int
		get() = instance.getInt(resources.getString(R.string.key_say_time_from_minute), 0)
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_from_minute), value)
		}

	/**
	 * Minute of [sayTimeToHour] after which the time is no longer spoken. [Units: min]
	 */
	var sayTimeToMinute: Int
		get() = instance.getInt(resources.getString(R.string.key_say_time_to_minute), 0)
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_to_minute), value)
		}

	/**
	 * Language the time is spoken in. Empty means the language of the phone.
	 */
	var sayTimeLanguage: String
		get()
		{
			val key = resources.getString(R.string.key_say_time_language)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			saveString(resources.getString(R.string.key_say_time_language), value)
		}

	/**
	 * Which clock the spoken time uses. 0 follows the phone, 1 is a 12 hour clock and
	 * 2 is a 24 hour clock.
	 */
	var sayTimeFormat: Int
		get()
		{
			val key = resources.getString(R.string.key_say_time_format)
			val defaultValue = resources.getInteger(R.integer.default_say_time_format)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_format), value)
		}

	/**
	 * Whether the time is said at all. The switch beside Speak the time in the
	 * settings list answers for this, and it holds back the shake and the hand
	 * together without touching either of their own switches.
	 *
	 * Before 1.88 that switch wrote the shake itself. When nothing has been saved
	 * yet, it reads as on whenever one of the two is on, so an update changes
	 * nothing that was working.
	 */
	var shouldSayTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_say_time_enabled)

			return if (instance.contains(key))
			{
				instance.getBoolean(key, true)
			}
			else
			{
				shouldShakeToSayTime || shouldWaveToSayTime
			}
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_say_time_enabled), value)

			// Turning it on is asking for it now, so a stop from the notification ends
			if (value)
			{
				sayTimePausedUntil = 0L
			}
		}

	/**
	 * Until when the spoken time was stopped from its notification, 0 when it was
	 * not. [Units: ms since epoch]
	 */
	var sayTimePausedUntil: Long
		get() = instance.getLong(resources.getString(R.string.key_say_time_paused_until), 0L)
		set(value)
		{
			saveLong(resources.getString(R.string.key_say_time_paused_until), value)
		}

	/**
	 * Whether the spoken time was stopped from its notification and the hours have not
	 * started again since.
	 */
	fun isSayTimePaused(): Boolean
	{
		return sayTimePausedUntil > System.currentTimeMillis()
	}

	/**
	 * Whether shaking the phone says the time out loud.
	 */
	var shouldShakeToSayTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_shake_to_say_time)
			val defaultValue = resources.getBoolean(R.bool.default_shake_to_say_time)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_shake_to_say_time), value)
		}

	/**
	 * Whether the alarm list rearranges itself, which brings whatever rings next to
	 * the top.
	 */
	var shouldAutoSortAlarms: Boolean
		get()
		{
			val key = resources.getString(R.string.key_tweak_auto_sort_alarms)
			val defaultValue = resources.getBoolean(R.bool.default_auto_sort_alarms)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_tweak_auto_sort_alarms), value)
		}

	/**
	 * Whether passing a hand over the phone says the time out loud.
	 */
	var shouldWaveToSayTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_wave_to_say_time)
			val defaultValue = resources.getBoolean(R.bool.default_wave_to_say_time)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_wave_to_say_time), value)
		}

	/**
	 * Volume of the spoken time, in percent of the loudest the phone can go, 1 to 100.
	 * 0 follows the volume of the alarms (2.13, by 1 %).
	 *
	 * Until 2.12 it went by 10 %, under another key, as 0 to 10. A value saved then is
	 * read once and carried over, times 10.
	 */
	var sayTimeVolume: Int
		get()
		{
			val key = resources.getString(R.string.key_say_time_volume_percent)

			if (instance.contains(key))
			{
				return instance.getInt(key, DEFAULT_SAY_TIME_VOLUME).coerceIn(0, 100)
			}

			val oldKey = resources.getString(R.string.key_say_time_volume)

			return if (instance.contains(oldKey))
			{
				(instance.getInt(oldKey, 3) * 10).coerceIn(0, 100)
			}
			else
			{
				DEFAULT_SAY_TIME_VOLUME
			}
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_say_time_volume_percent), value.coerceIn(0, 100))
		}

	/**
	 * How many hands passing over make the gesture, as a place in the list that is
	 * shown.
	 */
	var wavePasses: Int
		get()
		{
			val key = resources.getString(R.string.key_wave_passes)
			val defaultValue = resources.getInteger(R.integer.default_wave_passes)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_wave_passes), value)
		}

	/**
	 * How hard the phone has to be shaken, as a place in the list that is shown. 0
	 * asks for the least and 3 for the most.
	 */
	var shakeSensitivity: Int
		get()
		{
			val key = resources.getString(R.string.key_shake_sensitivity)
			val defaultValue = resources.getInteger(R.integer.default_shake_sensitivity)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_shake_sensitivity), value)
		}

	/**
	 * Whether the clock is inside the hours during which the time can be spoken.
	 */
	fun isWithinSayTimeHours(
		calendar: java.util.Calendar = java.util.Calendar.getInstance()
	): Boolean
	{
		// Everything is counted in minutes of the day, so that a span can start or
		// end at any minute and not only on the hour
		val from = sayTimeFromHour * 60 + sayTimeFromMinute
		val to = sayTimeToHour * 60 + sayTimeToMinute
		val hour = calendar[java.util.Calendar.HOUR_OF_DAY] * 60 +
			calendar[java.util.Calendar.MINUTE]

		// The whole day
		if (from == to)
		{
			return true
		}

		// A normal span, such as 3 to 10
		return if (from < to)
		{
			(hour >= from) && (hour < to)
		}
		// A span that runs over midnight, such as 22 to 7
		else
		{
			(hour >= from) || (hour < to)
		}
	}

	/**
	 * Last custom color chosen for a dawn, so that the custom swatch keeps showing
	 * it after a preset color is picked. 0 means that none has been chosen yet.
	 */
	var dawnCustomColor: Int
		get() = instance.getInt("dawnCustomColor", 0)
		set(value)
		{
			instance.edit { putInt("dawnCustomColor", value) }
		}

	/**
	 * Id of the alarm whose dawn options dialog should be opened again.
	 *
	 * Android closes that dialog while the image picker is in front, so the
	 * dialog is brought back once the picker is done.
	 */
	var dawnReopenAlarmId: Long
		get() = instance.getLong("dawnReopenAlarmId", 0L)
		set(value)
		{
			instance.edit { putLong("dawnReopenAlarmId", value) }
		}

	/**
	 * Whether to save battery when an alarm is active or not.
	 */
	var shouldSaveBatteryInAlarmScreen: Boolean
		get()
		{
			val key = resources.getString(R.string.key_alarm_screen_battery_saver)
			val defaultValue = resources.getBoolean(R.bool.default_alarm_screen_battery_saver)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_alarm_screen_battery_saver)

			saveBoolean(key, value)
		}

	/**
	 * Whether to say the alarm name or not via text-to-speech.
	 */
	var shouldSayAlarmName: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_tts_should_say_alarm_name)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_tts_should_say_alarm_name)

			saveBoolean(key, value)
		}

	/**
	 * Whether to say the timer name or not via text-to-speech.
	 */
	var shouldSayAlarmNameTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_tts_should_say_alarm_name)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_tts_should_say_alarm_name)

			saveBoolean(key, value)
		}

	/**
	 * Whether to say the current time or not via text-to-speech for an alarm.
	 */
	var shouldSayCurrentTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_tts_should_say_current_time)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_tts_should_say_current_time)

			saveBoolean(key, value)
		}

	/**
	 * Whether to say the current time or not via text-to-speech for a timer.
	 */
	var shouldSayCurrentTimeTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_tts_should_say_current_time)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_tts_should_say_current_time)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show the alarm name or not.
	 */
	var shouldShowAlarmName: Boolean
		get()
		{
			val key = resources.getString(R.string.key_alarm_screen_show_alarm_name)
			val defaultValue = resources.getBoolean(R.bool.default_alarm_screen_show_alarm_name)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_alarm_screen_show_alarm_name)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show the labels underneath buttons in an alarm/timer card or not.
	 */
	val shouldShowCardButtonLabels: Boolean
		get()
		{
			val key = resources.getString(R.string.key_style_should_show_card_button_labels)
			val defaultValue = resources.getBoolean(R.bool.default_style_should_show_card_button_labels)

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Whether to show the current date and time or not.
	 */
	var shouldShowCurrentDateAndTime: Boolean
		get()
		{
			val key = resources.getString(R.string.key_alarm_screen_show_current_date_and_time)
			val defaultValue = resources.getBoolean(R.bool.default_alarm_screen_show_current_date_and_time)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_alarm_screen_show_current_date_and_time)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show a notification for dismiss early or not.
	 */
	var shouldShowDismissEarlyNotification: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_show_dismiss_early_notification)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_show_dismiss_early_notification)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show or hide the flashlight button.
	 */
	val shouldShowFlashlightButton: Boolean
		get()
		{
			val key = resources.getString(R.string.key_show_hide_flashlight_button)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Whether the Manage NFC Tags preference should be visible or not.
	 */
	var shouldShowManageNfcTagsPreference: Boolean
		get()
		{
			val key = resources.getString(R.string.key_settings_should_show_manage_nfc_tags)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_settings_should_show_manage_nfc_tags)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show music information or not.
	 */
	var shouldShowMusicInfo: Boolean
		get()
		{
			val key = resources.getString(R.string.key_alarm_screen_show_music_info)
			val defaultValue = resources.getBoolean(R.bool.default_alarm_screen_show_music_info)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_alarm_screen_show_music_info)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show or hide the NFC button.
	 */
	val shouldShowNfcButton: Boolean
		get()
		{
			val key = resources.getString(R.string.key_show_hide_nfc_button)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Whether to show the onboarding screen not.
	 */
	var shouldShowOnboardingScreen: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_should_show_onboarding_screen)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_should_show_onboarding_screen)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show a reminder or not.
	 */
	var shouldShowReminder: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_should_show_reminder)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_should_show_reminder)

			saveBoolean(key, value)
		}

	/**
	 * Whether to show or hide the vibrate button.
	 */
	val shouldShowVibrateButton: Boolean
		get()
		{
			val key = resources.getString(R.string.key_show_hide_vibrate_button)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Whether to shuffle media or not for an alarm.
	 */
	var shouldShuffleMedia: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_media_should_shuffle_media)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_media_should_shuffle_media)

			saveBoolean(key, value)
		}

	/**
	 * Whether to shuffle media or not for a timer.
	 */
	var shouldShuffleMediaTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_media_should_shuffle_media)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_media_should_shuffle_media)

			saveBoolean(key, value)
		}

	/**
	 * Whether the flashlight should be used or not for an alarm.
	 */
	var shouldUseFlashlight: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_should_use_flashlight)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_flashlight_should_use_flashlight)

			saveBoolean(key, value)
		}

	/**
	 * Whether the flashlight should be used or not for a timer.
	 */
	var shouldUseFlashlightTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_should_use_flashlight)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_flashlight_should_use_flashlight)

			saveBoolean(key, value)
		}

	/**
	 * Whether the dawn simulation should be used or not for an alarm.
	 */
	var shouldUseDawn: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_should_use_dawn)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_should_use_dawn)

			saveBoolean(key, value)
		}

	/**
	 * Duration of the dawn simulation for an alarm. [Units: min]
	 */
	var dawnDuration: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_duration)
			val defaultValue = 8

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_duration)

			saveInt(key, value)
		}

	/**
	 * Color of the dawn simulation for an alarm. [Units: ARGB]
	 */
	var dawnColor: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_color)
			val defaultValue = -13942

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_color)

			saveInt(key, value)
		}

	/**
	 * Whether an image should be used instead of a color for the dawn simulation.
	 */
	var shouldUseDawnImage: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_should_use_image)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_should_use_image)

			saveBoolean(key, value)
		}

	/**
	 * URI of the image used for the dawn simulation.
	 */
	var dawnImagePath: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_image_path)
			val defaultValue = ""

			return instance.getString(key, defaultValue) ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_image_path)

			saveString(key, value)
		}

	/**
	 * How long before the alarm the flashlight fades in during the dawn. [Units: min]
	 */
	var dawnFlashlightLead: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_flashlight_lead)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dawn_flashlight_lead)

			saveInt(key, value)
		}

	/**
	 * Whether to show or hide the dawn button.
	 */
	val shouldShowDawnButton: Boolean
		get()
		{
			val key = resources.getString(R.string.key_show_hide_dawn_button)
			val defaultValue = resources.getBoolean(R.bool.default_show_hide_dawn_button)

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Wake-up phrases of one's own, one per line (2.07).
	 */
	var myPhrases: String
		get() = instance.getString(resources.getString(R.string.key_my_phrases), "") ?: ""
		set(value)
		{
			saveString(resources.getString(R.string.key_my_phrases), value)
		}

	/**
	 * Whether only the phrases of one's own are shown, not the ones of the app (2.07).
	 */
	val shouldUseOnlyMyPhrases: Boolean
		get() = instance.getBoolean(resources.getString(R.string.key_only_my_phrases), false)

	/**
	 * Whether to use the simple alarm screen, the original one: the name of the alarm
	 * and two buttons (2.05).
	 */
	var shouldUseSimpleAlarmScreen: Boolean
		get()
		{
			val key = resources.getString(R.string.key_use_simple_alarm_screen)
			val defaultValue = resources.getBoolean(R.bool.default_use_simple_alarm_screen)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			saveBoolean(resources.getString(R.string.key_use_simple_alarm_screen), value)
		}

	/**
	 * Whether snooze and dismiss slide (true) or are tapped (false) on the full alarm
	 * screen.
	 */
	var shouldUseNewAlarmScreen: Boolean
		get()
		{
			val key = resources.getString(R.string.key_use_new_alarm_screen)
			val defaultValue = resources.getBoolean(R.bool.default_use_new_alarm_screen)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_use_new_alarm_screen)

			saveBoolean(key, value)
		}

	/**
	 * Whether NFC is required or not for an alarm.
	 */
	var shouldUseNfc: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_should_use_nfc)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_should_use_nfc)

			saveBoolean(key, value)
		}

	/**
	 * Whether NFC is required or not for a timer.
	 */
	var shouldUseNfcTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_nfc_should_use_nfc)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_nfc_should_use_nfc)

			saveBoolean(key, value)
		}

	/**
	 * Whether NFC tags should be dismissed in order or not.
	 */
	var shouldUseNfcTagDismissOrder: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_should_use_nfc_tag_dismiss_order)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_nfc_should_use_nfc_tag_dismiss_order)

			saveBoolean(key, value)
		}

	/**
	 * Whether NFC tags should be dismissed in order or not for a timer.
	 */
	var shouldUseNfcTagDismissOrderTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_nfc_should_use_nfc_tag_dismiss_order)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_nfc_should_use_nfc_tag_dismiss_order)

			saveBoolean(key, value)
		}

	/**
	 * Whether to use text-to-speech for the reminder or not.
	 */
	var shouldUseTtsForReminder: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_should_use_tts_for_reminder)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_should_use_tts_for_reminder)

			saveBoolean(key, value)
		}

	/**
	 * Whether the alarm should vibrate the device or not.
	 */
	var shouldVibrate: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_should_vibrate)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_should_vibrate)

			saveBoolean(key, value)
		}

	/**
	 * Whether the timer should vibrate the device or not.
	 */
	var shouldVibrateTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_should_vibrate)
			val defaultValue = true

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_should_vibrate)

			saveBoolean(key, value)
		}

	/**
	 * Whether to vibrate using a pattern or not for an alarm.
	 */
	var shouldVibratePattern: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_should_vibrate_pattern)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_should_vibrate_pattern)

			saveBoolean(key, value)
		}

	/**
	 * Whether to vibrate using a pattern or not for a timer.
	 */
	var shouldVibratePatternTimer: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_should_vibrate_pattern)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_should_vibrate_pattern)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume dismiss is enabled or not.
	 */
	var shouldVolumeDismiss: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_use_volume_dismiss)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_dismiss_should_use_volume_dismiss)

			saveBoolean(key, value)
		}

	/**
	 * Whether volume snooze is enabled or not.
	 */
	var shouldVolumeSnooze: Boolean
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_use_volume_snooze)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_should_use_volume_snooze)

			saveBoolean(key, value)
		}

	/**
	 * Whether to write to the log or not.
	 */
	val shouldWriteToLog: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_should_write_to_log)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}

	/**
	 * Skip next alarm color.
	 */
	val skipNextAlarmColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_skip_next_alarm)
			val defaultValue = resources.getInteger(R.integer.default_skip_next_alarm_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Snooze duration.
	 */
	var snoozeDuration: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_duration)
			val defaultValue = 300

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_snooze_duration)

			saveInt(key, value)
		}

	/**
	 * Old index for the snooze duration.
	 */
	private val oldSnoozeDurationIndex: Int
		get()
		{
			val key = resources.getString(R.string.old_snooze_duration_key)
			val defaultValue = resources.getInteger(R.integer.default_snooze_duration_index)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Snooze duration.
	 *
	 * This is used when updating database versions.
	 */
	val oldSnoozeDurationValue: Int
		get()
		{
			return if (oldSnoozeDurationIndex < 9)
			{
				oldSnoozeDurationIndex + 1
			}
			else
			{
				(oldSnoozeDurationIndex - 7) * 5
			}
		}

	/**
	 * Value indicating which day to start on.
	 */
	val startWeekOn: Int
		get()
		{
			val key = resources.getString(R.string.key_style_start_week_on)
			val defaultValue = resources.getInteger(R.integer.default_start_week_on_index)
			val index = instance.getInt(key, defaultValue)

			// Automatic. Follow the first day of the week of the phone, not of the
			// language chosen for the app (2.01)
			return if (index == START_WEEK_ON_AUTOMATIC)
			{
				if (getPhoneFirstDayOfWeek(appContext) == Calendar.MONDAY) 1 else 0
			}
			else
			{
				index
			}
		}

	/**
	 * Theme color.
	 */
	val themeColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_theme)
			val defaultValue = resources.getInteger(R.integer.default_theme_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * Time color.
	 */
	val timeColor: Int
		get()
		{
			val key = resources.getString(R.string.key_color_time)
			val defaultValue = resources.getInteger(R.integer.default_time_color)

			return instance.getInt(key, defaultValue)
		}

	/**
	 * The time to start showing a reminder.
	 */
	var timeToShowReminder: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_time_to_show_reminder)
			val defaultValue = 5

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_reminder_time_to_show_reminder)

			saveInt(key, value)
		}

	/**
	 * How long the voice waits after the alarm starts, so that the music wakes you
	 * first. [Units: sec]
	 */
	var ttsDelay: Int
		get()
		{
			val key = resources.getString(R.string.key_tts_delay)
			val defaultValue = resources.getInteger(R.integer.default_tts_delay)

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			saveInt(resources.getString(R.string.key_tts_delay), value)
		}

	/**
	 * How often to speak via text-to-speech. [Units: min]
	 */
	var ttsFrequency: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_tts_speak_frequency)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_tts_speak_frequency)

			saveInt(key, value)
		}

	/**
	 * Text-to-speech frequency at which it will speak for a timer.
	 */
	var ttsFrequencyTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_tts_speak_frequency)
			val defaultValue = 0

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_tts_speak_frequency)

			saveInt(key, value)
		}

	/**
	 * Text-to-speech speech rate for an alarm.
	 */
	var ttsSpeechRate: Float
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_tts_speech_rate)

			return instance.getFloat(key, 0.7f)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_tts_speech_rate)

			saveFloat(key, value)
		}

	/**
	 * Text-to-speech speech rate for a timer.
	 */
	var ttsSpeechRateTimer: Float
		get()
		{
			val key = resources.getString(R.string.key_default_timer_tts_speech_rate)

			return instance.getFloat(key, 0.7f)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_tts_speech_rate)

			saveFloat(key, value)
		}

	/**
	 * Text-to-speech voice name for an alarm.
	 */
	var ttsVoice: String
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_tts_voice)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_tts_voice)

			saveString(key, value)
		}

	/**
	 * Text-to-speech voice name for a timer.
	 */
	var ttsVoiceTimer: String
		get()
		{
			val key = resources.getString(R.string.key_default_timer_tts_voice)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_tts_voice)

			saveString(key, value)
		}

	/**
	 * Duration to vibrate the device for, for an alarm.
	 */
	var vibrateDuration: Long
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_duration)
			val defaultValue = 500L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_duration)

			saveLong(key, value)
		}

	/**
	 * Duration to vibrate the device for, for a timer.
	 */
	var vibrateDurationTimer: Long
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_duration)
			val defaultValue = 500L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_duration)

			saveLong(key, value)
		}

	/**
	 * Number of times to repeat the vibration for an alarm.
	 */
	var vibrateRepeatPattern: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_repeat_pattern)
			val defaultValue = 3

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_repeat_pattern)

			saveInt(key, value)
		}

	/**
	 * Number of times to repeat the vibration for a timer.
	 */
	var vibrateRepeatPatternTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_repeat_pattern)
			val defaultValue = 3

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_repeat_pattern)

			saveInt(key, value)
		}

	/**
	 * Amount of time to wait in between vibrations for an alarm.
	 */
	var vibrateWaitTime: Long
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_wait_time)
			val defaultValue = 1000L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_wait_time)

			saveLong(key, value)
		}

	/**
	 * Amount of time to wait in between vibrations for a timer.
	 */
	var vibrateWaitTimeTimer: Long
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_wait_time)
			val defaultValue = 1000L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_wait_time)

			saveLong(key, value)
		}

	/**
	 * Amount of time to wait after the vibration has been repeated the set number of
	 * times for an alarm.
	 */
	var vibrateWaitTimeAfterPattern: Long
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_wait_time_after_pattern)
			val defaultValue = 2000L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_vibrate_wait_time_after_pattern)

			saveLong(key, value)
		}

	/**
	 * Amount of time to wait after the vibration has been repeated the set number of
	 * times for a timer.
	 */
	var vibrateWaitTimeAfterPatternTimer: Long
		get()
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_wait_time_after_pattern)
			val defaultValue = 2000L

			return instance.getLong(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_vibrate_wait_time_after_pattern)

			saveLong(key, value)
		}

	/**
	 * Alarm volume level.
	 */
	var volume: Int
		get()
		{
			val key = resources.getString(R.string.key_default_alarm_volume)
			val defaultValue = 30

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_alarm_volume)

			saveInt(key, value)
		}

	/**
	 * Timer volume level.
	 */
	var volumeTimer: Int
		get()
		{
			val key = resources.getString(R.string.key_default_timer_volume)

			// The same as the alarms. It was 75, loud for a timer, which is most often
			// started in a quiet room (1.94)
			val defaultValue = 40

			return instance.getInt(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_default_timer_volume)

			saveInt(key, value)
		}

	/**
	 * Whether the app was supported or not.
	 */
	var wasAppSupported: Boolean
		get()
		{
			val key = resources.getString(R.string.key_app_supported)
			val defaultValue = resources.getBoolean(R.bool.default_was_app_supported)

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_app_supported)

			saveBoolean(key, value)
		}

	/**
	 * Whether the permission to ignore battery optimization was requested.
	 */
	var wasIgnoreBatteryOptimizationPermissionRequested: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_ignore_battery_optimization_requested)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_ignore_battery_optimization_requested)

			saveBoolean(key, value)
		}

	/**
	 * Whether NFC was just scanned to dismiss an alarm or timer.
	 */
	var wasNfcJustScannedToDismiss: Boolean
		get()
		{
			val key = resources.getString(R.string.key_main_was_nfc_just_scanned_to_dismiss)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_main_was_nfc_just_scanned_to_dismiss)

			saveBoolean(key, value)
		}

	/**
	 * Whether the POST_NOTIFICATIONS permission was requested.
	 */
	var wasPostNotificationsPermissionRequested: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_post_notifications_requested)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_post_notifications_requested)

			saveBoolean(key, value)
		}

	/**
	 * Whether the USE_FULL_SCREEN_INTENT permission was ever in hand.
	 *
	 * A permission that was granted and is gone was taken away by something, an
	 * app update most of the time. That is not the same as one that was never
	 * given, and it is worth offering again.
	 */
	var wasFullScreenIntentPermissionGranted: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_granted)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_granted)

			saveBoolean(key, value)
		}

	/**
	 * The app version at which the USE_FULL_SCREEN_INTENT permission was last asked
	 * for.
	 *
	 * An app update is what takes this one away, so a version that has not yet asked
	 * should ask, however many times it was asked for before.
	 */
	var fullScreenIntentPermissionAskedAtVersion: String
		get()
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_asked_at_version)

			return instance.getString(key, "") ?: ""
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_asked_at_version)

			saveString(key, value)
		}

	/**
	 * Whether the SYSTEM_ALERT_WINDOW permission was ever in hand. See above.
	 */
	var wasSystemAlertWindowPermissionGranted: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_system_alert_window_granted)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_system_alert_window_granted)

			saveBoolean(key, value)
		}

	/**
	 * Whether the USE_FULL_SCREEN_INTENT permission was requested.
	 */
	var wasFullScreenIntentPermissionRequested: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_requested)

			return instance.getBoolean(key, false)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_full_screen_intent_requested)

			saveBoolean(key, value)
		}

	/**
	 * Whether the SYSTEM_ALERT_WINDOW permission was requested.
	 */
	var wasSystemAlertWindowPermissionRequested: Boolean
		get()
		{
			val key = resources.getString(R.string.key_permission_system_alert_window_requested)
			val defaultValue = false

			return instance.getBoolean(key, defaultValue)
		}
		set(value)
		{
			val key = resources.getString(R.string.key_permission_system_alert_window_requested)

			saveBoolean(key, value)
		}

	/**
	 * Copy shared preferences from a CSV file.
	 */
	fun copyFromCsv(context: Context, file: File)
	{
		// List of keys to ignore
		val ignoreList = getCsvKeysToIgnore()

		// Open the file for reading
		context.openFileInput(file.name).use { input ->

			// Change the reading mechanism so it reads a character stream instead of a
			// byte stream
			BufferedReader(InputStreamReader(input)).use { reader ->

				// Files written since 2.10 start with a line that says their text values
				// are escaped. Older files are read the way they always were
				var isEscaped = false

				// Read each line in the file
				while (true)
				{
					// Read the key, type, and value from the line. The value is whatever
					// comes after the second comma, commas included
					val line = reader.readLine() ?: break
					val parts = line.split(",", limit = 3)

					if (parts.size < 3)
					{
						continue
					}

					val (key, type, rawValue) = parts

					if (key == CSV_FORMAT_KEY)
					{
						isEscaped = true
						continue
					}

					val value = if (isEscaped) unescapeCsv(rawValue) else rawValue

					// Check if key is in the ignore list
					if (key in ignoreList)
					{
						continue
					}

					// Save the value, depending on the type
					when (type)
					{
						"Boolean" -> saveBoolean(key, value.toBoolean())
						"Float"   -> {}
						"Int"     -> saveInt(key, value.toInt())
						"Long"    -> {}
						"String"  -> saveString(key, value)
						else      -> continue
					}

				}

			}

		}
	}

	/**
	 * Put every setting back to what it was out of the box.
	 *
	 * Alarms are not touched: they live in the database, not here. Neither are the
	 * keys the app keeps for itself — first run, the measured card heights, the
	 * version it last saw — otherwise the app would behave as if it had just been
	 * installed and would build a new alarm.
	 */
	fun resetToDefaults()
	{
		val keysToKeep = getCsvKeysToIgnore()
		val kept = instance.all.filterKeys { keysToKeep.contains(it) }

		instance.edit {

			clear()

			// Put the housekeeping back
			kept.forEach { (key, value) ->

				when (value)
				{
					is Boolean -> putBoolean(key, value)
					is Float   -> putFloat(key, value)
					is Int     -> putInt(key, value)
					is Long    -> putLong(key, value)
					is String  -> putString(key, value)
					else       -> {}
				}

			}

		}
	}

	/**
	 * Get a list of keys to ignore when reading to/writing from a CSV file.
	 */
	private fun getCsvKeysToIgnore(): List<String>
	{
		return listOf(
			resources.getString(R.string.key_app_first_run),
			resources.getString(R.string.key_app_should_show_onboarding_screen),
			resources.getString(R.string.key_app_should_write_to_log),
			resources.getString(R.string.key_main_should_refresh_activity),
			resources.getString(R.string.key_main_card_height_collapsed),
			resources.getString(R.string.key_main_card_height_collapsed_dismiss),
			resources.getString(R.string.key_main_card_height_expanded),
			resources.getString(R.string.key_main_card_is_measured),
			resources.getString(R.string.key_media_current_playing_alarm),
			resources.getString(R.string.key_media_is_selected_for_alarm_not_available),
			resources.getString(R.string.key_main_delay_showing_whats_new_dialog_counter),
			resources.getString(R.string.key_permission_ignore_battery_optimization_requested),
			resources.getString(R.string.key_permission_post_notifications_requested),
			resources.getString(R.string.key_permission_schedule_exact_alarm_requested),
			resources.getString(R.string.key_app_previous_version),
			resources.getString(R.string.sys_previous_volume),
			resources.getString(R.string.old_auto_dismiss_key),
			resources.getString(R.string.old_max_snooze_key),
			resources.getString(R.string.old_snooze_duration_key),
		)
	}

	/**
	 * Run the event to fix any auto dismiss, auto snooze, or snooze duration values
	 * that are set to 0 in alarms.
	 */
	suspend fun runEventFixZeroAutoDismissAndSnooze(
		allAlarms: List<NacAlarm>,
		onAlarmChanged: suspend (NacAlarm) -> Unit = {})
	{
		// Set the default values
		val defaultAutoDismissTime = 900
		val defaultAutoSnoozeTime = 300
		val defaultSnoozeDuration = 300

		// Auto dismiss for the shared preferences
		if (autoDismissTime == 0)
		{
			autoDismissTime = defaultAutoDismissTime
		}

		// Auto snooze for the shared preferences
		if (autoSnoozeTime == 0)
		{
			autoSnoozeTime = defaultAutoSnoozeTime
		}

		// Snooze duration for the shared preferences
		if (snoozeDuration == 0)
		{
			snoozeDuration = defaultSnoozeDuration
		}

		// Iterate over each alarm that has the auto dismiss, auto snooze, or snooze
		// duration set incorrectly
		allAlarms
			.filter {
				(it.autoDismissTime == 0) || (it.autoSnoozeTime == 0) || (it.snoozeDuration == 0)
			}
			.forEach { alarm ->

				// Auto dismiss
				if (alarm.autoDismissTime == 0)
				{
					alarm.autoDismissTime = defaultAutoDismissTime
				}

				// Auto snooze
				if (alarm.autoSnoozeTime == 0)
				{
					alarm.autoSnoozeTime = defaultAutoSnoozeTime
				}

				// Snooze duration
				if (alarm.snoozeDuration == 0)
				{
					alarm.snoozeDuration = defaultSnoozeDuration
				}

				// Call the listener when the alarm is changed
				onAlarmChanged(alarm)

			}

		// Mark the event as completed
		eventFixZeroAutoDismissAndSnooze = true
	}

	/**
	 * Write down the audio source of every alarm by a name that does not change with
	 * the language.
	 *
	 * Until 1.83 an alarm held the words shown on screen. Change the language and
	 * those words matched nothing, so the sound fell back to the alarm channel and a
	 * choice of anything else was lost. What is read here is taken to be written in
	 * the language in use, which is true of anyone who has not changed it since
	 * choosing; anything unknown falls to the first source, as it already did.
	 */
	suspend fun runEventAudioSourceToKeys(
		allAlarms: List<NacAlarm>,
		onAlarmChanged: suspend (NacAlarm) -> Unit = {})
	{
		val keys = resources.getStringArray(R.array.audio_source_keys)

		fun toKey(value: String): String = audioSourceToKey(value)

		// The default that new alarms and timers are built from
		audioSource = toKey(audioSource)
		audioSourceTimer = toKey(audioSourceTimer)

		// Every alarm that still holds words
		allAlarms
			.filter { it.audioSource.isNotEmpty() && !keys.contains(it.audioSource) }
			.forEach { alarm ->

				alarm.audioSource = toKey(alarm.audioSource)

				// Call the listener when the alarm is changed
				onAlarmChanged(alarm)

			}

		// Mark the event as completed
		eventAudioSourceToKeys = true
	}

	/**
	 * The name that does not change with the language, for an audio source that may
	 * still be held as the words shown on screen.
	 *
	 * The words are looked up in the language in use. Words of another language, or
	 * nothing at all, fall back on the first source, which is the alarm one.
	 */
	private fun audioSourceToKey(value: String): String
	{
		val keys = resources.getStringArray(R.array.audio_source_keys)
		val words = resources.getStringArray(R.array.audio_sources)

		// Already written down
		if (keys.contains(value))
		{
			return value
		}

		// The words of the language in use
		val index = words.indexOf(value)

		return if (index >= 0) keys[index] else keys[0]
	}

	/**
	 * Write down the audio source of every timer as a name that does not change with
	 * the language, as 1.83 did for the alarms.
	 */
	suspend fun runEventTimerAudioSourceToKeys(
		allTimers: List<NacTimer>,
		onTimerChanged: suspend (NacTimer) -> Unit = {})
	{
		val keys = resources.getStringArray(R.array.audio_source_keys)

		// The default that new timers are built from. The event of the alarms already
		// did it, but it costs nothing to be sure
		audioSourceTimer = audioSourceToKey(audioSourceTimer)

		// Every timer that still holds words
		allTimers
			.filter { it.audioSource.isNotEmpty() && !keys.contains(it.audioSource) }
			.forEach { timer ->

				timer.audioSource = audioSourceToKey(timer.audioSource)

				// Call the listener when the timer is changed
				onTimerChanged(timer)

			}

		// Mark the event as completed
		eventTimerAudioSourceToKeys = true
	}

	/**
	 * Write down where each alarm sits in the list.
	 *
	 * Until 1.87 the list was always rearranged, so nothing had to be remembered. It
	 * can now be left alone, and an order that is left alone has to start somewhere:
	 * the one on screen. Every alarm is therefore given the rank it holds under the
	 * old sort, and nothing moves on the day the setting appears.
	 */
	suspend fun runEventFreezeAlarmOrder(
		allAlarms: List<NacAlarm>,
		onAlarmChanged: suspend (NacAlarm) -> Unit = {})
	{
		allAlarms.sorted()
			.forEachIndexed { index, alarm ->

				// Already where it should be
				if (alarm.sortOrder == index)
				{
					return@forEachIndexed
				}

				alarm.sortOrder = index

				// Call the listener when the alarm is changed
				onAlarmChanged(alarm)

			}

		// Mark the event as completed
		eventFreezeAlarmOrder = true
	}

	/**
	 * Move the shake sensitivity onto the list that gained two finer entries and lost
	 * the firmest one.
	 *
	 * Until 1.85 the list read soft, normal, firm. It now reads slightest, slight,
	 * light, normal, so what was chosen has to be carried over or a setting would
	 * quietly come to mean something else. The firmest is gone, so whoever had it
	 * lands on the firmest that is left.
	 */
	fun runEventShakeSensitivityFiner()
	{
		val key = resources.getString(R.string.key_shake_sensitivity)

		// Nothing was ever chosen, so the new default already says what it should
		if (instance.contains(key))
		{
			// 0 soft -> 2 light, 1 normal -> 3 normal, 2 firm -> 3 normal
			shakeSensitivity = when (instance.getInt(key, 0))
			{
				0 -> 2
				else -> 3
			}
		}

		// Mark the event as completed
		eventShakeSensitivityFiner = true
	}

	/**
	 * Save a boolean to the shared preference.
	 */
	private fun saveBoolean(key: String, value: Boolean)
	{
		instance.edit {
			putBoolean(key, value)
		}
	}

	/**
	 * Save a float to the shared preference.
	 */
	private fun saveFloat(key: String, value: Float)
	{
		instance.edit {
			putFloat(key, value)
		}
	}

	/**
	 * Save an int to the shared preference.
	 */
	private fun saveInt(key: String, value: Int)
	{
		instance.edit {
			putInt(key, value)
		}
	}

	/**
	 * Save an long to the shared preference.
	 */
	private fun saveLong(key: String, value: Long)
	{
		instance.edit {
			putLong(key, value)
		}
	}

	/**
	 * Find and save the next alarm.
	 */
	fun saveNextAlarm(allAlarms: List<NacAlarm>, snoozeCal: Calendar? = null)
	{
		// Find the next alarm
		val nextAlarm = NacCalendar.getNextAlarm(allAlarms)

		// Determine the time in milliseconds to use
		val millis = nextAlarm?.calendar?.let {

			// Snooze time
			if (snoozeCal?.before(it) == true)
			{
				snoozeCal.timeInMillis
			}
			// Next calendar time
			else
			{
				it.timeInMillis
			}

		} ?: 0L

		// Save the next alarm information
		appNextAlarmTimezoneId = Calendar.getInstance().timeZone.id
		appNextAlarmTimeMillis = millis
	}

	/**
	 * Save a string to the shared preference.
	 */
	private fun saveString(key: String, value: String?)
	{
		instance.edit {
			putString(key, value)
		}
	}

	/**
	 * Write the all the shared preferences to a CSV file.
	 */
	/**
	 * Escape a text so that it fits on one line: a backslash is doubled and a line
	 * break becomes a backslash followed by "n".
	 */
	private fun escapeCsv(text: String): String
	{
		return text.replace("\\", "\\\\")
			.replace("\r", "")
			.replace("\n", "\\n")
	}

	/**
	 * Undo [escapeCsv].
	 */
	private fun unescapeCsv(text: String): String
	{
		val builder = StringBuilder()
		var i = 0

		while (i < text.length)
		{
			val c = text[i]

			if ((c == '\\') && (i + 1 < text.length))
			{
				when (text[i + 1])
				{
					'n'  -> builder.append('\n')
					else -> builder.append(text[i + 1])
				}

				i += 2
			}
			else
			{
				builder.append(c)
				i++
			}
		}

		return builder.toString()
	}

	fun writeToCsv(context: Context, file: File)
	{
		// List of keys to ignore
		val ignoreList = getCsvKeysToIgnore()

		// Save shared preferences
		context.openFileOutput(file.name, Context.MODE_PRIVATE).use { output ->

			// Say that the text values below are escaped. An older version of the app
			// reads this line as a setting of an unknown type and skips it
			output.write("${CSV_FORMAT_KEY},Format,2\n".toByteArray())

			// Get all shared preferences
			instance.all.forEach {

				// Key value pair
				val key = it.key
				var value = it.value

				// Check if key is in the ignore list
				if (key in ignoreList)
				{
					return@forEach
				}

				// Determine the type of the value
				val type = when (value)
				{
					is Boolean -> "Boolean"
					is Float   -> "Float"
					is Int     -> "Int"
					is Long    -> "Long"
					is String  -> "String"
					else       -> return@forEach
				}

				// Keep the line breaks of a text, such as the list of my phrases, without
				// breaking the one line per setting of the file
				if (value is String)
				{
					value = escapeCsv(value)
				}

				// Build the line that will be written to the file
				val line = "${key},${type},${value}\n"

				// Write to the file
				output.write(line.toByteArray())
			}

		}
	}

	companion object
	{

		/**
		 * Default volume of the spoken time: 30 % of the loudest, quiet enough for the
		 * night (2.06).
		 */
		const val DEFAULT_SAY_TIME_VOLUME = 30

		/**
		 * First line of an exported settings file, saying that its text values are
		 * escaped (2.10).
		 */
		const val CSV_FORMAT_KEY = "#csv_format"

		/**
		 * Moved the shared preference to device protected storage.
		 */
		fun moveToDeviceProtectedStorage(context: Context): Boolean
		{
			// Get default shared preferences file name
			val name = "${context.packageName}_preferences"
			val file = File("${context.dataDir}/shared_prefs/${name}.xml")

			// Check if the file exists
			return if (file.exists())
			{
				// Get device protected storage context
				val deviceContext = context.createDeviceProtectedStorageContext()

				// Move shared preferences to device encrypted storage
				deviceContext.moveSharedPreferencesFrom(context, name)
			}
			else
			{
				// No need to move shared preferences because it has already been moved
				false
			}
		}

	}

}