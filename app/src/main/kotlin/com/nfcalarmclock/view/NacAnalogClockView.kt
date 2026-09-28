package com.nfcalarmclock.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A clock with hands, drawn by hand.
 *
 * Android has android.widget.AnalogClock, but its own documentation says it "is no
 * longer supported; except for RemoteViews use cases like app widgets". It also draws
 * from three drawables, which cannot be recoloured as the sunrise goes from black to
 * full light, and that is the whole point of showing a clock during a dawn.
 *
 * There is no second hand on purpose: a hand sweeping every second is agitating in a
 * dark room, and it would force a redraw every second all night.
 */
class NacAnalogClockView
	: View
{

	/**
	 * Colour of the hands and of the dial.
	 */
	var handColor: Int = Color.WHITE
		set(value)
		{
			field = value
			invalidate()
		}

	/**
	 * Hour that is shown, from 0 to 23.
	 */
	private var hour: Int = 0

	/**
	 * Minute that is shown, from 0 to 59.
	 */
	private var minute: Int = 0

	/**
	 * Brush used for everything that is drawn.
	 */
	private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}

	/**
	 * Constructors.
	 */
	constructor(context: Context) : super(context)

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
		: super(context, attrs, defStyleAttr)

	/**
	 * Constructor.
	 */
	init
	{
		val now = Calendar.getInstance()

		hour = now[Calendar.HOUR_OF_DAY]
		minute = now[Calendar.MINUTE]
	}

	/**
	 * Show this time. Nothing is redrawn when the minute has not turned.
	 */
	fun setTime(hour: Int, minute: Int)
	{
		if ((hour == this.hour) && (minute == this.minute))
		{
			return
		}

		this.hour = hour
		this.minute = minute

		invalidate()
	}

	/**
	 * Show the time it is now.
	 */
	fun setTimeToNow()
	{
		val now = Calendar.getInstance()

		setTime(now[Calendar.HOUR_OF_DAY], now[Calendar.MINUTE])
	}

	/**
	 * Keep the clock square, whatever it is given.
	 */
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		val side = min(measuredWidth, measuredHeight)

		setMeasuredDimension(side, side)
	}

	/**
	 * Draw the dial and the two hands.
	 */
	override fun onDraw(canvas: Canvas)
	{
		super.onDraw(canvas)

		val centerX = width / 2f
		val centerY = height / 2f
		val radius = (min(width, height) / 2f) - paddingLeft

		// Nothing to draw into
		if (radius <= 0f)
		{
			return
		}

		paint.color = handColor

		// The rim
		paint.strokeWidth = radius * RIM_WIDTH
		paint.alpha = RIM_ALPHA
		canvas.drawCircle(centerX, centerY, radius - (paint.strokeWidth / 2f), paint)

		// The twelve marks, the four quarters longer and brighter than the rest
		for (i in 0 until 12)
		{
			val isQuarter = (i % 3) == 0
			val markLength = radius * (if (isQuarter) QUARTER_MARK_LENGTH else MARK_LENGTH)

			paint.strokeWidth = radius * (if (isQuarter) QUARTER_MARK_WIDTH else MARK_WIDTH)
			paint.alpha = if (isQuarter) QUARTER_MARK_ALPHA else MARK_ALPHA

			// Straight up is -90 degrees, since zero points to the right
			val angle = Math.toRadians((i * 30) - 90.0)
			val cosine = cos(angle).toFloat()
			val sine = sin(angle).toFloat()
			val outer = radius * MARK_OUTER

			canvas.drawLine(
				centerX + (cosine * (outer - markLength)),
				centerY + (sine * (outer - markLength)),
				centerX + (cosine * outer),
				centerY + (sine * outer),
				paint)
		}

		// The hour hand, which creeps along with the minutes rather than jumping
		val hourAngle = (((hour % 12) + (minute / 60f)) * 30f) - 90f

		paint.strokeWidth = radius * HOUR_HAND_WIDTH
		paint.alpha = HAND_ALPHA
		drawHand(canvas, centerX, centerY, hourAngle, radius * HOUR_HAND_LENGTH)

		// The minute hand
		val minuteAngle = (minute * 6f) - 90f

		paint.strokeWidth = radius * MINUTE_HAND_WIDTH
		paint.alpha = HAND_ALPHA
		drawHand(canvas, centerX, centerY, minuteAngle, radius * MINUTE_HAND_LENGTH)

		// The pin the hands turn on
		paint.style = Paint.Style.FILL
		paint.alpha = HAND_ALPHA
		canvas.drawCircle(centerX, centerY, radius * CENTER_RADIUS, paint)
		paint.style = Paint.Style.STROKE
	}

	/**
	 * Draw one hand, from a little behind the centre to its tip.
	 */
	private fun drawHand(
		canvas: Canvas,
		centerX: Float,
		centerY: Float,
		degrees: Float,
		length: Float
	)
	{
		val angle = Math.toRadians(degrees.toDouble())
		val cosine = cos(angle).toFloat()
		val sine = sin(angle).toFloat()
		val tail = length * HAND_TAIL

		canvas.drawLine(
			centerX - (cosine * tail),
			centerY - (sine * tail),
			centerX + (cosine * length),
			centerY + (sine * length),
			paint)
	}

	companion object
	{

		/**
		 * Everything below is a fraction of the radius, so that the clock draws the same
		 * at any size, or an alpha out of 255.
		 */
		private const val RIM_WIDTH = 0.015f
		private const val RIM_ALPHA = 60

		private const val MARK_OUTER = 0.88f
		private const val MARK_LENGTH = 0.07f
		private const val MARK_WIDTH = 0.02f
		private const val MARK_ALPHA = 110

		private const val QUARTER_MARK_LENGTH = 0.14f
		private const val QUARTER_MARK_WIDTH = 0.04f
		private const val QUARTER_MARK_ALPHA = 200

		private const val HOUR_HAND_LENGTH = 0.48f
		private const val HOUR_HAND_WIDTH = 0.055f

		private const val MINUTE_HAND_LENGTH = 0.72f
		private const val MINUTE_HAND_WIDTH = 0.035f

		private const val HAND_ALPHA = 235
		private const val HAND_TAIL = 0.22f

		private const val CENTER_RADIUS = 0.045f

	}

}
