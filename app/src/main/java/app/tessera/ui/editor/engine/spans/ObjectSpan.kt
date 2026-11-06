package app.tessera.ui.editor.engine.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.ui.editor.engine.EditorStyle

/** The only character of an object paragraph. */
const val OBJECT_CHAR = '￼'

/** Placeholder char of an empty last non-Body paragraph. Only one marked by a [SentinelSpan] is ours. */
const val SENTINEL_CHAR = '​'

/** Marks the [SENTINEL_CHAR] that the normalizer may delete (a `U+200B` of the user text is never touched). */
class SentinelSpan

/**
 * A [NoteBlock.Image] / [NoteBlock.Table] / [NoteBlock.Divider] on a single [OBJECT_CHAR] that fills its paragraph.
 * Holds the source block, so serializing writes it back untouched. Stage 1: divider = a line, image and table =
 * a captioned frame (D9, no icon).
 */
class ObjectSpan(val block: NoteBlock, val style: EditorStyle) : ReplacementSpan() {
    /** Width the host gives the object (the text area); until set, the placeholder is as wide as its caption. */
    @Volatile var widthPx: Int = 0

    private val label: String? = when (block) {
        is NoteBlock.Image -> style.imageLabel
        is NoteBlock.Table -> style.tableLabel
        else -> null
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getSize(p: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null && label != null) {
            val pad = style.placeholderPaddingPx.toInt()
            fm.ascent -= pad; fm.top -= pad
            fm.descent += pad; fm.bottom += pad
        }
        return width(p).toInt().coerceAtLeast(1)
    }

    private fun width(p: Paint): Float =
        if (widthPx > 0) widthPx.toFloat() else p.measureText(label ?: "") + 2 * style.placeholderPaddingPx

    override fun draw(c: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
        val right = x + width(p)
        val cy = (top + bottom) / 2f
        paint.style = Paint.Style.STROKE
        if (label == null) {
            paint.color = style.dividerColor
            paint.strokeWidth = style.dividerThicknessPx
            c.drawLine(x, cy, right, cy, paint)
            return
        }
        val half = style.dividerThicknessPx / 2
        paint.color = style.placeholderBorderColor
        paint.strokeWidth = style.dividerThicknessPx
        rect.set(x + half, top + half, right - half, bottom - half)
        c.drawRoundRect(rect, style.placeholderCornerPx, style.placeholderCornerPx, paint)
        paint.style = Paint.Style.FILL
        paint.color = style.placeholderTextColor
        paint.textSize = p.textSize
        paint.typeface = p.typeface
        paint.textAlign = Paint.Align.CENTER
        c.drawText(label, (x + right) / 2, cy - (paint.ascent() + paint.descent()) / 2, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    fun restyled(style: EditorStyle) = ObjectSpan(block, style).also { it.widthPx = widthPx }
}
