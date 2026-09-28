package com.nfcalarmclock.alarm.options.dawn

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.alarm.options.NacGenericAlarmOptionsDialog
import com.nfcalarmclock.main.NacMainActivity
import com.nfcalarmclock.settings.colorpicker.NacColorPickerDialog
import com.nfcalarmclock.view.wheel.NacHorizontalWheel
import com.nfcalarmclock.view.calcAlpha
import com.nfcalarmclock.view.setupSwitchColor

/**
 * Dawn simulation options for an alarm.
 *
 * The screen gradually lights up, from black to the chosen color (or a chosen
 * image), during the minutes that precede the alarm.
 */
open class NacDawnOptionsDialog
	: NacGenericAlarmOptionsDialog()
{

	/**
	 * Layout resource ID.
	 */
	override val layoutId = R.layout.dlg_dawn

	/**
	 * Wheel for the duration of the dawn.
	 */
	private lateinit var durationWheel: NacHorizontalWheel

	/**
	 * Wheel for how long before the alarm the flashlight fades in.
	 */
	private lateinit var flashlightWheel: NacHorizontalWheel

	/**
	 * Text view showing how long before the alarm the flashlight fades in.
	 */
	private lateinit var flashlightTextView: TextView

	/**
	 * Text view showing the duration of the dawn.
	 */
	private lateinit var durationTextView: TextView

	/**
	 * The preset color swatches, plus the custom color swatch at the end.
	 */
	private val colorSwatches: MutableList<View> = mutableListOf()

	/**
	 * The dot under each swatch, which marks the color that is in use.
	 */
	private val colorDots: MutableList<TextView> = mutableListOf()

	/**
	 * Switch to use an image instead of a color.
	 */
	private lateinit var useImageSwitch: SwitchCompat

	/**
	 * Text view showing the name of the selected image.
	 */
	private lateinit var imageNameTextView: TextView

	/**
	 * Button to choose an image.
	 */
	private lateinit var chooseImageButton: MaterialButton

	/**
	 * Currently selected color.
	 */
	private var selectedColor: Int = DEFAULT_COLOR

	/**
	 * Last custom color chosen, kept so that the custom swatch goes on showing it
	 * after a preset color is picked. 0 means that none has been chosen yet.
	 */
	private var customColor: Int = 0

	/**
	 * Switches deciding what the dawn shows on screen, paired with the flag that
	 * each one hides.
	 */
	private val shownSwitches: MutableList<Pair<SwitchCompat, Int>> = mutableListOf()

	/**
	 * Switch deciding whether a tap shows the alarm screen as normal.
	 */
	private lateinit var revealSwitch: SwitchCompat

	/**
	 * Switch for whether the light comes back on its own after a tap.
	 */
	private lateinit var revealReturnSwitch: SwitchCompat

	/**
	 * Switch that shows a dial rather than the digits.
	 */
	private lateinit var analogClockSwitch: SwitchCompat

	/**
	 * Wheel for how long a tap shows the alarm screen as normal.
	 */
	private lateinit var revealWheel: NacHorizontalWheel

	/**
	 * Text view showing how long a tap shows the alarm screen as normal.
	 */
	private lateinit var revealTextView: TextView

	/**
	 * Currently selected image path. Empty when no image has been chosen.
	 */
	private var selectedImagePath: String = ""

	/**
	 * Id of the alarm being edited, used to name the copy of the image.
	 */
	private var dawnAlarmId: Long = 0




	/**
	 * Get the display name of the selected image.
	 */
	private fun getImageDisplayName(path: String): String
	{
		return NacDawnImage.displayName(requireContext(), path, dawnAlarmId)
			?: resources.getString(R.string.message_dawn_no_image)
	}


	/**
	 * Update the alarm with the selected options.
	 */
	override fun onOkClicked(alarm: NacAlarm)
	{
		alarm.dawnDuration = durationWheel.value
		alarm.dawnColor = selectedColor
		alarm.shouldUseDawnImage = useImageSwitch.isChecked && selectedImagePath.isNotEmpty()
		alarm.dawnImagePath = selectedImagePath
		alarm.dawnFlashlightLead = flashlightWheel.value

		// Collect the flags of everything that is turned off
		var hidden = 0

		shownSwitches.forEach { (switch, flag) ->

			if (!switch.isChecked)
			{
				hidden = hidden or flag
			}

		}

		alarm.dawnHiddenViews = hidden
		alarm.dawnRevealDuration = if (revealSwitch.isChecked) revealWheel.value else 0
		alarm.shouldDawnRevealReturn = revealReturnSwitch.isChecked
		alarm.shouldUseAnalogClock = analogClockSwitch.isChecked
	}

	/**
	 * Set the text showing the duration of the dawn.
	 */
	private fun setDurationTextView(minutes: Int)
	{
		durationTextView.text = resources.getString(R.string.message_dawn_duration_value, minutes)
	}

	/**
	 * Set the text showing the name of the selected image.
	 */
	private fun setImageName()
	{
		imageNameTextView.text = getImageDisplayName(selectedImagePath)
	}

	/**
	 * Set whether the image views can be used or not.
	 */
	private fun setImageUsability()
	{
		// Get the state and alpha
		val state = useImageSwitch.isChecked
		val alpha = calcAlpha(state)

		// Set the usability of the image views
		imageNameTextView.alpha = alpha
		chooseImageButton.alpha = alpha
		chooseImageButton.isEnabled = state

		// The color is ignored when an image is used
		colorSwatches.forEach { it.isEnabled = !state }

		// Refresh the swatches so the dimming matches
		setColorSwatches()
	}

	/**
	 * Set the appearance of every color swatch.
	 */
	private fun setColorSwatches()
	{
		// The color is not used when an image is shown
		val usingImage = useImageSwitch.isChecked
		val isCustomColor = !PRESET_COLORS.contains(selectedColor)

		// Preset colors
		PRESET_COLORS.forEachIndexed { i, color ->

			val swatch = colorSwatches[i]
			val isSelected = !usingImage && (color == selectedColor)

			// Note: the swatches are always shown at full strength, otherwise the
			// colors cannot be judged. The dot underneath says which one is in use
			swatch.backgroundTintList = ColorStateList.valueOf(color)
			swatch.alpha = if (usingImage) DIMMED_ALPHA else 1.0f

			// Mark the color that is in use with a dot underneath
			colorDots.getOrNull(i)?.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

		}

		// Custom color swatch, which is the last one. It goes on showing the last
		// custom color that was chosen, even while a preset color is in use, so that
		// the choice is not lost. While none has been chosen it shows a plus on a
		// muted disc, rather than a grey circle that reads as "the dawn will be grey"
		val customSwatch = colorSwatches.last()
		val hasCustomColor = isCustomColor || (customColor != 0)
		val shownCustomColor = when
		{
			isCustomColor -> selectedColor
			customColor != 0 -> customColor
			else -> DEFAULT_CUSTOM_COLOR
		}

		customSwatch.backgroundTintList = ColorStateList.valueOf(shownCustomColor)
		customSwatch.alpha = if (usingImage) DIMMED_ALPHA else 1.0f

		// The plus only shows while there is no color to show
		(customSwatch as? TextView)?.text = if (hasCustomColor) "" else "+"

		// Mark the custom color when it is the one in use
		colorDots.lastOrNull()?.visibility = if (!usingImage && isCustomColor)
		{
			View.VISIBLE
		}
		else
		{
			View.INVISIBLE
		}
	}

	/**
	 * Called when the dialog comes back to the front, in particular after the image
	 * picker has been used.
	 */
	override fun onResume()
	{
		// Super
		super.onResume()

		// Take the image that was just chosen, if there is one
		if (takePendingImage())
		{
			setImageName()
			setImageUsability()
			setColorSwatches()
		}
	}

	/**
	 * Take the image that was chosen through the main activity, if it belongs to the
	 * alarm being edited.
	 *
	 * @return True if an image was taken, and False otherwise.
	 */
	private fun takePendingImage(): Boolean
	{
		// Nothing was chosen for this alarm
		if ((sharedPreferences.dawnPendingImageAlarmId != dawnAlarmId)
			|| sharedPreferences.dawnPendingImagePath.isEmpty())
		{
			return false
		}

		selectedImagePath = sharedPreferences.dawnPendingImagePath

		// It has been taken, so let it go
		sharedPreferences.dawnPendingImagePath = ""
		sharedPreferences.dawnPendingImageAlarmId = 0

		// Choosing an image is reason enough to use it
		if (::useImageSwitch.isInitialized)
		{
			useImageSwitch.isChecked = true
		}

		return true
	}

	/**
	 * Setup all alarm options.
	 */
	override fun setupAlarmOptions(alarm: NacAlarm)
	{
		// Set the default selected values
		dawnAlarmId = alarm.id
		selectedColor = alarm.dawnColor

		// The alarm was made before the palette changed, and its color is the one
		// that used to be the default. Show the first color of the palette instead,
		// so that a swatch is marked rather than the custom one
		if (selectedColor == OLD_DEFAULT_COLOR)
		{
			selectedColor = PRESET_COLORS[0]
		}

		// Remember the custom color, either the one this alarm is using or the last
		// one that was chosen
		customColor = if (!PRESET_COLORS.contains(selectedColor))
		{
			selectedColor
		}
		else
		{
			sharedPreferences.dawnCustomColor
		}
		selectedImagePath = alarm.dawnImagePath

		// Pick an image back up that was chosen but never applied, because the dialog
		// was closed while the file picker was in front
		val useImage = alarm.shouldUseDawnImage || takePendingImage()

		// Setup the views
		setupDuration(alarm.dawnDuration)
		setupColors()
		setupImage(useImage)
		setupAnalogClock(alarm.shouldUseAnalogClock)
		setupShown(alarm.dawnHiddenViews)
		setupReveal(alarm.dawnRevealDuration, alarm.shouldDawnRevealReturn)
		setupFlashlight(alarm.dawnFlashlightLead)
	}

	/**
	 * Setup the color views.
	 */
	private fun setupColors()
	{
		// Get the views
		val ids = intArrayOf(R.id.dawn_color_0, R.id.dawn_color_1, R.id.dawn_color_2,
			R.id.dawn_color_3, R.id.dawn_color_4, R.id.dawn_color_5, R.id.dawn_color_custom)
		val dotIds = intArrayOf(R.id.dawn_color_dot_0, R.id.dawn_color_dot_1,
			R.id.dawn_color_dot_2, R.id.dawn_color_dot_3, R.id.dawn_color_dot_4,
			R.id.dawn_color_dot_5, R.id.dawn_color_dot_custom)
		val customColorButton: MaterialButton = dialog!!.findViewById(R.id.dawn_custom_color_button)

		colorSwatches.clear()
		colorDots.clear()

		// Collect the dots that mark which color is in use
		dotIds.forEach { colorDots.add(dialog!!.findViewById(it)) }

		// Setup each preset swatch
		ids.forEachIndexed { i, id ->

			val swatch: View = dialog!!.findViewById(id)

			colorSwatches.add(swatch)

			// The last swatch is the custom color. It picks the color that is already
			// there, if there is one, and otherwise opens the color picker. The button
			// next to it always opens the picker
			if (i == PRESET_COLORS.size)
			{
				swatch.setOnClickListener {

					// Ignore the click when an image is being used
					if (useImageSwitch.isChecked)
					{
						return@setOnClickListener
					}

					// No custom color has been chosen yet, so ask for one
					if (customColor == 0)
					{
						showColorPicker()
						return@setOnClickListener
					}

					selectedColor = customColor
					setColorSwatches()

				}
			}
			else
			{
				swatch.setOnClickListener {

					// Ignore the click when an image is being used
					if (useImageSwitch.isChecked)
					{
						return@setOnClickListener
					}

					selectedColor = PRESET_COLORS[i]
					setColorSwatches()

				}
			}

		}

		// Setup the custom color button
		setupSecondaryButton(customColorButton, listener = { showColorPicker() })
	}

	/**
	 * Setup the duration views.
	 */
	private fun setupDuration(default: Int)
	{
		// Get the views
		durationTextView = dialog!!.findViewById(R.id.dawn_duration_value)
		durationWheel = dialog!!.findViewById(R.id.dawn_duration_wheel)

		// Setup the wheel
		durationWheel.minValue = MIN_DURATION
		durationWheel.maxValue = MAX_DURATION
		durationWheel.accentColor = sharedPreferences.themeColor

		// Set the duration, making sure it stays within the bounds of the wheel
		val duration = default.coerceIn(MIN_DURATION, MAX_DURATION)

		durationWheel.value = duration
		setDurationTextView(duration)

		// Set the change listener
		durationWheel.onValueChangedListener = { v ->
			setDurationTextView(v)
		}
	}

	/**
	 * Setup the flashlight lead-in views.
	 */
	private fun setupFlashlight(default: Int)
	{
		// Get the views
		flashlightTextView = dialog!!.findViewById(R.id.dawn_flashlight_value)
		flashlightWheel = dialog!!.findViewById(R.id.dawn_flashlight_wheel)

		// Setup the wheel
		flashlightWheel.minValue = 0
		flashlightWheel.maxValue = MAX_FLASHLIGHT_LEAD
		flashlightWheel.accentColor = sharedPreferences.themeColor

		val lead = default.coerceIn(0, MAX_FLASHLIGHT_LEAD)

		flashlightWheel.value = lead
		setFlashlightTextView(lead)

		// Set the change listener
		flashlightWheel.onValueChangedListener = { v ->
			setFlashlightTextView(v)
		}
	}

	/**
	 * Set the text showing how long before the alarm the flashlight fades in.
	 */
	private fun setFlashlightTextView(minutes: Int)
	{
		flashlightTextView.text = if (minutes <= 0)
		{
			resources.getString(R.string.message_dawn_flashlight_off)
		}
		else
		{
			resources.getString(R.string.message_dawn_duration_value, minutes)
		}
	}

	/**
	 * Setup the image views.
	 */
	private fun setupImage(default: Boolean)
	{
		// Get the views
		val container: View = dialog!!.findViewById(R.id.dawn_use_image_container)
		useImageSwitch = dialog!!.findViewById(R.id.dawn_use_image_switch)
		imageNameTextView = dialog!!.findViewById(R.id.dawn_image_name)
		chooseImageButton = dialog!!.findViewById(R.id.dawn_choose_image_button)

		// Setup the switch
		useImageSwitch.isChecked = default && selectedImagePath.isNotEmpty()
		useImageSwitch.setupSwitchColor(sharedPreferences)

		// Setup the image name
		setImageName()

		// Keep the image views in step with the switch, whether it was the switch
		// itself or the row around it that was tapped
		useImageSwitch.setOnCheckedChangeListener { _, _ -> setImageUsability() }

		// Set the listener on the whole row
		container.setOnClickListener { useImageSwitch.toggle() }

		// Set the listener on the choose image button. The picker is opened by the
		// main activity, not from here: Android closes this dialog while the picker
		// is in front, and the choice would be delivered to a dialog that no longer
		// exists
		setupSecondaryButton(chooseImageButton, listener = {
			(activity as? NacMainActivity)?.pickDawnImage(dawnAlarmId)
		})

		// Set the initial usability
		setImageUsability()
	}

	/**
	 * Setup the switch that shows a dial rather than the digits.
	 */
	private fun setupAnalogClock(default: Boolean)
	{
		val container: View = dialog!!.findViewById(R.id.dawn_analog_clock_container)

		analogClockSwitch = dialog!!.findViewById(R.id.dawn_analog_clock_switch)
		analogClockSwitch.isChecked = default
		analogClockSwitch.setupSwitchColor(sharedPreferences)

		// The whole row toggles the switch
		container.setOnClickListener { analogClockSwitch.toggle() }
	}

	/**
	 * Setup the switches deciding what the dawn shows on screen.
	 */
	private fun setupShown(hidden: Int)
	{
		val rows = listOf(
			Triple(R.id.dawn_show_phrase_container, R.id.dawn_show_phrase_switch, NacAlarm.DAWN_HIDE_PHRASE),
			Triple(R.id.dawn_show_name_container, R.id.dawn_show_name_switch, NacAlarm.DAWN_HIDE_NAME),
			Triple(R.id.dawn_show_clock_container, R.id.dawn_show_clock_switch, NacAlarm.DAWN_HIDE_CLOCK),
			Triple(R.id.dawn_show_date_container, R.id.dawn_show_date_switch, NacAlarm.DAWN_HIDE_DATE),
			Triple(R.id.dawn_show_media_container, R.id.dawn_show_media_switch, NacAlarm.DAWN_HIDE_MEDIA))

		shownSwitches.clear()

		rows.forEach { (containerId, switchId, flag) ->

			val container: View = dialog!!.findViewById(containerId)
			val switch: SwitchCompat = dialog!!.findViewById(switchId)

			// A switch that is on means that the thing is shown
			switch.isChecked = (hidden and flag) == 0
			switch.setupSwitchColor(sharedPreferences)

			// The whole row toggles the switch
			container.setOnClickListener { switch.toggle() }

			shownSwitches.add(switch to flag)

		}
	}

	/**
	 * Setup how long a tap shows the alarm screen as normal during the dawn.
	 */
	private fun setupReveal(default: Int, shouldReturn: Boolean)
	{
		// Get the views
		val container: View = dialog!!.findViewById(R.id.dawn_reveal_container)

		revealSwitch = dialog!!.findViewById(R.id.dawn_reveal_switch)
		revealReturnSwitch = dialog!!.findViewById(R.id.dawn_reveal_return_switch)
		revealTextView = dialog!!.findViewById(R.id.dawn_reveal_value)
		revealWheel = dialog!!.findViewById(R.id.dawn_reveal_wheel)

		// Zero means that a tap does nothing
		val seconds = if (default > 0)
		{
			default.coerceIn(MIN_REVEAL_DURATION, MAX_REVEAL_DURATION)
		}
		else
		{
			DEFAULT_REVEAL_DURATION
		}

		// Setup the switch
		revealSwitch.isChecked = (default > 0)
		revealSwitch.setupSwitchColor(sharedPreferences)
		revealSwitch.setOnCheckedChangeListener { _, _ -> setRevealUsability() }

		// The whole row toggles the switch
		container.setOnClickListener { revealSwitch.toggle() }

		// Setup the switch that says whether the light comes back on its own
		val returnContainer: View = dialog!!.findViewById(R.id.dawn_reveal_return_container)

		revealReturnSwitch.isChecked = shouldReturn
		revealReturnSwitch.setupSwitchColor(sharedPreferences)
		revealReturnSwitch.setOnCheckedChangeListener { _, _ -> setRevealUsability() }

		returnContainer.setOnClickListener {
			if (revealReturnSwitch.isEnabled)
			{
				revealReturnSwitch.toggle()
			}
		}

		// Setup the wheel
		revealWheel.minValue = MIN_REVEAL_DURATION
		revealWheel.maxValue = MAX_REVEAL_DURATION
		revealWheel.accentColor = sharedPreferences.themeColor
		revealWheel.value = seconds

		setRevealTextView(seconds)

		revealWheel.onValueChangedListener = { v -> setRevealTextView(v) }

		// Set the initial usability
		setRevealUsability()
	}

	/**
	 * Set the text showing how long a tap shows the alarm screen as normal.
	 */
	private fun setRevealTextView(seconds: Int)
	{
		revealTextView.text = resources.getString(R.string.message_dawn_reveal_value, seconds)
	}

	/**
	 * Set whether the reveal wheel can be used or not.
	 */
	private fun setRevealUsability()
	{
		val state = revealSwitch.isChecked
		val alpha = calcAlpha(state)

		// The row below hangs on the tap being of any use at all
		revealReturnSwitch.isEnabled = state
		(revealReturnSwitch.parent as? View)?.alpha = alpha

		// The duration only means something when the light comes back on its own
		val durationState = state && revealReturnSwitch.isChecked
		val durationAlpha = calcAlpha(durationState)

		revealTextView.alpha = durationAlpha
		revealWheel.alpha = durationAlpha
		revealWheel.isEnabled = durationState
	}

	/**
	 * Setup any extra buttons.
	 */
	override fun setupExtraButtons(alarm: NacAlarm)
	{
		// Get the button
		val previewButton: MaterialButton = dialog!!.findViewById(R.id.preview_button)

		// Setup the button
		setupSecondaryButton(previewButton, listener = {

			// Show the preview over this dialog, with the options currently selected.
			// Note: a dialog and not a separate screen, otherwise the app would go
			// off screen and Android would close this dialog underneath, landing on
			// the alarm list on the way back
			val imagePath = if (useImageSwitch.isChecked) selectedImagePath else ""

			// Collect the flags of everything that is turned off, so that the preview
			// looks like the real thing
			var hidden = 0

			shownSwitches.forEach { (switch, flag) ->

				if (!switch.isChecked)
				{
					hidden = hidden or flag
				}

			}

			NacDawnPreviewDialog.show(parentFragmentManager, selectedColor, imagePath,
				hidden = hidden,
				revealSeconds = if (revealSwitch.isChecked) revealWheel.value else 0,
				alarmName = alarm.name,
				mediaTitle = alarm.mediaTitle,
				useAnalogClock = analogClockSwitch.isChecked)

		})
	}

	/**
	 * Show the color picker so that a custom color can be chosen.
	 */
	private fun showColorPicker()
	{
		// The color is ignored when an image is being used
		if (useImageSwitch.isChecked)
		{
			return
		}

		// Create the dialog
		val colorPicker = NacColorPickerDialog()

		// Setup the dialog
		colorPicker.initialColor = selectedColor
		colorPicker.onColorSelectedListener = NacColorPickerDialog.OnColorSelectedListener { color ->
			selectedColor = color

			// Keep the custom color, unless a preset color was picked in the picker
			if (!PRESET_COLORS.contains(color))
			{
				customColor = color
				sharedPreferences.dawnCustomColor = color
			}

			setColorSwatches()
		}
		colorPicker.onDefaultColorSelectedListener = NacColorPickerDialog.OnDefaultColorSelectedListener {
			selectedColor = DEFAULT_COLOR
			setColorSwatches()
		}

		// Show the dialog
		colorPicker.show(childFragmentManager, NacColorPickerDialog.TAG)
	}

	companion object
	{

		/**
		 * Default color of the dawn. Amber.
		 */
		const val DEFAULT_COLOR: Int = 0xFFFFEC9D.toInt()

		/**
		 * Color that used to be the default, before the palette was changed.
		 */
		private const val OLD_DEFAULT_COLOR: Int = 0xFFFFC98A.toInt()

		/**
		 * Color shown in the custom swatch when no custom color has been chosen.
		 */
		private const val DEFAULT_CUSTOM_COLOR: Int = 0xFF3C3C3C.toInt()

		/**
		 * Alpha of a swatch that is not selected.
		 */
		private const val UNSELECTED_ALPHA: Float = 0.45f

		/**
		 * Alpha of a swatch that cannot be used because an image is being shown.
		 */
		private const val DIMMED_ALPHA: Float = 0.25f

		/**
		 * Shortest a tap can show the alarm screen as normal. [Units: sec]
		 */
		const val MIN_REVEAL_DURATION: Int = 3

		/**
		 * Longest a tap can show the alarm screen as normal. [Units: sec]
		 */
		const val MAX_REVEAL_DURATION: Int = 60

		/**
		 * How long a tap shows the alarm screen as normal, when it is turned back
		 * on. [Units: sec]
		 */
		private const val DEFAULT_REVEAL_DURATION: Int = 10

		/**
		 * Minimum duration of the dawn. [Units: min]
		 */
		const val MIN_DURATION: Int = 1

		/**
		 * Maximum duration of the dawn. [Units: min]
		 */
		const val MAX_DURATION: Int = 60

		/**
		 * Longest lead-in of the flashlight. [Units: min]
		 */
		const val MAX_FLASHLIGHT_LEAD: Int = 5

		/**
		 * The preset colors that can be chosen.
		 */
		val PRESET_COLORS: IntArray = intArrayOf(
			0xFFFFEC9D.toInt(),  // Sunlight, the yellow of a low sun
			0xFFFFFFFF.toInt(),  // White
			0xFFFFB84D.toInt(),  // Amber
			0xFFD8F0C0.toInt(),  // Pale green
			0xFFCDE9FF.toInt(),  // Pale blue
			0xFFC9A7FF.toInt())  // Lilac

	}

}
