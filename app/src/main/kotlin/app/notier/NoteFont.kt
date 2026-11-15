package app.notier

import android.content.res.AssetManager
import android.graphics.Typeface

internal enum class NoteFont(
    val key: String,
    val title: String,
    private val family: String,
    private val webFamily: String,
    val asset: String? = null,
    private val weights: String = "400"
) {
    GEIST("geist", "Geist", "sans-serif", "Geist, system-ui, sans-serif", "fonts/geist.ttf", "100 900"),
    SYSTEM("system", "Android", "sans-serif", "system-ui, sans-serif"),
    PIXEL("pixel", "Pixelify Sans", "sans-serif", "Pixelify, monospace", "fonts/pixelify.ttf", "400 700"),
    ATKINSON("atkinson", "Atkinson Hyperlegible Next", "sans-serif", "Atkinson, system-ui, sans-serif", "fonts/atkinson.ttf", "200 800"),
    INSTRUMENT("instrument", "Instrument Serif", "serif", "Instrument, serif", "fonts/instrument.ttf"),
    PLEX("plex", "IBM Plex Mono", "monospace", "Plex, monospace", "fonts/plexmono.ttf"),
    DM_SANS("dm-sans", "DM Sans", "sans-serif", "DM Sans, system-ui, sans-serif", "fonts/dmsans.ttf", "100 1000"),
    BRICOLAGE("bricolage", "Bricolage Grotesque", "sans-serif", "Bricolage Grotesque, system-ui, sans-serif", "fonts/bricolagegrotesque.ttf", "200 800"),
    SOURCE_SANS("source-sans", "Source Sans 3", "sans-serif", "Source Sans 3, system-ui, sans-serif", "fonts/sourcesans3.ttf", "200 900"),
    PLAYFAIR("playfair", "Playfair Display", "serif", "Playfair Display, serif", "fonts/playfairdisplay.ttf", "400 900"),
    SPACE_MONO("space-mono", "Space Mono", "monospace", "Space Mono, monospace", "fonts/spacemono.ttf"),
    SERIF("serif", "Serif", "serif", "Georgia, serif"),
    MONO("mono", "Monospace", "monospace", "ui-monospace, monospace");

    fun load(assets: AssetManager): Typeface =
        asset?.let { Typeface.createFromAsset(assets, it) } ?: Typeface.create(family, Typeface.NORMAL)

    val previewPath get() = "/_notier/font/$key.ttf"

    fun previewCss(): String {
        val source = asset?.let {
            "@font-face { font-family: ${webFamily.substringBefore(',')}; src: url('$previewPath') format('truetype'); font-weight: $weights; font-display: swap; }"
        }.orEmpty()
        return "$source\narticle { font-family: $webFamily; }"
    }

    companion object {
        fun from(key: String?): NoteFont = entries.firstOrNull { it.key == key } ?: GEIST
    }
}
