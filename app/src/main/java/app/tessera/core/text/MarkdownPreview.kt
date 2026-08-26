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
fun markdownToPreviewPlainText(markdown: String): String =
    previewLines(markdown).firstOrNull() ?: ""

/**
 * Preview-friendly line stream: trimmed, non-empty, table markup flattened to plain cell text
 * (`| a | b |` -> "a b") with separator rows (`|---|---|`) dropped entirely, then sanitized.
 * Lines that sanitize to nothing are skipped so a card never shows an empty title/preview when
 * meaningful text follows.
 */
private fun previewLines(markdown: String): Sequence<String> =
    markdown.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !isTableSeparatorRow(it) }
        .map { sanitizePreviewLine(stripTableMarkup(it)) }
        .filter(String::isNotEmpty)

/** A markdown table alignment row: only pipes, dashes, colons and spaces. */
private fun isTableSeparatorRow(line: String): Boolean =
    line.startsWith("|") && line.any { it == '-' } && line.all { it == '|' || it == '-' || it == ':' || it == ' ' }

/** Flattens a table content row to its cell text: `| a | b |` -> "a b". */
private fun stripTableMarkup(line: String): String =
    if (line.startsWith("|")) {
        line.trim('|').split('|').joinToString(" ") { it.trim() }.trim()
    } else {
        line
    }

/**
 * Like [markdownToPreviewPlainText] but skips the first non-blank line — that line is
 * the auto-derived title, so showing it twice on a note card is duplication.
 */
fun markdownBodyPreviewPlainText(markdown: String): String =
    previewLines(markdown).drop(1).firstOrNull() ?: ""

private fun sanitizePreviewLine(line: String): String =
    line
        .replace(BLOCK_PREFIX, "")
        .replace(INLINE_BOLD) { it.groupValues[1] }
        .replace(INLINE_ITALIC) { it.groupValues[1] }
        .replace(INLINE_STRIKE) { it.groupValues[1] }
        .replace(HTML_TAG, "")
        .replace(WHITESPACE, " ")
        .trim()
