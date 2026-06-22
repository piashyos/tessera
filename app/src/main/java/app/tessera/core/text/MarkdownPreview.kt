package app.tessera.core.text

private val BLOCK_PREFIX = Regex("""^\s*(?:#{1,6}\s+|-\s+\[[ xX]]\s+|-\s+|>\s+)""")
private val INLINE_BOLD = Regex("""\*\*([^*]+)\*\*""")
private val INLINE_ITALIC = Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")
private val INLINE_STRIKE = Regex("""~~([^~]+)~~""")
private val HTML_TAG = Regex("""<[^>]+>""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Produces a single-line, marker-free preview of canonical Markdown for note-card display.
 * Picks the first non-blank line, strips supported block prefixes (#, ##, -, - [ ], - [x]),
 * unwraps inline emphasis (**bold**, *italic*, ~~strike~~), removes any HTML tags (in case a
 * note round-tripped through Aztec and kept stray markup), and collapses internal whitespace.
 * Returns "" for empty/blank input.
 */
fun markdownToPreviewPlainText(markdown: String): String {
    val firstLine = markdown
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    return sanitizePreviewLine(firstLine)
}

/**
 * Like [markdownToPreviewPlainText] but skips the first non-blank line — that line is
 * the auto-derived title, so showing it twice on a note card is duplication.
 */
fun markdownBodyPreviewPlainText(markdown: String): String {
    val lines = markdown.lineSequence().map(String::trim).filter(String::isNotEmpty).iterator()
    if (!lines.hasNext()) return ""
    lines.next() // skip title line
    if (!lines.hasNext()) return ""
    return sanitizePreviewLine(lines.next())
}

private fun sanitizePreviewLine(line: String): String =
    line
        .replace(BLOCK_PREFIX, "")
        .replace(INLINE_BOLD) { it.groupValues[1] }
        .replace(INLINE_ITALIC) { it.groupValues[1] }
        .replace(INLINE_STRIKE) { it.groupValues[1] }
        .replace(HTML_TAG, "")
        .replace(WHITESPACE, " ")
        .trim()
