package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.Spanned
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.normalize
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BLOCK_SPAN_FLAGS
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.OBJECT_CHAR
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SENTINEL_CHAR
import app.tessera.ui.editor.engine.spans.SentinelSpan
import app.tessera.ui.editor.engine.spans.markSpanOf

/**
 * Start of the grapheme cluster that ends at [c] (a flag, a skin tone, a ZWJ sequence and a keycap go whole), not before [cs]. Only the 256
 * characters before [c] and 8 after it are looked at: a Backspace in a paragraph of 10 000 characters must not copy the paragraph and
 * break it into clusters every time (a cluster longer than 256 characters is a stack of combining marks nobody reads).
 */
internal fun graphemeStart(e: CharSequence, cs: Int, c: Int): Int {
    var from = maxOf(cs, c - 256)
    if (from > cs && e[from].isLowSurrogate()) from--
    var to = c
    while (to < e.length && to < c + 8 && e[to] != '\n') to++
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(e.subSequence(from, to).toString())
    val p = it.preceding(c - from)
    return if (p == android.icu.text.BreakIterator.DONE) c - 1 else maxOf(from + p, cs)
}

/** Spec §4 rule 12: a boundary inside a surrogate pair moves to the start of the pair. */
internal fun pairStart(t: CharSequence, i: Int): Int =
    if (i in 1 until t.length && t[i - 1].isHighSurrogate() && t[i].isLowSurrogate()) i - 1 else i

/** Selection after a command: a caret when [end] == [start]. */
data class Sel(val start: Int, val end: Int = start)

internal const val MAX_LEVEL = 4

/** Start of the content of the paragraph at [ps]: after its sentinel (spec §6.1), which counts as the paragraph start. */
internal fun contentStart(t: Spanned, ps: Int): Int =
    if (ps < t.length && t[ps] == SENTINEL_CHAR && t.getSpans(ps, ps + 1, SentinelSpan::class.java).isNotEmpty()) ps + 1 else ps

internal fun isObjectParagraph(t: Spanned, ps: Int): Boolean =
    ps < t.length && paragraphEnd(t, ps) - ps == 1 && t[ps] == OBJECT_CHAR &&
        t.getSpans(ps, ps + 1, ObjectSpan::class.java).any { t.getSpanStart(it) == ps }

/** Kind of the paragraph at [ps]; an object paragraph counts as Body. */
internal fun kindOf(t: Spanned, ps: Int): ParagraphKind {
    if (ps >= t.length || isObjectParagraph(t, ps)) return ParagraphKind.Body
    return blockSpanAt(t, ps)?.kind ?: ParagraphKind.Body
}

/** Starts of the paragraphs a selection touches; a selection that ends right at a paragraph start leaves that paragraph out. */
internal fun touchedParagraphs(t: CharSequence, s: Int, e: Int): List<Int> {
    val first = paragraphStart(t, s)
    val last = paragraphStart(t, if (e > s) e - 1 else e)
    val out = ArrayList<Int>()
    var ps = first
    while (ps <= last) {
        out += ps
        val pe = paragraphEnd(t, ps)
        if (pe >= t.length) break
        ps = pe + 1
    }
    return out
}

private fun unchecked(k: ParagraphKind): ParagraphKind = if (k is ParagraphKind.ListItem && k.checked) k.copy(checked = false) else k

/** Same button state: list items of one type are one kind whatever their level and mark. */
internal fun sameFamily(a: ParagraphKind, b: ParagraphKind): Boolean =
    a == b || (a is ParagraphKind.ListItem && b is ParagraphKind.ListItem && a.type == b.type)

/**
 * The commands of spec §6.3 (1), §6.4 and §6.6 as plain operations over an `Editable`: Enter, Backspace (and the deletion of a
 * selection), levels, paragraph kind, inline marks over a selection and the divider. No View, no IME, no history.
 *
 * Each command edits the text itself and leaves the editable valid: the block spans are set explicitly, then
 * [Normalizer.normalize] repairs what the edit stretched and refreshes the neighbours (the numbers of the list runs, the edges
 * of the mono series) — a live layout re-measures only the changed range. The host runs a command under the same recursion guard
 * as the normalizer, because the edits fire its `TextWatcher`, and takes the returned [Sel] as the new selection. Objects created
 * here get [objectWidthPx]; the host also calls `DocBinder.setObjectWidth` after a command that may have added one.
 */
class EditRules(style: EditorStyle) {
    private val normalizer = Normalizer(style)
    var style: EditorStyle = style
        set(value) {
            field = value
            normalizer.style = value
        }
    var objectWidthPx: Int = 0

    // ---------------------------------------------------------------- Enter

    fun enter(e: Editable, selStart: Int, selEnd: Int): Sel {
        val c = if (selEnd > selStart) deleteRange(e, selStart, selEnd) else selStart
        val ps = paragraphStart(e, c)
        val pe = paragraphEnd(e, ps)
        if (isObjectParagraph(e, ps)) { // an object is atomic: the break goes before or after it, never into it (invariant 5)
            val at = if (c <= ps) ps else pe
            e.insert(at, "\n")
            finish(e, at, at + 1)
            return Sel(at + 1)
        }
        val kind = kindOf(e, ps)
        val cs = contentStart(e, ps)
        if (cs == pe) { // an empty paragraph (a sentinel paragraph is one)
            val exit = when {
                kind is ParagraphKind.ListItem -> if (kind.level > 0) kind.copy(level = kind.level - 1, checked = false) else ParagraphKind.Body
                kind == ParagraphKind.Quote -> ParagraphKind.Body
                else -> null
            }
            if (exit != null) {
                applyKind(e, ps, exit)
                finish(e, ps, ps)
                return Sel(contentStart(e, ps))
            }
            return Sel(split(e, pe, kind, if (kind == ParagraphKind.Mono) ParagraphKind.Mono else ParagraphKind.Body))
        }
        val fresh = unchecked(kind)
        return Sel(
            when {
                c <= cs -> split(e, ps, fresh, kind) // an empty line above, the text stays as it was
                c >= pe -> split(e, pe, kind, afterEnter(kind))
                else -> split(e, c, kind, fresh)
            },
        )
    }

    private fun afterEnter(kind: ParagraphKind): ParagraphKind = when (kind) {
        is ParagraphKind.ListItem -> unchecked(kind)
        ParagraphKind.Quote, ParagraphKind.Mono -> kind
        else -> ParagraphKind.Body
    }

    /** Breaks the paragraph at [c]; returns the caret in the second part. */
    private fun split(e: Editable, c: Int, headKind: ParagraphKind, tailKind: ParagraphKind): Int {
        val ps = paragraphStart(e, c)
        e.insert(c, "\n")
        splitMarksAt(e, c)
        applyKind(e, c + 1, tailKind)
        applyKind(e, ps, headKind)
        finish(e, c, c + 1)
        return contentStart(e, paragraphEnd(e, ps) + 1)
    }

    /** The marks that cover the new line break end before it and start again after it. */
    private fun splitMarksAt(e: Editable, nl: Int) {
        for (m in e.getSpans(nl, nl + 1, MarkSpan::class.java)) {
            val s = e.getSpanStart(m)
            val en = e.getSpanEnd(m)
            if (s < nl && en > nl) {
                e.removeSpan(m)
                e.setSpan(markSpanOf(m.mark, style), s, nl, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (en > nl + 1) e.setSpan(markSpanOf(m.mark, style), nl + 1, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    // ------------------------------------------------------------ Backspace

    /**
     * [afterSelectedObject]: the caret stands right after an object that was selected whole a moment ago (the IME moved the caret to the
     * end of the selection before its Backspace): the object goes. A caret that got there by itself selects the object first.
     */
    fun backspace(e: Editable, selStart: Int, selEnd: Int, afterSelectedObject: Boolean = false): Sel {
        if (selEnd > selStart) {
            if (selEnd - selStart == 1 && isObjectParagraph(e, selStart)) return Sel(deleteObjectParagraph(e, selStart))
            return Sel(deleteRange(e, selStart, selEnd))
        }
        val c = selStart
        val ps = paragraphStart(e, c)
        val pe = paragraphEnd(e, ps)
        val cs = contentStart(e, ps)
        if (isObjectParagraph(e, ps)) return backspaceAtObject(e, c, ps, pe, afterSelectedObject)
        if (c > cs) { // a character of the paragraph
            val from = graphemeStart(e, cs, c)
            val kind = kindOf(e, ps)
            e.delete(from, c)
            // the span of a paragraph whose only character went is gone with it
            if (kind != ParagraphKind.Body && contentStart(e, ps) == paragraphEnd(e, ps)) applyKind(e, ps, kind)
            finish(e, from, from, kind)
            return Sel(afterSentinel(e, from))
        }
        // the start of the paragraph: 1. a kind other than Body goes first
        val kind = kindOf(e, ps)
        if (kind != ParagraphKind.Body) {
            applyKind(e, ps, if (kind is ParagraphKind.ListItem && kind.level > 0) kind.copy(level = kind.level - 1) else ParagraphKind.Body)
            finish(e, ps, ps)
            return Sel(contentStart(e, ps))
        }
        if (ps == 0) return Sel(c)
        // 2. a Body paragraph right under an object selects it, the next Backspace deletes it
        val prev = paragraphStart(e, ps - 1)
        if (isObjectParagraph(e, prev)) return Sel(prev, prev + 1)
        // 3. a Body paragraph joins the previous one and takes its kind
        val prevKind = kindOf(e, prev)
        e.delete(ps - 1, ps)
        applyKind(e, prev, prevKind)
        finish(e, ps - 1, ps - 1, prevKind)
        return Sel(afterSentinel(e, ps - 1))
    }

    /** The caret is just after an object (select it; delete it after a selection of it, see [backspace]) or just before it (the line above is not touched, an empty one goes). */
    private fun backspaceAtObject(e: Editable, c: Int, ps: Int, pe: Int, afterSelectedObject: Boolean): Sel {
        if (c > ps) return if (afterSelectedObject) Sel(deleteObjectParagraph(e, ps)) else Sel(ps, pe)
        if (ps == 0) return Sel(c)
        val prev = paragraphStart(e, ps - 1)
        if (isObjectParagraph(e, prev)) return Sel(prev, prev + 1)
        if (contentStart(e, prev) != ps - 1) return Sel(ps - 1)
        e.delete(prev, ps)
        finish(e, prev, prev)
        return Sel(prev)
    }

    /** The paragraph of an object goes whole, with its line break (the one before it for the last paragraph). */
    private fun deleteObjectParagraph(e: Editable, ps: Int): Int {
        if (ps + 1 < e.length) {
            val nextKind = kindOf(e, ps + 2)
            e.delete(ps, ps + 2)
            applyKind(e, ps, nextKind) // the span of the paragraph after it was inside the deleted range
            finish(e, ps, ps, nextKind)
            return ps
        }
        if (ps == 0) {
            e.delete(0, 1)
            finish(e, 0, 0)
            return 0
        }
        val prevPs = paragraphStart(e, ps - 1)
        val prevKind = kindOf(e, prevPs)
        e.delete(ps - 1, ps + 1)
        applyKind(e, prevPs, prevKind)
        finish(e, ps - 1, ps - 1, prevKind)
        return afterSentinel(e, ps - 1)
    }

    /** Deletes [s]..[t]; the paragraph that is left takes the kind of the first one. Returns the caret. */
    private fun deleteRange(e: Editable, s: Int, t: Int): Int {
        val ps = paragraphStart(e, s)
        val first = kindOf(e, ps)
        e.delete(s, t)
        applyKind(e, ps, first)
        finish(e, s, s, first)
        return afterSentinel(e, s)
    }

    // ------------------------------------------------------ levels and kinds

    /** Indent ([delta] 1) / outdent ([delta] -1) of the list items the selection touches, 0..[MAX_LEVEL]. */
    fun changeLevel(e: Editable, selStart: Int, selEnd: Int, delta: Int): Sel = keepSelection(e, selStart, selEnd) {
        var changed = false
        for (ps in touchedParagraphs(e, selStart, selEnd).asReversed()) {
            val k = kindOf(e, ps) as? ParagraphKind.ListItem ?: continue
            val level = (k.level + delta).coerceIn(0, MAX_LEVEL)
            if (level == k.level) continue
            applyKind(e, ps, k.copy(level = level))
            changed = true
        }
        if (changed) finish(e, selStart, selEnd)
    }

    /**
     * The kind button: every touched paragraph gets [target]; when they all are of that kind already, they become Body.
     * Objects are skipped. Leaving a list resets the level; a list item changing its list type keeps it.
     */
    fun toggleKind(e: Editable, selStart: Int, selEnd: Int, target: ParagraphKind): Sel = keepSelection(e, selStart, selEnd) {
        val starts = touchedParagraphs(e, selStart, selEnd).filter { !isObjectParagraph(e, it) }
        if (starts.isEmpty()) return@keepSelection
        val all = starts.all { sameFamily(kindOf(e, it), target) }
        for (ps in starts.asReversed()) {
            val cur = kindOf(e, ps)
            val next = when {
                all -> ParagraphKind.Body
                sameFamily(cur, target) -> cur
                target is ParagraphKind.ListItem -> ParagraphKind.ListItem(target.type, (cur as? ParagraphKind.ListItem)?.level ?: 0)
                else -> target
            }
            applyKind(e, ps, next)
        }
        finish(e, selStart, selEnd)
    }

    /**
     * The mark button over a selection (spec §6.6): the whole fragment is marked already → unmark, otherwise mark all of it.
     * Line breaks, objects, sentinels and mono lines are skipped. Without a selection the button is a typing attribute
     * ([TypingAttributes.toggle]), not a command: nothing happens here.
     */
    fun toggleMark(e: Editable, selStart: Int, selEnd: Int, mark: Mark): Sel {
        val a = pairStart(e, selStart)
        val b = pairStart(e, selEnd)
        if (b <= a) return Sel(selStart, selEnd)
        // a mark never lies over the part of a code that the selection only touches (spec §4, rule 3), so those characters are not part of the toggle
        val ranges = MarkOps.eligibleRanges(e, a, b).flatMap { MarkOps.effectiveRanges(e, it.first, it.last + 1, mark) }
        if (ranges.isEmpty()) return Sel(selStart, selEnd)
        val all = ranges.all { MarkOps.covers(e, it.first, it.last + 1, mark) }
        for (r in ranges) if (all) MarkOps.remove(e, r.first, r.last + 1, mark, style) else MarkOps.add(e, r.first, r.last + 1, mark, style)
        return Sel(selStart, selEnd)
    }

    /** The check box of the list item whose paragraph starts at [ps] (a tap, TalkBack). False when it is not a check item. */
    fun toggleChecked(e: Editable, ps: Int): Boolean {
        val k = kindOf(e, ps) as? ParagraphKind.ListItem ?: return false
        if (k.type != ListType.Check) return false
        applyKind(e, ps, k.copy(checked = !k.checked))
        finish(e, ps, ps)
        return true
    }

    // --------------------------------------------------------------- Paste

    /**
     * A fragment copied inside Tessera (spec §6.8) over [selStart]..[selEnd]. Its first paragraph joins the paragraph of the caret, which
     * keeps its kind — except an empty Body paragraph, which takes the kind of the fragment's first one; the other paragraphs keep their
     * kinds, the last one is joined by the rest of the paragraph the caret was in. The marks of the pasted text are the fragment's own.
     */
    fun insertDoc(e: Editable, selStart: Int, selEnd: Int, doc: NoteDoc): Sel {
        val blocks = normalize(doc).blocks
        val c0 = if (selEnd > selStart) deleteRange(e, selStart, selEnd) else selStart
        val frag = DocBinder.build(NoteDoc(blocks), style)
        frag.getSpans(0, frag.length, SentinelSpan::class.java).firstOrNull()?.let { // an empty last item cannot be pasted: it is a Body line
            val i = frag.getSpanStart(it)
            frag.removeSpan(it)
            frag.getSpans(i, frag.length, BlockSpan::class.java).forEach { b -> frag.removeSpan(b) }
            frag.delete(i, i + 1)
        }
        val text = frag.toString()
        val ps0 = paragraphStart(e, c0)
        val c = if (isObjectParagraph(e, ps0)) (if (c0 <= ps0) ps0 else paragraphEnd(e, ps0)) else c0
        val targetKind = kindOf(e, ps0)
        val targetEmpty = contentStart(e, ps0) == paragraphEnd(e, ps0)
        e.insert(c, text)
        val end = c + text.length
        // paragraph starts of the fragment inside the editable; the first one is the caret's paragraph
        val starts = ArrayList<Int>()
        starts += ps0
        text.forEachIndexed { i, ch -> if (ch == '\n') starts += c + i + 1 }
        val kinds = blocks.map { (it as? NoteBlock.Text)?.kind ?: ParagraphKind.Body }
        for (i in starts.indices.reversed()) {
            val k = kinds.getOrElse(i) { ParagraphKind.Body }
            val kind = if (i == 0) (if (targetEmpty && targetKind == ParagraphKind.Body) k else targetKind) else k
            applyKind(e, if (i == 0) paragraphStart(e, ps0) else starts[i], kind)
        }
        for (o in frag.getSpans(0, frag.length, ObjectSpan::class.java)) {
            e.setSpan(ObjectSpan(o.block, style).also { it.widthPx = objectWidthPx }, c + frag.getSpanStart(o), c + frag.getSpanEnd(o), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (m in MarkOps.ALL) MarkOps.remove(e, c, end, m, style)
        for (m in frag.getSpans(0, frag.length, MarkSpan::class.java)) {
            for (r in MarkOps.eligibleRanges(e, c + frag.getSpanStart(m), c + frag.getSpanEnd(m))) MarkOps.add(e, r.first, r.last + 1, m.mark, style)
        }
        finish(e, c, end)
        return Sel(afterSentinel(e, minOf(end, e.length)))
    }

    // ------------------------------------------------------------- Divider

    /** A divider after the paragraph with the caret (spec §7); the caret goes to the paragraph after it (an empty Body at the end). */
    fun insertHorizontalRule(e: Editable, caret: Int): Sel {
        val ps = paragraphStart(e, caret)
        val pe = paragraphEnd(e, ps)
        val tail = if (pe >= e.length) "\n$OBJECT_CHAR\n" else "\n$OBJECT_CHAR"
        e.insert(pe, tail)
        e.setSpan(ObjectSpan(NoteBlock.Divider, style).also { it.widthPx = objectWidthPx }, pe + 1, pe + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        finish(e, pe, pe + tail.length)
        return Sel(paragraphEnd(e, paragraphEnd(e, ps) + 1) + 1) // the paragraph after the divider (a sentinel before it may have gone)
    }

    // --------------------------------------------------------------- shared

    /** Replaces the first [deleteLen] characters of the paragraph at [ps] by nothing and makes it [kind] (auto-replace, spec §6.5). */
    internal fun replaceMarker(e: Editable, ps: Int, deleteLen: Int, kind: ParagraphKind): Int {
        e.delete(ps, ps + deleteLen)
        applyKind(e, ps, kind)
        finish(e, ps, ps)
        return contentStart(e, ps)
    }

    private fun finish(e: Editable, s: Int, t: Int, origin: ParagraphKind = ParagraphKind.Body) = normalizer.normalize(e, s, t, origin)

    /** A caret in an empty paragraph with a sentinel sits after it. */
    private fun afterSentinel(e: Editable, pos: Int): Int = maxOf(pos, contentStart(e, paragraphStart(e, pos)))

    /** Only the last paragraph can gain or lose a sentinel, so only the offsets in it move. */
    private fun keepSelection(e: Editable, s: Int, t: Int, op: () -> Unit): Sel {
        val before = e.length
        val lastPs = paragraphStart(e, before)
        op()
        val d = e.length - before
        val move = { p: Int -> if (p >= lastPs) maxOf(lastPs, p + d).coerceAtMost(e.length) else p }
        return Sel(move(s), move(t))
    }

    /**
     * Gives the paragraph at [ps] exactly [kind]: its old block spans go, a new one covers the paragraph with its line break.
     * An empty last paragraph that gets a kind holds a sentinel (§6.1); one that becomes Body loses it. An object paragraph is left alone.
     */
    private fun applyKind(e: Editable, ps: Int, kind: ParagraphKind) {
        if (isObjectParagraph(e, ps)) return
        val pe = paragraphEnd(e, ps)
        val end = minOf(pe + 1, e.length)
        if (kind == ParagraphKind.Mono) MarkOps.removeAll(e, ps, pe) // a mono line has no marks (spec §4, rule 4), whichever path made it mono
        if (end > ps) {
            var collapsed = false
            for (b in e.getSpans(ps, end, BlockSpan::class.java)) if (e.getSpanStart(b) >= ps) { collapsed = collapsed || e.getSpanEnd(b) <= e.getSpanStart(b); e.removeSpan(b) }
            if (collapsed) Normalizer.relayout(e, ps, end) // the layout measured the paragraph with the collapsed span's margin and taking it off does not measure again
        }
        val last = pe >= e.length
        if (kind == ParagraphKind.Body) {
            if (last && contentStart(e, ps) > ps) {
                e.getSpans(ps, pe, SentinelSpan::class.java).forEach { e.removeSpan(it) }
                e.delete(ps, pe)
            }
            return
        }
        val span = BlockSpan.create(kind, style) ?: return
        if (last && pe == ps) {
            e.insert(ps, SENTINEL_CHAR.toString())
            e.setSpan(SentinelSpan(), ps, ps + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            e.setSpan(span, ps, ps + 1, BLOCK_SPAN_FLAGS)
        } else {
            e.setSpan(span, ps, end, BLOCK_SPAN_FLAGS)
        }
    }
}
