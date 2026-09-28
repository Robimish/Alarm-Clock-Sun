package com.nfcalarmclock.view.wheel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A horizontal wheel of values that is turned by dragging left or right.
 *
 * It draws a ruler of ticks, one per value, with a longer tick and a label every
 * fifth one, and a fixed marker in the middle. Letting go snaps to the nearest
 * value.
 */
class NacHorizontalWheel @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : View(context, attrs)
{

	/**
	 * Smallest value that can be chosen.
	 */
	var minValue: Int = 0

	/**
	 * Largest value that can be chosen.
	 */
	var maxValue: Int = 60

	/**
	 * Color of the center marker and of the value that is selected.
	 */
	var accentColor: Int = Color.WHITE
		set(value)
		{
			field = value
			invalidate()
		}

	/**
	 * Called every time the value changes, including while the wheel is turning.
	 */
	var onValueChangedListener: ((Int) -> Unit)? = null

	/**
	 * Distance between two values. [Units: px]
	 */
	private val spacing: Float = 26f * resources.displayMetrics.density

	/**
	 * How far the wheel has been turned, from the smallest value. [Units: px]
	 */
	private var offset: Float = 0f

	/**
	 * Scroller used for the fling and for the snap at the end of a gesture.
	 */
	private val scroller: OverScroller = OverScroller(context)

	/**
	 * Flag indicating that the scroller is settling on a value rather than flinging.
	 */
	private var isSnapping: Boolean = false

	/**
	 * Paint of the ticks.
	 */
	private val tickPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}

	/**
	 * Paint of the labels.
	 */
	private val labelPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		textAlign = Paint.Align.CENTER

		// scaledDensity is one number for the whole screen, and Android 14 no longer
		// has one: past a certain size the font scale stops being a simple multiple, so
		// that headings do not grow as fast as body text. applyDimension asks the
		// system what 12sp is worth here and now, whatever curve it follows
		textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
			resources.displayMetrics)
	}

	/**
	 * The value that the wheel is currently showing.
	 */
	var value: Int
		get() = (minValue + offset/spacing).roundToInt().coerceIn(minValue, maxValue)
		set(v)
		{
			offset = (v.coerceIn(minValue, maxValue) - minValue) * spacing
			invalidate()
		}

	/**
	 * Largest offset that the wheel can be turned to. [Units: px]
	 */
	private val maxOffset: Float
		get() = (maxValue - minValue) * spacing

	/**
	 * Detector of the drag and fling gestures.
	 */
	private val gestureDetector = GestureDetector(context,
		object : GestureDetector.SimpleOnGestureListener() {

			override fun onDown(e: MotionEvent): Boolean
			{
				// Stop whatever the wheel was doing
				scroller.forceFinished(true)
				isSnapping = false

				return true
			}

			override fun onScroll(
				e1: MotionEvent?,
				e2: MotionEvent,
				distanceX: Float,
				distanceY: Float
			): Boolean
			{
				// Dragging to the left turns the wheel towards the larger values
				setOffset(offset + distanceX)

				return true
			}

			override fun onFling(
				e1: MotionEvent?,
				e2: MotionEvent,
				velocityX: Float,
				velocityY: Float
			): Boolean
			{
				// Let the wheel keep turning on its own
				scroller.fling(offset.toInt(), 0, (-velocityX/2f).toInt(), 0,
					0, maxOffset.toInt(), 0, 0)
				postInvalidateOnAnimation()

				return true
			}

		})

	/**
	 * Move the wheel, keeping it within its bounds, and tell the listener when the
	 * value it shows has changed.
	 */
	private fun setOffset(newOffset: Float)
	{
		val before = value

		offset = newOffset.coerceIn(0f, maxOffset)

		if (value != before)
		{
			onValueChangedListener?.invoke(value)
		}

		invalidate()
	}

	/**
	 * Settle on the value that is closest to where the wheel stopped.
	 */
	private fun snap()
	{
		val target = ((value - minValue) * spacing).toInt()
		val distance = target - offset.toInt()

		isSnapping = true

		scroller.startScroll(offset.toInt(), 0, distance, 0, 220)
		postInvalidateOnAnimation()
	}

	/**
	 * Called while the wheel is flinging or settling.
	 */
	override fun computeScroll()
	{
		// Nothing is moving
		if (!scroller.computeScrollOffset())
		{
			// The fling has just ended, so settle on the nearest value
			if (!isSnapping && !scroller.isFinished)
			{
				snap()
			}

			return
		}

		setOffset(scroller.currX.toFloat())

		// The fling ran its course
		if (scroller.isFinished && !isSnapping)
		{
			snap()
		}
		else
		{
			postInvalidateOnAnimation()
		}
	}

	/**
	 * Called when the wheel is touched.
	 */
	override fun onTouchEvent(event: MotionEvent): Boolean
	{
		// Keep the dialog from stealing the gesture
		if (event.actionMasked == MotionEvent.ACTION_DOWN)
		{
			parent?.requestDisallowInterceptTouchEvent(true)
		}

		val handled = gestureDetector.onTouchEvent(event)

		// The finger was lifted without a fling, so settle on the nearest value
		if ((event.actionMasked == MotionEvent.ACTION_UP)
			|| (event.actionMasked == MotionEvent.ACTION_CANCEL))
		{
			parent?.requestDisallowInterceptTouchEvent(false)

			if (scroller.isFinished)
			{
				snap()
			}
		}

		return handled || true
	}

	/**
	 * Draw the wheel.
	 */
	override fun onDraw(canvas: Canvas)
	{
		// Super
		super.onDraw(canvas)

		val centerX = width / 2f
		val current = minValue + offset/spacing

		// Ticks and labels
		for (v in minValue..maxValue)
		{
			val x = centerX + (v - current)*spacing

			// Outside of the view
			if ((x < -spacing) || (x > width + spacing))
			{
				continue
			}

			// Ticks every fifth value are longer and carry a label. So are the two ends
			val isMajor = (v % 5 == 0) || (v == minValue) || (v == maxValue)

			// Values far from the middle fade out
			val fade = (1f - abs(v - current)/9f).coerceAtLeast(0.15f)

			tickPaint.color = Color.WHITE
			tickPaint.alpha = (255 * fade * (if (isMajor) 0.75f else 0.32f)).toInt()
			tickPaint.strokeWidth = if (isMajor) 2f*resources.displayMetrics.density
				else 1f*resources.displayMetrics.density

			val top = if (isMajor) height*0.20f else height*0.32f
			val bottom = if (isMajor) height*0.56f else height*0.48f

			canvas.drawLine(x, top, x, bottom, tickPaint)

			// Label
			if (isMajor)
			{
				labelPaint.color = Color.WHITE
				labelPaint.alpha = (255 * fade * 0.7f).toInt()

				canvas.drawText(v.toString(), x, height*0.82f, labelPaint)
			}
		}

		// Center marker
		tickPaint.color = accentColor
		tickPaint.alpha = 255
		tickPaint.strokeWidth = 3f * resources.displayMetrics.density

		canvas.drawLine(centerX, height*0.12f, centerX, height*0.64f, tickPaint)
	}

}
