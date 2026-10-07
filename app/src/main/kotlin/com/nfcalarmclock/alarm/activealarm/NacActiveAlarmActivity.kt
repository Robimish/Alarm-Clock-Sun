package com.nfcalarmclock.alarm.activealarm

import java.util.Calendar
import com.nfcalarmclock.system.media.NacAudioAttributes
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.options.NacAlarmButton
import com.nfcalarmclock.alarm.NacAlarmViewModel
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.nfc.NacDisableNfcJustScannedFlagService
import com.nfcalarmclock.nfc.NacNfc
import com.nfcalarmclock.nfc.NacNfcTagViewModel
import com.nfcalarmclock.nfc.db.NacNfcTag
import com.nfcalarmclock.nfc.getNfcTagNamesForDismissing
import com.nfcalarmclock.nfc.getNfcTagsForDismissing
import com.nfcalarmclock.nfc.shouldUseNfc
import com.nfcalarmclock.nfc.toNfcIdList
import com.nfcalarmclock.nfc.toNfcIdString
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.NacBundle
import com.nfcalarmclock.system.addAlarm
import com.nfcalarmclock.system.bindToService
import com.nfcalarmclock.system.broadcasts.shutdown.NacShutdownBroadcastReceiver
import com.nfcalarmclock.system.enableActivityAlias
import com.nfcalarmclock.system.getAlarm
import com.nfcalarmclock.system.registerMyReceiver
import com.nfcalarmclock.system.registerMyShutdownBroadcastReceiver
import com.nfcalarmclock.system.unregisterMyReceiver
import com.nfcalarmclock.view.NacAnalogClockView
import com.nfcalarmclock.view.quickToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity to dismiss/snooze the alarm.
 */
@UnstableApi
@AndroidEntryPoint
class NacActiveAlarmActivity
	: AppCompatActivity()
{

	/**
	 * Alarm view model.
	 */
	private val alarmViewModel: NacAlarmViewModel by viewModels()

	/**
	 * NFC tag view model.
	 */
	private val nfcTagViewModel: NacNfcTagViewModel by viewModels()

	/**
	 * Shared preferences.
	 */
	private lateinit var sharedPreferences: NacSharedPreferences

	/**
	 * The layout handler to use (either original or swipe).
	 */
	private lateinit var layoutHandler: NacActiveAlarmLayoutHandler

	/**
	 * Alarm.
	 */
	private var alarm: NacAlarm? = null

	/**
	 * Active alarm service.
	 */
	private var service: NacActiveAlarmService? = null

	/**
	 * List of NFC tags that are needed to dismiss the alarm.
	 */
	private var nfcTagsNeededToDismissList: MutableList<NacNfcTag>? = null

	/**
	 * Initial size of the list of NFC tags that are needed to dismiss the alarm, before a scan
	 * check occurs. The scan check may change the list, hence why this is needed.
	 */
	private var initialSizeOfNfcTagsNeededToDismiss: Int = 0

	/**
	 * Flag whether the service can be dismissed with NFC once it is connected or not.
	 */
	private var canDisimssServiceWithNfcUponConnection: Boolean = false

	/**
	 * Root view of the alarm screen, whose background is the dawn.
	 */
	private var dawnRoot: ViewGroup? = null

	/**
	 * Background the alarm screen had before the dawn took it over.
	 */
	private var dawnOriginalBackground: Drawable? = null

	/**
	 * Color of every text of the alarm screen before the dawn took it over.
	 */
	private val dawnOriginalTextColors: MutableMap<TextView, ColorStateList> = mutableMapOf()

	/**
	 * Line of text shown under the clock while the dawn is running.
	 */
	private var dawnPhraseTextView: TextView? = null

	/**
	 * Image shown instead of a color, when there is one. It is added behind the
	 * alarm screen and faded in.
	 */
	private var dawnImageView: ImageView? = null

	/**
	 * Flag indicating that the dawn is over and the alarm is ringing. The screen
	 * keeps the light the dawn left behind instead of going back to normal.
	 */
	private var isDawnFinished: Boolean = false

	/**
	 * Flag indicating that the screen has been momentarily put back to normal, so
	 * that the alarm screen can be read during the dawn.
	 */
	private var isDawnRevealing: Boolean = false

	/**
	 * Time at which the dawn started. [Units: ms]
	 */
	private var dawnStartMillis: Long = 0

	/**
	 * Time at which the dawn ends, which is when the alarm goes off. [Units: ms]
	 */
	private var dawnAlarmAtMillis: Long = 0

	/**
	 * Color the alarm screen fades to during the dawn.
	 */
	private var dawnColor: Int = Color.BLACK

	/**
	 * Which parts of the alarm screen the dawn hides, as a set of flags.
	 */
	private var dawnHiddenViews: Int = 0

	/**
	 * How long a tap shows the alarm screen as normal. Zero means a tap does
	 * nothing. [Units: ms]
	 */
	private var dawnRevealMillis: Long = 0

	/**
	 * Whether the light comes back on its own after a tap, or waits for the next.
	 */
	private var dawnRevealReturns: Boolean = true

	/**
	 * Whether the texts of the alarm screen are currently darkened, because the dawn
	 * has made the background too light for them.
	 */
	private var isDawnTextDark: Boolean = false

	/**
	 * Whether the alarm is ringing, as opposed to a sunrise still running ahead of
	 * it.
	 *
	 * The sunrise is the only thing that carries times on the intent. When the alarm
	 * rings the activity is started again without them, and an alarm with no sunrise
	 * never had any, so in both cases there is no start time left.
	 */
	private val isAlarmRinging: Boolean
		get() = (dawnStartMillis <= 0)

	/**
	 * Handler that updates the dawn.
	 */
	private val dawnHandler: Handler by lazy { Handler(mainLooper) }

	/**
	 * Runnable that updates the dawn.
	 */
	private val dawnTick: Runnable = Runnable { updateDawn() }

	/**
	 * Runnable that puts the dawn back after the screen was shown as normal.
	 */
	private val dawnEndRevealRunnable: Runnable = Runnable {

		isDawnRevealing = false

		// While the sunrise is running, its own tick puts the light back at whatever
		// point the ramp has reached. Once the alarm is ringing there is no tick left,
		// so the light the sunrise ended on is put back here

		if (isDawnFinished)
		{
			applyDawn(1f)
		}

	}

	/**
	 * Runnable that keeps the hidden parts of the screen hidden.
	 *
	 * The layout handler of the alarm screen owns the clock, the date, the name and
	 * the music, and puts them back on screen on its own schedule: once when it
	 * starts, and again every time it refreshes the clock. Hiding them once is
	 * therefore not enough, and there is no single moment after which it is safe.
	 * This simply checks, second by second, for as long as the screen is lit.
	 *
	 * A tap is no exception. What the alarm shows was chosen once, and a look at the
	 * screen is a look at the same screen with the light back on, not a different one.
	 */
	private val dawnKeepHiddenRunnable: Runnable = object: Runnable {

		override fun run()
		{
			setDawnViewsHidden()

			dawnHandler.postDelayed(this, DAWN_KEEP_HIDDEN_PERIOD_MILLIS)
		}

	}

	/**
	 * Service bound watchdog, to ensure that the service is actually bound. If it is not
	 * after a timeout then the activity is stopped.
	 */
	private val serviceBoundWatchdogHandler: Handler by lazy { Handler(mainLooper) }

	/**
	 * Keyguard manager.
	 */
	private val keyguardManager: KeyguardManager by lazy {
		getSystemService(KEYGUARD_SERVICE) as KeyguardManager
	}

	/**
	 * Shutdown broadcast receiver.
	 */
	private val shutdownBroadcastReceiver: NacShutdownBroadcastReceiver = NacShutdownBroadcastReceiver()

	/**
	 * Device unlocked broadcast receiver.
	 */
	private val deviceUnlockedBroadcastReceiver: BroadcastReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent)
		{
			// Setup NFC for the layout handler, only when the alarm has NFC tags to
			// scan (NFC Alarm Clock 12.7.3)
			if (nfcTagsNeededToDismissList != null)
			{
				setupLayoutHandlerNfc()
			}
		}
	}

	/**
	 * NFC adapter state changed broadcast receiver.
	 */
	private val nfcAdapterStateChangedBroadcastReceiver: BroadcastReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent)
		{
			// Setup NFC
			startNfc()

			if (nfcTagsNeededToDismissList != null)
			{
				setupLayoutHandlerNfc()
			}
		}
	}

	/**
	 * Screen off broadcast receiver.
	 *
	 * Android never hands the power button to an application: only the system sees
	 * it. What can be seen is the screen going out, which is what that button does,
	 * so that is what is read here. Anything else that turns the screen off is read
	 * the same way, which is why it starts off.
	 *
	 * Since 2.01 the power button is one of the buttons chosen in Settings, General,
	 * to dismiss or to snooze, and each alarm says whether it uses it.
	 */
	private val screenOffBroadcastReceiver: BroadcastReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent)
		{
			val currentAlarm = alarm ?: return

			// The sunrise is still running and the alarm has not gone off yet
			if (!isAlarmRinging)
			{
				return
			}

			val dismisses = NacAlarmButton.matchesPower(sharedPreferences.dismissButton)
				&& !currentAlarm.shouldUseNfc
			val snoozes = NacAlarmButton.matchesPower(sharedPreferences.snoozeButton)

			// The service is asked through an intent rather than through the binding,
			// because the screen going out stops the activity and the binding goes with it
			if (dismisses)
			{
				NacLog.i("Screen turned off while the alarm was ringing, dismissing it")
				NacActiveAlarmService.dismissAlarmService(context, currentAlarm)
			}
			else if (snoozes)
			{
				// The service checks the snooze limit and says so when no snooze is left
				NacActiveAlarmService.snoozeAlarmService(context, currentAlarm)

				// No snooze left: the alarm keeps ringing, so this screen stays
				if (!currentAlarm.canSnooze)
				{
					NacLog.i("Screen turned off while the alarm was ringing, but no snooze is left")
					return
				}

				NacLog.i("Screen turned off while the alarm was ringing, snoozing it")
			}
			// The power button does nothing for this alarm
			else
			{
				return
			}

			// And go. The service lets the screen know it is done through the binding,
			// which the screen going out has just cut, so otherwise this screen stayed
			// on the lock screen over an alarm that was over, and a swipe on it went to
			// a service that no longer existed (28 Sept, 1.89)
			finish()
		}
	}

	/**
	 * Listener for an alarm action, such as snooze or dismiss.
	 */
	private val onAlarmActionListener: NacActiveAlarmLayoutHandler.OnAlarmActionListener =
		object: NacActiveAlarmLayoutHandler.OnAlarmActionListener
		{

			/**
			 * Alarm should be snoozed.
			 */
			@OptIn(UnstableApi::class)
			override fun onSnooze(alarm: NacAlarm)
			{
				// The alarm is over and this screen outlived it
				if (service?.hasAlarm != true)
				{
					NacLog.w("No alarm left in the service to snooze. Closing the alarm screen")
					finish()
					return
				}

				// Snooze the alarm service. Whether the alarm is actually
				// snoozed is determined in the service
				service?.attemptSnooze()
			}

			/**
			 * Alarm should be dismissed.
			 */
			@OptIn(UnstableApi::class)
			override fun onDismiss(alarm: NacAlarm)
			{
				// The alarm is over and this screen outlived it
				if (service?.hasAlarm != true)
				{
					NacLog.w("No alarm left in the service to dismiss. Closing the alarm screen")
					finish()
					return
				}

				// Dismiss the alarm service
				service?.dismiss()
			}

		}

	/**
	 * Callback when back is pressed.
	 */
	private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed()
		{
		}
	}

	/**
	 * Connection to the active alarm service.
	 */
	private val serviceConnection = object : ServiceConnection
	{
		/**
		 * Binding to the service connection is dead.
		 */
		override fun onBindingDied(name: ComponentName?)
		{
			// Super
			super.onBindingDied(name)

			// Finish the activity
			finish()
		}

		/**
		 * Service connected.
		 */
		override fun onServiceConnected(className: ComponentName, serviceBinder: IBinder)
		{
			// Set the active alarm service
			val binder = serviceBinder as NacActiveAlarmService.NacLocalBinder
			service = binder.getService()

			NacLog.i("Active alarm activity connected to service")

			// Remove the service watchdog
			serviceBoundWatchdogHandler.removeCallbacksAndMessages(null)

			// Dismiss the service
			if (canDisimssServiceWithNfcUponConnection)
			{
				NacLog.i("Dismissing service with NFC after connecting")
				service!!.dismiss(usedNfc = true)
			}
		}

		/**
		 * Service disconnected.
		 */
		override fun onServiceDisconnected(className: ComponentName)
		{
			service = null
		}
	}

	/**
	 * Handle when an NFC tag is scanned.
	 *
	 */
	private fun handleNfcTagScanned()
	{
		// Dismiss the alarm service with NFC
		if (service != null)
		{
			NacLog.i("Dismissing the active alarm service with NFC from the activity")
			service!!.dismiss(usedNfc = true)
		}
		// Set flag that service can dismiss the alarm once it is connected, however,
		// if the service does not get connected after 2 sec, there may be a problem
		// with the alarm so use a handler to dismiss it
		else
		{
			NacLog.i("Active alarm service has not been bound yet, but will be dismiss with NFC upon connection")

			// Set the flag
			canDisimssServiceWithNfcUponConnection = true

			// Handler to dismiss the erroneous active alarm, in the event that the
			// service is not bound within 2 sec, and then finish the activity
			serviceBoundWatchdogHandler.postDelayed({
				lifecycleScope.launch {
					NacLog.w("Active alarm service was not bound after 2 sec. Starting the dismiss erroneous active alarm service")

					sharedPreferences.wasNfcJustScannedToDismiss = true
					NacDismissErroneousActiveAlarmService.startService(this@NacActiveAlarmActivity, alarm)
					NacDisableNfcJustScannedFlagService.startService(this@NacActiveAlarmActivity)
					delay(500)
					finish()
				}
			}, 2000)
		}
	}

	/**
	 * Activity is created.
	 */
	override fun onCreate(savedInstanceState: Bundle?)
	{
		// Super
		super.onCreate(savedInstanceState)

		NacLog.i("Creating active alarm activity")

		// Setup the shared preferences
		sharedPreferences = NacSharedPreferences(this)

		// Set the alarm from the bundle
		setAlarm(savedInstanceState)

		// Setup
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		setupAlarmScreen()
		setupScreenOn()
		setupDawn()
		onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

		// Register device unlocked receiver
		registerMyReceiver(this, deviceUnlockedBroadcastReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))

		// Register the screen off receiver. Here rather than in onResume, because the
		// screen going out is exactly what stops the activity: the receiver has to
		// outlive that
		registerMyReceiver(this, screenOffBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
	}

	/**
	 * Activity is destroyed.
	 */
	override fun onDestroy()
	{
		// Super
		super.onDestroy()

		NacLog.i("Destroying active alarm activity")

		// Unregister device unlocked receiver
		unregisterMyReceiver(this, deviceUnlockedBroadcastReceiver)

		// Unregister the screen off receiver
		unregisterMyReceiver(this, screenOffBroadcastReceiver)

		// Stop the watchdog
		serviceBoundWatchdogHandler.removeCallbacksAndMessages(null)

		// Stop the dawn
		stopDawn()
	}

	/**
	 * NFC tag discovered.
	 *
	 * After this, onResume() will be called, which will check if an NFC tag was scanned
	 * and, if so, will disimss the alarm.
	 */
	override fun onNewIntent(intent: Intent)
	{
		// Super
		super.onNewIntent(intent)

		NacLog.i("New intent received in active alarm activity")

		// Set the intent
		setIntent(intent)

		// The dawn may have started or ended
		setupDawn()
	}

	/**
	 * Activity is paused.
	 */
	public override fun onPause()
	{
		// Super
		super.onPause()

		// Stop scanning for NFC
		NacNfc.stop(this)

		// Re-enable the activity alias
		enableActivityAlias(this)

		// Unregister the broadcast receivers
		unregisterMyReceiver(this, shutdownBroadcastReceiver)
		unregisterMyReceiver(this, nfcAdapterStateChangedBroadcastReceiver)
	}

	/**
	 * Activity is resumed.
	 */
	@OptIn(UnstableApi::class)
	public override fun onResume()
	{
		// Super
		super.onResume()

		// Say that the alarm screen is on display, so that the service can take the
		// banner away. Android puts one across the top when it cannot open this screen
		// itself, which is exactly when this screen is already open
		tellServiceTheScreenIsShown()

		lifecycleScope.launch {

			// Start NFC and run setup
			startNfc()
			layoutHandler.setup(this@NacActiveAlarmActivity)
			setupNfcTags()

			// One listener for a tap on the screen, set after the layout handler so
			// that it is the one that stays (2.01)
			setupScreenTap()

			// NFC tag was scanned. This checks if multiple NFC tags needed to be scanned in
			// sequence as well
			if (wasNfcTagScanned())
			{
				handleNfcTagScanned()
				return@launch
			}

			NacLog.i("NFC tags needed to dismiss. nfcSize=${nfcTagsNeededToDismissList?.size} | initialSize=$initialSizeOfNfcTagsNeededToDismiss")

			// Size of the NFC tags dismiss list changed during the scan check.
			// Save the list to the alarm and update the database
			if (nfcTagsNeededToDismissList!!.size != initialSizeOfNfcTagsNeededToDismiss)
			{
				NacLog.i("List size of NFC tags needed to dismiss changed")

				// Get an up to date alarm to ensure that all the necessary flags and whatnot
				// (such as isActive) are set on the alarm
				val upToDateAlarm = alarmViewModel.findAlarm(alarm!!.id)
				alarm = upToDateAlarm ?: alarm

				// Save the list and update the database
				alarm!!.currentNfcTagsNeededToDismiss = nfcTagsNeededToDismissList!!.toNfcIdString()
				alarmViewModel.update(alarm!!)
			}

			// Setup NFC for the layout handler
			setupLayoutHandlerNfc()

		}

		// Register the broadcast receivers
		registerMyShutdownBroadcastReceiver(this, shutdownBroadcastReceiver)
		registerMyReceiver(this, nfcAdapterStateChangedBroadcastReceiver,
			IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
	}

	/**
	 * Save the alarm before the activity is killed.
	 */
	public override fun onSaveInstanceState(outState: Bundle)
	{
		// Super
		super.onSaveInstanceState(outState)

		// Save the alarm to the save instance state
		if (alarm != null)
		{
			outState.putParcelable(NacBundle.ALARM_PARCEL_NAME, alarm)
		}
	}


	/**
	 * Activity started.
	 */
	@OptIn(UnstableApi::class)
	override fun onStart()
	{
		// Super
		super.onStart()

		NacLog.i("Starting active alarm activity")

		// Bind to the active alarm service
		bindToService(NacActiveAlarmService::class.java, serviceConnection)
	}

	/**
	 * Activity stopped.
	 */
	override fun onStop()
	{
		// Super
		super.onStop()

		NacLog.i("Stopping active alarm activity")

		// Remove the back press callback
		onBackPressedCallback.remove()

		// Unbind from the active alarm service. The one held is let go as well: the
		// service may be gone by the time the screen comes back, and a binding that
		// is not remade does not say so
		unbindService(serviceConnection)
		service = null
	}

	/**
	 * Called when the window focus has changed. This is the best indicator of
	 * whether the activity is visible to the user, or not.
	 */
	override fun onWindowFocusChanged(hasFocus: Boolean)
	{
		// Super
		super.onWindowFocusChanged(hasFocus)

		NacLog.i("Window focus changed in active alarm activity")

		// Check if the window focus has changed
		if (hasFocus)
		{
			// Start the layout handler
			layoutHandler.start(this)
		}
		else
		{
			// Stop the layout handler
			layoutHandler.stop(this)
		}
	}

	/**
	 * A key was pressed while the alarm screen is on display.
	 *
	 * The volume keys are caught here rather than only by the volume manager of the
	 * service, which reads the volume once a second: a key pressed at the loudest or
	 * quietest volume changes nothing it could see, and it answered up to a second
	 * late. The key is swallowed when it is used, so the volume does not move and
	 * the volume manager has nothing to act on twice.
	 */
	@OptIn(UnstableApi::class)
	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean
	{
		if (isVolumeKey(keyCode) && isAlarmRinging)
		{
			// Holding the key sends it again and again. Only the first press counts,
			// and the ones after it are swallowed with it
			if ((event?.repeatCount ?: 0) > 0)
			{
				return (service != null) && hasVolumeKeyAction(keyCode)
			}

			if (service?.onVolumeKeyFromScreen(keyCode == KeyEvent.KEYCODE_VOLUME_UP) == true)
			{
				return true
			}
		}

		return super.onKeyDown(keyCode, event)
	}

	/**
	 * A key was let go while the alarm screen is on display. A volume key that was
	 * swallowed on the way down is swallowed on the way up as well.
	 */
	@OptIn(UnstableApi::class)
	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean
	{
		if (isVolumeKey(keyCode) && isAlarmRinging && (service != null) && hasVolumeKeyAction(keyCode))
		{
			return true
		}

		return super.onKeyUp(keyCode, event)
	}

	/**
	 * Whether this key dismisses or snoozes this alarm, with the buttons chosen in
	 * Settings, General (2.01). A volume key that does neither changes the volume as
	 * usual.
	 */
	private fun hasVolumeKeyAction(keyCode: Int): Boolean
	{
		if (alarm == null)
		{
			return false
		}

		return NacAlarmButton.matchesKey(sharedPreferences.dismissButton, keyCode)
			|| NacAlarmButton.matchesKey(sharedPreferences.snoozeButton, keyCode)
	}

	/**
	 * Whether a key is one of the two volume keys.
	 */
	private fun isVolumeKey(keyCode: Int): Boolean
	{
		return (keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
	}

	/**
	 * Set the alarm.
	 */
	private fun setAlarm(savedInstanceState: Bundle?)
	{
		// Attempt to get the alarm from the intent
		var intentAlarm = intent.getAlarm()

		// Unable to get the alarm from the intent
		if (intentAlarm == null)
		{
			// Attempt to get the alarm from the saved instance state
			intentAlarm = savedInstanceState?.getAlarm()
		}

		// Alarm is still null, finish the activity
		if (intentAlarm == null)
		{
			finish()
		}

		// Check if the current alarm and the intent alarm are different
		// Set the alarm
		this.alarm = intentAlarm
	}

	/**
	 * Setup the alarm screen.
	 */
	private fun setupAlarmScreen()
	{
		// The simple screen, the original one of NFC Alarm Clock: the name of the alarm
		// and two buttons, nothing else (2.05)
		if (sharedPreferences.shouldUseSimpleAlarmScreen)
		{
			setContentView(R.layout.act_alarm)
			layoutHandler = NacOriginalLayoutHandler(this, alarm, onAlarmActionListener)
			return
		}

		// The full screen, the same for both choices. The choice only says whether
		// snooze and dismiss slide or are tapped (2.01)
		setContentView(R.layout.act_alarm_new)

		layoutHandler = NacSwipeLayoutHandler(this, alarm, onAlarmActionListener,
			useButtons = !sharedPreferences.shouldUseNewAlarmScreen)
	}

	/**
	 * Setup the dawn.
	 *
	 * The alarm screen is shown ahead of the alarm and lights up gradually, from
	 * black to the color (or the image) chosen for the alarm. When the alarm goes
	 * off, the screen is started again without the dawn, so everything goes back to
	 * normal.
	 */
	private fun setupDawn()
	{
		// Stop whatever dawn may be running
		stopDawn()

		// This alarm has no dawn
		val dawnAlarm = alarm ?: return

		// What the alarm shows is chosen once and holds for both screens, the sunrise
		// and the ringing that follows it. An alarm with no sunrise at all still shows
		// what it was told to show, which is why this comes before the way out below
		dawnHiddenViews = dawnAlarm.dawnHiddenViews

		// Show one of the dawn phrases under the clock. With or without a sunrise: the
		// alarm screen shows the same things either way (2.01)
		dawnPhraseTextView = findViewById(R.id.dawn_phrase)

		// The phrases of the app and the ones of one's own, or only one's own when that
		// is asked and there is at least one (2.07)
		val mine = sharedPreferences.myPhrases.lines().map { it.trim() }.filter { it.isNotEmpty() }
		val phrases = if (sharedPreferences.shouldUseOnlyMyPhrases && mine.isNotEmpty())
		{
			mine.toTypedArray()
		}
		else
		{
			resources.getStringArray(R.array.dawn_phrases) + mine
		}

		if (phrases.isNotEmpty())
		{
			// Note: the phrase is not drawn at random, otherwise it would change when
			// the screen is started again for the alarm itself. It is tied to the
			// moment the alarm rings (2.05: it was tied to the day, so every ring of
			// the same alarm that day showed the same phrase). It holds from the dawn
			// through to the ringing and its snoozes, and changes with the next ring
			val index = phraseIndex(dawnAlarm, phrases.size)

			dawnPhraseTextView?.text = phrases[index]
			dawnPhraseTextView?.visibility = View.VISIBLE
		}

		setDawnViewsHidden()

		if (dawnHiddenViews != 0)
		{
			dawnHandler.postDelayed(dawnKeepHiddenRunnable, DAWN_KEEP_HIDDEN_PERIOD_MILLIS)
		}

		// This alarm has no sunrise, so there is no light to work out
		if (!dawnAlarm.shouldUseDawn)
		{
			return
		}

		// Get the times of the dawn
		dawnStartMillis = intent.getLongExtra(EXTRA_DAWN_START, 0L)
		dawnAlarmAtMillis = intent.getLongExtra(EXTRA_DAWN_ALARM_AT, 0L)

		NacLog.i("Setting up the dawn on the alarm screen")

		// Get the root of the alarm screen, whichever screen is in use
		dawnRoot = findViewById(R.id.act_alarm)
		dawnOriginalBackground = dawnRoot?.background
		dawnColor = dawnAlarm.dawnColor
		dawnRevealMillis = dawnAlarm.dawnRevealDuration * 1000L
		dawnRevealReturns = dawnAlarm.shouldDawnRevealReturn

		// Setup the image, when one is used instead of a color
		if (dawnAlarm.shouldUseDawnImage && dawnAlarm.dawnImagePath.isNotEmpty())
		{
			setupDawnImage(dawnAlarm.dawnImagePath)
		}

		// Wake the speech engine, so that the first press of the volume key does not
		// have to wait for it

		// Remember the color of every text, so that they can be darkened once the
		// background gets too light for them, and put back as they were afterwards
		dawnOriginalTextColors.clear()
		findDawnTextViews(dawnRoot)

		// Tapping anywhere that is not a button puts the screen back to normal for a
		// moment: during the sunrise, so that it is plain the alarm has not gone off
		// yet, and once it rings, because the screen keeps the light the sunrise left
		// behind and a tap is the way back to an ordinary, dark alarm screen. It is set
		// before the way out below so that the ringing keeps it
		dawnRoot?.setOnClickListener { onScreenTapped() }

		// There are no times, which means the dawn is over and the alarm is now
		// ringing. Keep the light that the dawn left behind, so that the screen does
		// not suddenly go back to a dark one
		if ((dawnStartMillis <= 0) || (dawnAlarmAtMillis <= dawnStartMillis))
		{
			NacLog.i("The dawn is over, keeping its light on the alarm screen")

			isDawnFinished = true
			applyDawn(1f)

			return
		}

		// Start updating the screen
		dawnHandler.post(dawnTick)
	}

	/**
	 * Which phrase goes with this ring of the alarm.
	 *
	 * The ring is the hour and minute of the alarm, on the day nearest to now: the
	 * sunrise starts before the alarm and can start the evening before, and the
	 * screen is shown again later for the ringing and the snoozes. Taking the nearest
	 * day keeps them all on the same ring.
	 */
	private fun phraseIndex(alarm: NacAlarm, size: Int): Int
	{
		val now = System.currentTimeMillis()
		val ring = Calendar.getInstance()

		ring[Calendar.HOUR_OF_DAY] = alarm.hour
		ring[Calendar.MINUTE] = alarm.minute
		ring[Calendar.SECOND] = 0
		ring[Calendar.MILLISECOND] = 0

		val halfDay = 12 * 60 * 60 * 1000L

		if (ring.timeInMillis - now > halfDay)
		{
			ring.add(Calendar.DAY_OF_YEAR, -1)
		}
		else if (now - ring.timeInMillis > halfDay)
		{
			ring.add(Calendar.DAY_OF_YEAR, 1)
		}

		// Minutes since 1970 of that ring, mixed with the alarm so that two alarms at
		// the same minute do not show the same phrase. The mix spreads rings that are
		// exactly a day apart, which a plain sum would keep in step with the list
		val ringMinute = ring.timeInMillis / 60000L
		var seed = ringMinute xor (alarm.id shl 32)

		seed *= -7046029254386353131L
		seed = seed xor (seed ushr 29)

		return ((seed and Long.MAX_VALUE) % size).toInt()
	}

	/**
	 * Tell the service that the alarm screen is on display.
	 */
	private fun tellServiceTheScreenIsShown()
	{
		try
		{
			val intent = Intent(NacActiveAlarmService.ACTION_ALARM_SCREEN_SHOWN, null,
				this, NacActiveAlarmService::class.java)

			startService(intent)
		}
		catch (e: Exception)
		{
			// The service is not running, which is fine: there is no notification then
			NacLog.i("Unable to tell the service that the screen is shown")
		}
	}

	/**
	 * Listen for a tap on the screen, anywhere that is not a button.
	 */
	private fun setupScreenTap()
	{
		val root: View = findViewById(R.id.act_alarm) ?: return

		// Nothing to do with a tap: no easy snooze and no sunrise to reveal
		if ((alarm?.shouldEasySnooze != true) && (dawnRoot == null))
		{
			return
		}

		root.setOnClickListener { onScreenTapped() }
	}

	/**
	 * The screen was tapped, anywhere that is not a button.
	 *
	 * Once the alarm rings, a tap snoozes it when the alarm allows it (easy snooze,
	 * in its snooze options). Otherwise, and during the sunrise, a tap shows the
	 * normal screen for a moment.
	 *
	 * Easy snooze did not work before 2.01: the classic screen read the setting of
	 * the default alarm instead of the one of the alarm, and the swipe screen had no
	 * tap at all (fixed in NFC Alarm Clock 12.7.3 as well).
	 */
	private fun onScreenTapped()
	{
		val currentAlarm = alarm

		if (isAlarmRinging && (currentAlarm != null) && currentAlarm.shouldEasySnooze)
		{
			NacLog.i("Screen tapped while the alarm rings: easy snooze")
			onAlarmActionListener.onSnooze(currentAlarm)
			return
		}

		revealDawn()
	}

	/**
	 * Put the screen back to normal for a few seconds, so that the alarm screen can
	 * be read during the dawn.
	 */
	private fun revealDawn()
	{
		// Nothing to reveal
		if (dawnRoot == null)
		{
			return
		}

		// A tap does nothing for this alarm
		if (dawnRevealMillis <= 0)
		{
			return
		}

		// Already showing the normal screen, so a second tap goes straight back to
		// the light instead of waiting for it
		if (isDawnRevealing)
		{
			dawnHandler.removeCallbacks(dawnEndRevealRunnable)
			dawnEndRevealRunnable.run()

			return
		}

		isDawnRevealing = true

		// Put the light back as it normally is. What is on the screen does not move:
		// what the alarm was told to show is what it shows, dark or lit
		setDawnTextDark(false)
		dawnImageView?.alpha = 0f
		dawnRoot?.background = dawnOriginalBackground
		setDawnBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

		// Say what is going on. Not once the alarm is ringing: it has gone off, so
		// there is nothing left to tell
		if (!isDawnFinished)
		{
			quickToast(this, R.string.message_dawn_not_ringing_yet)
		}

		// Go back to the light afterwards, after however long this alarm says. The same
		// for the sunrise and for the ringing that follows it
		// Left to itself the light comes back after a while. Turned off, the screen
		// stays as it is and only the next tap turns it over
		if (dawnRevealReturns)
		{
			dawnHandler.postDelayed(dawnEndRevealRunnable, dawnRevealMillis)
		}
	}

	/**
	 * Hide the parts of the alarm screen that the options say should not be seen.
	 *
	 * The same choice holds for the sunrise and for the ringing that follows it, and a
	 * tap during the sunrise brings back the light rather than the rest.
	 *
	 * Note: GONE and not INVISIBLE, so that whatever is left moves up into the place
	 * of what was turned off rather than leaving a hole in the middle of the screen.
	 */
	private fun setDawnViewsHidden()
	{
		// Nothing is hidden for this alarm
		if (dawnHiddenViews == 0)
		{
			return
		}

		val groups = listOf(
			NacAlarm.DAWN_HIDE_PHRASE to intArrayOf(R.id.dawn_phrase),
			NacAlarm.DAWN_HIDE_NAME to intArrayOf(R.id.alarm_name),
			NacAlarm.DAWN_HIDE_CLOCK to intArrayOf(R.id.current_time, R.id.current_meridian,
				R.id.current_analog_clock),
			NacAlarm.DAWN_HIDE_DATE to intArrayOf(R.id.current_date),
			NacAlarm.DAWN_HIDE_MEDIA to intArrayOf(R.id.music_and_warning_container))

		groups.forEach { (flag, ids) ->

			// This one is shown
			if ((dawnHiddenViews and flag) == 0)
			{
				return@forEach
			}

			ids.forEach { id ->

				val view: View = findViewById(id) ?: return@forEach

				// The phrase is only ever shown when it has something to say
				if ((id == R.id.dawn_phrase) && (dawnPhraseTextView?.text.isNullOrEmpty()))
				{
					return@forEach
				}

				view.visibility = View.GONE

			}

		}
	}

	/**
	 * Set the brightness of the screen.
	 */
	private fun setDawnBrightness(value: Float)
	{
		val attributes = window.attributes

		attributes.screenBrightness = value
		window.attributes = attributes
	}

	/**
	 * Show the dawn at the given point of its ramp, from 0 (night) to 1 (full).
	 */
	private fun applyDawn(curve: Float)
	{
		// Set the brightness of the screen
		setDawnBrightness((MIN_DAWN_BRIGHTNESS + (1f - MIN_DAWN_BRIGHTNESS)*curve)
			.coerceIn(0f, 1f))

		// Fade the image in, or blend the color
		val image = dawnImageView

		if (image != null)
		{
			image.alpha = curve
		}
		else
		{
			dawnRoot?.setBackgroundColor(blendDawn(Color.BLACK, dawnColor, curve))
		}

		// Keep the texts readable against the background
		setDawnTextDark(curve > 0.55f)
	}

	/**
	 * Setup the image that is shown instead of a color.
	 */
	private fun setupDawnImage(path: String)
	{
		val root = dawnRoot ?: return

		try
		{
			// Put the image behind everything else, on black, so that it can be
			// faded in
			val image = ImageView(this)

			image.layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT)
			image.scaleType = ImageView.ScaleType.CENTER_CROP
			image.setImageURI(path.toUri())

			// Nothing could be read from the image, so fall back on the color. This
			// happens when the app has lost read access to the file
			if (image.drawable == null)
			{
				NacLog.e("Unable to read the dawn image. Falling back on the color")
				return
			}

			image.alpha = 0f

			root.addView(image, 0)
			root.setBackgroundColor(Color.BLACK)

			dawnImageView = image
		}
		catch (e: Exception)
		{
			// Fall back on the color
			NacLog.e("Unable to show the dawn image", throwable = e)
			dawnImageView = null
		}
	}

	/**
	 * Collect every text of the alarm screen, along with its color.
	 */
	private fun findDawnTextViews(view: View?)
	{
		when (view)
		{

			// Note: the labels of the Snooze and Dismiss buttons are left out. They
			// sit on their own colored circles, so darkening them along with the rest
			// only made the Snooze one unreadable
			is TextView ->
			{
				if ((view.id != R.id.snooze_text) && (view.id != R.id.dismiss_text))
				{
					dawnOriginalTextColors[view] = view.textColors
				}
			}

			is ViewGroup ->
			{
				for (i in 0 until view.childCount)
				{
					findDawnTextViews(view.getChildAt(i))
				}
			}

		}
	}

	/**
	 * Update the dawn.
	 */
	private fun updateDawn()
	{
		val now = System.currentTimeMillis()
		val total = dawnAlarmAtMillis - dawnStartMillis

		// Work out how far along the dawn is
		val progress = if ((total <= 0) || (now >= dawnAlarmAtMillis))
		{
			1f
		}
		else
		{
			((now - dawnStartMillis) / total.toFloat()).coerceIn(0f, 1f)
		}

		// Gentle ramp up, the same curve that a sunrise follows
		val curve = progress * progress

		// Show the dawn, unless the screen has been put back to normal for a moment
		if (!isDawnRevealing)
		{
			applyDawn(curve)
		}

		// The dawn is over. The alarm service will start this screen again, without
		// the dawn, so there is nothing left to do here
		if (progress >= 1f)
		{
			return
		}

		// Schedule the next update
		dawnHandler.postDelayed(dawnTick, DAWN_UPDATE_PERIOD_MILLIS)
	}

	/**
	 * Darken every text of the alarm screen, or put them back as they were.
	 */
	private fun setDawnTextDark(isDark: Boolean)
	{
		// Nothing to change
		if (isDark == isDawnTextDark)
		{
			return
		}

		isDawnTextDark = isDark

		// The hands of the dial follow the texts, since they are read against the very
		// same background
		findViewById<NacAnalogClockView>(R.id.current_analog_clock)?.handColor =
			if (isDark) DARK_DAWN_TEXT_COLOR else LIGHT_DAWN_CLOCK_COLOR

		// Darken or restore every text
		for ((textView, color) in dawnOriginalTextColors)
		{
			// Put the text back as it was
			if (!isDark)
			{
				textView.setTextColor(color)
				continue
			}

			// The name of the alarm keeps its own color, which is what tells alarms
			// apart, only deepened enough to stay readable on the light background
			if (textView.id == R.id.alarm_name)
			{
				textView.setTextColor(deepen(color.defaultColor))
			}
			else
			{
				textView.setTextColor(DARK_DAWN_TEXT_COLOR)
			}
		}
	}

	/**
	 * Stop the dawn and put the alarm screen back as it was.
	 */
	private fun stopDawn()
	{
		dawnHandler.removeCallbacksAndMessages(null)

		// Let the speech engine go

		// Nothing was ever setup
		if (dawnRoot == null)
		{
			return
		}

		// Put the texts back
		setDawnTextDark(false)

		// Hide the dawn phrase
		dawnPhraseTextView?.visibility = View.GONE
		dawnPhraseTextView = null

		// Take the image away
		dawnImageView?.let { dawnRoot?.removeView(it) }

		// Put the background back
		dawnRoot?.background = dawnOriginalBackground

		// Stop listening for taps, unless a tap snoozes this alarm (easy snooze)
		if (alarm?.shouldEasySnooze == true)
		{
			dawnRoot?.setOnClickListener { onScreenTapped() }
		}
		else
		{
			dawnRoot?.setOnClickListener(null)
			dawnRoot?.isClickable = false
		}

		// Give the brightness back to the system
		setDawnBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

		dawnOriginalTextColors.clear()
		dawnImageView = null
		dawnRoot = null
		dawnStartMillis = 0
		dawnAlarmAtMillis = 0
		isDawnFinished = false
		isDawnRevealing = false
	}

	/**
	 * Deepen a color, so that it stays readable on a light background while keeping
	 * its own hue.
	 */
	private fun deepen(color: Int): Int
	{
		return Color.argb(255,
			(Color.red(color) * 0.45f).toInt(),
			(Color.green(color) * 0.45f).toInt(),
			(Color.blue(color) * 0.45f).toInt())
	}

	/**
	 * Blend two colors together.
	 */
	private fun blendDawn(from: Int, to: Int, fraction: Float): Int
	{
		val f = fraction.coerceIn(0f, 1f)
		val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * f).toInt()
		val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * f).toInt()
		val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * f).toInt()

		return Color.argb(255, r, g, b)
	}

	/**
	 * Setup NFC for the layout handler.
	 */
	private fun setupLayoutHandlerNfc()
	{
		// NFC does not need to be used so do nothing with the layout handler
		if (alarm?.shouldUseNfc(this) != true)
		{
			return
		}

		// Get the names of the NFC tags that can dismiss the alarm
		val prefix = "(${resources.getString(R.string.message_show_nfc_tag_id)}) "
		val nfcTagNames = alarm!!.getNfcTagNamesForDismissing(nfcTagsNeededToDismissList!!, prefix)

		// Setup the NFC tag
		layoutHandler.setupNfcTag(this, nfcTagNames, keyguardManager.isDeviceLocked)
	}

	/**
	 * Setup the NFC tags member variable.
	 *
	 * It will contain the list of NFC tags that can be used to dismiss the alarm, and
	 * will be ordered based on how the user wants them ordered (Sequential/Random)
	 */
	private suspend fun setupNfcTags()
	{
		// Already been setup
		if (nfcTagsNeededToDismissList != null)
		{
			NacLog.i("NFC tags needed to dismiss already setup. size=${nfcTagsNeededToDismissList!!.size}")

			// Set the size of the list
			initialSizeOfNfcTagsNeededToDismiss = nfcTagsNeededToDismissList!!.size
			return
		}

		// Find the current list of NFC tags that need to be dismissed
		val currentNfcTagsNeededToDismiss = alarmViewModel.findCurrentNfcTagsNeededToDismiss(alarm!!.id)

		NacLog.i("Current NFC tags needed to dismiss. size=${currentNfcTagsNeededToDismiss.toNfcIdList().size}")

		// The current list has not been saved yet
		if (currentNfcTagsNeededToDismiss.isEmpty())
		{
			// Get the list
			nfcTagsNeededToDismissList = alarm!!.getNfcTagsForDismissing(nfcTagViewModel)

			// Get an up to date alarm to ensure that all the necessary flags and whatnot
			// (such as isActive) are set on the alarm
			val upToDateAlarm = alarmViewModel.findAlarm(alarm!!.id)
			alarm = upToDateAlarm ?: alarm

			NacLog.i("Writing the updated list of NFC tags needed to dismiss to the db. isActive=${alarm!!.isActive} | nfcSize=${nfcTagsNeededToDismissList?.size}")

			// Save the list to the alarm and update the database
			alarm!!.currentNfcTagsNeededToDismiss = nfcTagsNeededToDismissList!!.toNfcIdString()
			alarmViewModel.update(alarm!!)
		}
		// The list has already been saved to the database
		else
		{
			// Re-compute the list of NFC tags from the current list
			nfcTagsNeededToDismissList = currentNfcTagsNeededToDismiss.toNfcIdList()
				.map { nfcTagViewModel.findNfcTag(it) ?: NacNfcTag("", it) }
				.toMutableList()
		}

		NacLog.i("Final setup of NFC tags needed to dismiss. nfcSize=${nfcTagsNeededToDismissList!!.size}")

		// Set the size of the list
		initialSizeOfNfcTagsNeededToDismiss = nfcTagsNeededToDismissList!!.size
	}

	/**
	 * Setup the screen and handle the case when the device is locked.
	 */
	@Suppress("deprecation")
	private fun setupScreenOn()
	{
		// Use updated method calls to control screen for APK >= 27
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
		{
			// Check if should NOT save battery and turn screen on
			if (!sharedPreferences.shouldSaveBatteryInAlarmScreen)
			{
				setTurnScreenOn(true)
			}

			// Show when locked
			setShowWhenLocked(true)
		}
		else
		{
			// Check if should NOT save battery and turn screen on
			if (!sharedPreferences.shouldSaveBatteryInAlarmScreen)
			{
				window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
			}

			// Add flag to show when locked
			window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
		}

		// Check if should NOT save battery and keep screen on
		if (!sharedPreferences.shouldSaveBatteryInAlarmScreen)
		{
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	/**
	 * Start NFC.
	 */
	private fun startNfc()
	{
		// NFC is not enabled
		if (!NacNfc.isEnabled(this))
		{
			return
		}

		// NFC exists on the device. The device is NFC capable
		if (NacNfc.exists(this))
		{
			// Create the intent
			val intent = Intent(this, NacActiveAlarmActivity::class.java)
				.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)

			// Start NFC
			NacNfc.start(this, intent)
		}
		// Unable to use NFC on this device
		else
		{
			// Show a toast
			quickToast(this, R.string.error_message_nfc_unsupported)
		}
	}

	/**
	 * Check whether an NFC tag was scanned and if it can dismiss the alarm.
	 *
	 * If multiple NFC tags need to be used to dismiss the alarm, canDismissWithScannedNfc()
	 * will handle removing the NFC tag that was just scanned.
	 */
	private fun wasNfcTagScanned(): Boolean
	{
		// Parse the NFC ID
		val nfcId = NacNfc.parseId(intent)

		// Run the check
		return NacNfc.wasScanned(intent)
				&& NacNfc.canDismissWithScannedNfc(this@NacActiveAlarmActivity, alarm, nfcId, nfcTagsNeededToDismissList)
	}

	companion object
	{

		/**
		 * Extra holding the time at which the dawn started.
		 */
		private const val EXTRA_DAWN_START = "com.nfcalarmclock.DAWN_START"

		/**
		 * Extra holding the time at which the dawn ends and the alarm goes off.
		 */
		private const val EXTRA_DAWN_ALARM_AT = "com.nfcalarmclock.DAWN_ALARM_AT"

		/**
		 * How often the dawn is updated. [Units: ms]
		 */
		private const val DAWN_UPDATE_PERIOD_MILLIS = 400L

		/**
		 * How often the hidden parts of the screen are checked. [Units: ms]
		 */
		private const val DAWN_KEEP_HIDDEN_PERIOD_MILLIS = 1000L

		/**
		 * Brightness the dawn starts at, so that the screen is not completely off.
		 */
		private const val MIN_DAWN_BRIGHTNESS = 0.02f

		/**
		 * Color the texts take once the dawn has made the background too light for
		 * them.
		 */
		private const val DARK_DAWN_TEXT_COLOR = 0xFF333333.toInt()

		/**
		 * Color of the hands of the dial while the screen is still dark.
		 */
		private const val LIGHT_DAWN_CLOCK_COLOR = 0xFFFFFFFF.toInt()

		/**
		 * Create an intent that will be used to start the Alarm activity.
		 *
		 * @param context A context.
		 * @param alarm   An alarm.
		 *
		 * @return The Alarm activity intent.
		 */
		fun getStartIntent(context: Context, alarm: NacAlarm?): Intent
		{
			// Intent flags
			val flags = (Intent.FLAG_ACTIVITY_NEW_TASK
				or Intent.FLAG_ACTIVITY_CLEAR_TASK)

			// Create the intent
			return Intent(context, NacActiveAlarmActivity::class.java)
				.addFlags(flags)
				.addAlarm(alarm)
		}

		/**
		 * Create an intent that will be used to start the Alarm activity.
		 *
		 * @param context A context.
		 * @param intent An intent.
		 * @param alarm An alarm.
		 *
		 * @return The Alarm activity intent.
		 */
		private fun getStartIntent(
			context: Context,
			intent: Intent,
			alarm: NacAlarm?
		): Intent
		{

			// Intent flags
			val flags = (Intent.FLAG_ACTIVITY_NEW_TASK
				or Intent.FLAG_ACTIVITY_CLEAR_TASK)

			// Set the class of the intent
			return intent.setClass(context, NacActiveAlarmActivity::class.java)
				.addFlags(flags)
				.addAlarm(alarm)
		}

		/**
		 * Put the dawn on an intent that already carries the alarm, so that it opens
		 * the sunrise rather than a bare alarm screen.
		 *
		 * @param intent An intent that already carries the alarm.
		 * @param dawnStartMillis Time at which the dawn started.
		 * @param dawnAlarmAtMillis Time at which the alarm will go off.
		 */
		fun addDawnExtras(
			intent: Intent,
			dawnStartMillis: Long,
			dawnAlarmAtMillis: Long
		): Intent
		{
			return intent
				.putExtra(EXTRA_DAWN_START, dawnStartMillis)
				.putExtra(EXTRA_DAWN_ALARM_AT, dawnAlarmAtMillis)
		}

		/**
		 * Start the alarm activity for the dawn, which is to say ahead of the alarm
		 * and with a screen that lights up.
		 *
		 * @param context A context.
		 * @param alarm An alarm.
		 * @param dawnStartMillis Time at which the dawn started.
		 * @param dawnAlarmAtMillis Time at which the alarm will go off.
		 */
		fun startAlarmActivity(
			context: Context,
			alarm: NacAlarm?,
			dawnStartMillis: Long,
			dawnAlarmAtMillis: Long
		)
		{
			// Create the intent
			val intent = addDawnExtras(getStartIntent(context, alarm), dawnStartMillis,
				dawnAlarmAtMillis)

			// Start the activity
			context.startActivity(intent)
		}

		/**
		 * Start the alarm activity with the given alarm.
		 */
		fun startAlarmActivity(context: Context, alarm: NacAlarm?)
		{
			// Create the intent
			val intent = getStartIntent(context, alarm)

			// Start the activity
			context.startActivity(intent)
		}

		/**
		 * Start the alarm activity with the given alarm.
		 */
		fun startAlarmActivity(context: Context, intent: Intent, alarm: NacAlarm?)
		{
			// Create the intent
			val updatedIntent = getStartIntent(context, intent, alarm)

			// Start the activity
			context.startActivity(updatedIntent)
		}

	}

}
