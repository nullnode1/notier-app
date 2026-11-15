package app.notier

import java.util.Locale

private fun String.markdownSuffix(): String? =
    substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it == "md" || it == "markdown" }

internal fun String.isMarkdownName(): Boolean = markdownSuffix() != null

internal fun normalizedName(raw: String, existingName: String = ""): String? {
    val input = raw.trim()
    val suppliedSuffix = input.markdownSuffix()
    val stem = (if (suppliedSuffix == null) input else input.substringBeforeLast('.')).trim().trimEnd('.').trimEnd()
    if (stem.isEmpty() || stem.toByteArray(Charsets.UTF_8).size > 220 ||
        stem.any { it.isISOControl() || it in "/\\:*?\"<>|" }) return null
    val suffix = suppliedSuffix ?: existingName.markdownSuffix() ?: "md"
    return "$stem.$suffix"
}
