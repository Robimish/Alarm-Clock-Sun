package com.nfcalarmclock.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A rail with a sun on it, which travels from left to right as the next alarm comes
 * closer. The part already covered lights up behind it.
 *
 * The point is to see how far off the alarm is without reading anything. The line
 * above still says it in words.
 *
 * The rail covers the last TRACK_SPAN_MILLIS before the alarm. An alarm further off
 * than that leaves the sun at the very start, which is the truth: nothing is near.
 *
 * With no alarm at all the rail is still there, with the sun resting at the start and
 * drawn in outline, so that the top of the screen keeps its shape either way and an
 * empty diary reads as an empty diary rather than as a fault.
 */
class NacNextAlarmTrackView
	: View
{

	/**
	 * How far along the sun is, from 0 at the far left to 1 at the alarm.
	 */
	var progress: Float = 0f
		set(value)
		{
			val clamped = value.coerceIn(0f, 1f)

			// A move too small to see is not worth a redraw, and this is asked for
			// every second while an alarm is snoozed
			if (kotlin.math.abs(clamped - field) < MIN_STEP)
			{
				return
			}

			field = clamped
			invalidate()
		}

	/**
	 * Whether there is an alarm to walk towards at all.
	 */
	var hasAlarm: Boolean = true
		set(value)
		{
			if (value == field)
			{
				return
			}

			field = value
			invalidate()
		}

	/**
	 * Brush used for everything that is drawn.
	 */
	private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
	 * Draw the rail, the part already covered, and the sun.
	 */
	override fun onDraw(canvas: Canvas)
	{
		super.onDraw(canvas)

		// The sun is inset by its whole reach, rays included, so that neither end of
		// its travel is cut off by the edge of the view
		val reach = SUN_RADIUS_DP * RAY_OUTER * density
		val left = paddingLeft.toFloat() + reach
		val right = width - paddingRight.toFloat() - reach
		val y = (paddingTop + height - paddingBottom) / 2f

		// Nothing to draw into
		if (right <= left)
		{
			return
		}

		val sunX = left + ((right - left) * progress)
		val radius = SUN_RADIUS_DP * density

		// The rail, all of it, barely there
		paint.style = Paint.Style.STROKE
		paint.shader = null
		paint.color = TRACK_COLOR
		paint.strokeWidth = TRACK_WIDTH_DP * density
		canvas.drawLine(left, y, right, y, paint)

		// The part already covered, coming up out of nothing on the left
		if (hasAlarm && (sunX > left + 1f))
		{
			paint.shader = LinearGradient(left, y, sunX, y,
				LIT_COLOR_START, LIT_COLOR_END, Shader.TileMode.CLAMP)
			paint.strokeWidth = LIT_WIDTH_DP * density
			canvas.drawLine(left, y, sunX, y, paint)
			paint.shader = null
		}

		// The rays, short, so that the sun stays a point on a line rather than a badge
		paint.color = if (hasAlarm) RAY_COLOR else RAY_COLOR_IDLE
		paint.strokeWidth = RAY_WIDTH_DP * density
		paint.style = Paint.Style.STROKE

		for (i in 0 until RAY_COUNT)
		{
			val angle = (2.0 * Math.PI * i) / RAY_COUNT
			val ca = cos(angle).toFloat()
			val sa = sin(angle).toFloat()

			canvas.drawLine(
				sunX + (ca * radius * RAY_INNER), y + (sa * radius * RAY_INNER),
				sunX + (ca * radius * RAY_OUTER), y + (sa * radius * RAY_OUTER),
				paint)
		}

		// The sun itself: filled while something is coming, an outline while nothing
		// is, so that a rail at rest is a drawing rather than a faded button
		if (hasAlarm)
		{
			paint.style = Paint.Style.FILL
			paint.color = SUN_COLOR
			canvas.drawCircle(sunX, y, radius, paint)
		}
		else
		{
			paint.style = Paint.Style.STROKE
			paint.color = SUN_COLOR_IDLE
			paint.strokeWidth = SUN_OUTLINE_DP * density
			canvas.drawCircle(sunX, y, radius - (SUN_OUTLINE_DP*density/2f), paint)
		}
	}

	/**
	 * Pixels in one dp, so that everything above can be written in dp.
	 */
	private val density: Float
		get() = resources.displayMetrics.density

	companion object
	{

		/**
		 * How long a stretch the rail covers. An alarm further off than this leaves the
		 * sun at the start. [Units: ms]
		 */
		const val TRACK_SPAN_MILLIS = 24L * 60L * 60L * 1000L

		/**
		 * Work out how far along the sun should be for an alarm at this time.
		 */
		fun progressFor(alarmMillis: Long, nowMillis: Long = System.currentTimeMillis()): Float
		{
			val left = alarmMillis - nowMillis

			// The alarm is behind us, or as good as
			if (left <= 0L)
			{
				return 1f
			}

			return max(0f, min(1f, 1f - (left.toFloat() / TRACK_SPAN_MILLIS)))
		}

		/**
		 * A move smaller than this is not redrawn.
		 */
		private const val MIN_STEP = 0.0015f

		private const val SUN_RADIUS_DP = 7f
		private const val TRACK_WIDTH_DP = 1.5f
		private const val LIT_WIDTH_DP = 2.5f
		private const val RAY_WIDTH_DP = 1.5f

		private const val RAY_COUNT = 12
		private const val RAY_INNER = 1.45f
		private const val RAY_OUTER = 2.1f

		private val TRACK_COLOR = Color.argb(60, 255, 255, 255)
		private val LIT_COLOR_START = Color.argb(0, 255, 233, 122)
		private val LIT_COLOR_END = Color.argb(210, 255, 233, 122)
		private val RAY_COLOR = Color.argb(150, 255, 233, 122)
		private val SUN_COLOR = Color.argb(255, 255, 196, 0)

		/**
		 * The sun with nothing to walk towards: white and in outline, so that the warm
		 * color means something is coming rather than being there all the time.
		 */
		private val RAY_COLOR_IDLE = Color.argb(140, 255, 255, 255)
		private val SUN_COLOR_IDLE = Color.argb(200, 255, 255, 255)

		/**
		 * How thick the ring of the sun at rest is drawn. [Units: dp]
		 */
		private const val SUN_OUTLINE_DP = 1.8f

	}

}
