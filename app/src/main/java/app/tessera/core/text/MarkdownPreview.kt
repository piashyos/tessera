package app.tessera.core.text

private val BLOCK_PREFIX = Regex("""^\s*(?:#{1,6}\s+|[-*]\s+\[[ xX]?]\s+|[-*+]\s+|>\s+|\d+\.\s+)""")
// Non-greedy with non-space boundaries: content may contain lone markers ("**220 000 * 0.2**"),
// but "2 * 2 * 2" must not be treated as emphasis (openers/closers can't touch whitespace).
private val INLINE_BOLD = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")
private val INLINE_ITALIC = Regex("""(?<!\*)\*(?=[^\s*])([^*]+?)(?<=\S)\*(?!\*)""")
private val INLINE_STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
private val INLINE_CODE = Regex("""`([^`]+)`""")
private val HTML_TAG = Regex("""<[^>]+>""")
private val WHITESPACE = Regex("""\s+""")
private val IMAGE_REF_LINE = Regex("""^!\[[^\]]*]\(tessera-img://[^)]*\)$""")

/**
 * Produces a single-line, marker-free preview of canonical Markdown for note-card display.
 * Picks the first non-blank line, strips supported block prefixes (#, ##, -, - [ ], - [x], 1.),
 * unwraps inline emphasis (**bold**, *italic*, ~~strike~~, `code`), removes any HTML tags (in case a
 * note round-tripped through Aztec and kept stray markup), and collapses internal whitespace.
 * Image-ref lines (`![](tessera-img://…)`) are dropped entirely. Returns "" for genuinely
 * empty/blank input; if the only content was image refs, returns "📷" so image-only notes still
 * show something on the card.
 */
fun markdownToPreviewPlainText(markdown: String): String {
    val preview = previewLines(markdown).firstOrNull() ?: ""
    if (preview.isNotEmpty()) return preview
    val hadImageRef = markdown.lineSequence().map(String::trim).any(IMAGE_REF_LINE::matches)
    return if (hadImageRef) "📷" else ""
}

/**
 * Preview-friendly line stream: trimmed, non-empty, table markup flattened to plain cell text
 * (`| a | b |` -> "a b") with separator rows (`|---|---|`) dropped entirely, then sanitized.
 * Lines that sanitize to nothing are skipped so a card never shows an empty title/preview when
 * meaningful text follows.
 */
private fun previewLines(markdown: String): Sequence<String> =
    markdown.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !isTableSeparatorRow(it) && !it.startsWith("```") && !IMAGE_REF_LINE.matches(it) }
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
        .replace(INLINE_CODE) { it.groupValues[1] }
        .replace(HTML_TAG, "")
        .replace(WHITESPACE, " ")
        .trim()
