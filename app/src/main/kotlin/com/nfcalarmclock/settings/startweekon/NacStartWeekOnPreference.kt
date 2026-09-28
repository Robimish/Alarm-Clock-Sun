package com.nfcalarmclock.settings.startweekon

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import java.util.Calendar
import com.nfcalarmclock.R
import com.nfcalarmclock.shared.NacSharedPreferences

/**
 * Preference that prompts the user what day to start the week on.
 */
class NacStartWeekOnPreference @JvmOverloads constructor(

	/**
	 * Context.
	 */
	context: Context,

	/**
	 * Attribute set.
	 */
	attrs: AttributeSet? = null,

	/**
	 * Default style.
	 */
	style: Int = 0

	// Constructor
) : Preference(context, attrs, style)
{
	/**
	 * Index of the day to start on.
	 */
	private var startWeekOnIndex = 0

	/**
	 * Constructor.
	 */
	init
	{
		layoutResource = R.layout.nac_preference
	}

	/**
	 * Get the summary text which is the name of the day to start on.
	 *
	 * @return The summary text which is the name of the day to start on.
	 */
	override fun getSummary(): CharSequence?
	{
		val week = context.resources.getStringArray(R.array.days_of_week_full)
		val options = context.resources.getStringArray(R.array.start_week_on)

		return when (startWeekOnIndex)
			{

				// Sunday or Monday, as chosen
				0, 1 -> week[startWeekOnIndex]

				// Automatic: also say which day the phone settles on, so that the
				// summary matches what the week actually shows
				2 ->
				{
					val day = Calendar.getInstance().firstDayOfWeek - Calendar.SUNDAY

					"${options[2]} (${week[day.coerceIn(0, 6)]})"
				}

				// Anything else should not happen
				else -> week[0]

			}
	}

	/**
	 * Get the default value.
	 *
	 * @return The default value.
	 */
	override fun onGetDefaultValue(a: TypedArray, index: Int): Any
	{
		val defaultValue = context.resources.getInteger(R.integer.default_start_week_on_index)

		return a.getInteger(index, defaultValue)
	}

	/**
	 * Set the initial preference value.
	 */
	override fun onSetInitialValue(defaultValue: Any?)
	{
		// Check if the default value is null
		if (defaultValue == null)
		{
			startWeekOnIndex = getPersistedInt(startWeekOnIndex)
		}
		// Convert the default value
		else
		{
			startWeekOnIndex = defaultValue as Int

			persistInt(startWeekOnIndex)
		}
	}

	/**
	 * Show the start week on dialog.
	 */
	fun showDialog(manager: FragmentManager)
	{
		// Create the dialog
		val dialog = NacStartWeekOnDialog()

		// Setup the dialog
		dialog.defaultSelectedIndex = startWeekOnIndex
		dialog.onStartWeekSelectedListener = NacStartWeekOnDialog.OnStartWeekSelectedListener { which ->

			// Set the index of the day to start on
			startWeekOnIndex = which

			// Persist this index
			persistInt(startWeekOnIndex)

			// Set flag to refresh the main activity so that the days are redrawn
			val shared = NacSharedPreferences(context)

			shared.shouldRefreshMainActivity = true

			// Notify that a change occurred
			notifyChanged()

		}

		// Show the dialog
		dialog.show(manager, NacStartWeekOnDialog.TAG)
	}

}