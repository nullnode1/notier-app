package app.notier

internal data class MarkdownEdit(val start: Int, val end: Int, val replacement: String, val selectStart: Int, val selectEnd: Int)

internal enum class MarkdownShortcut(
    val label: Int,
    val icon: Int? = null,
    val symbol: String = "",
    private val marker: String = ""
) {
    BOLD(R.string.bold, R.drawable.ic_bold, marker = "**"),
    ITALIC(R.string.italic, R.drawable.ic_italic_letter, marker = "*"),
    LINK(R.string.insert_link, R.drawable.ic_link_2),
    H1(R.string.heading_one, symbol = "H1", marker = "# "),
    H2(R.string.heading_two, symbol = "H2", marker = "## "),
    H3(R.string.heading_three, symbol = "H3", marker = "### "),
    STRIKE(R.string.strike, R.drawable.ic_strikethrough, marker = "~~"),
    BULLET(R.string.bullet_list, R.drawable.ic_list, marker = "- "),
    ORDERED(R.string.number_list, R.drawable.ic_list_ordered),
    TASK(R.string.task_list, R.drawable.ic_list_todo, marker = "- [ ] "),
    QUOTE(R.string.blockquote, R.drawable.ic_quote, marker = "> "),
    INLINE_CODE(R.string.inline_code, R.drawable.ic_code, marker = "`"),
    CODE_BLOCK(R.string.code_block, R.drawable.ic_file_code_2),
    IMAGE(R.string.insert_image, R.drawable.ic_image),
    RULE(R.string.divider, R.drawable.ic_minus);

    fun apply(text: CharSequence, from: Int, to: Int, placeholder: String): MarkdownEdit {
        val start = minOf(from, to).coerceIn(0, text.length)
        val end = maxOf(from, to).coerceIn(start, text.length)
        val selection = text.subSequence(start, end).toString()
        return when (this) {
            BOLD, ITALIC, STRIKE, INLINE_CODE -> {
                val fence = if (this == INLINE_CODE) {
                    "`".repeat((Regex("`+").findAll(selection).maxOfOrNull { it.value.length } ?: 0) + 1)
                } else marker
                val pad = if (this == INLINE_CODE && selection.isNotEmpty() &&
                    (selection.startsWith('`') || selection.endsWith('`'))) " " else ""
                val prefix = fence + pad
                MarkdownEdit(start, end, prefix + selection + pad + fence, start + prefix.length, end + prefix.length)
            }
            H1, H2, H3, BULLET, ORDERED, TASK, QUOTE -> lines(text, start, end)
            LINK, IMAGE -> {
                val prefix = if (this == IMAGE) "![" else "["
                val label = selection.ifEmpty { placeholder }.replace("]", "\\]")
                val url = "https://"
                val replacement = "$prefix$label]($url)"
                val target = start + prefix.length + label.length + 2
                MarkdownEdit(start, end, replacement, target, target + url.length)
            }
            CODE_BLOCK, RULE -> {
                val before = if (start > 0 && text[start - 1] != '\n') "\n\n" else if
                    (start > 1 && text[start - 2] != '\n') "\n" else ""
                val after = if (end < text.length && text[end] != '\n') "\n\n" else "\n"
                if (this == RULE) {
                    val replacement = before + "---" + after
                    val cursor = start + replacement.length
                    MarkdownEdit(start, end, replacement, cursor, cursor)
                } else {
                    val tickCount = (Regex("`+").findAll(selection).maxOfOrNull { it.value.length } ?: 0) + 1
                    val fence = "`".repeat(maxOf(3, tickCount))
                    val prefix = "$before$fence\n"
                    MarkdownEdit(start, end, "$prefix$selection\n$fence$after",
                        start + prefix.length, start + prefix.length + selection.length)
                }
            }
        }
    }

    private fun lines(text: CharSequence, start: Int, end: Int): MarkdownEdit {
        val first = if (start == 0) 0 else text.lastIndexOf('\n', start - 1) + 1
        val lastPosition = if (end > start && text[end - 1] == '\n') end - 1 else end
        val last = text.indexOf('\n', lastPosition).takeIf { it >= 0 } ?: text.length
        val contents = text.subSequence(first, last).toString().split('\n')
        val updated = contents.mapIndexed { index, line ->
            val prefix = if (this == ORDERED) "${index + 1}. " else marker
            when (this) {
                H1, H2, H3 -> prefix + line.replaceFirst(headingPrefix, "")
                QUOTE -> prefix + line
                else -> {
                    val indent = line.takeWhile { it == ' ' || it == '\t' }
                    indent + prefix + line.substring(indent.length).replaceFirst(listPrefix, "")
                }
            }
        }
        val replacement = updated.joinToString("\n")
        if (start != end) return MarkdownEdit(first, last, replacement, first, first + replacement.length)
        val cursor = first + (start - first + updated[0].length - contents[0].length).coerceIn(0, updated[0].length)
        return MarkdownEdit(first, last, replacement, cursor, cursor)
    }

    companion object {
        private val headingPrefix = Regex("^#{1,6}[ \\t]+")
        private val listPrefix = Regex("^(?:[-+*][ \\t]+(?:\\[[ xX]\\][ \\t]+)?|[0-9]+[.)][ \\t]+)")
    }
}
