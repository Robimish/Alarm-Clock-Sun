package com.nfcalarmclock.system.permission.systemalertwindow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.nfcalarmclock.shared.NacSharedPreferences

/**
 * Helper functions for the SCHEDULE_EXACT_ALARM permission.
 */
@Suppress("SameReturnValue")
object NacSystemAlertWindowPermission
{

	/**
	 * Check if the correct Android version is being used.
	 */
	val isCorrectAndroidVersion: Boolean
		get() {
			// Note: upstream only asked for this on Android 15. Android has
			// restricted starting an activity from the background since Android 10,
			// and the alarm screen, dawn included, is exactly that. Tested on a
			// phone running Android 16: without this permission the alarm rings but
			// no screen comes up. So ask for it from Android 10 onwards
			return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
		}

	/**
	 * The name of the SYSTEM_ALERT_WINDOW permission.
	 */
	val permissionName: String
		get() = Manifest.permission.SYSTEM_ALERT_WINDOW

	/**
	 * Check if the app has the SYSTEM_ALERT_WINDOW permission.
	 */
	fun hasPermission(context: Context): Boolean
	{
		// Android version not correct so indicate it already has the
		// permission, for simplicity
		if (!isCorrectAndroidVersion)
		{
			return true
		}

		// Check if the app has permission
		return Settings.canDrawOverlays(context)
	}

	/**
	 * Request the SYSTEM_ALERT_WINDOW permission.
	 */
	fun requestPermission(activity: Activity?)
	{
		// Permission not required for API level < 33
		if (!isCorrectAndroidVersion || (activity == null))
		{
			return
		}

		// If not, form up an Intent to launch the permission request
		val uri = ("package:"+activity.packageName).toUri()
		val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)

		// Launch Intent, with the supplied request code
		activity.startActivity(intent)
	}

	/**
	 * Check whether the app should request the SYSTEM_ALERT_WINDOW permission.
	 *
	 * @return True if the app should request the SYSTEM_ALERT_WINDOW permission,
	 *         and False otherwise.
	 */
	@Suppress("UNUSED_PARAMETER")
	fun shouldRequestPermission(
		context: Context,
		shared: NacSharedPreferences
	): Boolean
	{
		// Android version not correct so indicate it should not request
		// the permission, for simplicity
		return if (!isCorrectAndroidVersion)
		{
			false
		}
		// The app does not already have the permission.
		// The permission has not been requested yet.
		// The full screen notification is missing: that one is what brings the alarm
		// screen and the sunrise up, and where it is granted this permission has
		// nothing left to add, so there is no reason to ask for it
		// Already in hand
		else if (hasPermission(context))
		{
			false
		}
		// It was in hand and it is gone, an app update most of the time: it is not
		// offered again. The full screen notification is what brings the alarm screen,
		// the sunrise and the ringing timer up, and it is asked for again after every
		// update on its own. Asking for this one as well sent the user through "Allow
		// restricted settings" after each update for nothing (1.94)
		// Never asked for on its own any more, not even on a new install. In 1.94 it was
		// still offered when it had never been asked for and the full screen
		// notification was missing, which is the state right after an update, so the
		// 2/2 page kept coming back. The full screen notification is enough on its own:
		// the alarm, the sunrise and the timer have all been seen on a locked phone
		// without this permission (1.48, 1.94). It can still be given from About
		else
		{
			false
		}
	}

	/**
	 * Remember that the permission is in hand, so that its loss can be noticed later.
	 */
	fun rememberIfGranted(context: Context, shared: NacSharedPreferences)
	{
		if (isCorrectAndroidVersion && hasPermission(context))
		{
			shared.wasSystemAlertWindowPermissionGranted = true
		}
	}

}