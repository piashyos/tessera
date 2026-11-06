package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.normalize
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.SENTINEL_CHAR
import app.tessera.ui.editor.engine.spans.SentinelSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal const val CARET = '‸'
internal const val SEL_START = '«'
internal const val SEL_END = '»'

/**
 * A document written in Markdown with a caret marker [CARET] or a selection [SEL_START]..[SEL_END] in the text. `inSel` (`"3"` or
 * `"1-4"`, absolute offsets of the editable) replaces the markers where the caret sits next to an object, which has no text.
 */
internal class EditFixture(md: String, inSel: String = "", style: EditorStyle) {
    val e: Editable
    var selStart: Int
    var selEnd: Int

    init {
        val marks = markerPositions(md)
        e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(stripMarkers(md)), style))
        if (inSel.isNotEmpty()) {
            val p = inSel.split("-").map { it.toInt() }
            selStart = p.first(); selEnd = p.last()
        } else {
            selStart = afterSentinel(e, marks.first)
            selEnd = afterSentinel(e, marks.second)
        }
    }

    companion object {
        fun stripMarkers(md: String) = md.filter { it != CARET && it != SEL_START && it != SEL_END }

        /** Offsets of the caret or the selection in the text of [md] without its markers (a sentinel is not counted). */
        fun markerPositions(md: String): Pair<Int, Int> {
            val text = DocBinder.build(NoteMarkdown.parse(md), EngineTestStyle.create()).toString()
            val found = ArrayList<Pair<Char, Int>>()
            var removed = 0
            for ((i, ch) in text.withIndex()) if (ch == CARET || ch == SEL_START || ch == SEL_END) found += ch to (i - removed++)
            val caret = found.firstOrNull { it.first == CARET }?.second
            val a = found.firstOrNull { it.first == SEL_START }?.second ?: caret ?: 0
            val b = found.firstOrNull { it.first == SEL_END }?.second ?: caret ?: a
            return a to b
        }

        /** A caret in an empty paragraph with a sentinel sits after it. */
        fun afterSentinel(t: Spanned, pos: Int): Int = maxOf(pos, contentStart(t, paragraphStart(t, pos)))

        /** [pos] without the sentinels before it: comparable with the marker offsets. */
        fun visible(t: Spanned, pos: Int): Int =
            pos - (0 until pos).count { t[it] == SENTINEL_CHAR && t.getSpans(it, it + 1, SentinelSpan::class.java).isNotEmpty() }

        /** What every command must leave: invariant 1, text and numbers as a fresh build of what the editable holds. */
        fun assertSane(label: String, e: Editable, style: EditorStyle) {
            EngineAssertions.assertInvariant1(e, label)
            val fresh = DocBinder.build(DocBinder.read(e), style)
            assertEquals("$label: text as a fresh build", fresh.toString(), e.toString())
            // the marks on screen are the marks that `read` gives (whitespace at the edges of a mark is the one thing `read` trims)
            for (i in 0 until e.length) {
                if (e[i].isWhitespace()) continue
                assertEquals("$label: marks of the character at $i", marksOf(fresh, i), marksOf(e, i))
            }
            val live = e.getSpans(0, e.length, ListItemSpan::class.java).sortedBy { e.getSpanStart(it) }.map { it.number }
            val want = fresh.getSpans(0, fresh.length, ListItemSpan::class.java).sortedBy { fresh.getSpanStart(it) }.map { it.number }
            assertEquals("$label: numbers", want, live)
            assertTrue("$label: sentinel only as a whole last paragraph",
                e.getSpans(0, e.length, SentinelSpan::class.java).all { e.getSpanEnd(it) == e.length })
        }

        private fun marksOf(t: Spanned, i: Int) = t.getSpans(i, i + 1, MarkSpan::class.java).map { it.mark }.toSet()

        fun doc(md: String): NoteDoc = normalize(NoteMarkdown.parse(stripMarkers(md)))
    }
}
