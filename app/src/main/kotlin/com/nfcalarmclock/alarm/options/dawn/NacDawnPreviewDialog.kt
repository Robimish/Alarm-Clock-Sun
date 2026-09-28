package com.nfcalarmclock.alarm.options.dawn

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.nfcalarmclock.R
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.NacCalendar
import com.nfcalarmclock.view.NacAnalogClockView
import java.util.Calendar
import kotlin.random.Random

/**
 * Preview of the dawn.
 *
 * The screen gradually lights up, from black to the color (or the image) chosen
 * for the alarm, so that the choice can be judged before using it.
 *
 * Note: this is a dialog and not an activity on purpose. An activity would take
 * the app off screen, and Android closes the options dialog underneath while
 * that happens, so coming back from the preview landed on the alarm list instead
 * of back in the dawn settings. A dialog stays inside the same screen.
 */
class NacDawnPreviewDialog
	: DialogFragment()
{

	/**
	 * Root view, whose background is the color of the dawn.
	 */
	private lateinit var root: FrameLayout

	/**
	 * Image view, used when an image is shown instead of a color.
	 */
	private lateinit var imageView: ImageView

	/**
	 * Clock.
	 */
	private lateinit var clockTextView: TextView

	/**
	 * Phrase shown above the clock.
	 */
	private lateinit var hintTextView: TextView

	/**
	 * Handler used to update the screen.
	 */
	private val handler: Handler = Handler(Looper.getMainLooper())

	/**
	 * Runnable that updates the screen.
	 */
	private val tick: Runnable = Runnable { update() }

	/**
	 * Time at which the preview started. [Units: ms]
	 */
	private var startMillis: Long = 0

	/**
	 * How long a tap shows the screen as normal. [Units: ms]
	 */
	private var revealMillis: Long = 12000L

	/**
	 * Color the screen fades to.
	 */
	var dawnColor: Int = NacDawnOptionsDialog.DEFAULT_COLOR

	/**
	 * Path of the image shown instead of a color, when there is one.
	 */
	var dawnImagePath: String = ""

	/**
	 * Which parts of the screen are hidden, as a set of flags.
	 */
	var dawnHiddenViews: Int = 0

	/**
	 * How long a tap shows the screen as normal. Zero means a tap does nothing
	 * but close the preview once it is over. [Units: sec]
	 */
	var dawnRevealSeconds: Int = 0

	/**
	 * Whether a dial is shown rather than the digits.
	 */
	var dawnUseAnalogClock: Boolean = false

	/**
	 * Name of the alarm.
	 */
	var dawnAlarmName: String = ""

	/**
	 * Title of the media of the alarm.
	 */
	var dawnMediaTitle: String = ""

	/**
	 * Name of the alarm, shown as it would be during the dawn.
	 */
	private lateinit var nameTextView: TextView

	/**
	 * Date, shown as it would be during the dawn.
	 */
	private lateinit var dateTextView: TextView

	/**
	 * Title of the media, shown as it would be during the dawn.
	 */
	private lateinit var mediaTextView: TextView

	/**
	 * Clock with hands, shown instead of the digits when it was asked for.
	 */
	private lateinit var analogClockView: NacAnalogClockView

	/**
	 * Whether an image is shown instead of a color.
	 */
	private var useImage: Boolean = false

	/**
	 * How long the preview runs. [Units: ms]
	 */
	private var durationMillis: Long = 12000L

	/**
	 * Flag indicating that the preview has run its course, and is waiting for a
	 * tap to close.
	 */
	private var isFinished: Boolean = false

	/**
	 * Flag indicating that the screen has been momentarily put back to normal.
	 */
	private var isRevealing: Boolean = false

	/**
	 * Runnable that puts the preview back after the screen was shown as normal.
	 */
	private val endRevealRunnable: Runnable = Runnable {
		isRevealing = false
		setViewsHidden(true)
	}

	/**
	 * Called when the dialog is created.
	 */
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog
	{
		val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)

		dialog.setCanceledOnTouchOutside(false)

		return dialog
	}

	/**
	 * Called when the view is created.
	 */
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View
	{
		return inflater.inflate(R.layout.act_dawn, container, false)
	}

	/**
	 * Called once the view is there.
	 */
	override fun onViewCreated(view: View, savedInstanceState: Bundle?)
	{
		// Super
		super.onViewCreated(view, savedInstanceState)

		// Get the views
		root = view.findViewById(R.id.dawn_root)
		imageView = view.findViewById(R.id.dawn_image)
		clockTextView = view.findViewById(R.id.dawn_clock)
		hintTextView = view.findViewById(R.id.dawn_hint)
		nameTextView = view.findViewById(R.id.dawn_alarm_name)
		dateTextView = view.findViewById(R.id.dawn_date)
		mediaTextView = view.findViewById(R.id.dawn_media_title)
		analogClockView = view.findViewById(R.id.dawn_analog_clock)

		// Start the clock a tenth of the way down, as the alarm screen does through its
		// guideline. Worked out once the view has a height, since a fraction of nothing
		// is nothing
		val clockRow: View = view.findViewById(R.id.dawn_clock_row)

		clockRow.post {

			val params = clockRow.layoutParams as? FrameLayout.LayoutParams

			if ((params != null) && (root.height > 0))
			{
				params.topMargin = (root.height * CLOCK_TOP_FRACTION).toInt() +
					resources.getDimensionPixelSize(R.dimen.xlarge)
				clockRow.layoutParams = params
			}

		}

		// The two clocks take turns
		if (dawnUseAnalogClock)
		{
			clockTextView.visibility = View.GONE
			analogClockView.visibility = View.VISIBLE

			// A dial has no descender to breathe with, so it is given more air
			val gapView: View = view.findViewById(R.id.dawn_clock_text_gap)
			val gapParams = gapView.layoutParams

			gapParams.height = resources.getDimensionPixelSize(R.dimen.analog_clock_date_gap)
			gapView.layoutParams = gapParams
		}

		// Show the same things the alarm screen would show
		if (dawnAlarmName.isNotEmpty())
		{
			nameTextView.text = dawnAlarmName
			nameTextView.visibility = View.VISIBLE
		}

		if (dawnMediaTitle.isNotEmpty())
		{
			mediaTextView.text = dawnMediaTitle
			mediaTextView.visibility = View.VISIBLE
		}

		// The very views of the alarm screen, copied into this layout, shown the way the
		// alarm shows them at rest. There is nothing to postpone and nothing to stop in
		// a preview, so either one closes it, as a tap anywhere else does
		val snoozeView: View = view.findViewById(R.id.snooze_view)
		val dismissView: View = view.findViewById(R.id.dismiss_view)

		snoozeView.setOnClickListener { dismiss() }
		dismissView.setOnClickListener { dismiss() }

		root.setOnClickListener { dismiss() }

		// Show one of the phrases at random
		val phrases = resources.getStringArray(R.array.dawn_phrases)

		if (phrases.isNotEmpty())
		{
			hintTextView.text = phrases[Random.nextInt(phrases.size)]
		}

		// Setup the image, when one is used instead of a color
		useImage = dawnImagePath.isNotEmpty()

		if (useImage)
		{
			setupImage(dawnImagePath)
		}

		// Keep the screen awake for as long as the preview lasts
		dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		// How long the preview runs, and how long a tap shows the screen as normal
		val shared = NacSharedPreferences(requireContext())

		durationMillis = shared.dawnPreviewDuration * 1000L
		revealMillis = dawnRevealSeconds * 1000L

		// Hide whatever the options say should not be seen
		setViewsHidden(true)

		// Start updating the screen
		// The ramp is put off by a moment. Until then the progress below is negative
		// and clamped to zero, so the screen simply sits at its darkest and the eye has
		// somewhere to land before anything starts to move
		startMillis = System.currentTimeMillis() + LEAD_IN_MILLIS

		handler.post(tick)
	}

	/**
	 * Called when the view is destroyed.
	 */
	override fun onDestroyView()
	{
		// Stop updating the screen
		handler.removeCallbacksAndMessages(null)

		// Give the brightness back to the system
		setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

		// Super
		super.onDestroyView()
	}

	/**
	 * Setup the image that is shown instead of a color.
	 */
	private fun setupImage(path: String)
	{
		try
		{
			imageView.setImageURI(path.toUri())

			// Nothing could be read from the image
			if (imageView.drawable == null)
			{
				NacLog.e("Unable to read the dawn image. Falling back on the color")
				useImage = false

				return
			}

			imageView.visibility = View.VISIBLE
			imageView.alpha = 0f
			root.setBackgroundColor(Color.BLACK)
		}
		catch (e: Exception)
		{
			// Fall back on the color
			NacLog.e("Unable to show the dawn image", throwable = e)
			useImage = false
			imageView.visibility = View.GONE
		}
	}

	/**
	 * The screen was tapped.
	 */
	private fun onScreenTapped()
	{
		// Already showing the normal screen, so go straight back to the preview
		if (isRevealing)
		{
			isRevealing = false
			handler.removeCallbacks(endRevealRunnable)

			return
		}

		// The preview has run its course, so close it
		if (isFinished)
		{
			dismiss()
			return
		}

		// A tap does nothing for this alarm
		if (revealMillis <= 0)
		{
			return
		}

		reveal()
	}

	/**
	 * Put the screen back to normal for a few seconds.
	 */
	private fun reveal()
	{

		isRevealing = true

		// Put the screen back as it normally looks
		setViewsHidden(false)
		imageView.alpha = 0f
		root.setBackgroundColor(Color.BLACK)
		setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

		// Go back to the preview afterwards
		handler.postDelayed(endRevealRunnable, revealMillis)
	}

	/**
	 * Hide, or bring back, the parts of the screen that the options say should not
	 * be seen during the dawn.
	 */
	private fun setViewsHidden(hide: Boolean)
	{
		// Nothing is hidden
		if (dawnHiddenViews == 0)
		{
			return
		}

		// The dial is not a text, so it is handled on its own. It is only ever on
		// screen when it is the clock that was chosen
		if (dawnUseAnalogClock && ((dawnHiddenViews and NacAlarm.DAWN_HIDE_CLOCK) != 0))
		{
			analogClockView.visibility = if (hide) View.GONE else View.VISIBLE
		}

		val groups = listOf(
			NacAlarm.DAWN_HIDE_PHRASE to hintTextView,
			NacAlarm.DAWN_HIDE_NAME to nameTextView,
			NacAlarm.DAWN_HIDE_CLOCK to clockTextView,
			NacAlarm.DAWN_HIDE_DATE to dateTextView,
			NacAlarm.DAWN_HIDE_MEDIA to mediaTextView)

		groups.forEach { (flag, view) ->

			// This one is shown
			if ((dawnHiddenViews and flag) == 0)
			{
				return@forEach
			}

			// The digits gave their place to the dial, so they stay away
			if ((view === clockTextView) && dawnUseAnalogClock)
			{
				return@forEach
			}

			// A view that has nothing to say was never shown to begin with
			if (view.text.isNullOrEmpty())
			{
				return@forEach
			}

			view.visibility = if (hide) View.GONE else View.VISIBLE

		}
	}

	/**
	 * Set the brightness of the screen.
	 */
	private fun setBrightness(value: Float)
	{
		val window = dialog?.window ?: return
		val attributes = window.attributes

		attributes.screenBrightness = value
		window.attributes = attributes
	}

	/**
	 * Blend two colors together.
	 */
	private fun blend(from: Int, to: Int, fraction: Float): Int
	{
		val f = fraction.coerceIn(0f, 1f)
		val r = Color.red(from) + ((Color.red(to) - Color.red(from)) * f).toInt()
		val g = Color.green(from) + ((Color.green(to) - Color.green(from)) * f).toInt()
		val b = Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * f).toInt()

		return Color.argb(255, r, g, b)
	}

	/**
	 * Update the screen.
	 */
	private fun update()
	{
		// The dialog is on its way out
		if (!isAdded)
		{
			return
		}

		val now = System.currentTimeMillis()
		val progress = ((now - startMillis) / durationMillis.toFloat()).coerceIn(0f, 1f)

		// Gentle ramp up, the same curve that a sunrise follows
		val curve = progress * progress

		// Show the dawn, unless the screen has been put back to normal for a moment
		if (!isRevealing)
		{
			// Set the brightness of the screen
			setBrightness((MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS)*curve).coerceIn(0f, 1f))

			// Set the background
			if (useImage)
			{
				imageView.alpha = curve
			}
			else
			{
				root.setBackgroundColor(blend(Color.BLACK, dawnColor, curve))
			}
		}

		// Set the clock
		val cal = Calendar.getInstance()

		clockTextView.text = NacCalendar.getClockTime(requireContext(),
			cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
		dateTextView.text = NacCalendar.getDate(cal)
		analogClockView.setTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

		// Keep the hidden parts hidden. The clock and the date only get their text
		// here, so the first pass, back when the view was created, found them empty
		// and left them alone
		if (!isRevealing)
		{
			setViewsHidden(true)
		}

		// Keep the text readable against the background
		val isLightBackground = (curve > 0.55f)

		clockTextView.setTextColor(if (isLightBackground) DARK_TEXT_COLOR else LIGHT_TEXT_COLOR)
		analogClockView.handColor = if (isLightBackground) DARK_TEXT_COLOR else LIGHT_TEXT_COLOR
		hintTextView.setTextColor(if (isLightBackground) DARK_HINT_COLOR else LIGHT_HINT_COLOR)
		dateTextView.setTextColor(if (isLightBackground) DARK_HINT_COLOR else LIGHT_HINT_COLOR)
		nameTextView.setTextColor(if (isLightBackground) DARK_HINT_COLOR else LIGHT_HINT_COLOR)
		mediaTextView.setTextColor(if (isLightBackground) DARK_HINT_COLOR else LIGHT_HINT_COLOR)

		// The preview has run its course. The light is held at full for a moment, so
		// that it can be judged, and then it closes on its own
		if ((progress >= 1f) && !isFinished)
		{
			isFinished = true

			handler.postDelayed({ if (isAdded) dismiss() }, HOLD_AT_FULL_MILLIS)
		}

		// Schedule the next update
		handler.postDelayed(tick, UPDATE_PERIOD_MILLIS)
	}

	companion object
	{

		/**
		 * Tag for the dialog.
		 */
		const val TAG = "NacDawnPreviewDialog"

		/**
		 * How often the screen is updated. [Units: ms]
		 */
		private const val UPDATE_PERIOD_MILLIS = 400L

		/**
		 * How long the preview holds at full light before closing on its own, so that
		 * the colour can be judged at 100 %. [Units: ms]
		 */
		private const val HOLD_AT_FULL_MILLIS = 4000L

		/**
		 * How long the screen is held at its darkest before the ramp starts, so that
		 * the preview does not begin in the middle of its own movement. [Units: ms]
		 */
		private const val LEAD_IN_MILLIS = 1000L

		/**
		 * How far down the screen the clock starts, as a fraction of the height. The
		 * alarm screen uses the very same tenth, through a guideline.
		 */
		private const val CLOCK_TOP_FRACTION = 0.045f

		/**
		 * Brightness the preview starts at, so that the screen is not completely off.
		 */
		private const val MIN_BRIGHTNESS = 0.02f

		/**
		 * Color of the clock on a dark background.
		 */
		private const val LIGHT_TEXT_COLOR = 0x88FFFFFF.toInt()

		/**
		 * Color of the clock on a light background.
		 */
		private const val DARK_TEXT_COLOR = 0xCC333333.toInt()

		/**
		 * Color of the phrase on a dark background.
		 */
		private const val LIGHT_HINT_COLOR = 0x55FFFFFF.toInt()

		/**
		 * Color of the phrase on a light background.
		 */
		private const val DARK_HINT_COLOR = 0x88555555.toInt()

		/**
		 * Show the preview.
		 */
		fun show(
			manager: FragmentManager,
			color: Int,
			imagePath: String,
			hidden: Int = 0,
			revealSeconds: Int = 0,
			alarmName: String = "",
			mediaTitle: String = "",
			useAnalogClock: Boolean = false
		)
		{
			val dialog = NacDawnPreviewDialog()

			dialog.dawnColor = color
			dialog.dawnImagePath = imagePath
			dialog.dawnHiddenViews = hidden
			dialog.dawnRevealSeconds = revealSeconds
			dialog.dawnAlarmName = alarmName
			dialog.dawnMediaTitle = mediaTitle
			dialog.dawnUseAnalogClock = useAnalogClock

			dialog.show(manager, TAG)
		}

	}

}
