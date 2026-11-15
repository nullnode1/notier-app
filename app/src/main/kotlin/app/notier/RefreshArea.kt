package app.notier

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

internal class RefreshArea(context: Context, private val onRefresh: () -> Unit) : FrameLayout(context) {
    lateinit var scrollingList: View
    var onPull: (Float) -> Unit = {}
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val threshold = resources.displayMetrics.density * 64
    private var x = 0f
    private var y = 0f
    private var available = false
    private var pulling = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                available = !scrollingList.canScrollVertically(-1)
                pulling = false
                x = event.x
                y = event.y
            }
            MotionEvent.ACTION_MOVE -> if (available) {
                val delta = event.y - y
                val horizontal = abs(event.x - x)
                if (delta > slop && delta > horizontal * 1.25 && !scrollingList.canScrollVertically(-1)) {
                    pulling = true
                    onPull(((delta - slop) / threshold).coerceIn(0f, 1f))
                    return true
                }
                if (delta < -slop || horizontal > slop) available = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> available = false
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pulling && !available) return super.onTouchEvent(event)
        val progress = ((event.y - y - slop) / threshold).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onPull(progress)
            MotionEvent.ACTION_UP -> {
                onPull(0f)
                if (progress >= 1f) onRefresh()
                pulling = false
                available = false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                onPull(0f)
                pulling = false
                available = false
            }
        }
        return true
    }
}
