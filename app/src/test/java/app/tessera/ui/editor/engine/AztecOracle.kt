package app.tessera.ui.editor.engine

import android.text.Spanned
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SentinelSpan
import org.wordpress.aztec.spans.AztecHeadingSpan
import org.wordpress.aztec.spans.AztecHorizontalRuleSpan
import org.wordpress.aztec.spans.AztecImageSpan
import org.wordpress.aztec.spans.AztecListItemSpan
import org.wordpress.aztec.spans.AztecOrderedListSpan
import org.wordpress.aztec.spans.AztecPreformatSpan
import org.wordpress.aztec.spans.AztecQuoteSpan
import org.wordpress.aztec.spans.AztecTaskListSpan
import org.wordpress.aztec.spans.AztecUnorderedListSpan

/** Kind of a line as the user sees it (spec §8, oracle): list level and bullet/dash are not part of it. */
enum class OracleKind { Title, Heading, Subheading, Bullet, Number, CheckOff, CheckOn, Quote, Mono, Object, Body }

data class OracleLine(val text: String, val kind: OracleKind)

/** One document whose Aztec lines and engine lines differ. [rule] is the allowed divergence that explains every differing line, `null` if one is unexplained. */
data class Divergence(
    val markdown: String,
    val aztec: List<OracleLine>,
    val engine: List<OracleLine>,
    val rule: String?,
) {
    override fun toString() = "md=${markdown.replace("\n", "\\n")}\n  aztec  =$aztec\n  engine =$engine\n  rule=$rule"
}

/** A divergence the spec allows (§8). [explains] gets one differing pair of lines at the same index. */
class AllowedDivergence(val name: String, val comment: String, val explains: (aztec: OracleLine, engine: OracleLine) -> Boolean)

/**
 * Compares what Aztec shows for a stored Markdown with what the engine shows: visible text and kind of every line.
 * Pure over strings: the Aztec side is passed in, so the same function runs over the corpus (here) and over a dump of
 * real notes (`realNotesDump`, Chunk 6).
 */
object AztecOracle {

    /** Spec §8 "Допустимые расхождения". Each one has its own case in `AztecOracleTest`. */
    val allowed: List<AllowedDivergence> = listOf(
        AllowedDivergence(
            "literal block marker",
            "Aztec shows `* x`, `+ x`, `N) x`, `### x`..`###### x`, `* [ ] x`, `- [X] x` as literal text and trims the indent off an indented marker (`    - x` is the text `- x`); the dialect reads all of them as a block, and the kind it reads follows from the marker",
        ) { a, e ->
            (a.kind == OracleKind.Body && expectedKind(a.text) == e.kind && stripLiteralMarker(a.text) == e.text) ||
                // `- [X] x`: Aztec made a bullet with the literal `[X] `
                (a.kind == OracleKind.Bullet && e.kind == OracleKind.CheckOn && a.text.startsWith("[X] ") && a.text.drop(4) == e.text)
        },
        AllowedDivergence(
            "literal rule",
            "Aztec shows `***` / `___` as text; the dialect reads a divider (the object line itself is dropped by the normalization)",
        ) { a, e -> a.kind == OracleKind.Body && e.kind == OracleKind.Object && a.text.trim().matches(Regex("""[*_]{3,}""")) },
        AllowedDivergence(
            "emphasis flanking",
            "`2 * 3 * 4`: the old regex made italics, the delimiter-run parser leaves the text literal",
        ) { a, e ->
            a.kind == e.kind && extraDelimitersAreFreeStanding(e.text) &&
                collapse(e.text.replace("*", "").replace("~", "")) == collapse(a.text)
        },
        AllowedDivergence(
            "literal tags b i s",
            "Aztec kept a typed `<b>`, `<i>`, `<s>` as text; the dialect reads it as a mark",
        ) { a, e -> a.kind == e.kind && Regex("""</?[bis]>""").containsMatchIn(a.text) && a.text.replace(Regex("""</?[bis]>"""), "") == e.text },
        AllowedDivergence(
            "leading spaces",
            "`** bold**`: Aztec collapses the space that starts a paragraph on load (HTML rule), the engine keeps the stored one (\" bold\"); allowed only when the lines differ in nothing but leading spaces",
        ) { a, e -> a.kind == e.kind && a.text != e.text && e.text.trimStart() == a.text },
        AllowedDivergence(
            "backslash before punctuation",
            "`\\*` outside code: the dialect removes the backslash",
        ) { a, e -> a.kind == e.kind && a.text.replace(PUNCT_ESCAPE, "$1") == e.text && a.text != e.text },
    )

    /** Every `*` / `~` series of [t] has white space or an edge on both sides: `2 * 3 * 4`, not `**bold**`. */
    private fun extraDelimitersAreFreeStanding(t: String): Boolean {
        val runs = Regex("[*~]+").findAll(t).toList()
        return runs.isNotEmpty() && runs.all { r ->
            (r.range.first == 0 || t[r.range.first - 1].isWhitespace()) && (r.range.last == t.lastIndex || t[r.range.last + 1].isWhitespace())
        }
    }

    private fun collapse(s: String) = s.replace(Regex(" +"), " ")

    private val PUNCT_ESCAPE = Regex("""\\([!-/:-@\[-`{-~])""")
    private val LITERAL_MARKER = Regex("""^[ \t]*(?:[-*+] (?:\[[ xX]] )?|\d+[.)] |#{3,6} )""")

    /** The kind the dialect reads from the literal marker that starts [s], `null` if there is none. */
    private fun expectedKind(s: String): OracleKind? {
        val marker = LITERAL_MARKER.find(s)?.value?.trim() ?: return null
        return when {
            marker.startsWith("#") -> OracleKind.Subheading
            marker.first().isDigit() -> OracleKind.Number
            marker.endsWith("[ ]") -> OracleKind.CheckOff
            marker.endsWith("]") -> OracleKind.CheckOn
            else -> OracleKind.Bullet
        }
    }

    /** The text a literal block marker leaves after the dialect has read it (the marker, an optional box and the indent go). */
    private fun stripLiteralMarker(s: String): String = LITERAL_MARKER.replaceFirst(s, "")

    fun engineLines(markdown: String, style: EditorStyle): List<OracleLine> {
        val text = DocBinder.build(NoteMarkdown.parse(markdown), style)
        // only the characters the engine itself marked are dropped: a `U+200B` / `U+FFFC` of the user text stays
        val marked = (text.getSpans(0, text.length, SentinelSpan::class.java).map { text.getSpanStart(it) } +
            text.getSpans(0, text.length, ObjectSpan::class.java).map { text.getSpanStart(it) }).toSet()
        return lines(text, strip = { i, _ -> i in marked }) { t, ps, end -> engineKind(t, ps, end) }
    }

    private fun engineKind(text: Spanned, ps: Int, end: Int): OracleKind {
        if (ps < text.length && text.getSpans(ps, ps + 1, ObjectSpan::class.java).any { text.getSpanStart(it) == ps }) return OracleKind.Object
        val span = text.getSpans(ps, minOf(end + 1, text.length), BlockSpan::class.java).firstOrNull { text.getSpanStart(it) == ps }
        return when (val k = span?.kind) {
            null, ParagraphKind.Body -> OracleKind.Body
            ParagraphKind.Title -> OracleKind.Title
            ParagraphKind.Heading -> OracleKind.Heading
            ParagraphKind.Subheading -> OracleKind.Subheading
            ParagraphKind.Quote -> OracleKind.Quote
            ParagraphKind.Mono -> OracleKind.Mono
            is ParagraphKind.ListItem -> when (k.type) {
                ListType.Bullet, ListType.Dash -> OracleKind.Bullet
                ListType.Number -> OracleKind.Number
                ListType.Check -> if (k.checked) OracleKind.CheckOn else OracleKind.CheckOff
            }
            else -> OracleKind.Body
        }
    }

    /** Lines of what an `AztecText` shows (its `Editable`). */
    fun aztecLines(text: Spanned): List<OracleLine> =
        lines(text, strip = { i, c -> c == '\u200B' || c == '\uFFFC' || c == '\uFEFF' }) { t, ps, end -> aztecKind(t, ps, end) }

    private fun aztecKind(text: Spanned, ps: Int, end: Int): OracleKind {
        val to = maxOf(end, ps + 1).coerceAtMost(text.length)
        fun <T> at(c: Class<T>): List<T> = text.getSpans(ps, to, c).filter { text.getSpanStart(it) <= ps && text.getSpanEnd(it) > ps }
        if (at(AztecHorizontalRuleSpan::class.java).isNotEmpty() || at(AztecImageSpan::class.java).isNotEmpty()) return OracleKind.Object
        at(AztecHeadingSpan::class.java).firstOrNull()?.let {
            return when (it.heading) {
                AztecHeadingSpan.Heading.H1 -> OracleKind.Title
                AztecHeadingSpan.Heading.H2 -> OracleKind.Heading
                else -> OracleKind.Subheading
            }
        }
        if (at(AztecListItemSpan::class.java).isNotEmpty()) {
            val checked = at(AztecListItemSpan::class.java).any { it.attributes.getValue(AztecListItemSpan.CHECKED) == "true" }
            return when {
                at(AztecTaskListSpan::class.java).isNotEmpty() -> if (checked) OracleKind.CheckOn else OracleKind.CheckOff
                at(AztecOrderedListSpan::class.java).isNotEmpty() -> OracleKind.Number
                at(AztecUnorderedListSpan::class.java).isNotEmpty() -> OracleKind.Bullet
                else -> OracleKind.Body
            }
        }
        if (at(AztecQuoteSpan::class.java).isNotEmpty()) return OracleKind.Quote
        if (at(AztecPreformatSpan::class.java).isNotEmpty()) return OracleKind.Mono
        return OracleKind.Body
    }

    /**
     * NBSP-only lines are empty; the characters [strip] picks are dropped (markers and object placeholders; Aztec's divider is a
     * `U+FEFF`). The engine strips by its own spans, Aztec by character.
     */
    private fun lines(text: Spanned, strip: (Int, Char) -> Boolean, kind: (Spanned, Int, Int) -> OracleKind): List<OracleLine> {
        val s = text.toString()
        val out = ArrayList<OracleLine>()
        var ps = 0
        while (true) {
            val pe = paragraphEnd(s, ps)
            var t = (ps until pe).filterNot { strip(it, s[it]) }.joinToString("") { s[it].toString() }
            if (t.isNotEmpty() && t.all { it == '\u00A0' }) t = ""
            out += OracleLine(t, kind(text, ps, pe))
            if (pe >= s.length) break
            ps = pe + 1
        }
        return out
    }

    /**
     * [aztec] loads one stored Markdown into a live `AztecText` (`markdownToHtml` + `fromHtml`) and returns what it shows.
     * Every document whose lines differ is in the result; [Divergence.rule] tells whether an allowed divergence explains it.
     */
    fun compare(markdowns: List<String>, style: EditorStyle, aztec: (String) -> List<OracleLine>): List<Divergence> =
        markdowns.filterNot(::hasTable).mapNotNull { md ->
            val a = aztec(md)
            val e = engineLines(md, style)
            if (a == e) return@mapNotNull null
            Divergence(md, a, e, explain(md, a, e))
        }

    /** The Aztec path renders a table in `TableBlockEditor`, not through `markdownToHtml`: such notes are not compared. */
    fun hasTable(markdown: String) = NoteMarkdown.parse(markdown).blocks.any { it is NoteBlock.Table }

    /** The one rule that explains every differing line pair, or `null`. */
    private fun explain(md: String, a: List<OracleLine>, e: List<OracleLine>): String? {
        val firstDiff = e.indices.firstOrNull { it >= a.size || a[it] != e[it] } ?: a.size.takeIf { it < e.size }
        if (firstDiff != null && firstDiff < a.size && a[firstDiff].kind == OracleKind.Mono && a[firstDiff].text.startsWith("````")) return LONG_FENCE_RULE
        val phantom = a.size > e.size && a.size - e.size <= 2 && !md.endsWith("\n") && a.drop(e.size).all { it == EMPTY }
        if (a.size != e.size && !phantom) return null
        val rules = e.indices.filter { a[it] != e[it] }.map { i -> allowed.firstOrNull { it.explains(a[i], e[i]) }?.name ?: return null }
        return (if (phantom) listOf(END_OF_BUFFER) else emptyList<String>()).plus(rules).distinct().joinToString(" + ")
    }

    /**
     * Spec §8: a line of 4+ backticks inside legacy code. Aztec holds it and what follows up to the next ``` as Mono lines and
     * ends the block at that ```; the dialect follows CommonMark (the fence closes with a run at least as long as the opening
     * one), so the rest is read as ordinary text. Only the tail is explained: the lines before the first differing one match,
     * and that line is an Aztec Mono line of 4+ backticks.
     */
    const val LONG_FENCE_RULE = "fence of 4+ backticks in legacy code"

    private val EMPTY = OracleLine("", OracleKind.Body)

    /**
     * Spec §8: after `fromHtml` Aztec shows 1-2 empty lines past the end of a
     * plain-text note (its end-of-buffer marker; the caret cannot reach them, memory `aztec_heading_span_internals` §3).
     * Explained as a document-level divergence, as narrow as the runs show: the stored Markdown does not end with a line break, the engine's lines are a prefix and at most 2 empty Body lines follow. 
     */
    const val END_OF_BUFFER = "aztec end-of-buffer lines"
}
