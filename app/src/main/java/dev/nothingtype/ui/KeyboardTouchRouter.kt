package dev.nothingtype.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup

/** Routes each finger to a key, including the visual spacing around that key. */
internal class KeyboardTouchRouter(
    private val root: ViewGroup,
    private val hitTest: (Float, Float) -> InstantKey?,
    private val beforeDown: () -> Unit
) {
    private data class Target(val key: InstantKey, val downTime: Long)
    private val targets = mutableMapOf<Int, Target>()
    private var routing = false
    private val bounds = Rect()
    private val properties = arrayOf(MotionEvent.PointerProperties())
    private val coordinates = arrayOf(MotionEvent.PointerCoords())

    fun dispatch(event: MotionEvent): Boolean? {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            cancel()
            if (hitTest(event.x, event.y) == null) return null
            routing = true
        }
        if (!routing) return null
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val key = hitTest(event.getX(index), event.getY(index))
                if (key != null) {
                    beforeDown()
                    val target = Target(key, event.eventTime)
                    targets[event.getPointerId(index)] = target
                    send(event, index, MotionEvent.ACTION_DOWN, target)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    targets[event.getPointerId(index)]?.let { send(event, index, MotionEvent.ACTION_MOVE, it) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                targets.remove(event.getPointerId(index))?.let { send(event, index, MotionEvent.ACTION_UP, it) }
                if (action == MotionEvent.ACTION_UP) cancel()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun send(event: MotionEvent, index: Int, action: Int, target: Target) {
        bounds.setEmpty()
        root.offsetDescendantRectToMyCoords(target.key, bounds)
        val coords = coordinates[0]
        event.getPointerProperties(index, properties[0])
        event.getPointerCoords(index, coords)
        coords.x -= bounds.left
        coords.y -= bounds.top
        val single = MotionEvent.obtain(target.downTime, event.eventTime, action, 1,
            properties, coordinates, event.metaState, event.buttonState,
            event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
        try { target.key.dispatchTouchEvent(single) } finally { single.recycle() }
    }

    fun cancel() {
        routing = false
        val previous = targets.values.toList()
        targets.clear()
        val now = SystemClock.uptimeMillis()
        previous.forEach { target ->
            val event = MotionEvent.obtain(target.downTime, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try { target.key.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
}
