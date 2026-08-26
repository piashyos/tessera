package app.tessera.core.text

private const val MAX_TITLE_CHARS = 120

/**
 * Returns the first meaningful line of [content] as a plain-text title, clipped to
 * [MAX_TITLE_CHARS]. Mirrors Apple Notes' first-line-as-title convention.
 *
 * Delegates all markup stripping to [markdownToPreviewPlainText] so titles and card
 * previews sanitize identically: block prefixes (headings, bullets, checklists, ordered
 * lists, quotes), inline emphasis/code, HTML tags and comments (incl. the tessera table
 * sizing comment), table pipe markup, and code-fence lines.
 */
fun deriveTitleFromContent(content: String): String =
    markdownToPreviewPlainText(content).take(MAX_TITLE_CHARS)
