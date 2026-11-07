package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.NoCopySpan
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.ParagraphStyle
import android.text.style.SuggestionSpan
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BLOCK_SPAN_FLAGS
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.CodeMarkSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.OBJECT_CHAR
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SENTINEL_CHAR
import app.tessera.ui.editor.engine.spans.SentinelSpan

/** Extension point of [Normalizer] (Chunk 4a: typing attributes, auto-replace). Runs after the invariants are restored. */
fun interface NormalizeStep {
    /** [start]..[end] is the repaired window: the paragraphs touched by the edit. */
    fun apply(editable: Editable, start: Int, end: Int)
}

/**
 * Restores the invariants of spec §6.2 on the paragraphs touched by an edit. Plain functions over an `Editable`:
 * the host (`TesseraEditText`, Chunk 4b) calls [originKind] from `beforeTextChanged` and [normalize] from
 * `afterTextChanged` under its own recursion guard — this class edits the text itself (deletes `U+FFFC`, splits an
 * object paragraph, adds/removes the sentinel).
 *
 * 1. a paragraph has at most one [BlockSpan], exactly on its bounds;
 * 3. nothing but [BlockSpan] / `MarkSpan` / [ObjectSpan] / [SentinelSpan] formats the text: foreign `CharacterStyle` /
 *    `ParagraphStyle` go, except `SuggestionSpan`, `SuggestionRangeSpan` and spans flagged `SPAN_COMPOSING`;
 * 5. `U+FFFC` lives only in its own paragraph under an [ObjectSpan]; `U+200B` only as a [SentinelSpan] of an empty
 *    last non-Body paragraph.
 *
 * New paragraphs (a paste with line breaks, a split) take the kind of the paragraph where the edit started.
 */
class Normalizer(var style: EditorStyle, private val steps: List<NormalizeStep> = emptyList()) {

    /** Kind of the paragraph at [index]. Call before the edit is applied. */
    fun originKind(text: Spanned, index: Int): ParagraphKind {
        val ps = paragraphStart(text, index)
        if (ps < text.length && text.getSpans(ps, ps + 1, ObjectSpan::class.java).any { text.getSpanStart(it) == ps }) return ParagraphKind.Body
        return blockSpanAt(text, ps)?.kind ?: ParagraphKind.Body
    }

    /**
     * [changeStart]..[changeEnd] is the edited range in the text after the edit (`onTextChanged`: `start`,
     * `start + count`). [originKind] comes from [originKind] before the edit; without it the kind is guessed
     * from the spans around [changeStart].
     */
    fun normalize(editable: Editable, changeStart: Int, changeEnd: Int, originKind: ParagraphKind? = null) {
        var cs = changeStart.coerceIn(0, editable.length)
        var ce = changeEnd.coerceIn(cs, editable.length)
        val origin = originKind ?: guessOrigin(editable, cs)

        val window = affectedRange(editable, cs, ce)
        val ws = window.start
        var we = window.end

        removeForeignSpans(editable, ws, we)

        // rule 11 of spec §4: a carriage return is a space in the document, so it is one on the screen
        for (i in ws until minOf(we, editable.length)) if (editable[i] == '\r') editable.replace(i, i + 1, " ")

        // U+FFFC without an ObjectSpan goes; an ObjectSpan that no longer wraps exactly one U+FFFC goes.
        for (o in editable.getSpans(ws, we, ObjectSpan::class.java)) {
            val s = editable.getSpanStart(o)
            if (editable.getSpanEnd(o) - s != 1 || editable[s] != OBJECT_CHAR) editable.removeSpan(o)
        }
        var i = we - 1
        while (i >= ws) {
            if (editable[i] == OBJECT_CHAR && objectAt(editable, i) == null) {
                editable.delete(i, i + 1)
                we--
                if (ce > i) ce--
                if (cs > i) cs--
            }
            i--
        }

        // An object shares its paragraph with typed text: split it, the object stays alone.
        var ps = ws
        while (ps < we && ps < editable.length) {
            val pe = paragraphEnd(editable, ps)
            val at = (ps until pe).firstOrNull { objectAt(editable, it) != null }
            if (at != null && pe - ps > 1) {
                if (at + 1 < pe) { editable.insert(at + 1, "\n"); we++; ce++ }
                if (at > ps) { editable.insert(at, "\n"); we++; ce++ }
            }
            ps = paragraphEnd(editable, ps) + 1
        }

        // A sentinel is valid only as the whole of the last paragraph.
        for (sn in editable.getSpans(ws, we, SentinelSpan::class.java)) {
            val s = editable.getSpanStart(sn)
            val valid = s >= 0 && editable.getSpanEnd(sn) == s + 1 && s + 1 == editable.length &&
                editable[s] == SENTINEL_CHAR && paragraphStart(editable, s) == s
            if (valid) continue
            editable.removeSpan(sn)
            if (s >= 0 && s < editable.length && editable[s] == SENTINEL_CHAR) { editable.delete(s, s + 1); we--; if (ce > s) ce-- }
        }

        // Block spans, paragraph by paragraph.
        ps = ws
        while (ps <= editable.length && ps <= we) {
            val pe = paragraphEnd(editable, ps)
            fixParagraph(editable, ps, pe, derivedKind = if (ps >= cs && ps <= ce) derived(origin) else ParagraphKind.Body)
            if (pe >= editable.length) break
            ps = pe + 1
        }

        we = minOf(we, editable.length)
        we += separateTables(editable, ws, we)
        we = minOf(we, editable.length)
        // rule 1 of spec §4: marks of one kind that overlap or touch are one (a paste next to a mark of the same kind leaves two), because rule 3
        // below decides on the whole mark
        val marksHere = editable.getSpans(maxOf(ws - 1, 0), minOf(we + 1, editable.length), MarkSpan::class.java)
        for ((_, group) in marksHere.groupBy { it.mark }) {
            val sorted = group.filter { editable.getSpanStart(it) >= 0 }.sortedBy { editable.getSpanStart(it) }
            var cur = sorted.firstOrNull() ?: continue
            for (next in sorted.drop(1)) {
                val ce = editable.getSpanEnd(cur)
                if (editable.getSpanStart(next) <= ce) {
                    editable.setSpan(cur, editable.getSpanStart(cur), maxOf(ce, editable.getSpanEnd(next)), editable.getSpanFlags(cur))
                    editable.removeSpan(next)
                } else {
                    cur = next
                }
            }
        }
        // rule 3 of spec §4: no other mark lies over a code (a paste inside an underlined text brought one along with the code)
        for (m in editable.getSpans(maxOf(ws - 1, 0), minOf(we + 1, editable.length), MarkSpan::class.java)) {
            if (m is CodeMarkSpan || editable.getSpanStart(m) < 0) continue
            MarkOps.clipAtCode(editable, m, editable.getSpanStart(m), editable.getSpanEnd(m))
        }
        // rule 12: a mark boundary never splits a surrogate pair (a half of a pair went, or a selection ended inside one)
        for (m in editable.getSpans(maxOf(ws - 1, 0), minOf(we + 1, editable.length), MarkSpan::class.java)) {
            val s = editable.getSpanStart(m)
            val en = editable.getSpanEnd(m)
            val s2 = pairStart(editable, s)
            val e2 = pairStart(editable, en)
            if (s2 == s && e2 == en) continue
            if (s2 < e2) editable.setSpan(m, s2, e2, editable.getSpanFlags(m)) else editable.removeSpan(m)
        }
        refreshNeighbours(editable, ws, we)
        steps.forEach { it.apply(editable, ws, we) }
    }

    private fun derived(origin: ParagraphKind): ParagraphKind =
        if (origin is ParagraphKind.ListItem && origin.type == ListType.Check) origin.copy(checked = false) else origin

    private fun guessOrigin(text: Spanned, index: Int): ParagraphKind {
        val ps = paragraphStart(text, index)
        return blockSpanAt(text, ps)?.kind
            ?: text.getSpans(ps, minOf(paragraphEnd(text, ps) + 1, text.length), BlockSpan::class.java)
                .minByOrNull { text.getSpanStart(it) }?.kind
            ?: ParagraphKind.Body
    }

    private fun fixParagraph(e: Editable, ps: Int, pe: Int, derivedKind: ParagraphKind) {
        val end = minOf(pe + 1, e.length)
        val (alive, collapsed) = e.getSpans(ps, end, BlockSpan::class.java).partition { e.getSpanEnd(it) > e.getSpanStart(it) }
        if (collapsed.isNotEmpty()) {
            // a deletion left it at nothing: it is no paragraph's. The layout measured the paragraph with it (a collapsed list item or quote still
            // adds its margin to the line width, which broke a word onto a second line), and taking it off does not remeasure: poke the paragraph
            collapsed.forEach { e.removeSpan(it) }
            relayout(e, ps, end)
        }
        val here = alive
        val homes = here.filter { e.getSpanStart(it) == ps }
        val strays = here.filter { e.getSpanStart(it) > ps }
        val isObject = pe - ps == 1 && objectAt(e, ps) != null
        val last = pe >= e.length
        val sentinelOnly = last && pe - ps == 1 && e[ps] == SENTINEL_CHAR && e.getSpans(ps, pe, SentinelSpan::class.java).isNotEmpty()

        val home = if (isObject) null else homes.firstOrNull()
        val kind = if (isObject) ParagraphKind.Body else home?.kind ?: derivedKind
        val keep: BlockSpan? = when {
            kind == ParagraphKind.Body -> null
            home != null -> home
            else -> strays.firstOrNull { it.kind == kind } ?: BlockSpan.create(kind, style)
        }
        (homes + strays).filter { it !== keep }.forEach { e.removeSpan(it) }

        if (sentinelOnly && keep == null) { // the paragraph became Body: the sentinel is no longer needed
            e.getSpans(ps, pe, SentinelSpan::class.java).forEach { e.removeSpan(it) }
            e.delete(ps, pe)
            return
        }
        if (keep == null) return
        if (kind == ParagraphKind.Mono) MarkOps.removeAll(e, ps, pe) // a mono line has no marks (spec §4 rule 4), whatever way it became mono
        if (pe - ps == 0 && last) { // an empty last non-Body paragraph cannot hold a span: sentinel
            e.removeSpan(keep)
            e.insert(ps, SENTINEL_CHAR.toString())
            e.setSpan(SentinelSpan(), ps, ps + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            e.setSpan(keep, ps, ps + 1, BLOCK_SPAN_FLAGS)
            // the insertion at the end of the text stretched the span of the previous paragraph over the sentinel
            for (prev in e.getSpans(0, ps, BlockSpan::class.java)) {
                if (prev !== keep && e.getSpanEnd(prev) > ps) e.setSpan(prev, e.getSpanStart(prev), ps, BLOCK_SPAN_FLAGS)
            }
            return
        }
        if (e.getSpanStart(keep) != ps || e.getSpanEnd(keep) != end || e.getSpanFlags(keep) != BLOCK_SPAN_FLAGS) {
            e.setSpan(keep, ps, end, BLOCK_SPAN_FLAGS)
        }
    }

    /**
     * Rule 9 of spec §4 on the screen: two tables in a row would read back as one, so an empty Body paragraph goes between them (a paste,
     * or the deletion of the empty paragraph that held them apart). Returns how many characters were inserted.
     */
    private fun separateTables(e: Editable, ws: Int, we: Int): Int {
        var added = 0
        var ps = if (ws > 0) paragraphStart(e, ws - 1) else 0
        while (ps < e.length) {
            val pe = paragraphEnd(e, ps)
            if (pe + 1 < e.length && isTable(e, ps) && isTable(e, pe + 1)) {
                e.insert(pe + 1, "\n")
                added++
            }
            if (ps > we + added) break
            ps = pe + 1
        }
        return added
    }

    private fun isTable(e: Spanned, ps: Int) = isObjectParagraph(e, ps) && objectAt(e, ps)?.block is NoteBlock.Table

    private fun objectAt(text: Spanned, i: Int): ObjectSpan? =
        text.getSpans(i, i + 1, ObjectSpan::class.java).firstOrNull { text.getSpanStart(it) == i && text.getSpanEnd(it) == i + 1 }

    private fun removeForeignSpans(e: Editable, from: Int, to: Int) {
        for (s in e.getSpans(from, to, Any::class.java)) if (isForeignFormatting(s, e.getSpanFlags(s))) e.removeSpan(s)
    }

    /** A paragraph-aligned window of the text: [start] is the start of its first paragraph, [end] the end after the line break of its last. */
    data class Window(val start: Int, val end: Int)

    companion object {
        /** Makes the `DynamicLayout` measure [from]..[to] again: an `UpdateLayout` marker span is set over it and taken off at once. */
        internal fun relayout(e: Editable, from: Int, to: Int) {
            if (e.isEmpty()) return
            val a = from.coerceIn(0, e.length - 1)
            val marker = RelayoutMarker()
            e.setSpan(marker, a, to.coerceIn(a + 1, e.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            e.removeSpan(marker)
        }

        private class RelayoutMarker : android.text.style.MetricAffectingSpan() {
            override fun updateMeasureState(paint: android.text.TextPaint) = Unit
            override fun updateDrawState(tp: android.text.TextPaint) = Unit
        }

        /**
         * The paragraphs an edit of [changedStart]..[changedEnd] touches, widened to the block spans that reach into it.
         * `EditRules` (4a) uses it after a command that changes a kind, a level or a list type.
         */
        fun affectedRange(text: Spanned, changedStart: Int, changedEnd: Int): Window {
            val cs = changedStart.coerceIn(0, text.length)
            val ce = changedEnd.coerceIn(cs, text.length)
            var ws = paragraphStart(text, cs)
            var we = minOf(paragraphEnd(text, ce) + 1, text.length)
            for (b in text.getSpans(ws, we, BlockSpan::class.java)) {
                ws = minOf(ws, paragraphStart(text, text.getSpanStart(b)))
                we = maxOf(we, text.getSpanEnd(b))
            }
            return Window(ws, we)
        }

        /**
         * Last step after an edit or a command on [start]..[end]: what is drawn or measured from the neighbours is refreshed.
         * A mono paragraph whose place in its series changed (first / last) is set again, so the layout re-measures its padding
         * and the corners are redrawn; the numbers of the list runs around the window are renumbered.
         */
        fun refreshNeighbours(text: Spannable, start: Int, end: Int) {
            val from = if (start > 0) paragraphStart(text, start - 1) else 0
            val to = if (end < text.length) minOf(paragraphEnd(text, end) + 1, text.length) else text.length
            for (span in text.getSpans(from, to, Any::class.java)) {
                // what a paragraph measures from its neighbours (gaps, series edges): set again when a neighbour changed it, so the layout re-measures
                val now = when (span) {
                    is BlockSpan -> if (span.baked != null && span.baked != span.spacing(text)) span else null
                    is ObjectSpan -> if (span.baked != null && span.baked != span.spacing(text)) span else null
                    else -> null
                } ?: continue
                text.setSpan(now, text.getSpanStart(now), text.getSpanEnd(now), text.getSpanFlags(now))
            }
            Numbering.renumber(text, from, to)
        }

        /** Formatting not made by the engine and not kept by the framework / IME (spec §6.2, rule 4). */
        internal fun isForeignFormatting(span: Any, flags: Int): Boolean {
            if (span !is CharacterStyle && span !is ParagraphStyle) return false
            if (DocBinder.isEngineSpan(span)) return false
            if (span is NoCopySpan || span is SpanWatcher || span is TextWatcher) return false
            if (span is SuggestionSpan || span.javaClass.name == "android.text.style.SuggestionRangeSpan") return false
            return flags and Spanned.SPAN_COMPOSING == 0
        }
    }
}
