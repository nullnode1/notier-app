package app.notier

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Image
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlRenderer
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

internal data class Preview(val html: String, val localImages: Map<String, List<String>>,
    val hasFrames: Boolean, val codeBlocks: List<String>, val outline: List<OutlineHeading>)

internal data class OutlineHeading(val id: String, val title: String, val level: Int)

internal class PreviewStrings(val language: String, private val translate: (Int, Array<out Any>) -> String) {
    fun text(id: Int, vararg args: Any) = translate(id, args)
}

internal object Markdown {
    const val HOST = "app.notier"
    const val ORIGIN = "https://app.notier/"
    const val IMAGE_PATH = "/_notier/image/"
    val imageTypes = setOf("png", "jpg", "jpeg", "webp", "gif", "avif", "svg", "bmp", "ico")
    private val videoTypes = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm",
        "ogv" to "video/ogg", "mov" to "video/quicktime", "m3u8" to "application/vnd.apple.mpegurl"
    )
    private val audioTypes = mapOf(
        "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "m4a" to "audio/mp4", "wav" to "audio/wav", "aac" to "audio/aac", "flac" to "audio/flac"
    )
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val vimeoId = Regex("[0-9]{1,14}")
    private val extensions = listOf<Extension>(
        TablesExtension.create(), StrikethroughExtension.create(),
        TaskListItemsExtension.create(), AutolinkExtension.create()
    )
    private val parser = Parser.builder().extensions(extensions).build()
    private val headingPunctuation = Regex("[^\\p{L}\\p{N}\\p{M}_\\-\\s]")
    private val headingSpaces = Regex("\\s+")

    private fun renderer(anchors: Map<Heading, OutlineHeading>) = HtmlRenderer.builder().extensions(extensions)
        .escapeHtml(true).sanitizeUrls(true).percentEncodeUrls(true)
        .attributeProviderFactory {
            org.commonmark.renderer.html.AttributeProvider { node, tag, attributes ->
                if (node is Heading) anchors[node]?.let { attributes["id"] = it.id }
                if (tag == "img") {
                    attributes["loading"] = "lazy"
                    attributes["decoding"] = "async"
                    attributes["referrerpolicy"] = "no-referrer"
                }
            }
        }
        .nodeRendererFactory { context ->
            object : NodeRenderer {
                override fun getNodeTypes(): Set<Class<out Node>> = setOf(MediaNode::class.java)
                override fun render(node: Node) { context.writer.raw((node as MediaNode).html) }
            }
        }.build()

    fun article(markdown: String): String =
        renderer(emptyMap()).render(parser.parse(markdown.removePrefix("\uFEFF")))

    fun releasePage(
        title: String, metadata: String, body: String, empty: String,
        language: String, css: String, pixel: Boolean, links: List<Pair<String, String>>
    ): String {
        val actions = links.joinToString("") { (label, url) ->
            "<a href=\"${escape(url)}\">${escape(label)}</a>"
        }
        val article = if (body.isBlank()) "<p>${escape(empty)}</p>" else article(body)
        return """
            <!doctype html>
            <html lang="${escape(language)}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; font-src 'self'; base-uri 'none'; form-action 'none'">
              <style>$css</style>
            </head>
            <body class="${if (pixel) "pixel" else ""}">
              <article dir="auto">
                <header class="release-heading"><p>${escape(metadata)}</p><h1>${escape(title)}</h1></header>
                <section class="release-body">$article</section>
                <footer class="release-links">$actions</footer>
              </article>
            </body></html>
        """.trimIndent()
    }

    fun render(markdown: String, css: String, localMedia: Boolean, copyIcon: String, copyScript: String, strings: PreviewStrings): Preview {
        val tree = parser.parse(markdown.removePrefix("\uFEFF"))
        val localImages = linkedMapOf<String, List<String>>()
        val codeBlocks = mutableListOf<String>()
        val headings = linkedMapOf<Heading, OutlineHeading>()
        val headingIds = mutableSetOf<String>()
        var hasFrames = false

        fun replaceImage(node: Image) {
            val embedded = embed(node.destination, strings)
            if (embedded != null && !embedded.html.startsWith("<img")) {
                node.insertBefore(embedded)
                node.unlink()
                hasFrames = hasFrames || embedded.frame
                return
            }
            if (secureUrl(node.destination) != null) return
            val path = if (localMedia) relativeImage(node.destination) else null
            if (path == null) {
                val alt = buildString {
                    var child = node.firstChild
                    while (child != null) {
                        if (child is Text) append(child.literal)
                        child = child.next
                    }
                }
                node.insertBefore(Text(alt.ifBlank { strings.text(R.string.image_label) }))
                node.unlink()
            } else {
                val key = localImages.size.toString()
                localImages[key] = path
                node.destination = ORIGIN.trimEnd('/') + IMAGE_PATH + key
            }
        }

        fun visit(node: Node) {
            if (node is Heading) {
                val title = buildString { appendHeadingText(node) }.trim()
                if (title.isNotEmpty()) {
                    val slug = Normalizer.normalize(title, Normalizer.Form.NFC).lowercase(Locale.ROOT)
                        .replace(headingPunctuation, "").trim().replace(headingSpaces, "-")
                        .ifEmpty { "section" }
                    var id = slug
                    var duplicate = 1
                    while (!headingIds.add(id)) id = "$slug-${duplicate++}"
                    headings[node] = OutlineHeading(id, title, node.level)
                }
            }
            val code = when (node) {
                is FencedCodeBlock -> node.literal
                is IndentedCodeBlock -> node.literal
                else -> null
            }
            if (code != null) {
                val index = codeBlocks.size
                codeBlocks += code
                val language = (node as? FencedCodeBlock)?.info.orEmpty().trim()
                    .substringBefore(' ').substringBefore('\t').take(30)
                val badge = language.takeIf { it.isNotEmpty() }?.let { "<span>${escape(it)}</span>" }.orEmpty()
                val copy = escape(strings.text(R.string.copy_code))
                val copied = escape(strings.text(R.string.code_copied))
                val button = "<button type=\"button\" class=\"copy-code\" data-code=\"$index\" data-copied=\"$copied\" aria-label=\"$copy\" title=\"$copy\" disabled>$copyIcon<svg class=\"copy-check\" aria-hidden=\"true\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"m5 12 4 4L19 6\"/></svg></button>"
                node.insertBefore(MediaNode("<div class=\"code-block\"><div class=\"code-toolbar\">$badge$button</div><pre><code>${escape(code)}</code></pre></div>"))
                node.unlink()
                return
            }
            if (node is Paragraph && node.firstChild === node.lastChild) {
                val child = node.firstChild
                val destination = when (child) {
                    is Link -> child.destination
                    is Text -> child.literal.trim()
                    else -> null
                }
                val embedded = destination?.let { embed(it, strings) }
                if (embedded != null) {
                    node.insertBefore(embedded)
                    node.unlink()
                    hasFrames = hasFrames || embedded.frame
                    return
                }
            }
            if (node is Image) replaceImage(node)
            var child = node.firstChild
            while (child != null) {
                val next = child.next
                visit(child)
                child = next
            }
        }

        visit(tree)
        val body = renderer(headings).render(tree).ifBlank { "<p class=\"empty\">${escape(strings.text(R.string.empty_note))}</p>" }
        val nonce = if (codeBlocks.isEmpty()) null else UUID.randomUUID().toString()
        val scripts = if (nonce == null) "" else "script-src 'nonce-$nonce';"
        val bridge = if (nonce == null) "" else "<script nonce=\"$nonce\">$copyScript</script>"
        val html = """
            <!doctype html>
            <html lang="${escape(strings.language)}" dir="${if (strings.language.substringBefore('-') in setOf("ar", "fa", "he", "iw", "ur", "ps")) "rtl" else "ltr"}">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; $scripts style-src 'unsafe-inline'; font-src 'self'; img-src https:; media-src https:; frame-src https://www.youtube.com https://player.vimeo.com; base-uri 'none'; form-action 'none'">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>$css</style>
            </head><body><article dir="auto">$body</article>$bridge</body></html>
        """.trimIndent()
        return Preview(html, localImages, hasFrames, codeBlocks, headings.values.toList())
    }

    private fun StringBuilder.appendHeadingText(node: Node) {
        when (node) {
            is Text -> append(node.literal)
            is Code -> append(node.literal)
            is SoftLineBreak, is HardLineBreak -> append(' ')
            else -> {
                var child = node.firstChild
                while (child != null) {
                    appendHeadingText(child)
                    child = child.next
                }
            }
        }
    }

    private class MediaNode(val html: String, val frame: Boolean = false) : org.commonmark.node.CustomNode()

    private fun embed(destination: String, strings: PreviewStrings): MediaNode? {
        val uri = secureUrl(destination) ?: return null
        val source = escape(uri.toASCIIString())
        val host = uri.host.lowercase(Locale.ROOT)
        val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        val query = uri.rawQuery.orEmpty().split('&').associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }
        val id = when {
            host == "youtu.be" -> segments.singleOrNull()
            host in youtubeHosts && segments == listOf("watch") -> query["v"]
            host in youtubeHosts && segments.size == 2 && segments[0] in setOf("shorts", "live", "embed") -> segments[1]
            else -> null
        }
        if (id != null && videoId.matches(id)) {
            return frame("https://www.youtube.com/embed/$id", source, "YouTube", strings)
        }
        val vimeo = when {
            host in setOf("vimeo.com", "www.vimeo.com") && segments.size in 1..2 -> segments.first()
            host == "player.vimeo.com" && segments.size == 2 && segments[0] == "video" -> segments[1]
            else -> null
        }
        if (vimeo != null && vimeoId.matches(vimeo)) {
            val token = query["h"] ?: segments.getOrNull(1)?.takeIf { it != vimeo }
            val suffix = token?.takeIf { it.matches(Regex("[a-fA-F0-9]{6,32}")) }?.let { "?h=$it" }.orEmpty()
            return frame("https://player.vimeo.com/video/$vimeo$suffix", source, "Vimeo", strings)
        }
        val type = uri.path.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (type in imageTypes) {
            return MediaNode("<img class=\"standalone\" src=\"$source\" alt=\"${escape(strings.text(R.string.remote_image))}\" loading=\"lazy\" decoding=\"async\" referrerpolicy=\"no-referrer\">")
        }
        val tag = when (type) {
            in videoTypes -> "video"
            in audioTypes -> "audio"
            else -> return null
        }
        val mime = videoTypes[type] ?: audioTypes.getValue(type)
        return MediaNode("<figure><$tag controls preload=\"none\" playsinline><source src=\"$source\" type=\"$mime\"></$tag><figcaption><a href=\"$source\">${escape(strings.text(R.string.open_link))}</a></figcaption></figure>")
    }

    private fun frame(player: String, source: String, provider: String, strings: PreviewStrings) = MediaNode(
        "<figure><iframe title=\"${escape(strings.text(R.string.provider_player, provider))}\" src=\"$player\" loading=\"lazy\" referrerpolicy=\"strict-origin-when-cross-origin\" sandbox=\"allow-scripts allow-same-origin allow-presentation\" allow=\"encrypted-media; picture-in-picture; fullscreen\" allowfullscreen></iframe><figcaption><a href=\"$source\">${escape(strings.text(R.string.open_provider, provider))}</a></figcaption></figure>",
        frame = true
    )

    private fun secureUrl(value: String): URI? {
        val input = if (value.startsWith("www.", true)) "https://$value" else value
        val uri = runCatching { URI(input.replace(" ", "%20")) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme.equals("https", true) && it.host != null && it.userInfo == null }
    }

    private fun relativeImage(value: String): List<String>? {
        val uri = runCatching { URI(value.replace(" ", "%20")) }.getOrNull() ?: return null
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.path ?: return null
        if (path.startsWith('/') || path.contains('\\') || path.any { it.isISOControl() }) return null
        return path.split('/').filter { it.isNotEmpty() && it != "." }
            .takeIf { it.isNotEmpty() && it.size <= 24 && ".." !in it }
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")
}
