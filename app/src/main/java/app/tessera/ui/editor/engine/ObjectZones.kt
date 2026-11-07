package app.tessera.ui.editor.engine

import android.graphics.Rect
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.ui.editor.engine.spans.ObjectSpan

/** An image of the text on screen: where it is in the view, and the paragraph it stands in (the id of its TalkBack node). */
internal class ObjectZone(val ps: Int, val rect: Rect)

/**
 * Where the object of the paragraph at [ps] is in the view (view coordinates: the layout, less the scroll, plus the padding), without the margins its
 * line carries around it; `null` without a layout or an object there. The one definition for the selection overlay, the taps and TalkBack.
 */
internal fun TesseraEditText.objectRect(ps: Int): Rect? {
    val l = layout ?: return null
    val t = text ?: return null
    val span = t.getSpans(ps, minOf(ps + 1, t.length), ObjectSpan::class.java).firstOrNull { t.getSpanStart(it) == ps } ?: return null
    val line = l.getLineForOffset(ps)
    val left = totalPaddingLeft - scrollX
    val box = span.imageBox()
    val w = box?.width ?: if (span.widthPx > 0) span.widthPx else (width - totalPaddingLeft - totalPaddingRight).coerceAtLeast(0)
    val top = totalPaddingTop - scrollY + l.getLineTop(line) + (span.baked?.gapTop ?: 0)
    val bottom = if (box != null) top + box.height else totalPaddingTop - scrollY + l.getLineBottom(line) - (span.baked?.gapBottom ?: 0)
    return Rect(left, top, left + w, maxOf(bottom, top))
}

/** The images on screen (and a little beyond it): the objects TalkBack announces. */
internal fun TesseraEditText.imageZones(): List<ObjectZone> {
    val t = text ?: return emptyList()
    if (layout == null || t.isEmpty()) return emptyList()
    val visible = Rect(0, 0, width, height)
    return t.getSpans(0, t.length, ObjectSpan::class.java)
        .filter { it.block is NoteBlock.Image }
        .sortedBy { t.getSpanStart(it) }
        .mapNotNull { span ->
            val ps = t.getSpanStart(span)
            objectRect(ps)?.takeIf { Rect.intersects(it, visible) }?.let { ObjectZone(ps, it) }
        }
}
