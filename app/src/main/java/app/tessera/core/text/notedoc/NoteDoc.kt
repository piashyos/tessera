package app.tessera.core.text.notedoc

import app.tessera.core.text.markdownblocks.TableGrid

/** Never empty after [normalize]: at least one block. */
data class NoteDoc(val blocks: List<NoteBlock>)

sealed interface NoteBlock {
    data class Text(val kind: ParagraphKind, val content: Inline) : NoteBlock
    data object Divider : NoteBlock
    /** `tessera-img://…` as is, including `?w=&rot=`. */
    data class Image(val src: String) : NoteBlock
    data class Table(val grid: TableGrid) : NoteBlock
}

sealed interface ParagraphKind {
    data object Body : ParagraphKind
    data object Title : ParagraphKind
    data object Heading : ParagraphKind
    data object Subheading : ParagraphKind
    data object Mono : ParagraphKind
    data object Quote : ParagraphKind
    /** [level] is 0..4; [checked] is meaningful for [ListType.Check] only. */
    data class ListItem(val type: ListType, val level: Int, val checked: Boolean = false) : ParagraphKind
}

enum class ListType { Bullet, Dash, Number, Check }

data class Inline(val text: String, val marks: List<MarkRange> = emptyList())

data class MarkRange(val start: Int, val end: Int, val mark: Mark)

sealed interface Mark {
    data object Bold : Mark
    data object Italic : Mark
    data object Underline : Mark
    data object Strike : Mark
    data object Code : Mark
}

internal val EMPTY_BODY = NoteBlock.Text(ParagraphKind.Body, Inline(""))

private val IMAGE_SRC = Regex("""^!\[]\((tessera-img://[A-Za-z0-9-]+(?:\?[^)]*)?)\)$""")

/** The note's image line: `![](tessera-img://<id>[?params])`; returns the src. */
internal fun imageLineSrc(line: String): String? = IMAGE_SRC.matchEntire(line)?.groupValues?.get(1)

internal const val MAX_LEVEL = 4

/** The canonical form (spec §4, rules 1-10). [NoteMarkdown.parse] always returns a canonical document. */
fun normalize(doc: NoteDoc): NoteDoc {
    val out = ArrayList<NoteBlock>(doc.blocks.size + 1)
    for (block in doc.blocks) {
        val n = normalizeBlock(block)
        if (n is NoteBlock.Table && out.lastOrNull() is NoteBlock.Table) out += EMPTY_BODY // rule 9
        out += n
    }
    if (out.isEmpty()) out += EMPTY_BODY // rule 7
    return NoteDoc(out)
}

private fun normalizeBlock(block: NoteBlock): NoteBlock = when (block) {
    NoteBlock.Divider -> block
    is NoteBlock.Text -> NoteBlock.Text(normalizeKind(block.kind), normalizeContent(block))
    is NoteBlock.Image -> {
        val src = oneLine(block.src) // rule 11
        if (imageLineSrc("![]($src)") != null) NoteBlock.Image(src)
        else NoteBlock.Text(ParagraphKind.Body, Inline("![]($src)")) // rule 10
    }
    is NoteBlock.Table -> normalizeTable(block)
}

private fun normalizeKind(kind: ParagraphKind): ParagraphKind =
    if (kind is ParagraphKind.ListItem) {
        kind.copy(level = kind.level.coerceIn(0, MAX_LEVEL), checked = kind.checked && kind.type == ListType.Check) // rule 5
    } else {
        kind
    }

private fun normalizeContent(block: NoteBlock.Text): Inline {
    val content = withoutObjectChar(block.content)
    return if (block.kind == ParagraphKind.Mono) Inline(oneLine(content.text)) // rule 4
    else normalizeInline(content.copy(text = oneLine(content.text)))
}

/** Rule 13: U+FFFC is the editor's placeholder of an object, it has no place in the text of a paragraph; the marks around it stay whole. */
private fun withoutObjectChar(inline: Inline): Inline {
    val t = inline.text
    if ('\uFFFC' !in t) return inline
    val kept = IntArray(t.length + 1) // kept[i]: how many characters before index i stay
    for (i in t.indices) kept[i + 1] = kept[i] + if (t[i] == '\uFFFC') 0 else 1
    fun at(i: Int) = kept[i.coerceIn(0, t.length)]
    return Inline(
        t.replace("\uFFFC", ""),
        inline.marks.map { MarkRange(at(it.start), at(it.end), it.mark) }.filter { it.start < it.end },
    )
}

/** Rule 11: one line is one paragraph; a line break in a paragraph, source or table cell becomes a space. */
private fun oneLine(s: String) = if ('\n' in s || '\r' in s) s.replace('\n', ' ').replace('\r', ' ') else s

/** Rule 8: a table is what its own record parses to. A table that has no record (no columns) is dropped to an empty body. */
private fun normalizeTable(table: NoteBlock.Table): NoteBlock {
    val g = table.grid
    val flat = g.copy(headers = g.headers.map(::oneLine), rows = g.rows.map { r -> r.map(::oneLine) })
    return readTable(tableLines(flat), 0)?.first ?: EMPTY_BODY
}

// ---- marks (rules 1, 2, 3, 6) ----

private fun rank(mark: Mark) = when (mark) {
    Mark.Bold -> 0
    Mark.Italic -> 1
    Mark.Underline -> 2
    Mark.Strike -> 3
    Mark.Code -> 4
}

/** Unicode white space of CommonMark (Zs, tab, LF, FF, CR). The parser's flanking rules use the same set. */
internal fun isWs(c: Char) =
    c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

private class Span(var start: Int, var end: Int)

internal fun normalizeInline(inline: Inline): Inline {
    val t = inline.text
    val n = t.length

    fun trim(s: Span): Boolean {
        while (s.start < s.end && isWs(t[s.start])) s.start++
        while (s.end > s.start && isWs(t[s.end - 1])) s.end--
        return s.start < s.end
    }

    // rule 12: a boundary inside a surrogate pair moves to the start of the pair
    fun snap(i: Int) = if (i in 1 until n && t[i - 1].isHighSurrogate() && t[i].isLowSurrogate()) i - 1 else i

    fun merged(mark: Mark): List<Span> {
        val sorted = inline.marks.filter { it.mark == mark }
            .map { Span(snap(it.start.coerceIn(0, n)), snap(it.end.coerceIn(0, n))) }
            .filter { it.start < it.end }
            .sortedBy { it.start }
        val out = ArrayList<Span>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && s.start <= last.end) last.end = maxOf(last.end, s.end) else out += s
        }
        return out
    }

    val codes = merged(Mark.Code).filter(::trim)
    val result = codes.map { MarkRange(it.start, it.end, Mark.Code) }.toMutableList()
    for (mark in listOf(Mark.Bold, Mark.Italic, Mark.Underline, Mark.Strike)) {
        for (s in merged(mark)) {
            if (!trim(s)) continue
            for (c in codes) {
                if (s.start <= c.start && c.end <= s.end) continue // covers the code: stays
                if (c.start <= s.start && s.start < c.end) s.start = c.end // starts inside the code
                else if (c.start < s.end && s.end <= c.end) s.end = c.start // ends inside the code
                if (s.start >= s.end) break
            }
            if (s.start < s.end && trim(s)) result += MarkRange(s.start, s.end, mark)
        }
    }
    result.sortWith(compareBy({ it.start }, { it.end }, { rank(it.mark) }))
    return Inline(t, result)
}
