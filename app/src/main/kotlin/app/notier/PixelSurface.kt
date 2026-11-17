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
        val s = minOf(step.toFloat(), (r - l) / 10f, (b - t) / 10f)
        val d = 2 * s
        val c = 3 * s
        val q = 4 * s
        path.moveTo(l + q, t)
        path.lineTo(r - q, t)
        path.lineTo(r - q, t + s)
        path.lineTo(r - c, t + s)
        path.lineTo(r - c, t + d)
        path.lineTo(r - d, t + d)
        path.lineTo(r - d, t + c)
        path.lineTo(r - s, t + c)
        path.lineTo(r - s, t + q)
        path.lineTo(r, t + q)
        path.lineTo(r, b - q)
        path.lineTo(r - s, b - q)
        path.lineTo(r - s, b - c)
        path.lineTo(r - d, b - c)
        path.lineTo(r - d, b - d)
        path.lineTo(r - c, b - d)
        path.lineTo(r - c, b - s)
        path.lineTo(r - q, b - s)
        path.lineTo(r - q, b)
        path.lineTo(l + q, b)
        path.lineTo(l + q, b - s)
        path.lineTo(l + c, b - s)
        path.lineTo(l + c, b - d)
        path.lineTo(l + d, b - d)
        path.lineTo(l + d, b - c)
        path.lineTo(l + s, b - c)
        path.lineTo(l + s, b - q)
        path.lineTo(l, b - q)
        path.lineTo(l, t + q)
        path.lineTo(l + s, t + q)
        path.lineTo(l + s, t + c)
        path.lineTo(l + d, t + c)
        path.lineTo(l + d, t + d)
        path.lineTo(l + c, t + d)
        path.lineTo(l + c, t + s)
        path.lineTo(l + q, t + s)
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
