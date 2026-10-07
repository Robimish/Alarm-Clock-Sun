package com.nfcalarmclock.settings

import android.os.Bundle
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
import com.nfcalarmclock.alarm.options.NacAlarmButton
import com.nfcalarmclock.alarm.options.NacAlarmOptionsDialog
import com.nfcalarmclock.alarm.options.dismissoptions.NacDismissOptionsDialog
import com.nfcalarmclock.alarm.options.name.NacNameDialog
import com.nfcalarmclock.alarm.options.snoozeoptions.NacSnoozeOptionsDialog
import com.nfcalarmclock.alarm.options.tts.NacSayTimeService
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
 * General settings fragment.
 */
@AndroidEntryPoint
class NacGeneralSettingFragment
	: NacBaseSettingFragment()
{

	/**
	 * Settings navigation controller.
	 */
	val navController by lazy {
		(requireActivity().supportFragmentManager.findFragmentById(R.id.settings_media_content) as NavHostFragment).navController
	}

	/**
	 * NFC tag view model.
	 */
	private val nfcTagViewModel: NacNfcTagViewModel by viewModels()

	/**
	 * Alarm view model, used to know which sounds and images are still in use.
	 */
	private val alarmViewModel: NacAlarmViewModel by viewModels()

	/**
	 * Called when the preference is created.
	 */
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
	{
		NacLog.i("Creating general settings screen")

		// Get the device protected storage context, if available
		val deviceContext = getDeviceProtectedStorageContext(requireContext())

		// Set device protected storage as the storage location to use
		preferenceManager.setStorageDeviceProtected()

		// Inflate the XML file and add the hierarchy to the current preference
		addPreferencesFromResource(R.xml.general_preferences)

		// Set the default values on this preference that are in the "android:defaultValue"
		// attribute
		PreferenceManager.setDefaultValues(deviceContext, R.xml.general_preferences,  false)

		// Setup the preferences
		setupDefaultAlarmCard()
		setupAlarmButtons()
		setupSwipeThreshold()
		setupAppLanguage()
		setupResetSettings()
		setupCleanup()
	}

	/**
	 * Setup the language the app is shown in.
	 *
	 * This is the very same setting that Android keeps for each app, so changing it
	 * here and changing it in the settings of the phone are one and the same thing.
	 */
	private fun setupAppLanguage()
	{
		val pref = findPreference<Preference>(getString(R.string.key_app_language)) ?: return
		val names = resources.getStringArray(R.array.say_time_language_entries).clone()
		val codes = resources.getStringArray(R.array.say_time_language_values)

		// The first entry means the language of the phone, which is worth saying in the
		// language the app is showing rather than always in English
		names[0] = getString(R.string.title_language_system)

		// Which entry is the one in use
		fun currentIndex(): Int
		{
			val locales = AppCompatDelegate.getApplicationLocales()

			if (locales.isEmpty())
			{
				return 0
			}

			val tag = locales[0]?.toLanguageTag() ?: return 0

			// A tag such as "fr-BE" belongs to the French entry
			return codes
				.indexOfFirst { it.isNotEmpty() && ((tag == it) || tag.startsWith("$it-")) }
				.coerceAtLeast(0)
		}

		pref.summary = names[currentIndex()]

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			AlertDialog.Builder(requireContext())
				.setTitle(R.string.title_app_language)
				.setSingleChoiceItems(names, currentIndex()) { d, which ->

					val code = codes.getOrNull(which) ?: ""

					d.dismiss()

					// Android puts the screen back together in the new language by
					// itself, so there is nothing to refresh here
					AppCompatDelegate.setApplicationLocales(
						if (code.isEmpty())
						{
							LocaleListCompat.getEmptyLocaleList()
						}
						else
						{
							LocaleListCompat.forLanguageTags(code)
						})

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the two buttons that snooze and dismiss a ringing alarm (2.01).
	 *
	 * Which button does what is chosen here. Each alarm then says in its snooze and
	 * dismiss options whether it uses its button. One press must not be read as both,
	 * so a button that overlaps the other one is refused.
	 */
	private fun setupAlarmButtons()
	{
		setupAlarmButton(R.string.key_snooze_button, isSnooze = true)
		setupAlarmButton(R.string.key_dismiss_button, isSnooze = false)
	}

	/**
	 * Setup how far the slider of the alarm screen must go, from 80 to 100 %.
	 */
	private fun setupSwipeThreshold()
	{
		val pref = findPreference<Preference>(getString(R.string.key_swipe_threshold)) ?: return
		val shared = sharedPreferences ?: return
		val context = requireContext()

		fun refreshSummary()
		{
			pref.summary = getString(R.string.description_swipe_threshold, shared.swipeThreshold)
		}

		refreshSummary()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			// One wheel, from 80 to 100 %
			val picker = NumberPicker(context)

			picker.minValue = 80
			picker.maxValue = 100
			picker.displayedValues = Array(21) { "${it + 80} %" }
			picker.wrapSelectorWheel = false
			picker.value = shared.swipeThreshold

			AlertDialog.Builder(context)
				.setTitle(pref.title)
				.setView(picker)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					picker.clearFocus()
					shared.swipeThreshold = picker.value
					refreshSummary()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup one of the two alarm buttons.
	 */
	private fun setupAlarmButton(keyId: Int, isSnooze: Boolean)
	{
		val pref = findPreference<Preference>(getString(keyId)) ?: return
		val shared = sharedPreferences ?: return
		val context = requireContext()
		val buttons = NacAlarmButton.ALL
		val names = buttons.map { NacAlarmButton.name(context, it) }.toTypedArray()

		fun current(): String = if (isSnooze) shared.snoozeButton else shared.dismissButton
		fun other(): String = if (isSnooze) shared.dismissButton else shared.snoozeButton

		fun refreshSummary()
		{
			val button = current()
			val note = if (NacAlarmButton.matchesPower(button)) getString(R.string.message_power_button_note) else ""

			pref.summary = NacAlarmButton.name(context, button) + "\n" +
				getString(R.string.description_alarm_buttons, note)
		}

		refreshSummary()

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			AlertDialog.Builder(context)
				.setTitle(pref.title)
				.setSingleChoiceItems(names, buttons.indexOf(current()).coerceAtLeast(0)) { d, which ->

					val chosen = buttons[which]

					d.dismiss()

					// One press must not both snooze and dismiss
					if (NacAlarmButton.overlap(chosen, other()))
					{
						quickToast(context, if (isSnooze) R.string.message_button_used_by_other
							else R.string.message_button_used_by_other_snooze)
						return@setSingleChoiceItems
					}

					if (isSnooze)
					{
						shared.snoozeButton = chosen
					}
					else
					{
						shared.dismissButton = chosen
					}

					refreshSummary()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the preference that puts every setting back to what it was out of the box.
	 */
	private fun setupResetSettings()
	{
		val pref = findPreference<Preference>(getString(R.string.key_reset_settings)) ?: return

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			AlertDialog.Builder(requireContext())
				.setTitle(pref.title)
				.setMessage(R.string.message_reset_settings_confirm)
				.setPositiveButton(R.string.action_ok) { _, _ ->

					val context = requireContext()

					NacSharedPreferences(context).resetToDefaults()

					quickToast(context, R.string.message_reset_settings_done)

					// The screen is showing what was just thrown away, so build it again
					requireActivity().recreate()

				}
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Setup the preferences that free up the copies the app is holding on to.
	 */
	private fun setupCleanup()
	{
		setupCleanupPreference(R.string.key_clean_sounds, sounds = true, images = false)
		setupCleanupPreference(R.string.key_clean_images, sounds = false, images = true)
		setupCleanupPreference(R.string.key_clean_all, sounds = true, images = true)
	}

	/**
	 * Setup one of the clean up preferences.
	 */
	private fun setupCleanupPreference(keyId: Int, sounds: Boolean, images: Boolean)
	{
		val pref = findPreference<Preference>(getString(keyId)) ?: return

		pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

			AlertDialog.Builder(requireContext())
				.setTitle(pref.title)
				.setMessage(R.string.message_clean_confirm)
				.setPositiveButton(R.string.action_ok) { _, _ -> runCleanup(sounds, images) }
				.setNegativeButton(R.string.action_cancel, null)
				.show()

			true
		}
	}

	/**
	 * Throw away the copies that no alarm needs, and say what that freed.
	 */
	private fun runCleanup(sounds: Boolean, images: Boolean)
	{
		val context = requireContext()

		lifecycleScope.launch {

			val alarms = alarmViewModel.getAllAlarms()

			var files = 0
			var bytes = 0L

			// Images first, since it also tells which alarms were pointing at one
			if (images)
			{
				val result = NacStorageCleanup.cleanImages(context, alarms)

				files += result.files
				bytes += result.bytes

				// Forget the images that are gone, so that nothing points at a file
				// that no longer exists
				result.alarmsToClear.forEach {

					it.dawnImagePath = ""
					it.shouldUseDawnImage = false

					alarmViewModel.update(it)

				}
			}

			if (sounds)
			{
				val result = NacStorageCleanup.cleanSounds(context, alarms)

				files += result.files
				bytes += result.bytes
			}

			// Say what happened
			if (files > 0)
			{
				quickToast(context, getString(R.string.message_clean_done,
					NacStorageCleanup.readableSize(bytes), files))
			}
			else
			{
				quickToast(context, R.string.message_clean_nothing)
			}

		}
	}

	/**
	 * Called after the view is created.
	 */
	override fun onViewCreated(view: View, savedInstanceState: Bundle?)
	{
		// Super
		super.onViewCreated(view, savedInstanceState)

		// Set the observer for the media picker
		findNavController().currentBackStackEntry
			?.savedStateHandle
			?.getLiveData<Bundle>("YOYOYO")
			?.observe(viewLifecycleOwner) { result ->

				// Get the preference
				val context = requireContext()
				val key = getString(R.string.key_default_alarm_card)
				val pref = findPreference<NacCardPreference>(key)!!

				// Save the media info for this preference
				sharedPreferences!!.mediaPath = result.getMediaPath()
				sharedPreferences!!.mediaArtist = result.getMediaArtist()
				sharedPreferences!!.mediaTitle = result.getMediaTitle()
				sharedPreferences!!.mediaType	= result.getMediaType()
				sharedPreferences!!.localMediaPath = buildLocalMediaPath(
					context,
					sharedPreferences!!.mediaArtist,
					sharedPreferences!!.mediaTitle,
					sharedPreferences!!.mediaType)
				sharedPreferences!!.shouldShuffleMedia = result.getShuffleMedia()
				sharedPreferences!!.recursivelyPlayMedia = result.getRecursivelyPlayMedia()

				// Card is initialized
				if (pref.isCardInitialized())
				{
					// Update the card
					pref.card.alarm!!.mediaPath = sharedPreferences!!.mediaPath
					pref.card.alarm!!.mediaArtist = sharedPreferences!!.mediaArtist
					pref.card.alarm!!.mediaTitle = sharedPreferences!!.mediaTitle
					pref.card.alarm!!.mediaType = sharedPreferences!!.mediaType
					pref.card.setMediaButton()
				}
				// Card has not been initialized so setting any properties would fail
				else
				{
					// Update the card once the card is bound
					pref.onCardViewHolderBoundListener = NacCardPreference.OnCardViewHolderBoundListener {
						pref.card.alarm!!.mediaPath = sharedPreferences!!.mediaPath
						pref.card.alarm!!.mediaArtist = sharedPreferences!!.mediaArtist
						pref.card.alarm!!.mediaTitle = sharedPreferences!!.mediaTitle
						pref.card.alarm!!.mediaType = sharedPreferences!!.mediaType
						pref.card.setMediaButton()
					}
				}

			}
	}

	/**
	 * Setup the default alarm card.
	 *
	 * Note: Only actions that open up a dialog are here, otherwise, the simple stuff is
	 *       handled in NacCardPreference
	 */
	@OptIn(UnstableApi::class)
	private fun setupDefaultAlarmCard()
	{
		// Get the preference
		val key = getString(R.string.key_default_alarm_card)
		val pref = findPreference<NacCardPreference>(key)!!

		// Set the list of NFC tags
		lifecycleScope.launch {
			pref.allNfcTags = nfcTagViewModel.getAllNfcTags()
		}

		// Media
		pref.onCardMediaClickedListener = NacCardPreference.OnCardMediaClickedListener { alarm ->

			// Create a bundle with the media info
			val mediaBundle = Bundle()
				.addMediaInfo(
					alarm.mediaPath,
					alarm.mediaArtist,
					alarm.mediaTitle,
					alarm.mediaType,
					alarm.shouldShuffleMedia,
					alarm.shouldRecursivelyPlayMedia)

			// Navigate to the media picker
			findNavController().navigate(R.id.action_nacGeneralSettingFragment_to_nacAlarmMainMediaPickerFragment2, mediaBundle)

		}

		// Name
		pref.onCardNameClickedListener = NacCardPreference.OnCardNameClickedListener { alarm ->

			// Show the name dialog
			NacNameDialog.create(
				alarm.name,
				onNameEnteredListener = {

					// Save the name
					sharedPreferences!!.name = it

					// Refresh the views
					pref.card.refreshNameViews()

				})
				.show(childFragmentManager, NacNameDialog.TAG)

		}

		// Dismiss options
		pref.onCardDismissOptionsClickedListener = NacCardPreference.OnCardDismissOptionsClickedListener { alarm ->

			// Show the dismiss options dialog
			NacDismissOptionsDialog.create(
				alarm,
				onSaveAlarmListener = { a ->

					// Save the changes
					sharedPreferences!!.shouldAutoDismiss = a.shouldAutoDismiss
					sharedPreferences!!.autoDismissTime = a.autoDismissTime
					sharedPreferences!!.canDismissEarly = a.canDismissEarly
					sharedPreferences!!.shouldShowDismissEarlyNotification = a.shouldShowDismissEarlyNotification
					sharedPreferences!!.dismissEarlyTime = a.dismissEarlyTime
					sharedPreferences!!.shouldDeleteAfterDismissed = a.shouldDeleteAfterDismissed

				})
				.show(childFragmentManager, NacDismissOptionsDialog.TAG)

		}

		// Snooze options
		pref.onCardSnoozeOptionsClickedListener = NacCardPreference.OnCardSnoozeOptionsClickedListener { alarm ->

			// Show the snooze options dialog
			NacSnoozeOptionsDialog.create(
				alarm,
				onSaveAlarmListener = { a ->

					// Save the changes
					sharedPreferences!!.shouldAutoSnooze = a.shouldAutoSnooze
					sharedPreferences!!.autoSnoozeTime = a.autoSnoozeTime
					sharedPreferences!!.maxSnooze = a.maxSnooze
					sharedPreferences!!.snoozeDuration = a.snoozeDuration
					sharedPreferences!!.shouldEasySnooze = a.shouldEasySnooze

				})
				.show(childFragmentManager, NacSnoozeOptionsDialog.TAG)

		}

		// TODO: Repeat, vibrate, NFC, and flashlight long click listener

		// Alarm options
		pref.onCardAlarmOptionsClickedListener = NacCardPreference.OnCardAlarmOptionsClickedListener { alarm ->

			// Show the alarm options dialog
			NacAlarmOptionsDialog.navigate(navController, alarm)
				?.observe(this) { a ->

					// Check which destination this alarm update came from
					when (navController.currentDestination?.id)
					{

						// Repeat
						R.id.nacRepeatOptionsDialog -> {
							sharedPreferences!!.shouldRepeat = true
							sharedPreferences!!.repeatFrequency = a.repeatFrequency
							sharedPreferences!!.repeatFrequencyUnits = a.repeatFrequencyUnits
							sharedPreferences!!.repeatFrequencyDaysToRunBeforeStarting = a.repeatFrequencyDaysToRunBeforeStarting.daysToValue()

							// Weekly frequency unit
							if (a.repeatFrequencyUnits == 4)
							{
								// Days are empty
								if (a.days.isEmpty())
								{
									sharedPreferences!!.days = a.days.daysToValue()
								}
							}
							// Every other frequency unit
							else
							{
								sharedPreferences!!.days = a.days.daysToValue()
							}
						}

						// Vibrate
						R.id.nacVibrateOptionsDialog -> {
							sharedPreferences!!.vibrateDuration = a.vibrateDuration
							sharedPreferences!!.vibrateWaitTime = a.vibrateWaitTime
							sharedPreferences!!.shouldVibratePattern = a.shouldVibratePattern
							sharedPreferences!!.vibrateRepeatPattern = a.vibrateRepeatPattern
							sharedPreferences!!.vibrateWaitTimeAfterPattern = a.vibrateWaitTimeAfterPattern
						}

						// NFC
						R.id.nacScanNfcTagDialog -> {
							sharedPreferences!!.nfcTagId = a.nfcTagId
							sharedPreferences!!.shouldUseNfcTagDismissOrder = a.shouldUseNfcTagDismissOrder
							sharedPreferences!!.nfcTagDismissOrder = a.nfcTagDismissOrder
						}

						// Flashlight
						R.id.nacFlashlightOptionsDialog -> {
							sharedPreferences!!.flashlightStrengthLevel = a.flashlightStrengthLevel
							sharedPreferences!!.shouldBlinkFlashlight = a.shouldBlinkFlashlight
							sharedPreferences!!.flashlightOnDuration = a.flashlightOnDuration
							sharedPreferences!!.flashlightOffDuration = a.flashlightOffDuration
						}

						// Audio source
						R.id.nacAudioSourceDialog -> {
							sharedPreferences!!.audioSource = a.audioSource
							sharedPreferences!!.shouldPlayAudioThroughSpeakersAndBluetooth = a.shouldPlayAudioThroughSpeakersAndBluetooth
						}

						// Text-to-speech
						R.id.nacTextToSpeechDialog -> {
							sharedPreferences!!.shouldSayCurrentTime = a.shouldSayCurrentTime
							sharedPreferences!!.shouldSayAlarmName = a.shouldSayName
							sharedPreferences!!.ttsFrequency = a.ttsFrequency
							sharedPreferences!!.ttsDelay = a.ttsDelay
							sharedPreferences!!.ttsVoice = a.ttsVoice
						}

						// Upcoming reminder
						R.id.nacUpcomingReminderDialog -> {
							sharedPreferences!!.shouldShowReminder = a.shouldShowReminder
							sharedPreferences!!.timeToShowReminder = a.timeToShowReminder
							sharedPreferences!!.reminderFrequency = a.reminderFrequency
							sharedPreferences!!.shouldUseTtsForReminder = a.shouldUseTts && a.shouldUseTtsForReminder
						}

						// Volume
						R.id.nacVolumeOptionsDialog -> {
							sharedPreferences!!.shouldGraduallyIncreaseVolume = a.shouldGraduallyIncreaseVolume
							sharedPreferences!!.graduallyIncreaseVolumeWaitTime = a.graduallyIncreaseVolumeWaitTime
							sharedPreferences!!.shouldRestrictVolume = a.shouldRestrictVolume
						}

						// Unknown
						else -> {}

					}

				}

		}
	}

}
