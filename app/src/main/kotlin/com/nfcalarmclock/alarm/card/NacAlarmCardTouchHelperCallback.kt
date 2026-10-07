package com.nfcalarmclock.alarm.card

import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.RecyclerView
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.card.NacBaseCardTouchHelperCallback

/**
 * Swipe an alarm card to copy or delete it, and, since 2.01, hold it a moment to drag
 * it up or down the list.
 *
 * Dragging only works when the list is not rearranged on its own (otherwise the order
 * would be undone straight away), and only on a closed card: an open card has buttons
 * of its own that answer a long press.
 */
class NacAlarmCardTouchHelperCallback(
	onCardSwipedListener: OnCardSwipedListener<NacAlarm>,
	private val onCardDragListener: OnCardDragListener? = null
) : NacBaseCardTouchHelperCallback<NacAlarm>(onCardSwipedListener)
{

	/**
	 * Listener for dragging a card up and down the list.
	 */
	interface OnCardDragListener
	{

		/**
		 * Whether cards may be dragged at the moment.
		 */
		fun canDrag(): Boolean

		/**
		 * A card was dragged over another one, so the two change places.
		 */
		fun onCardMoved(fromIndex: Int, toIndex: Int)

		/**
		 * The card was let go, so the new order can be saved.
		 */
		fun onDragEnded()

		/**
		 * The finger started to move with the card held, once per drag.
		 */
		fun onDragMoved() {}

	}

	/**
	 * Whether a card is being dragged.
	 */
	private var isDragging: Boolean = false

	/**
	 * Whether the held card has moved yet in this drag.
	 */
	private var hasDragMoved: Boolean = false

	/**
	 * Swipe left and right as before (same as NacBaseCardTouchHelperCallback), and up and down when a card can be dragged.
	 */
	override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int
	{
		val canDrag = (onCardDragListener?.canDrag() == true)
			&& ((vh as? NacAlarmCardHolder)?.isCollapsed == true)
		val dragFlags = if (canDrag) (ItemTouchHelper.UP or ItemTouchHelper.DOWN) else 0

		return makeMovementFlags(dragFlags, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
	}

	/**
	 * A long press starts a drag.
	 */
	override fun isLongPressDragEnabled(): Boolean
	{
		return onCardDragListener?.canDrag() == true
	}

	/**
	 * The dragged card passed over another one.
	 */
	override fun onMove(
		rv: RecyclerView,
		vh: RecyclerView.ViewHolder,
		target: RecyclerView.ViewHolder
	): Boolean
	{
		val listener = onCardDragListener ?: return false
		val from = vh.bindingAdapterPosition
		val to = target.bindingAdapterPosition

		if ((from == RecyclerView.NO_POSITION) || (to == RecyclerView.NO_POSITION))
		{
			return false
		}

		listener.onCardMoved(from, to)

		return true
	}

	/**
	 * A drag starts: say so with a little vibration.
	 */
	override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int)
	{
		super.onSelectedChanged(vh, actionState)

		if (actionState == ACTION_STATE_DRAG)
		{
			isDragging = true
			hasDragMoved = false
			vh?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
		}
	}

	/**
	 * The card was let go.
	 */
	override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder)
	{
		super.clearView(rv, vh)
		getDefaultUIUtil().clearView(vh.itemView)

		if (isDragging)
		{
			isDragging = false
			onCardDragListener?.onDragEnded()
		}
	}

	/**
	 * A dragged card moves as a whole. A swiped one is drawn as before.
	 */
	override fun onChildDraw(
		c: Canvas,
		rv: RecyclerView,
		vh: RecyclerView.ViewHolder,
		dx: Float,
		dy: Float,
		action: Int,
		active: Boolean)
	{
		if (action == ACTION_STATE_DRAG)
		{
			// A few pixels of movement, not the slightest tremor of a held finger
			if (active && !hasDragMoved && (kotlin.math.abs(dy) > DRAG_MOVE_THRESHOLD_PX))
			{
				hasDragMoved = true
				onCardDragListener?.onDragMoved()
			}

			getDefaultUIUtil().onDraw(c, rv, vh.itemView, dx, dy, action, active)
			return
		}

		super.onChildDraw(c, rv, vh, dx, dy, action, active)
	}

	companion object
	{

		/**
		 * How far the held card has to move before it counts as a drag. [Units: px]
		 */
		private const val DRAG_MOVE_THRESHOLD_PX = 24f

	}

}
