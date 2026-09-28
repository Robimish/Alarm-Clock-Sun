package com.nfcalarmclock.system.permission.scheduleexactalarm

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.nfcalarmclock.log.NacLog

/**
 * Helper functions for scheduling exact alarms.
 *
 * Before Android 12 there is nothing to hold. On Android 12 and 12L the app holds
 * SCHEDULE_EXACT_ALARM, which the user can take away in the settings. From Android 13
 * it holds USE_EXACT_ALARM, granted at install time, which cannot be taken away.
 *
 * The About page used to say "you already have this permission" without checking
 * anything. The alarm manager is asked instead, since it is the one that refuses.
 */
object NacScheduleExactAlarmPermission
{

	/**
	 * Whether this Android version has a permission for exact alarms at all.
	 */
	val isCorrectAndroidVersion: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

	/**
	 * Check if the app can schedule exact alarms.
	 */
	fun hasPermission(context: Context): Boolean
	{
		// Nothing to hold before Android 12
		if (!isCorrectAndroidVersion)
		{
			return true
		}

		val manager = context.getSystemService(Context.ALARM_SERVICE)
			as? AlarmManager
			?: return true

		return manager.canScheduleExactAlarms()
	}

	/**
	 * Open the screen where the user gives the permission back.
	 */
	fun requestPermission(activity: Activity?)
	{
		if (!isCorrectAndroidVersion || (activity == null))
		{
			return
		}

		val uri = ("package:" + activity.packageName).toUri()
		val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)

		try
		{
			activity.startActivity(intent)
		}
		catch (e: Exception)
		{
			// Some makers do not carry that screen. The app settings are the next best
			// place to land
			NacLog.e("No screen for the exact alarm permission", throwable = e)

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
