package app.tessera.core.text

private const val MAX_TITLE_CHARS = 120

private val HeadingPrefix = Regex("^#{1,6}\\s+")
private val ChecklistPrefix = Regex("^[-*]\\s+\\[[ xX]?]\\s+")
private val ListPrefix = Regex("^[-*+]\\s+")

/**
 * Returns the first non-blank line of [content], stripped of markdown leading markers
 * (heading hashes, list bullets, checklist boxes) and clipped to [MAX_TITLE_CHARS].
 * Mirrors Apple Notes' first-line-as-title convention.
 */
fun deriveTitleFromContent(content: String): String {
    val firstLine = content.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    val stripped = firstLine
        .replaceFirst(HeadingPrefix, "")
        .replaceFirst(ChecklistPrefix, "")
        .replaceFirst(ListPrefix, "")
        .trim()
    return if (stripped.length > MAX_TITLE_CHARS) stripped.take(MAX_TITLE_CHARS) else stripped
}
