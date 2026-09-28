package com.nfcalarmclock.system.permission.fullscreenintent

import com.nfcalarmclock.BuildConfig
import com.nfcalarmclock.R
import com.nfcalarmclock.system.permission.NacPermissionRequestDialog

/**
 * Dialog to request the USE_FULL_SCREEN_INTENT permission.
 *
 * This is the permission that brings the alarm screen, and the sunrise, up on a
 * locked phone. Until now only the tutorial and the About page offered it, so a
 * refusal at the first launch was never brought up again.
 */
class NacFullScreenIntentPermissionRequestDialog
	: NacPermissionRequestDialog()
{

	/**
	 * The name of the permission.
	 */
	override val permission: String = NacFullScreenIntentPermission.permissionName

	/**
	 * The ID of the icon.
	 */
	override val iconId: Int = R.drawable.desktop_windows_32

	/**
	 * The ID of the title string.
	 */
	override val titleId: Int = R.string.title_permission_full_screen_intent

	/**
	 * The ID of the text string.
	 */
	override val descriptionId: Int = R.string.description_onboarding_permission_full_screen_intent

	/**
	 * The actions to execute when the permission request is accepted.
	 */
	override fun doPermissionRequestAccepted()
	{
		// Set the flag that the permission was requested, and forget that it was ever
		// granted, so that one loss brings up one dialog and not one at every launch
		sharedPreferences.wasFullScreenIntentPermissionRequested = true
		sharedPreferences.wasFullScreenIntentPermissionGranted = false
		sharedPreferences.fullScreenIntentPermissionAskedAtVersion = BuildConfig.VERSION_NAME

		// Call the accepted listeners
		super.doPermissionRequestAccepted()
	}

	/**
	 * The actions to execute when the permission request is canceled.
	 */
	override fun doPermissionRequestCanceled()
	{
		// Set the flag that the permission was requested, and forget that it was ever
		// granted, so that one loss brings up one dialog and not one at every launch
		sharedPreferences.wasFullScreenIntentPermissionRequested = true
		sharedPreferences.wasFullScreenIntentPermissionGranted = false
		sharedPreferences.fullScreenIntentPermissionAskedAtVersion = BuildConfig.VERSION_NAME

		// Call the canceled listeners
		super.doPermissionRequestCanceled()
	}

	companion object
	{

		/**
		 * Tag for the class.
		 */
		const val TAG = "NacFullScreenIntentPermissionDialog"

	}

}
