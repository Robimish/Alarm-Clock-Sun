package com.nfcalarmclock.system.permission.setalarm

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper functions for the SET_ALARM permission, which lets another app, such as a
 * voice assistant, set an alarm here.
 *
 * It is a normal permission: Android grants it at install time and the user has no
 * switch to take it away, so the check below is expected to always pass. It is asked
 * all the same, rather than taken for granted, so that the About page says what
 * Android says.
 */
object NacSetAlarmPermission
{

	/**
	 * The name of the permission.
	 */
	const val permissionName: String = "com.android.alarm.permission.SET_ALARM"

	/**
	 * Check if the app holds the permission.
	 */
	fun hasPermission(context: Context): Boolean
	{
		return ContextCompat.checkSelfPermission(context, permissionName) ==
			PackageManager.PERMISSION_GRANTED
	}

}
