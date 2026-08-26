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
        .map(::flattenTableLine)
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    val stripped = firstLine
        .replaceFirst(HeadingPrefix, "")
        .replaceFirst(ChecklistPrefix, "")
        .replaceFirst(ListPrefix, "")
        .trim()
    return if (stripped.length > MAX_TITLE_CHARS) stripped.take(MAX_TITLE_CHARS) else stripped
}

/**
 * A note that STARTS with a table must not surface pipe markup as its title: a content row
 * (`| a | b |`) flattens to its cell text ("a b") and the alignment row (`|---|---|`) to ""
 * (skipped by the caller's non-empty filter).
 */
private fun flattenTableLine(line: String): String = when {
    // Table sizing metadata (<!-- tessera:cols=...;rows=... -->) and any other HTML comment
    // line is invisible content — never a title.
    line.startsWith("<!--") -> ""
    !line.startsWith("|") -> line
    line.any { it == '-' } && line.all { it == '|' || it == '-' || it == ':' || it == ' ' } -> ""
    else -> line.trim('|').split('|').joinToString(" ") { it.trim() }.trim()
}
