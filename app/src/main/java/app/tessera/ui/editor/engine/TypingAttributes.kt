package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.CodeMarkSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.markSpanOf

/** Mark spans over ranges of text, shared by the mark button and the typing attributes. */
internal object MarkOps {
    val ALL = listOf(Mark.Bold, Mark.Italic, Mark.Underline, Mark.Strike, Mark.Code)

    fun marksAt(t: Spanned, i: Int): Set<Mark> = t.getSpans(i, i + 1, MarkSpan::class.java).map { it.mark }.toSet()

    /** Runs of characters in [a]..[b] that may carry a mark: not a line break, an object, a sentinel, nor a mono line. */
    fun eligibleRanges(t: Spanned, a: Int, b: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = a.coerceAtLeast(0)
        val to = b.coerceAtMost(t.length)
        while (i < to) {
            val ps = paragraphStart(t, i)
            val pe = paragraphEnd(t, i)
            if (kindOf(t, ps) != ParagraphKind.Mono && !isObjectParagraph(t, ps)) {
                val from = maxOf(i, contentStart(t, ps))
                val lim = minOf(pe, to)
                if (from < lim) out += from until lim
            }
            i = pe + 1
        }
        return out
    }

    private fun spansOf(t: Spanned, a: Int, b: Int, mark: Mark) =
        t.getSpans(a.coerceAtLeast(0), b.coerceAtMost(t.length), MarkSpan::class.java).filter { it.mark == mark }

    /** Is every character of [a]..[b] covered by [mark]. */
    fun covers(t: Spanned, a: Int, b: Int, mark: Mark): Boolean {
        var pos = a
        for (s in spansOf(t, a, b, mark).sortedBy { t.getSpanStart(it) }) {
            if (t.getSpanStart(s) > pos) return false
            pos = maxOf(pos, t.getSpanEnd(s))
        }
        return pos >= b
    }

    /** Marks [a]..[b], joined with the spans of the mark that overlap or touch it. */
    fun add(e: Editable, a: Int, b: Int, mark: Mark, style: EditorStyle) {
        var s = a
        var en = b
        for (sp in spansOf(e, a - 1, b + 1, mark)) {
            s = minOf(s, e.getSpanStart(sp))
            en = maxOf(en, e.getSpanEnd(sp))
            e.removeSpan(sp)
        }
        val span = markSpanOf(mark, style)
        e.setSpan(span, s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (mark != Mark.Code) clipAtCode(e, span, s, en)
    }

    /**
     * Rule 3 of spec §4 on the screen: a mark that starts or ends inside `Code` is cut at the code, one that lies inside it goes,
     * one that covers it whole stays. The same three cases as `normalizeInline`, so `read` returns what is shown.
     */
    private fun clipAtCode(e: Editable, span: Any, from: Int, to: Int) {
        var s = from
        var en = to
        for (c in e.getSpans(from, to, CodeMarkSpan::class.java).sortedBy { e.getSpanStart(it) }) {
            val cs = e.getSpanStart(c)
            val ce = e.getSpanEnd(c)
            if (s <= cs && ce <= en) continue
            if (cs <= s && s < ce) s = ce else if (cs < en && en <= ce) en = cs
            if (s >= en) break
        }
        when {
            s >= en -> e.removeSpan(span)
            s != from || en != to -> e.setSpan(span, s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * The characters of [a]..[b] that the [mark] button works on: for a mark other than `Code`, those the clipping would leave —
     * the part of a code that the selection only touches is out, a code the selection covers whole stays in.
     */
    fun effectiveRanges(t: Spanned, a: Int, b: Int, mark: Mark): List<IntRange> {
        if (a >= b) return emptyList()
        if (mark == Mark.Code) return listOf(a until b)
        var parts = listOf(a until b)
        for (c in t.getSpans(a, b, CodeMarkSpan::class.java)) {
            val cs = t.getSpanStart(c)
            val ce = t.getSpanEnd(c)
            if (a <= cs && ce <= b) continue
            parts = parts.flatMap { r ->
                if (ce <= r.first || cs > r.last) listOf(r) else listOfNotNull(
                    (r.first until minOf(cs, r.last + 1)).takeIf { !it.isEmpty() },
                    (maxOf(ce, r.first) until r.last + 1).takeIf { !it.isEmpty() },
                )
            }
        }
        return parts
    }

    /** Unmarks [a]..[b]: the spans of the mark are cut around it. */
    fun remove(e: Editable, a: Int, b: Int, mark: Mark, style: EditorStyle) {
        for (sp in spansOf(e, a, b, mark)) {
            val s = e.getSpanStart(sp)
            val en = e.getSpanEnd(sp)
            e.removeSpan(sp)
            if (s < a) e.setSpan(markSpanOf(mark, style), s, a, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (en > b) e.setSpan(markSpanOf(mark, style), b, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun removeAll(e: Spannable, a: Int, b: Int) {
        for (sp in e.getSpans(a, b, MarkSpan::class.java)) e.removeSpan(sp)
    }
}

/**
 * Marks for the text typed next (spec §6.6). The default is the marks of the character before the caret (the first character of
 * the paragraph at its start), except `Code`; a mark button without a selection toggles the mark in this set; the set is back to
 * the default when the caret moves.
 *
 * IME often replaces a whole composing region, which can sweep the marks off the characters that were there. So the host calls
 * [beforeChange] from `beforeTextChanged` (it snapshots the replaced text, its marks and the typing set) and [afterChange] from
 * `afterTextChanged`: the common prefix and suffix of the old and the new text get their old marks back, only the middle — the
 * really new characters — gets the typing set. Call [afterChange] before the normalizer (indices are the ones of the edit).
 * [onSelectionChanged] resets the toggled set; the host calls it when the selection moves.
 *
 * Contract: only for what the keyboard / IME / a paste edits. A rebuild of the text (`DocBinder.bind`, undo / redo, an external
 * update) must not go through [beforeChange] / [afterChange]: the binder removes the spans before it replaces the text, so the snapshot
 * would hold no marks and the result would wipe them. The host runs such edits under its recursion guard.
 */
class TypingAttributes(var style: EditorStyle) {
    private var toggled: Set<Mark>? = null
    private var toggledAt = -1
    private var pending: Snapshot? = null

    private class Snapshot(
        val start: Int,
        val oldText: String,
        val oldMarks: Map<Mark, BooleanArray>,
        val toggled: Set<Mark>?,
        val atStart: Set<Mark>,
        /** `Code` on the character right after the replaced range. */
        val codeAfter: Boolean,
    )

    /** Marks of the next typed character at [caret]. */
    fun active(text: Spanned, caret: Int): Set<Mark> = if (typesMarks(text, caret)) toggled ?: defaultAt(text, caret) else emptySet()

    /**
     * [after] is the character that follows the edited region (the caret for an insertion): `Code` is continued only inside a code
     * span, when it is on the character before the caret and on the one after the region.
     */
    fun defaultAt(text: Spanned, caret: Int, after: Int = caret): Set<Mark> {
        if (!typesMarks(text, caret)) return emptySet()
        val ps = paragraphStart(text, caret)
        val cs = contentStart(text, ps)
        val i = when {
            caret > cs -> caret - 1
            cs < paragraphEnd(text, ps) -> cs
            else -> return emptySet()
        }
        val marks = MarkOps.marksAt(text, i)
        val insideCode = Mark.Code in marks && caret > cs && after < text.length && Mark.Code in MarkOps.marksAt(text, after)
        return if (insideCode) marks else marks - Mark.Code
    }

    /** The mark button without a selection. Does nothing where nothing can be marked (a mono line, an object). */
    fun toggle(text: Spanned, caret: Int, mark: Mark): Set<Mark> {
        if (!typesMarks(text, caret)) return emptySet()
        val now = active(text, caret)
        toggled = if (mark in now) now - mark else now + mark
        toggledAt = caret
        return toggled!!
    }

    fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (toggled != null && (selStart != toggledAt || selEnd != toggledAt)) toggled = null
    }

    fun reset() {
        toggled = null
        pending = null
    }

    fun beforeChange(text: Spanned, start: Int, end: Int) {
        val old = text.subSequence(start, end).toString()
        val marks = HashMap<Mark, BooleanArray>()
        for (m in MarkOps.ALL) {
            val cover = BooleanArray(old.length)
            for (s in text.getSpans(start, end, MarkSpan::class.java)) {
                if (s.mark != m) continue
                for (i in maxOf(text.getSpanStart(s), start) until minOf(text.getSpanEnd(s), end)) cover[i - start] = true
            }
            marks[m] = cover
        }
        val codeAfter = end < text.length && Mark.Code in MarkOps.marksAt(text, end)
        val atStart = if (typesMarks(text, start)) defaultAt(text, start, end) else emptySet()
        pending = Snapshot(start, old, marks, if (typesMarks(text, start)) toggled else emptySet(), atStart, codeAfter)
    }

    /**
     * [newEnd] is the end of the edited range in the text after the edit (`start + count`). Returns the really new characters — the
     * middle between the kept prefix and suffix — as absolute offsets (empty when the IME only re-committed the same text).
     */
    fun afterChange(e: Editable, newEnd: Int): IntRange {
        val p = pending ?: return IntRange.EMPTY
        pending = null
        val start = p.start
        val end = newEnd.coerceAtMost(e.length)
        val n = end - start
        if (n <= 0) return IntRange.EMPTY
        val new = e.subSequence(start, end).toString()
        val old = p.oldText
        var pre = 0
        while (pre < minOf(old.length, n) && old[pre] == new[pre]) pre++
        var suf = 0
        while (suf < minOf(old.length, n) - pre && old[old.length - 1 - suf] == new[n - 1 - suf]) suf++
        val codeCover = p.oldMarks.getValue(Mark.Code)
        val codeNext = if (suf > 0) codeCover[old.length - suf] else p.codeAfter
        // the new part continues the marks of the old character it follows (the first replaced one when nothing was kept before it);
        // a pure insertion takes the default at the caret
        val from = if (pre > 0) pre - 1 else 0
        val typing = p.toggled ?: if (old.isNotEmpty()) {
            MarkOps.ALL.filter { p.oldMarks.getValue(it)[from] && (it != Mark.Code || codeNext) }.toSet()
        } else {
            p.atStart
        }
        val eligible = BooleanArray(n)
        for (r in MarkOps.eligibleRanges(e, start, end)) for (i in r) eligible[i - start] = true
        for (m in MarkOps.ALL) {
            val want = BooleanArray(n)
            val oldCover = p.oldMarks.getValue(m)
            for (i in 0 until pre) want[i] = oldCover[i] && eligible[i]
            for (j in 0 until suf) want[n - suf + j] = oldCover[old.length - suf + j] && eligible[n - suf + j]
            if (m in typing) for (i in pre until n - suf) want[i] = eligible[i]
            apply(e, start, m, want)
        }
        return start + pre until end - suf
    }

    /** Makes the coverage of [mark] on `start until start + want.size` equal to [want]; spans that already agree are not touched. */
    private fun apply(e: Editable, start: Int, mark: Mark, want: BooleanArray) {
        val have = BooleanArray(want.size)
        for (s in e.getSpans(start, start + want.size, MarkSpan::class.java)) {
            if (s.mark != mark) continue
            for (i in maxOf(e.getSpanStart(s), start) until minOf(e.getSpanEnd(s), start + want.size)) have[i - start] = true
        }
        var i = 0
        while (i < want.size) {
            if (want[i] == have[i]) { i++; continue }
            var j = i
            while (j < want.size && want[j] == want[i] && have[j] != want[j]) j++
            if (want[i]) MarkOps.add(e, start + i, start + j, mark, style) else MarkOps.remove(e, start + i, start + j, mark, style)
            i = j
        }
    }

    private fun typesMarks(text: Spanned, caret: Int): Boolean {
        val ps = paragraphStart(text, caret)
        return kindOf(text, ps) != ParagraphKind.Mono && !isObjectParagraph(text, ps)
    }
}
