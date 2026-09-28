package com.nfcalarmclock.settings.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.options.tts.NacSayTimeService
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.view.setupSwitchColor

/**
 * A row with two controls: the text opens a page, the switch beside it turns the
 * spoken time on or off without leaving the list: the shake and the hand together,
 * each keeping its own switch on the page for when it comes back on.
 *
 * The row keeps its own key, so the fragment still knows where to navigate. The
 * switch answers for another key entirely, which it reads and writes itself rather
 * than through the preference framework, and it reads that key afresh every time the
 * row is bound so that a change made on the page it opens shows on the way back.
 *
 * @param context Context.
 * @param attrs Attribute set.
 */
class NacTwoTargetSwitchPreference(
	context: Context,
	attrs: AttributeSet?
) : Preference(context, attrs)
{

	/**
	 * Constructor.
	 */
	init
	{
		// Set the layout
		layoutResource = R.layout.nac_preference_switch_two_target
	}

	/**
	 * Called when the view holder is bound.
	 */
	override fun onBindViewHolder(holder: PreferenceViewHolder)
	{
		// Super
		super.onBindViewHolder(holder)

		// Create the shared preferences
		val shared = NacSharedPreferences(context)

		// Setup the views
		val imageFrame = holder.findViewById(R.id.icon_frame) as LinearLayout
		val imageView = holder.findViewById(R.id.icon) as ImageView
		val widgetFrame = holder.findViewById(R.id.widget_frame) as FrameLayout
		val switch = holder.findViewById(R.id.widget) as SwitchCompat

		// Setup the icon
		if (icon != null)
		{
			imageView.setImageDrawable(icon)
			imageFrame.visibility = View.VISIBLE
		}
		// Hide the icon frame
		else
		{
			imageFrame.visibility = View.GONE
		}

		// Setup the switch
		switch.setupSwitchColor(shared)
		switch.isChecked = shared.shouldSayTime

		// Only the frame takes the touch. The row underneath keeps the click that
		// opens the page, because a child that handles a touch does not pass it on
		widgetFrame.setOnClickListener {

			val state = !switch.isChecked

			// The service reads what is saved, so write it before asking for it
			switch.isChecked = state
			NacSharedPreferences(context).shouldSayTime = state

			// Writing the setting is not enough: the service goes on listening, and its
			// notification stays, until it is told to look again. The page does the
			// same after its own switch
			NacSayTimeService.refresh(context)

		}
	}

}
