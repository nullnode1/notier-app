package app.notier

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

internal class PixelSurface(
    private val fill: Int,
    private val border: Int?,
    private val step: Int,
    private val stroke: Int
) : Drawable() {
    private val paint = Paint()
    private val outer = Path()
    private val inner = Path()
    private var opacity = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        outline(outer, bounds, 0)
        if (border != null) outline(inner, bounds, stroke)
    }

    private fun outline(path: Path, bounds: Rect, inset: Int) {
        path.reset()
        val l = (bounds.left + inset).toFloat()
        val t = (bounds.top + inset).toFloat()
        val r = (bounds.right - inset).toFloat()
        val b = (bounds.bottom - inset).toFloat()
        if (l >= r || t >= b) return
        val s = minOf(step.toFloat(), (r - l) / 8f, (b - t) / 8f)
        val d = 2 * s
        val c = 3 * s
        path.moveTo(l + c, t)
        path.lineTo(r - c, t)
        path.lineTo(r - c, t + s)
        path.lineTo(r - d, t + s)
        path.lineTo(r - d, t + d)
        path.lineTo(r - s, t + d)
        path.lineTo(r - s, t + c)
        path.lineTo(r, t + c)
        path.lineTo(r, b - c)
        path.lineTo(r - s, b - c)
        path.lineTo(r - s, b - d)
        path.lineTo(r - d, b - d)
        path.lineTo(r - d, b - s)
        path.lineTo(r - c, b - s)
        path.lineTo(r - c, b)
        path.lineTo(l + c, b)
        path.lineTo(l + c, b - s)
        path.lineTo(l + d, b - s)
        path.lineTo(l + d, b - d)
        path.lineTo(l + s, b - d)
        path.lineTo(l + s, b - c)
        path.lineTo(l, b - c)
        path.lineTo(l, t + c)
        path.lineTo(l + s, t + c)
        path.lineTo(l + s, t + d)
        path.lineTo(l + d, t + d)
        path.lineTo(l + d, t + s)
        path.lineTo(l + c, t + s)
        path.close()
    }

    override fun draw(canvas: Canvas) {
        if (border == null) {
            drawFill(canvas, outer, fill)
        } else {
            drawFill(canvas, outer, border)
            drawFill(canvas, inner, fill)
        }
    }

    private fun drawFill(canvas: Canvas, path: Path, color: Int) {
        paint.color = color
        paint.alpha = Color.alpha(color) * opacity / 255
        canvas.drawPath(path, paint)
    }

    override fun getOutline(outline: Outline) {
        outline.setRect(bounds)
        outline.alpha = 0f
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
