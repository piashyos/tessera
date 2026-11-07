package app.tessera.ui.editor.engine.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import android.util.Size
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.domain.model.ImageRef
import app.tessera.domain.model.parseImageRef
import app.tessera.ui.editor.engine.EditorStyle
import app.tessera.ui.editor.engine.Spacing
import app.tessera.ui.editor.engine.SpacingRules

/** The only character of an object paragraph. */
const val OBJECT_CHAR = '￼'

/** Placeholder char of an empty last non-Body paragraph. Only one marked by a [SentinelSpan] is ours. */
const val SENTINEL_CHAR = '​'

/** Marks the [SENTINEL_CHAR] that the normalizer may delete (a `U+200B` of the user text is never touched). */
class SentinelSpan

/**
 * What an image object holds besides its block (phase 2 spec §3). One instance lives through [ObjectSpan.restyled], so a load that is in flight
 * (`ImageObjects`) still reaches the span that stands in the text after a change of the theme.
 */
internal class ImageState {
    /** What is drawn: the decoded image, or one that is shown until the new one comes (a resized or rotated image). `null`: the tile. */
    var drawable: Drawable? = null

    /** The `w` percent the [drawable] was baked at; the span draws it scaled by (its own percent / this), so a resize needs no new decode to show. */
    var bakedPercent = 100

    /** Percent while a handle is dragged (not an edit yet). */
    var previewPercent: Int? = null

    /** The image could not be read: the tile says so. */
    var failed = false

    /** Final size of the image at the text width when it is known before the decode (`ObjectImageSource.size`). */
    var planned: Size? = null

    // the bookkeeping of ImageObjects
    var requested: String? = null
    var cancel: (() -> Unit)? = null
    var span: ObjectSpan? = null

    /** The state that took over this one's load after a rebuild of the text; the result goes there. */
    var forward: ImageState? = null

    fun resolve(): ImageState { var s = this; while (true) s = s.forward ?: return s }
}

/**
 * A [NoteBlock.Image] / [NoteBlock.Table] / [NoteBlock.Divider] on a single [OBJECT_CHAR] that fills its paragraph.
 * Holds the source block, so serializing writes it back untouched. Stage 1: divider = a line, image and table =
 * a captioned frame (D9, no icon).
 */
class ObjectSpan internal constructor(val block: NoteBlock, val style: EditorStyle, state: ImageState?) : ReplacementSpan(), LineHeightSpan {
    constructor(block: NoteBlock, style: EditorStyle) : this(block, style, null)

    /** The state of an image; `null` for the other objects. */
    internal val image: ImageState? = (block as? NoteBlock.Image)?.let { state ?: ImageState() }
    private val ref: ImageRef? = (block as? NoteBlock.Image)?.let { parseImageRef(it.src) }

    init { image?.span = this }

    /** Width the host gives the object (the text area); until set, the placeholder is as wide as its caption. */
    @Volatile var widthPx: Int = 0

    /**
     * Height the table's overlay takes (phase 2 spec §4, variant A): the span reserves it and draws nothing, the host's overlay sits on it. `0`:
     * not known yet, the editor gives a first value (the height this grid had, or an estimate). Set by the host, quietly.
     */
    @Volatile var tableHeightPx: Int = 0

    /** The caption of a frame (a placeholder, an image that is loading or unavailable, a table); `null` for the divider. */
    private val label: String?
        get() = when (block) {
            is NoteBlock.Image -> if (image?.failed == true) style.imageUnavailableLabel else style.imageLabel
            is NoteBlock.Table -> style.tableLabel
            else -> null
        }

    /** Margins above and below the object (see [Spacing]), as measured by the last layout pass. */
    @Volatile internal var baked: Spacing? = null

    internal fun spacing(text: Spanned): Spacing = SpacingRules.of(text, text.getSpanStart(this), style)

    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val t = text as? Spanned ?: return
        if (t.getSpanStart(this) < 0) return
        val sp = spacing(t)
        baked = sp
        fm.ascent -= sp.gapTop; fm.top -= sp.gapTop
        fm.descent += sp.gapBottom; fm.bottom += sp.gapBottom
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /**
     * The size an image takes (the box the picture or its tile is drawn in), `null` for the other objects. The object defines its size, the
     * layout asks: the decoded image in its own size, scaled by its percent over the one it was baked at (a resize shows at once); else the size that
     * is known before the decode; else the full width and the height of a placeholder. Never wider than the text area. The host sets the span again
     * [app.tessera.ui.editor.engine.EngineEditable.quietly] when this changes.
     */
    internal fun imageBox(): Size? {
        val st = image ?: return null
        val maxW = widthPx
        val d = st.drawable
        val w: Int
        val h: Int
        if (d != null && !st.failed && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
            val scale = (st.previewPercent ?: ref?.wPercent ?: st.bakedPercent).toFloat() / st.bakedPercent
            w = Math.round(d.intrinsicWidth * scale).coerceAtLeast(1)
            h = Math.round(d.intrinsicHeight * scale).coerceAtLeast(1)
        } else if (st.planned != null) {
            w = st.planned!!.width
            h = st.planned!!.height
        } else {
            return Size(if (maxW > 0) maxW else 1, style.imagePlaceholderHeightPx.toInt().coerceAtLeast(1))
        }
        if (maxW > 0 && w > maxW) return Size(maxW, Math.round(h * maxW.toFloat() / w).coerceAtLeast(1))
        return Size(w, h)
    }

    /** The size a table takes: the text width and the height the overlay measured; `null` for the other objects. */
    internal fun tableBox(): Size? =
        if (block is NoteBlock.Table) Size(if (widthPx > 0) widthPx else 1, tableHeightPx.coerceAtLeast(1)) else null

    /** The box of an object whose size its content sets (an image, a table); `null` for a divider. */
    internal fun objectBox(): Size? = imageBox() ?: tableBox()

    override fun getSize(p: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val box = objectBox()
        if (fm != null && box != null) { // the line is as high as the picture: nothing of the text font is in it
            fm.ascent = -box.height; fm.top = -box.height
            fm.descent = 0; fm.bottom = 0
        } else if (fm != null && label != null) {
            val pad = style.placeholderPaddingPx.toInt()
            fm.ascent -= pad; fm.top -= pad
            fm.descent += pad; fm.bottom += pad
        }
        return width(p).toInt().coerceAtLeast(1)
    }

    private fun width(p: Paint): Float =
        if (widthPx > 0) widthPx.toFloat() else objectBox()?.width?.toFloat() ?: (p.measureText(label ?: "") + 2 * style.placeholderPaddingPx)

    override fun draw(c: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, lineTop: Int, y: Int, lineBottom: Int, p: Paint) {
        val top = lineTop + (baked?.gapTop ?: 0) // the margins are not part of the object
        if (block is NoteBlock.Table) return // the overlay of the host is drawn there
        val box = imageBox()
        if (box != null) return drawImage(c, box, x, top, p)
        val bottom = lineBottom - (baked?.gapBottom ?: 0)
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
        drawCaption(c, label ?: return, x, right, cy, p)
    }

    private fun drawImage(c: Canvas, box: Size, x: Float, top: Int, p: Paint) {
        val st = image ?: return
        val d = st.drawable
        if (d != null && !st.failed) {
            d.setBounds(x.toInt(), top, x.toInt() + box.width, top + box.height)
            d.draw(c)
            return
        }
        paint.style = Paint.Style.FILL
        paint.color = style.imageFillColor
        rect.set(x, top.toFloat(), x + box.width, (top + box.height).toFloat())
        c.drawRoundRect(rect, style.placeholderCornerPx, style.placeholderCornerPx, paint)
        drawCaption(c, label ?: return, x, x + box.width, top + box.height / 2f, p)
    }

    private fun drawCaption(c: Canvas, text: String, left: Float, right: Float, cy: Float, p: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = style.placeholderTextColor
        paint.textSize = p.textSize
        paint.typeface = p.typeface
        paint.textAlign = Paint.Align.CENTER
        c.drawText(text, (left + right) / 2, cy - (paint.ascent() + paint.descent()) / 2, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** The same object with the colors of [style]; an image keeps its state (and its load in flight). */
    fun restyled(style: EditorStyle) = ObjectSpan(block, style, image).also { it.widthPx = widthPx; it.tableHeightPx = tableHeightPx }

    /**
     * The span of a block that replaces this one on the same character (a resized or rotated image): until its own decode comes, it shows the picture
     * of the old one, scaled by the new `w` percent over the one that picture was baked at (a rotation shows it as it was).
     */
    internal fun replacedBy(block: NoteBlock, style: EditorStyle): ObjectSpan = ObjectSpan(block, style, null).also {
        it.tableHeightPx = tableHeightPx // an edited table keeps its height until its overlay measures the new grid
        val from = image ?: return@also
        val to = it.image ?: return@also
        to.drawable = from.drawable
        to.bakedPercent = from.bakedPercent
        to.failed = from.failed
        to.planned = null
    }
}
