package com.nfcalarmclock.settings

import android.app.AlertDialog
import android.os.Bundle
import android.widget.NumberPicker
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.options.nextalarmformat.NacNextAlarmFormatPreference
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.settings.startweekon.NacStartWeekOnPreference
import com.nfcalarmclock.system.getDeviceProtectedStorageContext
import com.nfcalarmclock.settings.colorpicker.NacColorPickerPreference

/**
 * Appearance fragment.
 */
class NacAppearanceSettingFragment
	: NacBaseSettingFragment()
{

	/**
	 * Initialize the color settings fragment.
	 */
	private fun init()
	{
		// Get the device protected storage context, if available
		val deviceContext = getDeviceProtectedStorageContext(requireContext())

		// Inflate the XML file and add the hierarchy to the current preference
		addPreferencesFromResource(R.xml.appearance_preferences)

		// Set the default values in the XML
		PreferenceManager.setDefaultValues(deviceContext, R.xml.appearance_preferences, false)

		// Setup color and styles
		setupColorPreferences()
		setupShowHideButtonPreferences()
		setupDayButtonStylePreference()
		setupCardButtonLabelsPreference()

		// Setup on click listeners
		setupColorPickerOnClickListeners()
		setupStartWeekOnClickListener()
		setupNexAlarmFormatOnClickListener()
		setupDawnPreviewDurationClickListener()
	}

	/**
	 * Setup the preference that says how long a dawn preview runs.
	 */
	private fun setupDawnPreviewDurationClickListener()
	{
		// Get the preference
		val key = getString(R.string.key_dawn_preview_duration)
		val pref = findPreference<Preference>(key) ?: return
		val shared = NacSharedPreferences(requireContext())

		// The allowed range has shrunk since this setting first shipped, so bring
		// any older value back inside it
		val previewDuration = shared.dawnPreviewDuration
			.coerceIn(MIN_DAWN_PREVIEW_DURATION, MAX_DAWN_PREVIEW_DURATION)

		if (previewDuration != shared.dawnPreviewDuration)
		{
			shared.dawnPreviewDuration = previewDuration
		}

		// Show the current value
		pref.summary = getString(R.string.description_dawn_preview_duration,
			previewDuration)

		// Set the on click listener
		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			// Build the picker. The wheel counts down, so that the big numbers are
			// at the top
			val picker = NumberPicker(requireContext())
			val span = MAX_DAWN_PREVIEW_DURATION - MIN_DAWN_PREVIEW_DURATION

			picker.minValue = 0
			picker.maxValue = span
			picker.displayedValues = Array(span + 1) {
				(MAX_DAWN_PREVIEW_DURATION - it).toString()
			}
			picker.value = MAX_DAWN_PREVIEW_DURATION - shared.dawnPreviewDuration
				.coerceIn(MIN_DAWN_PREVIEW_DURATION, MAX_DAWN_PREVIEW_DURATION)
			picker.wrapSelectorWheel = false

			// Show it
			AlertDialog.Builder(requireContext())
				.setTitle(R.string.title_dawn_preview_duration)
				.setView(picker)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					picker.clearFocus()

					val seconds = MAX_DAWN_PREVIEW_DURATION - picker.value

					shared.dawnPreviewDuration = seconds
					pref.summary = getString(R.string.description_dawn_preview_duration,
						seconds)

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			// Return
			true
		}
	}

	/**
	 * Called when the preferences are created.
	 */
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		NacLog.i("Creating appearance settings screen")

		// Set device protected storage as the storage location to use
		preferenceManager.setStorageDeviceProtected()

		// Initialize the color settings
		init()

		// The date, the time and the music used to be greyed out when the switch to
		// swipe was off: the original screen did not show them. Both ways of stopping
		// the alarm show the same full screen since 2.01. Only the simple screen, which
		// has none of it, greys them out now, with the switch to swipe (2.05)
		setupSimpleAlarmScreen()
		setupMyPhrases()
	}

	/**
	 * Setup the wake-up phrases of one's own: a box to write them, one per line (2.07).
	 */
	private fun setupMyPhrases()
	{
		val pref = findPreference<Preference>(getString(R.string.key_my_phrases)) ?: return
		val shared = sharedPreferences ?: return

		fun refresh()
		{
			val count = shared.myPhrases.lines().count { it.isNotBlank() }

			pref.summary = if (count == 0) getString(R.string.description_my_phrases_none)
				else getString(R.string.description_my_phrases_count, count)
		}

		refresh()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			val context = requireContext()
			val padding = (20 * resources.displayMetrics.density).toInt()
			val edit = android.widget.EditText(context).apply {
				setText(shared.myPhrases)
				hint = getString(R.string.message_my_phrases_hint)
				minLines = 4
				maxLines = 12
				gravity = android.view.Gravity.TOP or android.view.Gravity.START
				inputType = android.text.InputType.TYPE_CLASS_TEXT or
					android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
					android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
			}
			val frame = android.widget.FrameLayout(context).apply {
				setPadding(padding, padding / 2, padding, 0)
				addView(edit)
			}

			AlertDialog.Builder(context)
				.setTitle(R.string.title_my_phrases)
				.setMessage(R.string.message_my_phrases_hint)
				.setView(frame)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					// Empty lines and spaces around a phrase are left out
					shared.myPhrases = edit.text.toString().lines()
						.map { it.trim() }
						.filter { it.isNotEmpty() }
						.joinToString("\n")

					refresh()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the should show card button labels preference.
	 */
	private fun setupCardButtonLabelsPreference()
	{
		// Get the preference
		val key = getString(R.string.key_style_should_show_card_button_labels)
		val pref = findPreference<Preference>(key)

		// Set the listener for when the preference is changed
		pref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->

			NacLog.i("Changed card button label preference to: $newValue. Main activity will be refreshed")

			// Set flag to refresh the main activity
			sharedPreferences!!.shouldRefreshMainActivity = true

			// Return
			true

		}
	}

	/**
	 * Setup the listeners for when a color picker preference is clicked.
	 */
	private fun setupColorPickerOnClickListeners()
	{
		// Get the keys
		val themeKey = getString(R.string.key_color_theme)
		val nameKey = getString(R.string.key_color_name)
		val dayKey = getString(R.string.key_color_days)
		val timeKey = getString(R.string.key_color_time)
		val amKey = getString(R.string.key_color_am)
		val pmKey = getString(R.string.key_color_pm)
		val deleteAfterDismissedKey = getString(R.string.key_color_delete_after_dismissed)
		val skipNextAlarmKey = getString(R.string.key_color_skip_next_alarm)

		// Get the color preferences
		val themePref = findPreference<NacColorPickerPreference>(themeKey)
		val namePref = findPreference<NacColorPickerPreference>(nameKey)
		val daysPref = findPreference<NacColorPickerPreference>(dayKey)
		val timePref = findPreference<NacColorPickerPreference>(timeKey)
		val amPref = findPreference<NacColorPickerPreference>(amKey)
		val pmPref = findPreference<NacColorPickerPreference>(pmKey)
		val deleteAfterDismissedPref = findPreference<NacColorPickerPreference>(deleteAfterDismissedKey)
		val skipNextAlarmPref = findPreference<NacColorPickerPreference>(skipNextAlarmKey)

		// Create list of all color preferences
		val allPrefs = listOf(themePref, namePref, daysPref, timePref, amPref, pmPref,
			deleteAfterDismissedPref, skipNextAlarmPref)

		// Iterate over each color preference
		for (p in allPrefs)
		{
			// Show the dialog for the on click listener
			p!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { pref ->

				NacLog.i("Showing color picker dialog for '${pref.key}'")
				(pref as NacColorPickerPreference).showDialog(childFragmentManager)

				// Return
				true
			}
		}
	}

	/**
	 * Setup the color preferences.
	 */
	private fun setupColorPreferences()
	{
		// Get the keys
		val themeKey = getString(R.string.key_color_theme)
		val nameKey = getString(R.string.key_color_name)
		val dayKey = getString(R.string.key_color_days)
		val timeKey = getString(R.string.key_color_time)
		val amKey = getString(R.string.key_color_am)
		val pmKey = getString(R.string.key_color_pm)
		val deleteAfterDismissedKey = getString(R.string.key_color_delete_after_dismissed)
		val skipNextAlarmKey = getString(R.string.key_color_skip_next_alarm)

		// Put the keys in a list
		val allKeys = arrayOf(themeKey, nameKey, dayKey, timeKey, amKey, pmKey,
			deleteAfterDismissedKey, skipNextAlarmKey)

		// Iterate over each color key
		for (k in allKeys)
		{
			// Get the preference
			val pref = findPreference<Preference>(k)

			// Set the listener for when the prference is changed
			pref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { p, newValue ->

				NacLog.i("Color preference '${pref.key}' change to: $newValue. Main activity will be refreshed")

				// Set flag to refresh the main activity
				sharedPreferences!!.shouldRefreshMainActivity = true

				// Preference key is for the theme
				if (p.key == themeKey)
				{
					// Reset the screen
					preferenceScreen = null

					// Reinitialize the colors
					init()
				}

				// Return
				true

			}
		}
	}

	/**
	 * Setup the day button style preference.
	 */
	private fun setupDayButtonStylePreference()
	{
		// Get the preference
		val key = getString(R.string.key_style_day_button)
		val pref = findPreference<Preference>(key)

		// Set the listener for when the preference is changed
		pref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->

			NacLog.i("Day button style changed to: $newValue. Main activity will be refreshed")

			// Set flag to refresh the main activity
			sharedPreferences!!.shouldRefreshMainActivity = true

			// Return
			true

		}
	}

	/**
	 * Grey out what the simple alarm screen does not show, and follow the switch.
	 */
	private fun setupSimpleAlarmScreen()
	{
		val simplePref = findPreference<Preference>(getString(R.string.key_use_simple_alarm_screen))
			?: return
		val keys = listOf(R.string.key_use_new_alarm_screen,
			R.string.key_alarm_screen_show_current_date_and_time,
			R.string.key_alarm_screen_show_music_info)

		fun apply(isSimple: Boolean)
		{
			keys.forEach { findPreference<Preference>(getString(it))?.isEnabled = !isSimple }
		}

		apply(sharedPreferences?.shouldUseSimpleAlarmScreen == true)

		simplePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
			apply(value as Boolean)
			true
		}
	}

	/**
	 * Setup the listener for when the next alarm format preference is clicked.
	 */
	private fun setupNexAlarmFormatOnClickListener()
	{
		// Get the preference
		val key = getString(R.string.key_tweak_next_alarm_format)
		val pref = findPreference<NacNextAlarmFormatPreference>(key)

		// Set the on click listener
		pref!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { p ->

			// Show the dialog
			NacLog.i("Showing next alarm format dialog")
			(p as NacNextAlarmFormatPreference).showDialog(childFragmentManager)

			// Return
			true

		}
	}

	/**
	 * Setup the show/hide buttons.
	 */
	private fun setupShowHideButtonPreferences()
	{
		// Get the keys
		val vibrateKey = getString(R.string.key_show_hide_vibrate_button)
		val nfcKey = getString(R.string.key_show_hide_nfc_button)
		val flashlightKey = getString(R.string.key_show_hide_flashlight_button)

		// Put the keys in a list
		val allKeys = arrayOf(vibrateKey, nfcKey, flashlightKey)

		// Iterate over each color key
		for (k in allKeys)
		{
			// Get the preference
			val pref = findPreference<Preference>(k)

			// Set the listener for when the prference is changed
			pref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->

				NacLog.i("Show hide '${pref.key}' button changed to: $newValue. Main activity will be refreshed")

				// Set flag to refresh the main activity
				sharedPreferences!!.shouldRefreshMainActivity = true

				// Return
				true

			}
		}
	}

	/**
	 * Setup the listener for when the start week on preference is clicked.
	 */
	private fun setupStartWeekOnClickListener()
	{
		// Get the preference
		val key = getString(R.string.key_style_start_week_on)
		val pref = findPreference<NacStartWeekOnPreference>(key)

		// Set the on click listener
		pref!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { p ->

			// Show the dialog
			NacLog.i("Showing start week on dialog")
			(p as NacStartWeekOnPreference).showDialog(childFragmentManager)

			// Return
			true
		}
	}

	companion object
	{

		/**
		 * Shortest and longest time that a tap can show the screen as normal during
		 * a dawn. [Units: sec]
		 */

		/**
		 * Shortest and longest a dawn preview can run. [Units: sec]
		 */
		private const val MIN_DAWN_PREVIEW_DURATION = 5
		private const val MAX_DAWN_PREVIEW_DURATION = 30

	}

}
