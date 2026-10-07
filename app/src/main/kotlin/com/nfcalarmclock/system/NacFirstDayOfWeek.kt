package com.nfcalarmclock.system

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Calendar
import java.util.Locale

/**
 * First day of the week of the phone, as a Calendar day (Calendar.SUNDAY,
 * Calendar.MONDAY...).
 *
 * The phone, not the app: the language chosen for the app (Settings, General) must
 * not move the week. So the locale is the one of the system, and on Android 14 and
 * later the "First day of week" of the regional preferences comes first, when one
 * is set. It travels with the system locale as the "fw" Unicode extension, for
 * instance en-US-u-fw-mon (2.01).
 *
 * Then the country the phone is in, from its SIM card, or else from the network. The
 * country of the system language says little: "English" is often English (United
 * States), which starts on Sunday, on a phone used in Belgium or in Spain. ColorOS
 * keeps its own "Region" apart, in a place Android does not let an app read.
 *
 * Last, the language and country of the system.
 */
fun getPhoneFirstDayOfWeek(context: Context): Int
{
	val systemLocale: Locale = Resources.getSystem().configuration.locales[0]
		?: Locale.getDefault()

	// Regional preference of the phone
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	{
		val day = when (systemLocale.getUnicodeLocaleType("fw"))
		{
			"sun" -> Calendar.SUNDAY
			"mon" -> Calendar.MONDAY
			"tue" -> Calendar.TUESDAY
			"wed" -> Calendar.WEDNESDAY
			"thu" -> Calendar.THURSDAY
			"fri" -> Calendar.FRIDAY
			"sat" -> Calendar.SATURDAY
			else -> null
		}

		if (day != null)
		{
			return day
		}
	}

	// Country the phone is in
	val country = getPhoneCountry(context)

	if (country.length == 2)
	{
		try
		{
			val locale = Locale.Builder()
				.setLanguage(systemLocale.language)
				.setRegion(country)
				.build()

			return Calendar.getInstance(locale).firstDayOfWeek
		}
		catch (e: Exception)
		{
			// Not a country code Java knows. The system locale decides
		}
	}

	// Language and region of the phone
	return Calendar.getInstance(systemLocale).firstDayOfWeek
}

/**
 * Country of the SIM card, or else of the network, in capitals ("BE", "ES"...), or
 * an empty string when there is neither. Neither asks for a permission.
 */
private fun getPhoneCountry(context: Context): String
{
	return try
	{
		val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
			?: return ""
		val sim = manager.simCountryIso ?: ""
		val country = sim.ifEmpty { manager.networkCountryIso ?: "" }

		country.uppercase(Locale.ROOT)
	}
	catch (e: Exception)
	{
		""
	}
}
