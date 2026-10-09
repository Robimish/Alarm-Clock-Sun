package com.nfcalarmclock.settings

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.annotation.OptIn
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.NacAlarmViewModel
import com.nfcalarmclock.alarm.options.NacAlarmOptionsDialog
import com.nfcalarmclock.alarm.options.dismissoptions.NacDismissOptionsDialog
import com.nfcalarmclock.alarm.options.name.NacNameDialog
import com.nfcalarmclock.alarm.options.snoozeoptions.NacSnoozeOptionsDialog
import com.nfcalarmclock.alarm.options.tts.NacSayTimeService
import com.nfcalarmclock.alarm.options.tts.NacTranslate
import com.nfcalarmclock.card.NacCardPreference
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.nfc.NacNfcTagViewModel
import com.nfcalarmclock.system.addMediaInfo
import com.nfcalarmclock.system.daysToValue
import com.nfcalarmclock.system.getDeviceProtectedStorageContext
import com.nfcalarmclock.system.getMediaArtist
import com.nfcalarmclock.system.getMediaPath
import com.nfcalarmclock.system.getMediaTitle
import com.nfcalarmclock.system.getMediaType
import com.nfcalarmclock.system.getRecursivelyPlayMedia
import com.nfcalarmclock.system.getShuffleMedia
import com.nfcalarmclock.system.media.buildLocalMediaPath
import com.nfcalarmclock.view.quickToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Settings of the spoken time, which used to sit at the bottom of the general screen.
 *
 * It has a page of its own because nothing about it belongs with the rest: it is the
 * only part of the app that talks, and burying the switch that turns it on under the
 * language, the storage and the default alarm made it hard to find.
 */
@AndroidEntryPoint
class NacSpeakTimeSettingFragment
	: NacBaseSettingFragment()
{

	/**
	 * Called when the preference is created.
	 */
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		NacLog.i("Creating the spoken time settings screen")

		// Get the device protected storage context, if available
		val deviceContext = getDeviceProtectedStorageContext(requireContext())

		// Set device protected storage as the storage location to use
		preferenceManager.setStorageDeviceProtected()

		// Inflate the XML file and add the hierarchy to the current preference
		addPreferencesFromResource(R.xml.speak_time_preferences)

		// Set the default values on this preference that are in the
		// "android:defaultValue" attribute
		PreferenceManager.setDefaultValues(deviceContext, R.xml.speak_time_preferences, false)

		// Setup the preferences
		setupSayTime()
	}

	/**
	 * Setup the preferences that say the time out loud.
	 */
	private fun setupSayTime()
	{
		val shared = NacSharedPreferences(requireContext())

		setupSayTimeHours(shared)
		setupSayTimeLanguage(shared)
		setupSayTimeFormat(shared)
		setupShakeToSayTime(shared)
		setupShakeSensitivity(shared)
		setupWaveToSayTime(shared)
		setupWavePasses(shared)
		setupSayTimeVolume(shared)
		setupProximityTest()
	}

	/**
	 * Setup the volume of the spoken time (2.06).
	 */
	private fun setupSayTimeVolume(shared: NacSharedPreferences)
	{
		val pref = findPreference<Preference>(getString(R.string.key_say_time_volume)) ?: return
		val sameAsAlarms = resources.getStringArray(R.array.say_time_volume_entries).first()

		fun name(percent: Int): String = if (percent <= 0) sameAsAlarms else "$percent %"

		fun refresh()
		{
			val value = shared.sayTimeVolume

			pref.summary = if (value <= 0) name(value)
				else "${name(value)} (${getString(R.string.description_say_time_volume)})"
		}

		refresh()

		// One wheel, by 1 %: "Same as the alarms", then 1 % to 100 % (2.13)
		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			val picker = NumberPicker(requireContext())

			picker.minValue = 0
			picker.maxValue = 100
			picker.displayedValues = Array(101) { name(it) }
			picker.wrapSelectorWheel = false
			picker.value = shared.sayTimeVolume

			AlertDialog.Builder(requireContext())
				.setTitle(R.string.title_say_time_volume)
				.setView(picker)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					picker.clearFocus()
					shared.sayTimeVolume = picker.value
					refresh()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the test of the proximity sensor: a dialog that shows what it reads, live
	 * (2.06). Some sensors give a distance, most only say near or far, and only the
	 * first kind could ever have a distance to choose.
	 */
	private fun setupProximityTest()
	{
		val pref = findPreference<Preference>(getString(R.string.key_proximity_test)) ?: return

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			val context = requireContext()
			val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE)
				as? android.hardware.SensorManager
			val sensor = manager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)

			if ((manager == null) || (sensor == null))
			{
				AlertDialog.Builder(context)
					.setTitle(R.string.title_proximity_test)
					.setMessage(R.string.message_proximity_none)
					.setPositiveButton(R.string.action_ok, null)
					.show()

				return@OnPreferenceClickListener true
			}

			// Every value read so far. A sensor that only knows near and far keeps
			// coming back with the same two values
			val seen = mutableSetOf<Float>()

			// How many times something arrived in front of the sensor, to see which
			// passes it catches and which ones are too quick for it (2.07)
			var passes = 0
			var wasNear = false
			val dialog = AlertDialog.Builder(context)
				.setTitle(R.string.title_proximity_test)
				.setMessage(R.string.message_proximity_test_hint)
				.setPositiveButton(R.string.action_ok, null)
				.create()

			val listener = object : android.hardware.SensorEventListener
			{
				override fun onSensorChanged(event: android.hardware.SensorEvent?)
				{
					val value = event?.values?.getOrNull(0) ?: return
					val near = value < sensor.maximumRange

					seen.add(value)

					if (near && !wasNear)
					{
						passes++
					}

					wasNear = near

					val kind = when
					{
						seen.size > 2 -> getString(R.string.message_proximity_distance)
						seen.size == 2 -> getString(R.string.message_proximity_binary)
						else -> getString(R.string.message_proximity_test_hint)
					}

					dialog.setMessage(getString(R.string.message_proximity_test_values,
						sensor.name, "%.1f".format(sensor.maximumRange), "%.1f".format(value),
						getString(if (near) R.string.word_proximity_near else R.string.word_proximity_far))
						+ "\n" + getString(R.string.message_proximity_passes, passes)
						+ "\n\n" + kind)
				}

				override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
			}

			dialog.setOnDismissListener { manager.unregisterListener(listener) }
			dialog.show()

			manager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)

			true
		}
	}

	/**
	 * Setup the switch that says the time when the phone is shaken.
	 */
	private fun setupShakeToSayTime(shared: NacSharedPreferences)
	{
		val pref = findPreference<Preference>(getString(R.string.key_shake_to_say_time)) ?: return
		val context = requireContext()

		pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->

			// The service reads what is saved, so write it before asking for it
			val isOn = (newValue as? Boolean) ?: false

			shared.shouldShakeToSayTime = isOn

			// Turning it on here means it is wanted, so the switch in the settings
			// list, which holds back the shake and the hand together, comes on too
			if (isOn)
			{
				shared.shouldSayTime = true
			}

			NacSayTimeService.refresh(context)

			true
		}
	}

	/**
	 * Setup how hard the phone has to be shaken.
	 */
	private fun setupShakeSensitivity(shared: NacSharedPreferences)
	{
		val context = requireContext()

		setupChoicePreference(R.string.key_shake_sensitivity, R.string.title_shake_sensitivity,
			R.array.shake_sensitivity_entries,
			get = { shared.shakeSensitivity },
			set = {

				shared.shakeSensitivity = it

				// The service works out the threshold when it starts, so it is told again
				NacSayTimeService.refresh(context)

			})
	}

	/**
	 * Setup the switch that says the time when a hand passes over the phone.
	 */
	private fun setupWaveToSayTime(shared: NacSharedPreferences)
	{
		val pref = findPreference<Preference>(getString(R.string.key_wave_to_say_time)) ?: return
		val context = requireContext()

		pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->

			// The service reads what is saved, so write it before asking for it
			val isOn = (newValue as? Boolean) ?: false

			shared.shouldWaveToSayTime = isOn

			// Turning it on here means it is wanted, so the switch in the settings
			// list, which holds back the shake and the hand together, comes on too
			if (isOn)
			{
				shared.shouldSayTime = true
			}

			NacSayTimeService.refresh(context)

			true
		}
	}

	/**
	 * Setup how many hands passing over say the time.
	 */
	private fun setupWavePasses(shared: NacSharedPreferences)
	{
		val context = requireContext()

		setupChoicePreference(R.string.key_wave_passes, R.string.title_wave_passes,
			R.array.wave_passes_entries,
			get = { shared.wavePasses },
			set = {

				shared.wavePasses = it

				// The service works out the gesture when it starts, so it is told again
				NacSayTimeService.refresh(context)

			})
	}

	/**
	 * Setup which clock the spoken time uses.
	 */
	private fun setupSayTimeFormat(shared: NacSharedPreferences)
	{
		setupChoicePreference(R.string.key_say_time_format, R.string.title_say_time_format,
			R.array.say_time_format_entries,
			get = { shared.sayTimeFormat },
			set = { shared.sayTimeFormat = it },
			extra = { index ->

				// "The same as the phone" says nothing by itself. What the phone is set
				// to is what will be spoken, so it is named here
				if (index == NacTranslate.TIME_FORMAT_FOLLOW_PHONE)
				{
					getString(if (DateFormat.is24HourFormat(requireContext()))
						R.string.message_clock_24_hour
					else
						R.string.message_clock_12_hour)
				}
				else
				{
					null
				}

			})
	}

	/**
	 * Setup a preference that picks one entry out of a list and shows it as its summary.
	 */
	private fun setupChoicePreference(
		keyId: Int,
		titleId: Int,
		arrayId: Int,
		get: () -> Int,
		set: (Int) -> Unit,
		extra: ((Int) -> String?)? = null
	)
	{
		val pref = findPreference<Preference>(getString(keyId)) ?: return
		val names = resources.getStringArray(arrayId)

		fun refresh()
		{
			val name = names.getOrNull(get()) ?: names.first()
			val more = extra?.invoke(get())

			pref.summary = if (more.isNullOrEmpty()) name else "$name ($more)"
		}

		refresh()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			val current = get().coerceIn(0, names.size - 1)

			AlertDialog.Builder(requireContext())
				.setTitle(titleId)
				.setSingleChoiceItems(names, current) { d, which ->

					set(which)

					refresh()
					d.dismiss()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the hours between which the time can be spoken.
	 */
	private fun setupSayTimeHours(shared: NacSharedPreferences)
	{
		val pref = findPreference<Preference>(getString(R.string.key_say_time_from_hour)) ?: return

		// Show the hours that are set
		fun refresh()
		{
			pref.summary = getString(R.string.description_say_time_hours,
				"%02d:%02d".format(shared.sayTimeFromHour, shared.sayTimeFromMinute),
				"%02d:%02d".format(shared.sayTimeToHour, shared.sayTimeToMinute))
		}

		refresh()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			// Two wheels side by side, one for each end of the span, going by half an
			// hour: 00:00, 00:30, 01:00 ... 23:30
			val context = requireContext()
			val slots = Array(48) { "%02d:%02d".format(it / 2, (it % 2) * 30) }
			val row = LinearLayout(context)
			val fromPicker = NumberPicker(context)
			val toPicker = NumberPicker(context)
			val pickers = listOf(fromPicker, toPicker)

			pickers.forEach {

				it.minValue = 0
				it.maxValue = 47
				it.displayedValues = slots
				it.wrapSelectorWheel = true

				row.addView(it, LinearLayout.LayoutParams(0,
					LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

			}

			row.orientation = LinearLayout.HORIZONTAL
			fromPicker.value = (shared.sayTimeFromHour * 2 + shared.sayTimeFromMinute / 30)
				.coerceIn(0, 47)
			toPicker.value = (shared.sayTimeToHour * 2 + shared.sayTimeToMinute / 30)
				.coerceIn(0, 47)

			AlertDialog.Builder(requireContext())
				.setTitle(R.string.title_say_time_hours)
				.setView(row)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					pickers.forEach { it.clearFocus() }

					shared.sayTimeFromHour = fromPicker.value / 2
					shared.sayTimeFromMinute = (fromPicker.value % 2) * 30
					shared.sayTimeToHour = toPicker.value / 2
					shared.sayTimeToMinute = (toPicker.value % 2) * 30

					// The shake service stands up or down on those very hours
					NacSayTimeService.refresh(requireContext())

					refresh()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the language the time is spoken in.
	 */
	private fun setupSayTimeLanguage(shared: NacSharedPreferences)
	{
		val pref = findPreference<Preference>(getString(R.string.key_say_time_language)) ?: return
		val names = resources.getStringArray(R.array.say_time_language_entries)
		val codes = resources.getStringArray(R.array.say_time_language_values)

		fun refresh()
		{
			val index = codes.indexOf(shared.sayTimeLanguage).coerceAtLeast(0)

			pref.summary = names.getOrNull(index) ?: names.first()
		}

		refresh()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			val current = codes.indexOf(shared.sayTimeLanguage).coerceAtLeast(0)

			AlertDialog.Builder(requireContext())
				.setTitle(R.string.title_say_time_language)
				.setSingleChoiceItems(names, current) { d, which ->

					shared.sayTimeLanguage = codes.getOrNull(which) ?: ""

					refresh()
					d.dismiss()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

}
