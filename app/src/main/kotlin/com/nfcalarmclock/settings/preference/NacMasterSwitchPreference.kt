package com.nfcalarmclock.settings.preference

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import com.nfcalarmclock.R
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.view.setupSwitchColor

/**
 * The switch a whole section hangs on, written larger than the rows it governs.
 *
 * Nothing else sets it apart from an ordinary setting, and a section whose other rows
 * are greyed out is only clear once it is obvious which switch turns them back on.
 *
 * @param context Context.
 * @param attrs Attribute set.
 */
class NacMasterSwitchPreference(
	context: Context,
	attrs: AttributeSet?
) : NacCompoundButtonPreference(context, attrs, R.layout.nac_preference_switch_master)
{

	/**
	 * Called when the view holder is bound.
	 */
	override fun onBindViewHolder(holder: PreferenceViewHolder)
	{
		// Super
		super.onBindViewHolder(holder)

		// Create the shared preferences
		val shared = NacSharedPreferences(context)

		// Setup the color
		(compoundButton as SwitchCompat).setupSwitchColor(shared)
	}

}
