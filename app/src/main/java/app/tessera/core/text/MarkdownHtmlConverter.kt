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
        private val INLINE_TAGS = setOf("b", "strong", "i", "em", "s", "del", "strike", "span", "u", "code")

        /** Matches an ordered-list item prefix like "1. " / "12. ". */
        private val ORDERED_ITEM = Regex("""^\d+\. """)

        /** Private-use sentinel wrapping protected inline-code content during conversion. */
        private const val CODE_SENTINEL = "\uE000"

        /**
         * Zero-width space used to materialize a blank line in the HTML we feed Aztec. An empty
         * line (no characters) gets silently dropped by Aztec.fromHtml — especially right after a
         * block element or when several blanks are stacked — so we give the line one invisible
         * character to keep it alive. It serializes back out as an ordinary empty line ("\n") and
         * is stripped again on import, so it never leaks into the stored Markdown.
         */
        private const val BLANK_LINE_PLACEHOLDER = "\u00A0"

        /** Matches an HTML line break in any of the forms Aztec / our converter emit. */
        private val BR_TAG = Regex("<br\\s*/?>")
    }

    // -----------------------------------------------------------------------------------------
    // Markdown → HTML
    // -----------------------------------------------------------------------------------------

    fun markdownToHtml(markdown: String): String {
        if (markdown.isEmpty()) return ""

        val lines = markdown.split("\n")
        // One HTML fragment per logical line/block. Plain lines are emitted as bare (inline-only)
        // HTML rather than wrapped in <p>; a "<br>" separator between two PLAIN fragments is what
        // Aztec.fromHtml turns back into a single "\n" line break. Empty lines use the zero-width
        // placeholder so Aztec keeps them; the import step reverses both transforms.
        //
        // Crucially, a "<br>" placed immediately AFTER (or before) a block element — `</h1><br>`,
        // `</ul><br>` … — is rendered by Aztec.fromHtml as a phantom EMPTY line (the block already
        // ends its line, so the <br> adds a second break). That surfaced as a stray blank line
        // under every heading/list. So each fragment is tagged as block or not, and a separator is
        // emitted only between two non-block fragments; blocks self-separate in HTML.
        val fragments = mutableListOf<Pair<String, Boolean>>()
        fun addBlock(html: String) = fragments.add(html to true)
        fun addInline(html: String) = fragments.add(html to false)

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                // Fenced code block — a line that starts with ``` opens a block consumed
                // verbatim until the next ``` line (the optional language after the opening
                // fence is ignored). Emitted as <pre>, content escaped and never inline-parsed
                // so markup characters inside code stay literal. Blank code lines use the
                // zero-width placeholder so Aztec keeps them.
                line.startsWith("```") -> {
                    i++ // skip opening fence
                    val codeLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimEnd() != "```") {
                        codeLines.add(lines[i])
                        i++
                    }
                    if (i < lines.size) i++ // skip closing fence
                    val body = codeLines.joinToString("<br>") {
                        if (it.isEmpty()) BLANK_LINE_PLACEHOLDER else escapeHtml(it)
                    }
                    addBlock("<pre>$body</pre>")
                }
                // H1
                line.startsWith("# ") -> {
                    addBlock("<h1>${inlineToHtml(line.removePrefix("# "))}</h1>")
                    i++
                }
                // H2
                line.startsWith("## ") -> {
                    addBlock("<h2>${inlineToHtml(line.removePrefix("## "))}</h2>")
                    i++
                }
                // Task-list items — consume consecutive task items into one <ul type="task-list">
                line.startsWith("- [ ] ") || line.startsWith("- [x] ") -> {
                    val sb = StringBuilder("<ul type=\"task-list\">")
                    while (i < lines.size &&
                        (lines[i].startsWith("- [ ] ") || lines[i].startsWith("- [x] "))
                    ) {
                        val checked = lines[i].startsWith("- [x] ")
                        val text = lines[i].removePrefix(if (checked) "- [x] " else "- [ ] ")
                        val checkedAttr = if (checked) " checked" else ""
                        sb.append(
                            "<li><input type=\"checkbox\" class=\"task-list-item-checkbox\"$checkedAttr />${inlineToHtml(text)}</li>"
                        )
                        i++
                    }
                    sb.append("</ul>")
                    addBlock(sb.toString())
                }
                // Horizontal rule — a line that is exactly "---".
                line.trim() == "---" -> {
                    addBlock("<hr>")
                    i++
                }
                // Ordered list items — consume consecutive "N. " lines into one <ol>.
                ORDERED_ITEM.containsMatchIn(line) -> {
                    val sb = StringBuilder("<ol>")
                    while (i < lines.size && ORDERED_ITEM.containsMatchIn(lines[i])) {
                        sb.append("<li>${inlineToHtml(lines[i].replaceFirst(ORDERED_ITEM, ""))}</li>")
                        i++
                    }
                    sb.append("</ol>")
                    addBlock(sb.toString())
                }
                // Blockquote — consume consecutive "> " lines into one <blockquote>, lines
                // joined with <br> (mirrors Aztec's single-block multiline quote).
                line.startsWith("> ") -> {
                    val quoted = mutableListOf<String>()
                    while (i < lines.size && lines[i].startsWith("> ")) {
                        quoted.add(inlineToHtml(lines[i].removePrefix("> ")))
                        i++
                    }
                    addBlock("<blockquote>${quoted.joinToString("<br>")}</blockquote>")
                }
                // Regular bullet list items — consume consecutive bullets into one <ul>
                line.startsWith("- ") -> {
                    val sb = StringBuilder("<ul>")
                    while (i < lines.size &&
                        lines[i].startsWith("- ") &&
                        !lines[i].startsWith("- [ ] ") &&
                        !lines[i].startsWith("- [x] ")
                    ) {
                        sb.append("<li>${inlineToHtml(lines[i].removePrefix("- "))}</li>")
                        i++
                    }
                    sb.append("</ul>")
                    addBlock(sb.toString())
                }
                // Plain line — bare inline HTML, no <p> wrapper. An EMPTY line is a blank line the
                // user typed; emit it as an empty PARAGRAPH carrying the zero-width placeholder
                // (`<p>​</p>`). This is the only encoding Aztec.fromHtml keeps as a real blank row:
                // a bare empty fragment, an empty `<p></p>`, or a double `<br>` are all silently
                // collapsed. Critically, `<p>​</p>` reproduces the SAME paragraph structure a typed
                // blank line has, so Aztec's toHtml serializes it as the doubled "\n\n\n\n" run that
                // htmlToMarkdown's halving recovers — making the blank line a stable round-trip.
                else -> {
                    if (line.isEmpty()) addBlock("<p>$BLANK_LINE_PLACEHOLDER</p>") else addInline(inlineToHtml(line))
                    i++
                }
            }
        }

        val sb = StringBuilder()
        fragments.forEachIndexed { idx, (html, isBlock) ->
            if (idx > 0) {
                val (prevHtml, prevBlock) = fragments[idx - 1]
                val blankInvolved = prevHtml == BLANK_LINE_PLACEHOLDER || html == BLANK_LINE_PLACEHOLDER
                // A blank-line placeholder needs <br> separators on both sides to materialize.
                // Otherwise a <br> adjacent to a block renders as a phantom empty line, so omit it
                // between a block and a (non-blank) neighbour — blocks self-separate in HTML.
                val needBr = blankInvolved || !(prevBlock || isBlock)
                if (needBr) sb.append("<br>")
            }
            sb.append(html)
        }
        return sb.toString()
    }

    /** Convert inline Markdown spans (bold, italic, strike) to HTML inside a block. */
    private fun inlineToHtml(text: String): String {
        // Escape HTML-special chars FIRST so literal '<', '>', '&' in the note text can't be
        // mistaken for markup. Markdown markers ('*', '~') are not HTML-special, so the
        // bold/italic/strike regexes below still match and wrap the already-escaped content
        // in real tags. '&' must be escaped before '<'/'>' to avoid double-escaping entities.
        var result = escapeHtml(text)
        // Protect inline-code spans first so emphasis markers inside them stay literal.
        val codeSpans = mutableListOf<String>()
        result = result.replace(Regex("`([^`]+)`")) { m ->
            codeSpans.add(m.groupValues[1])
            "$CODE_SENTINEL${codeSpans.size - 1}$CODE_SENTINEL"
        }
        // Order matters: bold (** **) must be matched before single * italic.
        // Bold: **text** → <b>text</b>
        result = result.replace(Regex("""\*\*(.+?)\*\*""")) { "<b>${it.groupValues[1]}</b>" }
        // Italic: *text* → <i>text</i>  (only bare * not preceded by another *)
        result = result.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")) { "<i>${it.groupValues[1]}</i>" }
        // Strikethrough: ~~text~~ → <s>text</s>
        result = result.replace(Regex("""~~(.+?)~~""")) { "<s>${it.groupValues[1]}</s>" }
        // Restore inline-code spans as real tags.
        result = result.replace(Regex("$CODE_SENTINEL(\\d+)$CODE_SENTINEL")) {
            "<code>${codeSpans[it.groupValues[1].toInt()]}</code>"
        }
        // Underline has no Markdown syntax, so it is stored as a literal <u> tag. escapeHtml
        // turned it into entities; turn just those back into real tags.
        result = result.replace("&lt;u&gt;", "<u>").replace("&lt;/u&gt;", "</u>")
        return result
    }

    // -----------------------------------------------------------------------------------------
    // HTML → Markdown
    // -----------------------------------------------------------------------------------------

    fun htmlToMarkdown(html: String): String {
        if (html.isEmpty()) return ""

        // Aztec's toHtml() serializes every plain-text paragraph break as a DOUBLE bare "\n"
        // (verified via Robolectric: editable "a\nb" -> "a\n\nb"; "a\n\nb" -> "a\n\n\n\nb"), i.e.
        // its bare-newline runs are 2x the line breaks the user actually sees, while a block↔text
        // boundary stays a single "\n". So halve each bare-"\n" run to recover the visible line/
        // blank structure (one typed Enter -> one break, two Enters -> one blank line). Our OWN
        // markdownToHtml never emits a bare "\n" (it uses "<br>" for breaks and a zero-width
        // placeholder for blank lines), so this only rescales Aztec output — the "<br>" path below
        // is left at 1:1. <pre> code blocks are masked first: their line breaks aren't doubled and
        // may include intentional blank rows, so they must survive verbatim.
        // A bare-"\n" run BETWEEN two block tags is unambiguous (verified via Robolectric):
        // adjacency serializes as exactly ONE "\n" (`</h1>\n<ul>`), while a LOADED blank line is an
        // NBSP paragraph that serializes as a text row (`</h1>\n \n<ul>`) and never reaches the
        // bare-run rules. So a bare run of 2+ between blocks can only be a blank line the user
        // TYPED since the last load — it must be kept (capped at one blank by rule (3) below; the
        // NBSP encoding makes it stable on every following round-trip). The historical "collapse
        // to adjacency" rule here predated the NBSP marker and silently deleted typed blanks.
        val (masked, preBlocks) = maskPreBlocks(html)

        // (1) Normalize a TOGGLED heading. Toggling an existing line to a heading leaves a trailing
        // "\n" INSIDE the tag (`<h1>body\n</h1>`) AND serializes the break to the following content
        // DOUBLED (plain-paragraph style): editable `body\nXxx` -> `<h1>body\n</h1>\n\nXxx` (adjacent)
        // and `body\n\nXxx` -> `<h1>body\n</h1>\n\n\n\nXxx` (one blank). A heading TYPED cleanly has
        // no in-tag "\n" and a 1:1 break. So when the in-tag "\n" is present, drop it and HALVE the
        // following run — turning the toggle form into the clean typed form the rules below expect.
        val toggleFixed = masked.replace(Regex("\\n+</h([12])>(\\n*)")) { m ->
            "</h${m.groupValues[1]}>" + "\n".repeat((m.groupValues[2].length + 1) / 2)
        }

        // (3) After a block, ONE blank line is stable and kept; 2+ inflate unstably, so cap at
        // one. This also covers a blank TYPED between two blocks (run of 2+ before the next tag).
        val capped = toggleFixed.replace(Regex("(?<=[>$CODE_SENTINEL])\\n{2,}"), "\n\n")

        // (4) PLAIN-text paragraph breaks serialize DOUBLED ("\n\n" per break), so halve any run
        // whose LEFT neighbour is plain text (not a tag's ">" / masked sentinel). This preserves
        // blanks plain→plain and plain→block for any count (the doubling there is linear/stable).
        // parseBlocks then emits (run-1) blank lines per remaining run.
        val halved = capped.replace(Regex("(?<=[^>\\n$CODE_SENTINEL])\\n+")) {
            "\n".repeat((it.value.length + 1) / 2)
        }
        val restored = restorePreBlocks(halved, preBlocks)

        // Now fold the remaining markup to plain newlines: "<br>" (our adjacency markup / Aztec
        // soft breaks) -> a single "\n"; the placeholder is removed but its row survives as an
        // empty line. parseBlocks then preserves every blank line; joining with "\n" reproduces
        // the user's paragraph spacing exactly.
        val normalized = restored.replace(BR_TAG, "\n").replace(BLANK_LINE_PLACEHOLDER, "")

        // Exiting a list/quote/code by typing Enter serializes as a SINGLE "\n" before the next
        // paragraph (`</ul>\ntext`) — Aztec drops the blank line the user added, and a single
        // newline is a lazy continuation in Markdown anyway. Promote that to a blank line so the
        // separation the user typed is kept. Only fires for a block close directly followed by one
        // newline and then non-whitespace, non-tag text: a run with an existing blank ("\n\n…") or
        // a following tag/empty-paragraph placeholder is left untouched, so the load path (which
        // encodes blanks as 2 newlines / placeholders) stays idempotent.
        val separated = normalized.replace(
            Regex("(</ul>|</ol>|</blockquote>|</pre>)\\n(?=[^<\\s])"),
        ) { "${it.groupValues[1]}\n\n" }
        return parseBlocks(separated).joinToString("\n")
    }

    /**
     * Replace each `<pre>…</pre>` block with an opaque sentinel so the bare-newline halving in
     * [htmlToMarkdown] can't rescale code-block line breaks. Returns the masked HTML plus the
     * extracted blocks in order; [restorePreBlocks] swaps them back.
     */
    private fun maskPreBlocks(html: String): Pair<String, List<String>> {
        val blocks = mutableListOf<String>()
        val masked = Regex("<pre[\\s>].*?</pre>", RegexOption.DOT_MATCHES_ALL).replace(html) { m ->
            blocks.add(m.value)
            "$CODE_SENTINEL${blocks.size - 1}$CODE_SENTINEL"
        }
        return masked to blocks
    }

    private fun restorePreBlocks(html: String, blocks: List<String>): String =
        if (blocks.isEmpty()) html
        else html.replace(Regex("$CODE_SENTINEL(\\d+)$CODE_SENTINEL")) { blocks[it.groupValues[1].toInt()] }

    /**
     * Parse the top-level HTML into a flat list of Markdown lines (one per block element).
     * Each block element becomes one or more Markdown lines.
     */
    private fun parseBlocks(html: String): List<String> {
        val lines = mutableListOf<String>()
        var remaining = html.trim()

        while (remaining.isNotEmpty()) {
            // The newline run separating this element from the previous one carries the user's
            // blank lines: the FIRST newline is the line separator (adjacency), and each EXTRA
            // newline is a blank line. (Aztec's doubled plain-text breaks were already halved to
            // the visible count in htmlToMarkdown.) Emit one empty line per extra newline, then
            // drop the run. Leading newlines at the very start (lines still empty) are ignored so a
            // note never opens with a blank row.
            val newlineRun = remaining.takeWhile { it == '\n' }.length
            if (newlineRun > 0) {
                if (lines.isNotEmpty()) repeat(newlineRun - 1) { lines.add("") }
                remaining = remaining.substring(newlineRun)
                if (remaining.isEmpty()) break
            }

            when {
                // A toggled heading can carry a spurious trailing "\n" inside the tag
                // (`<h1>body\n</h1>`); trim it so it doesn't leak an extra line into the Markdown.
                remaining.startsWith("<h1>") ->
                    remaining = consumeTag(remaining, "h1") { inner -> lines.add("# ${inlineToMarkdown(inner.trimEnd('\n'))}") }

                remaining.startsWith("<h2>") ->
                    remaining = consumeTag(remaining, "h2") { inner -> lines.add("## ${inlineToMarkdown(inner.trimEnd('\n'))}") }

                remaining.startsWith("<ul type=\"task-list\">") ->
                    remaining = consumeTaskList(remaining, lines)

                remaining.startsWith("<ul>") ->
                    remaining = consumeBulletList(remaining, lines)

                remaining.startsWith("<ol>") ->
                    remaining = consumeOrderedList(remaining, lines)

                remaining.startsWith("<pre>") || remaining.startsWith("<pre ") ->
                    remaining = consumePreformat(remaining, lines)

                remaining.startsWith("<blockquote>") ->
                    remaining = consumeBlockquote(remaining, lines)

                remaining.startsWith("<hr") -> {
                    lines.add("---")
                    val close = remaining.indexOf('>')
                    remaining = if (close == -1) "" else remaining.substring(close + 1)
                }

                remaining.startsWith("<p>") ->
                    remaining = consumeTag(remaining, "p") { inner -> lines.add(inlineToMarkdown(inner)) }

                remaining.startsWith("<") && extractTagName(remaining) !in INLINE_TAGS -> {
                    // Unknown block-level tag: strip outer tag, recursively process inner content.
                    val (inner, rest) = stripOuterTag(remaining)
                    lines.addAll(parseBlocks(inner))
                    remaining = rest
                }

                else -> {
                    // Plain-text run (may start with an inline tag like <b>). Consume up to the
                    // next block-level tag; drop trailing newlines (left for the next iteration's
                    // blank-line accounting) and emit one Markdown line per "\n".
                    val nextBlock = indexOfNextBlockTagFrom(remaining, 0)
                    val chunk = if (nextBlock == -1) remaining else remaining.substring(0, nextBlock)
                    val core = chunk.trimEnd('\n')
                    addTextLines(core, lines)
                    remaining = remaining.substring(core.length)
                    if (nextBlock == -1 && remaining.all { it == '\n' }) remaining = ""
                }
            }
        }

        return lines
    }

    /**
     * Append a plain-text run as discrete Markdown lines, preserving blank lines.
     *
     * By the time a run reaches here, [htmlToMarkdown] has already halved Aztec's doubled
     * plain-text breaks, so the newlines in [core] match the line/blank structure the user sees:
     * a single "\n" separates adjacent lines and each additional "\n" is a blank line. So split on
     * "\n" verbatim — empty segments become blank Markdown lines.
     */
    private fun addTextLines(core: String, lines: MutableList<String>) {
        if (core.isEmpty()) return
        core.split("\n").forEach { line ->
            lines.add(inlineToMarkdown(line.trim()))
        }
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
        val rest = html.substring(end + closeUl.length)
        // An empty list item is the blank line Aztec absorbed into the list (when a break was
        // inserted between this list and a neighbour). Emit it as a real blank line — UNLESS it's
        // the genuine LAST item of a list that ends the note (a not-yet-typed item being built),
        // i.e. nothing meaningful follows the list.
        val items = extractListItems(inner)
        items.forEachIndexed { idx, itemHtml ->
            val md = inlineToMarkdown(itemHtml)
            if (md.isBlank() && (idx < items.lastIndex || rest.isNotBlank())) lines.add("") else lines.add("- $md")
        }
        return rest
    }

    private fun consumeOrderedList(html: String, lines: MutableList<String>): String {
        val closeOl = "</ol>"
        val olStart = "<ol>"
        val start = html.indexOf(olStart)
        if (start == -1) return html
        val contentStart = start + olStart.length
        val end = html.indexOf(closeOl, contentStart)
        if (end == -1) return ""
        val inner = html.substring(contentStart, end)
        val rest = html.substring(end + closeOl.length)
        val items = extractListItems(inner)
        var n = 0
        items.forEachIndexed { idx, itemHtml ->
            val md = inlineToMarkdown(itemHtml)
            if (md.isBlank() && (idx < items.lastIndex || rest.isNotBlank())) lines.add("") else { n++; lines.add("$n. $md") }
        }
        return rest
    }

    /**
     * Consume a `<pre>` code block, emitting a fenced Markdown block:
     * ```
     * line one
     * line two
     * ```
     * Content is preserved verbatim (inline tags stripped, entities decoded) — no Markdown
     * inline parsing applies inside code. The htmlToMarkdown normalization already turned the
     * `<br>` separators (ours) and any `\n` (Aztec's) into newlines, so split on either form.
     */
    private fun consumePreformat(html: String, lines: MutableList<String>): String {
        val openEnd = html.indexOf('>')
        if (openEnd == -1) return ""
        val contentStart = openEnd + 1
        val closeTag = "</pre>"
        val end = html.indexOf(closeTag, contentStart)
        val inner = if (end == -1) html.substring(contentStart) else html.substring(contentStart, end)
        val codeLines = inner.split(Regex("<br\\s*/?>|\n")).map { part ->
            val decoded = decodeHtmlEntities(part.replace(Regex("<[^>]+>"), ""))
            // A blank code line is carried by the NBSP placeholder (which Aztec may emit as a raw
            // NBSP or as "&nbsp;" -> a space); normalize any whitespace-only line back to empty so
            // the placeholder never leaks into the stored code.
            if (decoded.isBlank()) "" else decoded
        }.toMutableList()
        // Aztec may append a structural trailing newline to the block; drop a single empty
        // tail line so the code block doesn't gain a phantom blank row each round-trip.
        if (codeLines.size > 1 && codeLines.last().isEmpty()) codeLines.removeAt(codeLines.lastIndex)
        lines.add("```")
        lines.addAll(codeLines)
        lines.add("```")
        return if (end == -1) "" else html.substring(end + closeTag.length)
    }

    private fun consumeBlockquote(html: String, lines: MutableList<String>): String {
        val closeQ = "</blockquote>"
        val qStart = "<blockquote>"
        val start = html.indexOf(qStart)
        if (start == -1) return html
        val contentStart = start + qStart.length
        val end = html.indexOf(closeQ, contentStart)
        if (end == -1) return ""
        val inner = html.substring(contentStart, end)
        // Aztec keeps a multiline quote as one block with <br> separators; our markdownToHtml
        // does the same. The htmlToMarkdown normalization may already have turned those into
        // "\n", so split on either form. Split them back into one "> " line each.
        inner.split(Regex("<br\\s*/?>|\n")).forEach { part ->
            val text = inlineToMarkdown(part.trim())
            if (text.isNotEmpty()) lines.add("> $text")
        }
        return html.substring(end + closeQ.length)
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
        val rest = html.substring(end + closeUl.length)
        // An EMPTY task item is the blank line Aztec absorbed into the list when a break was
        // inserted between this list and a neighbour — emit it as a real blank line (round-trips as
        // an NBSP paragraph). EXCEPTION: a trailing empty item of a list that ENDS the note is a
        // genuine not-yet-typed checkbox (checklist being built), so keep it.
        val items = extractListItems(inner)
        items.forEachIndexed { idx, itemHtml ->
            val (checked, text) = parseTaskItem(itemHtml)
            val md = inlineToMarkdown(text)
            if (md.isBlank() && (idx < items.lastIndex || rest.isNotBlank())) {
                lines.add("")
            } else {
                lines.add("${if (checked) "- [x] " else "- [ ] "}$md")
            }
        }
        return rest
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
     * Find the index (>= [from]) of the next block-level opening tag in [html], or -1 if none.
     * Inline tags (<b>, <i>, …) are deliberately ignored so a plain-text run that contains
     * emphasis is consumed as a single paragraph.
     */
    private fun indexOfNextBlockTagFrom(html: String, from: Int): Int {
        val blockTags = listOf("<h1>", "<h2>", "<ul>", "<ul ", "<ol>", "<blockquote>", "<pre>", "<pre ", "<hr", "<p>")
        var idx = -1
        for (tag in blockTags) {
            val found = html.indexOf(tag, from)
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
        // Inline code: <code>...</code> → `text`
        result = result.replace(Regex("<code>(.+?)</code>")) { "`${it.groupValues[1]}`" }
        // Underline has no Markdown form — keep the literal <u> tag through the strip below.
        result = result.replace("<u>", CODE_SENTINEL + "u").replace("</u>", CODE_SENTINEL + "/u")
        // Strip any remaining unknown inline tags
        result = result.replace(Regex("<[^>]+>"), "")
        result = result.replace(CODE_SENTINEL + "/u", "</u>").replace(CODE_SENTINEL + "u", "<u>")
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
