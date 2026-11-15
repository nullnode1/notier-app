package app.notier

internal enum class NotierTheme(val key: String, val title: Int, val style: Int, val palette: Palette) {
    LIGHT("light", R.string.theme_light, R.style.NotierTheme, Palette(
        0xffffff, 0x101010, 0x525252, 0xf4f4f4, 0xe8e8e8, 0x878787, 0x606060,
        0xeeeeee, 0xe5e5e5, 0x101010, 0xffffff, 0x242424, 0x333333
    )),
    DARK("dark", R.string.theme_dark, R.style.NotierThemeDark, Palette(
        0x101010, 0xe8e8e8, 0xb0b0b0, 0x1d1d1d, 0x353535, 0x787878, 0x272727,
        0x262626, 0x343434, 0x2d2d2d, 0xe0e0e0, 0x393939, 0x454545
    )),
    PIXEL("pixel", R.string.theme_pixel, R.style.NotierThemePixel, Palette(
        0xf7efdc, 0x2a2017, 0x584936, 0xf1e3c2, 0xb39a71, 0x65513a, 0xa49173,
        0xecdbb2, 0xdcc69a, 0x55733e, 0xffffff, 0x456230, 0x334d24
    ));

    val pixel get() = this == PIXEL

    fun previewCss(): String = with(palette) {
        val scheme = if (this@NotierTheme == DARK) "dark" else "light"
        val properties = mapOf(
            "paper" to paper, "ink" to ink, "muted" to muted, "surface" to surface,
            "line" to line, "outline" to outline, "hover" to hover,
            "pressed" to pressed, "accent" to accent, "on-accent" to onAccent,
            "accent-pressed" to accentPressed
        )
        properties.entries.joinToString("", prefix = ":root{color-scheme:$scheme;", postfix = "}") { (name, color) ->
            "--$name:#${(color and 0xffffff).toString(16).padStart(6, '0')};"
        }
    }

    companion object {
        fun from(key: String?): NotierTheme = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

internal class Palette(
    paper: Int, ink: Int, muted: Int, surface: Int, line: Int, outline: Int, disabled: Int,
    hover: Int, pressed: Int, accent: Int, onAccent: Int, accentHover: Int, accentPressed: Int
) {
    val paper = opaque(paper)
    val ink = opaque(ink)
    val muted = opaque(muted)
    val surface = opaque(surface)
    val line = opaque(line)
    val outline = opaque(outline)
    val disabled = opaque(disabled)
    val hover = opaque(hover)
    val pressed = opaque(pressed)
    val accent = opaque(accent)
    val onAccent = opaque(onAccent)
    val accentHover = opaque(accentHover)
    val accentPressed = opaque(accentPressed)

    private fun opaque(rgb: Int) = rgb or 0xff000000.toInt()
}
