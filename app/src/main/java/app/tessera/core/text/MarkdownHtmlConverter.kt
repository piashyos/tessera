package app.tessera.core.text

/**
 * Translates between the app's canonical Markdown (the stored format) and the HTML the Aztec
 * editor consumes/produces. Scope is strictly the supported feature set: bold, italic,
 * strikethrough, H1/H2, bullet list, and task-list (checked/unchecked).
 *
 * Risk-isolation unit: pure, no Android deps, fully covered by round-trip unit tests.
 * The task-list HTML shape matches what Aztec emits (captured in aztec-spike-findings.md).
 */
class MarkdownHtmlConverter {

    companion object {
        /** Tag names that are inline (not block-level) and should be processed via inlineToMarkdown. */
        private val INLINE_TAGS = setOf("b", "strong", "i", "em", "s", "del", "strike", "span")
    }

    // -----------------------------------------------------------------------------------------
    // Markdown → HTML
    // -----------------------------------------------------------------------------------------

    fun markdownToHtml(markdown: String): String {
        if (markdown.isEmpty()) return ""

        val lines = markdown.split("\n")
        val output = StringBuilder()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                // H1
                line.startsWith("# ") -> {
                    val content = line.removePrefix("# ")
                    output.append("<h1>${inlineToHtml(content)}</h1>")
                    i++
                }
                // H2
                line.startsWith("## ") -> {
                    val content = line.removePrefix("## ")
                    output.append("<h2>${inlineToHtml(content)}</h2>")
                    i++
                }
                // Task-list items — consume consecutive task items into one <ul type="task-list">
                line.startsWith("- [ ] ") || line.startsWith("- [x] ") -> {
                    output.append("<ul type=\"task-list\">")
                    while (i < lines.size &&
                        (lines[i].startsWith("- [ ] ") || lines[i].startsWith("- [x] "))
                    ) {
                        val taskLine = lines[i]
                        val checked = taskLine.startsWith("- [x] ")
                        val text = if (checked) taskLine.removePrefix("- [x] ")
                                   else taskLine.removePrefix("- [ ] ")
                        val checkedAttr = if (checked) " checked" else ""
                        output.append(
                            "<li><input type=\"checkbox\" class=\"task-list-item-checkbox\"$checkedAttr />${inlineToHtml(text)}</li>"
                        )
                        i++
                    }
                    output.append("</ul>")
                }
                // Regular bullet list items — consume consecutive bullets into one <ul>
                line.startsWith("- ") -> {
                    output.append("<ul>")
                    while (i < lines.size &&
                        lines[i].startsWith("- ") &&
                        !lines[i].startsWith("- [ ] ") &&
                        !lines[i].startsWith("- [x] ")
                    ) {
                        val text = lines[i].removePrefix("- ")
                        output.append("<li>${inlineToHtml(text)}</li>")
                        i++
                    }
                    output.append("</ul>")
                }
                // Plain paragraph
                else -> {
                    output.append("<p>${inlineToHtml(line)}</p>")
                    i++
                }
            }
        }

        return output.toString()
    }

    /** Convert inline Markdown spans (bold, italic, strike) to HTML inside a block. */
    private fun inlineToHtml(text: String): String {
        // Escape HTML-special chars FIRST so literal '<', '>', '&' in the note text can't be
        // mistaken for markup. Markdown markers ('*', '~') are not HTML-special, so the
        // bold/italic/strike regexes below still match and wrap the already-escaped content
        // in real tags. '&' must be escaped before '<'/'>' to avoid double-escaping entities.
        var result = escapeHtml(text)
        // Order matters: bold (** **) must be matched before single * italic.
        // Bold: **text** → <b>text</b>
        result = result.replace(Regex("""\*\*(.+?)\*\*""")) { "<b>${it.groupValues[1]}</b>" }
        // Italic: *text* → <i>text</i>  (only bare * not preceded by another *)
        result = result.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")) { "<i>${it.groupValues[1]}</i>" }
        // Strikethrough: ~~text~~ → <s>text</s>
        result = result.replace(Regex("""~~(.+?)~~""")) { "<s>${it.groupValues[1]}</s>" }
        return result
    }

    // -----------------------------------------------------------------------------------------
    // HTML → Markdown
    // -----------------------------------------------------------------------------------------

    fun htmlToMarkdown(html: String): String {
        if (html.isEmpty()) return ""

        val blocks = parseBlocks(html)
        // Aztec's toHtml() separates plain paragraphs with a *double* newline ("\n\n"),
        // whereas our markdownToHtml treats every single "\n" as its own paragraph (<p>).
        // Left unreconciled, each load→edit→save cycle re-expands one logical break into an
        // extra blank line, so paragraph gaps grow without bound. Collapsing runs of blank
        // lines back to a single newline makes the round-trip idempotent AND retroactively
        // heals notes that already accumulated stray blank lines.
        return blocks.joinToString("\n").replace(Regex("\n{2,}"), "\n")
    }

    /**
     * Parse the top-level HTML into a flat list of Markdown lines (one per block element).
     * Each block element becomes one or more Markdown lines.
     */
    private fun parseBlocks(html: String): List<String> {
        val lines = mutableListOf<String>()
        var remaining = html.trim()

        while (remaining.isNotEmpty()) {
            remaining = remaining.trimStart()
            if (remaining.isEmpty()) break

            when {
                remaining.startsWith("<h1>") ->
                    remaining = consumeTag(remaining, "h1") { inner -> lines.add("# ${inlineToMarkdown(inner)}") }

                remaining.startsWith("<h2>") ->
                    remaining = consumeTag(remaining, "h2") { inner -> lines.add("## ${inlineToMarkdown(inner)}") }

                remaining.startsWith("<ul type=\"task-list\">") ->
                    remaining = consumeTaskList(remaining, lines)

                remaining.startsWith("<ul>") ->
                    remaining = consumeBulletList(remaining, lines)

                remaining.startsWith("<p>") ->
                    remaining = consumeTag(remaining, "p") { inner -> lines.add(inlineToMarkdown(inner)) }

                remaining.startsWith("<") -> {
                    // Check whether the leading tag is a known inline tag.
                    // If so, consume everything up to the next block-level tag as inline content.
                    val leadingTag = extractTagName(remaining)
                    if (leadingTag in INLINE_TAGS) {
                        // Accumulate all content until a block tag (or end of string)
                        val nextBlock = indexOfNextBlockTag(remaining)
                        val chunk = if (nextBlock == -1) remaining else remaining.substring(0, nextBlock)
                        val text = inlineToMarkdown(chunk.trim())
                        if (text.isNotEmpty()) lines.add(text)
                        remaining = if (nextBlock == -1) "" else remaining.substring(nextBlock)
                    } else {
                        // Unknown block tag: strip outer tag, recursively process inner content
                        val (inner, rest) = stripOuterTag(remaining)
                        lines.addAll(parseBlocks(inner))
                        remaining = rest
                    }
                }

                else -> {
                    // Bare text with no block tag — treat as paragraph up to next tag
                    val nextTag = remaining.indexOf('<')
                    if (nextTag == -1) {
                        val text = inlineToMarkdown(remaining.trim())
                        if (text.isNotEmpty()) lines.add(text)
                        remaining = ""
                    } else {
                        val text = inlineToMarkdown(remaining.substring(0, nextTag).trim())
                        if (text.isNotEmpty()) lines.add(text)
                        remaining = remaining.substring(nextTag)
                    }
                }
            }
        }

        return lines
    }

    /** Consume a known paired tag, call [handler] with inner content, return the remainder. */
    private inline fun consumeTag(html: String, tag: String, handler: (String) -> Unit): String {
        val openTag = "<$tag>"
        val closeTag = "</$tag>"
        require(html.startsWith(openTag)) { "consumeTag called on non-leading tag" }
        val contentStart = openTag.length
        val end = html.indexOf(closeTag, contentStart)
        if (end == -1) {
            // Malformed — take everything after open tag
            handler(html.substring(contentStart))
            return ""
        }
        handler(html.substring(contentStart, end))
        return html.substring(end + closeTag.length)
    }

    private fun consumeBulletList(html: String, lines: MutableList<String>): String {
        val closeUl = "</ul>"
        val ulStart = "<ul>"
        val start = html.indexOf(ulStart)
        if (start == -1) return html
        val contentStart = start + ulStart.length
        val end = html.indexOf(closeUl, contentStart)
        if (end == -1) return ""
        val inner = html.substring(contentStart, end)
        // Extract each <li>...</li>
        extractListItems(inner).forEach { itemHtml ->
            lines.add("- ${inlineToMarkdown(itemHtml)}")
        }
        return html.substring(end + closeUl.length)
    }

    private fun consumeTaskList(html: String, lines: MutableList<String>): String {
        val closeUl = "</ul>"
        val ulStart = "<ul type=\"task-list\">"
        val start = html.indexOf(ulStart)
        if (start == -1) return html
        val contentStart = start + ulStart.length
        val end = html.indexOf(closeUl, contentStart)
        if (end == -1) return ""
        val inner = html.substring(contentStart, end)
        extractListItems(inner).forEach { itemHtml ->
            val (checked, text) = parseTaskItem(itemHtml)
            val marker = if (checked) "- [x] " else "- [ ] "
            lines.add("$marker${inlineToMarkdown(text)}")
        }
        return html.substring(end + closeUl.length)
    }

    /** Extract raw inner HTML for each <li>…</li> in a list's inner HTML. */
    private fun extractListItems(inner: String): List<String> {
        val items = mutableListOf<String>()
        var rem = inner.trim()
        while (rem.isNotEmpty()) {
            if (!rem.startsWith("<li>")) break
            val end = rem.indexOf("</li>")
            if (end == -1) break
            items.add(rem.substring(4, end)) // 4 = "<li>".length
            rem = rem.substring(end + 5).trimStart() // 5 = "</li>".length
        }
        return items
    }

    /**
     * Parse a task-list item's inner HTML.
     * Accepts both self-closing `<input ... />` (our `markdownToHtml` output) and the
     * non-self-closing `<input ...>` that Aztec's `toHtml()` actually emits.
     * Returns Pair(checked, remainingText).
     */
    private fun parseTaskItem(itemHtml: String): Pair<Boolean, String> {
        if (!itemHtml.trimStart().startsWith("<input")) return Pair(false, itemHtml)
        val inputEnd = itemHtml.indexOf('>')
        if (inputEnd == -1) return Pair(false, itemHtml)
        val inputTag = itemHtml.substring(0, inputEnd + 1)
        val text = itemHtml.substring(inputEnd + 1)
        // checked if the input tag contains the word "checked" (any of: checked, checked="checked", checked="")
        val checked = Regex("""\bchecked\b""").containsMatchIn(inputTag)
        return Pair(checked, text)
    }

    /**
     * Strip the outermost tag from an unknown HTML element and return (innerHtml, restOfHtml).
     * If no closing tag is found, consumes everything.
     */
    private fun stripOuterTag(html: String): Pair<String, String> {
        // Find the tag name
        val tagNameEnd = html.indexOfFirst { it == '>' || it == ' ' || it == '/' }.takeIf { it > 0 } ?: return Pair("", "")
        val tagName = html.substring(1, tagNameEnd)
        val openEnd = html.indexOf('>')
        if (openEnd == -1) return Pair("", "")
        val contentStart = openEnd + 1
        val closeTag = "</$tagName>"
        val closeStart = html.indexOf(closeTag, contentStart)
        return if (closeStart == -1) {
            Pair(html.substring(contentStart), "")
        } else {
            Pair(html.substring(contentStart, closeStart), html.substring(closeStart + closeTag.length))
        }
    }

    /** Extract the tag name from the start of an HTML string like "<strong>...". */
    private fun extractTagName(html: String): String {
        if (!html.startsWith("<")) return ""
        val end = html.indexOfFirst { it == '>' || it == ' ' || it == '/' }
        return if (end <= 1) "" else html.substring(1, end).lowercase()
    }

    /**
     * Find the index of the next block-level opening tag in [html], or -1 if none.
     * Used to split "inline content before the next block tag".
     */
    private fun indexOfNextBlockTag(html: String): Int {
        val blockTags = listOf("<h1>", "<h2>", "<ul>", "<ul ", "<p>")
        var idx = -1
        // Search from position 1 to skip current opening tag
        val searchFrom = html.indexOf('>') + 1
        if (searchFrom <= 0 || searchFrom >= html.length) return -1
        for (tag in blockTags) {
            val found = html.indexOf(tag, searchFrom)
            if (found != -1 && (idx == -1 || found < idx)) idx = found
        }
        return idx
    }

    /** Convert inline HTML spans back to Markdown. */
    private fun inlineToMarkdown(html: String): String {
        var result = html
        // Bold: <b>...</b> or <strong>...</strong> → **text**
        result = result.replace(Regex("<b>(.+?)</b>")) { "**${it.groupValues[1]}**" }
        result = result.replace(Regex("<strong>(.+?)</strong>")) { "**${it.groupValues[1]}**" }
        // Italic: <i>...</i> or <em>...</em> → *text*
        result = result.replace(Regex("<i>(.+?)</i>")) { "*${it.groupValues[1]}*" }
        result = result.replace(Regex("<em>(.+?)</em>")) { "*${it.groupValues[1]}*" }
        // Strikethrough: <s>...</s> or <del>...</del> or <strike>...</strike> → ~~text~~
        result = result.replace(Regex("<s>(.+?)</s>")) { "~~${it.groupValues[1]}~~" }
        result = result.replace(Regex("<del>(.+?)</del>")) { "~~${it.groupValues[1]}~~" }
        result = result.replace(Regex("<strike>(.+?)</strike>")) { "~~${it.groupValues[1]}~~" }
        // Strip any remaining unknown inline tags
        result = result.replace(Regex("<[^>]+>"), "")
        // Decode HTML entities back to literal characters. Aztec's toHtml() emits these for
        // any special char the user typed; without decoding they would persist verbatim in the
        // stored Markdown ('&amp;' instead of '&'). Decode '&amp;' LAST so a literal,
        // double-encoded sequence like '&amp;lt;' resolves to the text '&lt;', not '<'.
        result = decodeHtmlEntities(result)
        return result
    }

    /** Escape the three HTML-significant characters. Order: '&' first to avoid double-escaping. */
    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    /** Decode the entities our [escapeHtml] produces plus the non-breaking space Aztec emits. */
    private fun decodeHtmlEntities(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
}
