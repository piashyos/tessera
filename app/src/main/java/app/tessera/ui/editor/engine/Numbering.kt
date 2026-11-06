package app.tessera.ui.editor.engine

import android.text.Spannable
import android.text.Spanned
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan

private const val LEVELS = 5

/** Counter per level, reset by a non-list paragraph, a shallower item or an item of another type on the same level (the rule of `NoteMarkdown.serialize`). */
internal class NumberCounter {
    private val counters = IntArray(LEVELS)
    private val types = arrayOfNulls<ListType>(LEVELS)

    fun reset() {
        counters.fill(0)
        types.fill(null)
    }

    fun next(kind: ParagraphKind.ListItem): Int {
        val level = kind.level.coerceIn(0, LEVELS - 1)
        for (l in level + 1 until LEVELS) { counters[l] = 0; types[l] = null }
        if (types[level] != kind.type) { counters[level] = 0; types[level] = kind.type }
        return ++counters[level]
    }
}

internal object Numbering {
    /**
     * Writes `ListItemSpan.number` of the list runs that touch [from]..[to]: one pass forward from the start of the run that
     * holds [from], on to the first non-list paragraph at or after [to]. A span whose number changed is set again, so the
     * layout invalidates it (a number far below an insertion is not in a changed block).
     */
    fun renumber(text: Spannable, from: Int, to: Int) {
        var start = paragraphStart(text, from)
        while (start > 0 && blockAt(text, paragraphStart(text, start - 1)) is ListItemSpan) start = paragraphStart(text, start - 1)
        val counter = NumberCounter()
        var ps = start
        while (ps <= text.length) {
            val pe = paragraphEnd(text, ps)
            val span = blockAt(text, ps)
            if (span is ListItemSpan) {
                val n = counter.next(span.kind)
                if (span.number != n) {
                    span.number = n
                    if (span.kind.type == ListType.Number) text.setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), text.getSpanFlags(span))
                }
            } else {
                if (ps >= to) break
                counter.reset()
            }
            if (pe >= text.length) break
            ps = pe + 1
        }
    }

    private fun blockAt(text: Spanned, ps: Int): BlockSpan? =
        if (ps >= text.length) null else text.getSpans(ps, minOf(paragraphEnd(text, ps) + 1, text.length), BlockSpan::class.java).firstOrNull { text.getSpanStart(it) == ps }
}
