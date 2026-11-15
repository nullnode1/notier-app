package app.notier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

internal enum class IconStyle(val key: String, val label: Int) {
    LUCIDE("lucide", R.string.icon_lucide),
    PIXEL("pixel", R.string.icon_pixel),
    BOLD("bold", R.string.icon_bold);

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: LUCIDE
    }
}

internal class AppIcons(private val context: Context) {
    private val boldIcons = mutableMapOf<Int, Bitmap>()

    fun drawable(resource: Int, style: IconStyle): Drawable? {
        if (style == IconStyle.PIXEL) return context.getDrawable(pixelIcon(resource))
        if (style == IconStyle.LUCIDE || resource == R.drawable.app_icon) return context.getDrawable(resource)
        val bitmap = boldIcons.getOrPut(resource) {
            val source = context.getDrawable(resource) ?: return null
            val size = 64
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                source.setBounds(0, 0, size, size)
                source.draw(canvas)
                for ((x, y) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    source.draw(canvas)
                    canvas.restore()
                }
            }
        }
        return BitmapDrawable(context.resources, bitmap).apply {
            isFilterBitmap = true
            isAutoMirrored = resource == R.drawable.ic_arrow_left || resource == R.drawable.ic_chevron_right
        }
    }
}
