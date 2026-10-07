package com.nfcalarmclock.alarm.options

import android.content.Context
import android.view.KeyEvent
import com.nfcalarmclock.R

/**
 * The buttons of the phone that can snooze or dismiss a ringing alarm.
 *
 * Which button does what is chosen in Settings, General, and holds for every alarm
 * (2.02: the switches of each alarm are gone, "None" turns it off). The power button
 * is seen as the screen turning off, which is the only sign of it Android gives an app.
 */
object NacAlarmButton
{

	/**
	 * No button.
	 */
	const val NONE = "none"

	/**
	 * Volume up.
	 */
	const val VOLUME_UP = "volume_up"

	/**
	 * Volume down.
	 */
	const val VOLUME_DOWN = "volume_down"

	/**
	 * Either volume key.
	 */
	const val VOLUME_ANY = "volume_any"

	/**
	 * The power button.
	 */
	const val POWER = "power"

	/**
	 * Any of the buttons: volume up, volume down or power.
	 */
	const val ANY = "any"

	/**
	 * Default button that snoozes: none, so that nothing changes until it is chosen.
	 */
	const val DEFAULT_SNOOZE = NONE

	/**
	 * Default button that dismisses: none.
	 */
	const val DEFAULT_DISMISS = NONE

	/**
	 * Every choice, in the order they are offered.
	 */
	val ALL = listOf(NONE, VOLUME_UP, VOLUME_DOWN, VOLUME_ANY, POWER, ANY)

	/**
	 * Name of a button, as shown in the settings.
	 */
	fun name(context: Context, button: String): String
	{
		val resId = when (button)
		{
			NONE -> R.string.word_button_none
			VOLUME_UP -> R.string.word_button_volume_up
			VOLUME_DOWN -> R.string.word_button_volume_down
			POWER -> R.string.word_button_power
			ANY -> R.string.word_button_any
			else -> R.string.word_button_volume_any
		}

		return context.getString(resId)
	}

	/**
	 * Whether a button was chosen at all.
	 */
	fun isOn(button: String): Boolean
	{
		return button in ALL && (button != NONE)
	}

	/**
	 * Whether this choice includes a volume key.
	 */
	fun usesVolume(button: String): Boolean
	{
		return (button == VOLUME_UP) || (button == VOLUME_DOWN)
			|| (button == VOLUME_ANY) || (button == ANY)
	}

	/**
	 * Whether a press of volume up (or down) is this choice.
	 */
	fun matchesVolume(button: String, isUp: Boolean): Boolean
	{
		return when (button)
		{
			VOLUME_UP -> isUp
			VOLUME_DOWN -> !isUp
			VOLUME_ANY, ANY -> true
			else -> false
		}
	}

	/**
	 * Whether the power button is this choice.
	 */
	fun matchesPower(button: String): Boolean
	{
		return (button == POWER) || (button == ANY)
	}

	/**
	 * Whether a key code is this choice.
	 */
	fun matchesKey(button: String, keyCode: Int): Boolean
	{
		return when (keyCode)
		{
			KeyEvent.KEYCODE_VOLUME_UP -> matchesVolume(button, isUp = true)
			KeyEvent.KEYCODE_VOLUME_DOWN -> matchesVolume(button, isUp = false)
			else -> false
		}
	}

	/**
	 * Whether two choices share a button, which would make one press both snooze and
	 * dismiss. None shares nothing.
	 */
	fun overlap(a: String, b: String): Boolean
	{
		if ((a == NONE) || (b == NONE))
		{
			return false
		}

		if ((a == ANY) || (b == ANY))
		{
			return true
		}

		if ((a == POWER) || (b == POWER))
		{
			return a == b
		}

		return (a == b) || (a == VOLUME_ANY) || (b == VOLUME_ANY)
	}

}
