package com.nfcalarmclock.settings.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Preference that fades when whatever it hangs on is turned off.
 *
 * A disabled preference of the library simply stops answering to a tap. The colors of
 * this app are fixed rather than taken from a state list, so nothing on screen would
 * say that the row is no longer listening. This one dims itself, as the switches do.
 *
 * @param context Context.
 * @param attrs Attribute set.
 */
open class NacPreference(
	context: Context,
	attrs: AttributeSet?
) : Preference(context, attrs)
{

	/**
	 * Called when the view holder is bound.
	 */
	override fun onBindViewHolder(holder: PreferenceViewHolder)
	{
		// Super
		super.onBindViewHolder(holder)

		// Say plainly whether this row is listening
		holder.itemView.alpha = if (isEnabled) 1.0f else DISABLED_ALPHA
	}

	companion object
	{

		/**
		 * How faint a row that is turned off becomes. The same as the switches use.
		 */
		private const val DISABLED_ALPHA = 0.25f

	}

}
