package app.tessera.core.text.notedoc

import app.tessera.core.text.markdownblocks.SEPARATOR
import app.tessera.core.text.markdownblocks.TESSERA_META
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.markdownblocks.applyMeta
import app.tessera.core.text.markdownblocks.metaComment
import app.tessera.core.text.markdownblocks.parseGfmTable
import app.tessera.core.text.markdownblocks.serializeGfmTable

/**
 * Markdown dialect of the editor (spec §5). One line is one paragraph, an empty line is an empty paragraph.
 * `parse` always returns a canonical document and `parse(serialize(doc)) == normalize(doc)`.
 */
object NoteMarkdown {

    fun parse(md: String): NoteDoc {
        val lines = md.split("\n")
        val out = ArrayList<NoteBlock>()
        var i = 0
        while (i < lines.size) {
            val fence = fenceLength(lines[i])
            if (fence > 0) {
                i++
                while (i < lines.size && !closesFence(lines[i], fence)) out += NoteBlock.Text(ParagraphKind.Mono, Inline(lines[i++]))
                i++ // the closing fence; an unclosed fence runs to the end
                continue
            }
            val table = readTable(lines, i)
            if (table != null) {
                out += table.first
                i = table.second
                continue
            }
            out += readLine(lines[i++])
        }
        return normalize(NoteDoc(out))
    }

    fun serialize(doc: NoteDoc): String {
        val blocks = normalize(doc).blocks
        val lines = ArrayList<String>()
        val numbering = Numbering()
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            if (block is NoteBlock.Text && block.kind is ParagraphKind.ListItem) {
                lines += writeText(block, numbering.next(block.kind))
                i++
                continue
            }
            numbering.reset()
            when {
                block is NoteBlock.Text && block.kind == ParagraphKind.Mono -> {
                    val run = blocks.drop(i).takeWhile { it is NoteBlock.Text && it.kind == ParagraphKind.Mono }
                        .map { (it as NoteBlock.Text).content.text }
                    val fence = "`".repeat(maxOf(3, run.maxOf { backtickRunAtStart(it) + 1 }))
                    lines += fence
                    lines += run
                    lines += fence
                    i += run.size
                    continue
                }
                block is NoteBlock.Text -> lines += writeText(block, 0)
                block is NoteBlock.Image -> lines += "![](${block.src})"
                block is NoteBlock.Table -> lines += tableLines(block.grid)
                else -> lines += "---"
            }
            i++
        }
        return lines.joinToString("\n")
    }
}

// ---- reading ----

/** Opening fence: three or more backticks at column 0 and no other backtick on the line. Returns its length or 0. */
private fun fenceLength(line: String): Int {
    val n = backtickRunAtStart(line)
    return if (n >= 3 && line.indexOf('`', n) < 0) n else 0
}

private fun closesFence(line: String, open: Int): Boolean {
    val n = backtickRunAtStart(line)
    return n >= open && line.substring(n).all { it == ' ' || it == '\t' }
}

private fun backtickRunAtStart(s: String): Int {
    var n = 0
    while (n < s.length && s[n] == '`') n++
    return n
}

/** Detects a GFM table (with its optional sizing comment) at a line. Returns the table and the index after it. */
internal fun readTable(lines: List<String>, i: Int): Pair<NoteBlock.Table, Int>? {
    val meta = TESSERA_META.matchEntire(lines[i])
    val start = if (meta != null) i + 1 else i
    if (start + 1 >= lines.size || !lines[start].trimStart().startsWith("|") || !SEPARATOR.containsMatchIn(lines[start + 1])) return null
    var end = start + 2
    while (end < lines.size && lines[end].trimStart().startsWith("|")) end++
    val grid = parseGfmTable(lines.subList(start, end)) ?: return null
    val sized = meta?.groupValues?.get(1)?.let { applyMeta(grid, it) } ?: grid
    return NoteBlock.Table(sized) to end
}

internal fun tableLines(grid: TableGrid): List<String> =
    listOfNotNull(metaComment(grid)) + serializeGfmTable(grid).split("\n")

private val DIVIDER = Regex("""^(?:-{3,}|\*{3,}|_{3,})[ \t]*$""")

/** A line that is neither inside a fence nor part of a table. */
private fun readLine(line: String): NoteBlock {
    imageLineSrc(line)?.let { return NoteBlock.Image(it) }
    if (DIVIDER.matches(line)) return NoteBlock.Divider
    var hashes = 0
    while (hashes < line.length && line[hashes] == '#') hashes++
    if (hashes in 1..6 && line.startsWith(" ", hashes)) {
        val kind = when (hashes) { 1 -> ParagraphKind.Title; 2 -> ParagraphKind.Heading; else -> ParagraphKind.Subheading }
        return text(kind, line.substring(hashes + 1))
    }
    if (line.startsWith("> ")) return text(ParagraphKind.Quote, line.substring(2))
    readListItem(line)?.let { return it }
    return text(ParagraphKind.Body, line)
}

private fun text(kind: ParagraphKind, s: String) = NoteBlock.Text(kind, parseInline(s))

private fun readListItem(line: String): NoteBlock? {
    var i = 0
    var width = 0
    while (i < line.length && (line[i] == ' ' || line[i] == '\t')) { width += if (line[i] == '\t') 4 else 1; i++ }
    val level = minOf((width + 3) / 4, MAX_LEVEL)
    if (i >= line.length) return null
    val c = line[i]
    if (c == '-' || c == '*' || c == '+') {
        if (!line.startsWith(" ", i + 1)) return null
        val rest = line.substring(i + 2)
        for (box in listOf("[ ] ", "[x] ", "[X] ")) {
            if (rest.startsWith(box)) {
                return text(ParagraphKind.ListItem(ListType.Check, level, box != "[ ] "), rest.substring(4))
            }
        }
        return text(ParagraphKind.ListItem(if (c == '+') ListType.Dash else ListType.Bullet, level), rest)
    }
    var d = i
    while (d < line.length && line[d] in '0'..'9') d++
    if (d > i && d + 1 < line.length && (line[d] == '.' || line[d] == ')') && line[d + 1] == ' ') {
        return text(ParagraphKind.ListItem(ListType.Number, level), line.substring(d + 2))
    }
    return null
}

// ---- writing ----

/** Counter per level; reset by a non-list paragraph, a shallower item or an item of another type on the same level. */
private class Numbering {
    private val counters = IntArray(MAX_LEVEL + 1)
    private val types = arrayOfNulls<ListType>(MAX_LEVEL + 1)

    fun reset() {
        counters.fill(0)
        types.fill(null)
    }

    fun next(kind: ParagraphKind.ListItem): Int {
        for (l in kind.level + 1..MAX_LEVEL) { counters[l] = 0; types[l] = null }
        if (types[kind.level] != kind.type) { counters[kind.level] = 0; types[kind.level] = kind.type }
        return ++counters[kind.level]
    }
}

private fun writeText(block: NoteBlock.Text, number: Int): String {
    val kind = block.kind
    val prefix = when (kind) {
        ParagraphKind.Title -> "# "
        ParagraphKind.Heading -> "## "
        ParagraphKind.Subheading -> "### "
        ParagraphKind.Quote -> "> "
        is ParagraphKind.ListItem -> "    ".repeat(kind.level) + when (kind.type) {
            ListType.Bullet -> "- "
            ListType.Dash -> "+ "
            ListType.Number -> "$number. "
            ListType.Check -> if (kind.checked) "- [x] " else "- [ ] "
        }
        else -> ""
    }
    fun line(inline: String) = prefix + when (kind) {
        ParagraphKind.Body -> escapeBodyStart(inline)
        is ParagraphKind.ListItem -> if (checkboxLike(inline, 0)) "\\$inline" else inline
        else -> inline
    }
    return line(serializeInline(block.content) { readLine(line(it)) == block })
}

private fun checkboxLike(s: String, at: Int) =
    s.startsWith("[ ] ", at) || s.startsWith("[x] ", at) || s.startsWith("[X] ", at)

/** If [s] would be read as a block start, put `\` before the punctuation that makes it one (§5.3 table). */
private fun escapeBodyStart(s: String): String {
    var p = 0
    while (p < s.length && s[p].isWhitespace()) p++
    val at = blockStartEscapeIndex(s, p)
    return if (at < 0) s else s.substring(0, at) + "\\" + s.substring(at)
}

private fun blockStartEscapeIndex(s: String, p: Int): Int {
    if (p >= s.length) return -1
    val c = s[p]
    if (s.startsWith("|", p) || s.startsWith("<!--", p) || s.startsWith("![](", p) || s.startsWith("> ", p)) return p
    if (c == '#') {
        var h = p
        while (h < s.length && s[h] == '#') h++
        if (h - p in 1..6 && s.startsWith(" ", h)) return p
    }
    if ((c == '-' || c == '+' || c == '*') && s.startsWith(" ", p + 1)) return p
    if (c in '0'..'9') {
        var d = p
        while (d < s.length && s[d] in '0'..'9') d++
        if (d < s.length && (s[d] == '.' || s[d] == ')') && s.startsWith(" ", d + 1)) return d
    }
    if (c == '`') {
        val n = backtickRunAtStart(s.substring(p))
        if (n >= 3 && s.indexOf('`', p + n) < 0) return p
    }
    if (c == '-' || c == '*' || c == '_') {
        val rest = s.substring(p)
        if (rest.all { it in "-*_ \t" } && rest.count { it in "-*_" } >= 3) return p
    }
    return -1
}
