package com.nfcalarmclock

import android.app.Application
import android.os.Build
import com.nfcalarmclock.alarm.options.tts.NacSayTimeService
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import dagger.hilt.android.HiltAndroidApp

/**
 * NFC Alarm Clock application.
 */
@HiltAndroidApp
class NacNfcAlarmClockApplication : Application()
{

	/**
	 * Application is created.
	 */
	override fun onCreate()
	{
		// Super
		super.onCreate()

		// Move the shared preference to device protected storage
		NacSharedPreferences.moveToDeviceProtectedStorage(this)

		// Create shared preferences
		val sharedPreferences = NacSharedPreferences(this)

		// Initialize logger
		NacLog.init(this, sharedPreferences)
		NacLog.i("Starting app v${BuildConfig.VERSION_NAME} (Android API ${Build.VERSION.SDK_INT})")

		// Check if the shake sensitivity still points into the list of three that was
		// replaced by one of four. Here rather than with the other events, which wait
		// for the main screen: the service below reads that setting straight away, and
		// would spend a whole launch on the wrong one
		if (!sharedPreferences.eventShakeSensitivityFiner)
		{
			sharedPreferences.runEventShakeSensitivityFiner()
		}

		// Put the notification that says the time back, if it is wanted. It is gone
		// after an update, and the user may have found a way to swipe it away
		// Put the listeners back, if they are wanted. This can be refused when the
		// process was woken in the background, and the next launch will do it
		NacSayTimeService.refresh(this)
	}

}