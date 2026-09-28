package com.nfcalarmclock.system.permission.fullscreenintent

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.nfcalarmclock.BuildConfig
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences

/**
 * Helper functions for the USE_FULL_SCREEN_INTENT permission.
 *
 * This is the permission that lets the alarm screen come up by itself when the phone
 * is locked or asleep. Up to Android 13 it was granted at install time and nothing
 * could take it away. From Android 14 the user can refuse it, and then the alarm rings
 * with only a notification, no screen.
 *
 * The About page used to say "you already have this permission" without checking
 * anything, which was true until Android 14 and is a lie since.
 */
object NacFullScreenIntentPermission
{

	/**
	 * The name of the permission.
	 */
	const val permissionName: String = "android.permission.USE_FULL_SCREEN_INTENT"

	/**
	 * Whether this Android version lets the user refuse the permission.
	 */
	val isCorrectAndroidVersion: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

	/**
	 * Check if the app can show a full screen intent.
	 */
	fun hasPermission(context: Context): Boolean
	{
		// Granted at install time on every version before Android 14, and it cannot be
		// taken away there
		if (!isCorrectAndroidVersion)
		{
			return true
		}

		val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
			as? NotificationManager
			?: return true

		return manager.canUseFullScreenIntent()
	}

	/**
	 * Check whether the app should ask for the permission.
	 */
	fun shouldRequestPermission(
		context: Context,
		shared: NacSharedPreferences
	): Boolean
	{
		// Nothing to ask for before Android 14, where it is granted at install time
		if (!isCorrectAndroidVersion || hasPermission(context))
		{
			return false
		}

		// It was in hand and it is gone, or this version of the app has not asked yet.
		// An app update is what takes this one away, and without it the alarm rings
		// behind a notification with no screen, so it is worth one dialog per update
		return shared.wasFullScreenIntentPermissionGranted
			|| !shared.wasFullScreenIntentPermissionRequested
			|| (shared.fullScreenIntentPermissionAskedAtVersion != BuildConfig.VERSION_NAME)
	}

	/**
	 * Remember that the permission is in hand, so that its loss can be noticed later.
	 */
	fun rememberIfGranted(context: Context, shared: NacSharedPreferences)
	{
		if (isCorrectAndroidVersion && hasPermission(context))
		{
			shared.wasFullScreenIntentPermissionGranted = true
		}
	}

	/**
	 * Open the screen where the permission is given.
	 */
	fun requestPermission(activity: Activity?)
	{
		if (!isCorrectAndroidVersion || (activity == null))
		{
			return
		}

		val uri = ("package:" + activity.packageName).toUri()
		val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, uri)

		try
		{
			activity.startActivity(intent)
		}
		catch (e: Exception)
		{
			// Some makers do not carry that screen. The app settings are the next best
			// place to land
			NacLog.e("No screen for the full screen intent permission", throwable = e)

			try
			{
				activity.startActivity(
					Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to open the app settings either", throwable = e)
			}
		}
	}

}
