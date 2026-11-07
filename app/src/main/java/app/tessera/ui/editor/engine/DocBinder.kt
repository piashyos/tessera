package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.Inline
import app.tessera.core.text.notedoc.MarkRange
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.text.notedoc.normalize
import app.tessera.ui.editor.engine.spans.BLOCK_SPAN_FLAGS
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.CodeMarkSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.OBJECT_CHAR
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SENTINEL_CHAR
import app.tessera.ui.editor.engine.spans.SentinelSpan
import app.tessera.ui.editor.engine.spans.markSpanOf

/**
 * `NoteDoc` ⇄ `Editable` (spec §6.1). Paragraphs are joined by `\n`; a [BlockSpan] covers its paragraph with the
 * trailing `\n`; marks are `SPAN_EXCLUSIVE_EXCLUSIVE`; an object is one `U+FFFC` with an [ObjectSpan]; an empty last
 * non-Body paragraph holds a [SENTINEL_CHAR] marked by a [SentinelSpan].
 * `read(bind(doc)) == normalize(doc)`.
 */
object DocBinder {

    /** The document as a new text with spans. */
    fun build(doc: NoteDoc, style: EditorStyle): SpannableStringBuilder {
        val blocks = normalize(doc).blocks
        val sb = SpannableStringBuilder()
        // Spans go on after the whole text is built: a `SPAN_PARAGRAPH` span that ends at the end of the
        // text grows when the next paragraph is appended.
        val spans = ArrayList<Triple<Any, IntRange, Int>>()
        val numbers = NumberCounter()
        blocks.forEachIndexed { i, block ->
            val last = i == blocks.lastIndex
            val ps = sb.length
            var kind: ParagraphKind = ParagraphKind.Body
            if (block is NoteBlock.Text) {
                kind = block.kind
                val sentinel = last && block.content.text.isEmpty() && kind != ParagraphKind.Body
                sb.append(if (sentinel) SENTINEL_CHAR.toString() else block.content.text)
                if (sentinel) spans += Triple(SentinelSpan(), ps until ps + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                for (m in block.content.marks) {
                    spans += Triple(markSpanOf(m.mark, style), ps + m.start until ps + m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                sb.append(OBJECT_CHAR)
                spans += Triple(ObjectSpan(block, style), ps until ps + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (!last) sb.append('\n')
            if (kind !is ParagraphKind.ListItem) numbers.reset()
            BlockSpan.create(kind, style)?.let {
                if (it is ListItemSpan) it.number = numbers.next(it.kind)
                spans += Triple(it, ps until sb.length, BLOCK_SPAN_FLAGS)
            }
        }
        for ((span, range, flags) in spans) sb.setSpan(span, range.first, range.last + 1, flags)
        return sb
    }

    /** Replaces the whole content of [editable] (and the engine's spans on it) with [doc]. */
    fun bind(doc: NoteDoc, editable: Editable, style: EditorStyle) {
        removeEngineSpans(editable)
        editable.replace(0, editable.length, build(doc, style))
    }

    fun read(text: Spanned): NoteDoc {
        val s = text.toString()
        val blocks = ArrayList<NoteBlock>()
        var ps = 0
        while (true) {
            val pe = paragraphEnd(s, ps)
            blocks += readParagraph(text, s, ps, pe)
            if (pe >= s.length) break
            ps = pe + 1
        }
        return normalize(NoteDoc(blocks))
    }

    private fun readParagraph(text: Spanned, s: String, ps: Int, pe: Int): NoteBlock {
        if (pe - ps == 1 && s[ps] == OBJECT_CHAR) {
            text.getSpans(ps, pe, ObjectSpan::class.java).firstOrNull { text.getSpanStart(it) == ps }?.let { return it.block }
        }
        val kind = blockSpanAt(text, ps)?.kind ?: ParagraphKind.Body
        val sentinel = text.getSpans(ps, pe, SentinelSpan::class.java)
            .map { text.getSpanStart(it) }.firstOrNull { it in ps until pe && s[it] == SENTINEL_CHAR }
        val content = if (sentinel == null) s.substring(ps, pe) else s.substring(ps, sentinel) + s.substring(sentinel + 1, pe)
        val marks = text.getSpans(ps, pe, MarkSpan::class.java).mapNotNull {
            val a = maxOf(text.getSpanStart(it), ps) - ps
            val b = minOf(text.getSpanEnd(it), pe) - ps
            if (a < b) MarkRange(a, b, it.mark) else null
        }
        return NoteBlock.Text(kind, Inline(content, marks))
    }

    /** Rebuilds the spans with the colors/sizes of [style]; the text is not touched, so no edit is reported. */
    fun reapplyTheme(text: Spannable, style: EditorStyle) {
        for (s in text.getSpans(0, text.length, Any::class.java)) {
            val replacement: Any = when (s) {
                is BlockSpan -> s.restyled(style)
                is ObjectSpan -> s.restyled(style)
                is CodeMarkSpan -> CodeMarkSpan(style)
                else -> continue
            }
            val start = text.getSpanStart(s)
            val end = text.getSpanEnd(s)
            val flags = text.getSpanFlags(s)
            text.removeSpan(s)
            text.setSpan(replacement, start, end, flags)
        }
    }

    /** Gives every object the width of the text area (the host calls it when the width is known or changes, then relayouts). */
    fun setObjectWidth(text: Spanned, widthPx: Int) {
        text.getSpans(0, text.length, ObjectSpan::class.java).forEach { it.widthPx = widthPx }
    }

    internal fun isEngineSpan(o: Any) = o is BlockSpan || o is MarkSpan || o is ObjectSpan || o is SentinelSpan

    private fun removeEngineSpans(text: Spannable) {
        for (s in text.getSpans(0, text.length, Any::class.java)) if (isEngineSpan(s)) text.removeSpan(s)
    }
}

/**
 * The [BlockSpan] that starts at the paragraph start [ps] — the kind of that paragraph; `null` is Body. The one definition of "the span of a
 * paragraph". A span that a deletion collapsed to nothing is not a paragraph's (the layout reads the neighbours while the replace is still
 * going, and a collapsed span of the paragraph before made the item after it measure as the second of a series).
 */
internal fun blockSpanAt(text: Spanned, ps: Int): BlockSpan? =
    if (ps >= text.length) null else text.getSpans(ps, minOf(paragraphEnd(text, ps) + 1, text.length), BlockSpan::class.java)
        .firstOrNull { text.getSpanStart(it) == ps && text.getSpanEnd(it) > ps }

internal fun paragraphEnd(s: CharSequence, from: Int): Int {
    var i = from
    while (i < s.length && s[i] != '\n') i++
    return i
}

/** Start of the paragraph that contains [index] (an index just after a `\n` starts the next paragraph). */
internal fun paragraphStart(s: CharSequence, index: Int): Int {
    var i = index.coerceIn(0, s.length)
    while (i > 0 && s[i - 1] != '\n') i--
    return i
}
