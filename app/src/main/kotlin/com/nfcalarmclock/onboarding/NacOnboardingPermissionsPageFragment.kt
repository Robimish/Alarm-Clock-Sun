package com.nfcalarmclock.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.nfcalarmclock.R
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.permission.fullscreenintent.NacFullScreenIntentPermission
import com.nfcalarmclock.system.permission.postnotifications.NacPostNotificationsPermission
import com.nfcalarmclock.system.permission.systemalertwindow.NacSystemAlertWindowPermission
import com.nfcalarmclock.view.animateHide
import com.nfcalarmclock.view.animateShow

/**
 * Onboarding permissions page.
 */
class NacOnboardingPermissionsPageFragment
	: Fragment()
{

	/**
	 * Shared preference.
	 */
	private val sharedPreferences: NacSharedPreferences by lazy { NacSharedPreferences(requireContext()) }

	/**
	 * Show notifications permission allow button.
	 */
	private	lateinit var showNotificationsAllowButton: MaterialButton

	/**
	 * Full screen notification permission allow button.
	 */
	private	lateinit var fullScreenAllowButton: MaterialButton

	/**
	 * System alert permission allow button.
	 */
	private	lateinit var systemAlertAllowButton: MaterialButton

	/**
	 * Show notifications permission check mark, for when it is allowed.
	 */
	private	lateinit var showNotificationsCheckMark: ImageView

	/**
	 * Full screen notification permission check mark, for when it is allowed.
	 */
	private	lateinit var fullScreenCheckMark: ImageView

	/**
	 * System alert permission check mark, for when it is allowed.
	 */
	private	lateinit var systemAlertCheckMark: ImageView

	/**
	 * The rest of the system alert section, so that it can step aside once the full
	 * screen notification is granted and there is nothing left for it to add.
	 */
	private	lateinit var systemAlertTitle: TextView
	private	lateinit var systemAlertDescription: TextView
	private	lateinit var systemAlertAppInfoButton: MaterialButton

	/**
	 * Handler to periodically check if the SYSTEM_ALERT permission was accepted.
	 */
	private lateinit var systemAlertCheckHandler: Handler

	/**
	 * Get the result after the show notifications permission is requested.
	 */
	private val showNotificationsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->

		// Permission not granted
		if (!granted)
		{
			return@registerForActivityResult
		}

		// Animate the views
		showNotificationsAllowButton.animateHide(300)
		showNotificationsCheckMark.animateShow(400)

		// Set the flag indicating the permission was requested
		sharedPreferences.wasPostNotificationsPermissionRequested = true

	}

	/**
	 * Create view.
	 */
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View?
	{
		return inflater.inflate(R.layout.frg_onboarding_permissions_page, container, false)
	}

	/**
	 * Fragment started.
	 */
	override fun onStart()
	{
		// Super
		super.onStart()

		// Start the system alert permission checker
		systemAlertPermissionChecker()
	}

	/**
	 * Fragment stopped.
	 */
	override fun onStop()
	{
		// Super
		super.onStop()

		// Stop the system alert permission checker
		systemAlertCheckHandler.removeCallbacksAndMessages(null)
	}

	/**
	 * View created.
	 */
	@SuppressLint("NewApi")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?)
	{
		// Super
		super.onViewCreated(view, savedInstanceState)

		// Get the activity and context to request permissions
		//val activity = requireActivity()
		val context = requireContext()

		// Set the handler
		systemAlertCheckHandler = Handler(context.mainLooper)

		// Set the main views
		showNotificationsAllowButton = view.findViewById(R.id.onboarding_notification_allow_button)
		showNotificationsCheckMark = view.findViewById(R.id.onboarding_notification_check_mark)
		fullScreenAllowButton = view.findViewById(R.id.onboarding_full_screen_allow_button)
		fullScreenCheckMark = view.findViewById(R.id.onboarding_full_screen_check_mark)
		systemAlertAllowButton = view.findViewById(R.id.onboarding_system_alert_allow_button)
		systemAlertAppInfoButton = view.findViewById(R.id.onboarding_system_alert_app_info_button)
		systemAlertCheckMark = view.findViewById(R.id.onboarding_system_alert_check_mark)

		// Show notification views and visibility
		val showNotificationsTitle: TextView = view.findViewById(R.id.onboarding_notification_title)
		val showNotificationsDescription: TextView = view.findViewById(R.id.onboarding_notification_description)
		val showNotificationsVisibility = if (NacPostNotificationsPermission.shouldRequestPermission(context, sharedPreferences))
		{
			View.VISIBLE
		}
		else
		{
			View.GONE
		}

		// Full screen notification views and visibility. This is the permission that
		// actually brings the alarm screen up, so it comes first of the two
		val fullScreenTitle: TextView = view.findViewById(R.id.onboarding_full_screen_title)
		val fullScreenDescription: TextView = view.findViewById(R.id.onboarding_full_screen_description)
		val fullScreenVisibility = if (NacFullScreenIntentPermission.shouldRequestPermission(context, sharedPreferences))
		{
			View.VISIBLE
		}
		else
		{
			View.GONE
		}

		// System alert (display over other apps) views and visibilty. It is only
		// offered while the full screen notification is missing: that one is what
		// brings the screen up, and where it is granted this one has nothing to add
		systemAlertTitle = view.findViewById(R.id.onboarding_system_alert_title)
		systemAlertDescription = view.findViewById(R.id.onboarding_system_alert_description)
		val systemAlertVisibility = if (NacSystemAlertWindowPermission.shouldRequestPermission(context, sharedPreferences))
		{
			View.VISIBLE
		}
		else
		{
			View.GONE
		}

		// Set the visibility of everything
		showNotificationsAllowButton.visibility = showNotificationsVisibility
		showNotificationsTitle.visibility = showNotificationsVisibility
		showNotificationsDescription.visibility = showNotificationsVisibility

		fullScreenAllowButton.visibility = fullScreenVisibility
		fullScreenTitle.visibility = fullScreenVisibility
		fullScreenDescription.visibility = fullScreenVisibility

		systemAlertAllowButton.visibility = systemAlertVisibility
		systemAlertTitle.visibility = systemAlertVisibility
		systemAlertDescription.visibility = systemAlertVisibility
		systemAlertAppInfoButton.visibility = systemAlertVisibility

		// Set the visibility of the textview that says no permissions are needed
		val noPermissionsNeededTitle: TextView = view.findViewById(R.id.onboarding_no_permissions_needed_title)
		noPermissionsNeededTitle.visibility = if (showNotificationsTitle.isGone
			&& fullScreenTitle.isGone
			&& systemAlertTitle.isGone)
		{
			View.VISIBLE
		}
		else
		{
			View.GONE
		}

		// Request the notification permission
		showNotificationsAllowButton.setOnClickListener {
			showNotificationsPermissionLauncher.launch(NacPostNotificationsPermission.permissionName)
		}

		// Request the full screen notification permission
		fullScreenAllowButton.setOnClickListener {
			NacFullScreenIntentPermission.requestPermission(activity)
		}

		// Request the system alert (display over other apps) permission
		systemAlertAllowButton.setOnClickListener {
			NacSystemAlertWindowPermission.requestPermission(activity)
		}

		// Open the app info page. This is where restricted settings are unlocked,
		// which is needed before the switch above can be turned on at all when the
		// app was installed from an APK file
		systemAlertAppInfoButton.setOnClickListener {
			try
			{
				val uri = ("package:" + context.packageName).toUri()

				startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to open the app info page", throwable = e)
			}
		}
	}

	/**
	 * Periodically check if the SYSTEM_ALERT or the full screen permission was granted.
	 *
	 * Both are given on a settings screen of their own, which comes back without
	 * telling the app anything, so the only way to notice is to keep looking.
	 */
	private fun systemAlertPermissionChecker()
	{
		// Get the context
		val context = requireContext()

		// Recursively call this method so the check can be done every 500 ms
		systemAlertCheckHandler.postDelayed({

			// The full screen notification permission was just granted
			if (NacFullScreenIntentPermission.isCorrectAndroidVersion
				&& fullScreenCheckMark.isGone
				&& NacFullScreenIntentPermission.hasPermission(context)
				&& !sharedPreferences.wasFullScreenIntentPermissionRequested)
			{
				// Animate the views
				fullScreenAllowButton.animateHide(300)
				fullScreenCheckMark.animateShow(400)

				// Set the flag indicating the permission was requested
				sharedPreferences.wasFullScreenIntentPermissionRequested = true

				// The section below has nothing left to offer, so it steps aside
				systemAlertAllowButton.visibility = View.GONE
				systemAlertTitle.visibility = View.GONE
				systemAlertDescription.visibility = View.GONE
				systemAlertAppInfoButton.visibility = View.GONE
			}

			// Using the correct version of Android
			// The check mark is not shown yet
			// The app has the permission granted
			// The shared preference flag has not been set yet, so the app has not registered
			// that the permission has been granted yet
			if (NacSystemAlertWindowPermission.isCorrectAndroidVersion
				&& systemAlertCheckMark.isGone
				&& NacSystemAlertWindowPermission.hasPermission(context)
				&& !sharedPreferences.wasSystemAlertWindowPermissionRequested)
			{
				// Animate the views
				systemAlertAllowButton.animateHide(300)
				systemAlertCheckMark.animateShow(400)

				// Set the flag indicating the permission was requested
				sharedPreferences.wasSystemAlertWindowPermissionRequested = true
				return@postDelayed
			}

			// Recursively call this method
			systemAlertPermissionChecker()

		}, 500L)

	}

}