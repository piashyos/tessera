package app.tessera.ui.editor.engine.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.components.CheckboxPalette
import app.tessera.ui.editor.components.TesseraCheckboxDrawable
import app.tessera.ui.editor.engine.EditorStyle
import app.tessera.ui.editor.engine.blockSpanAt

/**
 * Flags of every [BlockSpan]: a paragraph span with a higher priority than the marks, so the heading's typeface and
 * size are applied first and bold / italic / code of a mark span refine them, whatever the order they were set in.
 */
const val BLOCK_SPAN_FLAGS = Spanned.SPAN_PARAGRAPH or (1 shl Spanned.SPAN_PRIORITY_SHIFT)

/**
 * One composite span per paragraph (spec §6.1): the paragraph's [kind] and how it is drawn. It covers the
 * paragraph text together with its trailing `\n` (`SPAN_PARAGRAPH`). A paragraph without a [BlockSpan] is Body.
 * Every subclass is a [MetricAffectingSpan] so the one span can also set text size, typeface and color.
 */
sealed class BlockSpan : MetricAffectingSpan() {
    abstract val kind: ParagraphKind
    abstract val style: EditorStyle

    /** The same paragraph data painted with another theme (`DocBinder.reapplyTheme`). */
    abstract fun restyled(style: EditorStyle): BlockSpan

    override fun updateMeasureState(paint: TextPaint) = Unit
    override fun updateDrawState(tp: TextPaint) = Unit

    companion object {
        /** `null` for Body. */
        fun create(kind: ParagraphKind, style: EditorStyle): BlockSpan? = when (kind) {
            ParagraphKind.Body -> null
            ParagraphKind.Title, ParagraphKind.Heading, ParagraphKind.Subheading -> HeadingSpan(kind, style)
            ParagraphKind.Quote -> QuoteSpan(style)
            ParagraphKind.Mono -> MonoSpan(style)
            is ParagraphKind.ListItem -> ListItemSpan(kind, style)
        }
    }
}

/** Title / Heading (Playfair, as the Aztec path) and Subheading (Inter W600). Absolute sizes: nothing stacks. */
class HeadingSpan(override val kind: ParagraphKind, override val style: EditorStyle) : BlockSpan() {
    private val size = when (kind) {
        ParagraphKind.Title -> style.titleTextSizePx
        ParagraphKind.Heading -> style.headingTextSizePx
        else -> style.subheadingTextSizePx
    }
    private val serif = kind == ParagraphKind.Title || kind == ParagraphKind.Heading

    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    override fun updateDrawState(tp: TextPaint) = apply(tp)

    private fun apply(p: TextPaint) {
        p.textSize = size
        if (serif) p.typeface = style.serifTypeface ?: Typeface.SERIF else p.fontVariationSettings = "'wght' 600"
    }

    override fun restyled(style: EditorStyle) = HeadingSpan(kind, style)
}

/** Marker (•, –, N., checkbox), indent `level` steps, checked text struck and muted (spec table §2 row 3). */
class ListItemSpan(override val kind: ParagraphKind.ListItem, override val style: EditorStyle) : BlockSpan(), LeadingMarginSpan {
    private val palette = CheckboxPalette().apply {
        boxColor = style.accentColor
        tickColor = style.checkboxTickColor
    }
    private val checkbox = TesseraCheckboxDrawable(kind.checked, palette, style.checkboxCornerPx, style.checkboxStrokePx)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The shown number of a [ListType.Number] item. Kept up to date by `Numbering` (the binder, and the normalizer after
     * every edit), so drawing a marker never looks at the neighbours.
     */
    @Volatile internal var number: Int = 1

    override fun updateDrawState(tp: TextPaint) {
        if (kind.type == ListType.Check && kind.checked) {
            tp.isStrikeThruText = true
            tp.color = style.mutedTextColor
        }
    }

    override fun getLeadingMargin(first: Boolean): Int = (style.listMarkerAreaPx + kind.level * style.listLevelStepPx).toInt()

    // ponytail: marker geometry is LTR-only (dir is used to mirror the offsets, not the glyph run)
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, l: Layout,
    ) {
        if (!first) return
        val size = style.checkboxSizePx
        val left = if (dir > 0) x + kind.level * style.listLevelStepPx else x - kind.level * style.listLevelStepPx - size
        val cy = (top + bottom) / 2f
        when (kind.type) {
            ListType.Check -> {
                checkbox.setBounds(left.toInt(), (cy - size / 2).toInt(), (left + size).toInt(), (cy + size / 2).toInt())
                checkbox.draw(c)
            }
            ListType.Bullet -> withPaint(p, fill) {
                it.color = style.accentColor
                it.style = Paint.Style.FILL
                c.drawCircle(left + size / 2, cy, style.bulletRadiusPx, it)
            }
            ListType.Dash -> drawGlyph(c, p, "–", left + size / 2, baseline, centered = true)
            ListType.Number -> drawGlyph(c, p, "$number.", left + size, baseline, centered = false)
        }
    }

    private fun drawGlyph(c: Canvas, p: Paint, s: String, anchor: Float, baseline: Int, centered: Boolean) {
        val savedColor = p.color
        val savedVariation = p.fontVariationSettings
        val savedAlign = p.textAlign
        p.color = style.accentColor
        p.fontVariationSettings = "'wght' 500"
        p.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.RIGHT
        c.drawText(s, anchor, baseline.toFloat(), p)
        p.color = savedColor
        p.fontVariationSettings = savedVariation
        p.textAlign = savedAlign
    }

    override fun restyled(style: EditorStyle) = ListItemSpan(kind, style).also { it.number = number }
}

private inline fun withPaint(p: Paint, scratch: Paint, draw: (Paint) -> Unit) {
    scratch.set(p)
    draw(scratch)
}

/** Quote: bar and fill drawn per line without gaps, continuous over a series of quote paragraphs. */
class QuoteSpan(override val style: EditorStyle) : BlockSpan(), LeadingMarginSpan, LineBackgroundSpan {
    override val kind: ParagraphKind get() = ParagraphKind.Quote
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }

    override fun updateDrawState(tp: TextPaint) {
        tp.color = style.quoteTextColor
    }

    override fun getLeadingMargin(first: Boolean): Int = (style.quoteBarWidthPx + style.quoteGapPx).toInt()
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, l: Layout,
    ) = Unit

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lnum: Int,
    ) {
        paint.color = style.quoteFillColor
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        paint.color = style.quoteBarColor
        c.drawRect(left.toFloat(), top.toFloat(), left + style.quoteBarWidthPx, bottom.toFloat(), paint)
    }

    override fun restyled(style: EditorStyle) = QuoteSpan(style)
}

/** Mono: monospace on a rounded block; the corners are rounded only at the first and last line of a series. */
class MonoSpan(override val style: EditorStyle) : BlockSpan(), LeadingMarginSpan, LineBackgroundSpan, LineHeightSpan {
    override val kind: ParagraphKind get() = ParagraphKind.Mono
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = Path()

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = Typeface.MONOSPACE
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = Typeface.MONOSPACE
        tp.color = style.monoTextColor
    }

    override fun getLeadingMargin(first: Boolean): Int = style.monoPaddingHorizontalPx.toInt()
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, l: Layout,
    ) = Unit

    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val (first, last) = edges(text, start, end)
        (text as? Spanned)?.let { bakedSeriesEdges = seriesEdges(it) }
        val pad = style.monoPaddingVerticalPx.toInt()
        if (first) { fm.ascent -= pad; fm.top -= pad }
        if (last) { fm.descent += pad; fm.bottom += pad }
    }

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lnum: Int,
    ) {
        val (first, last) = edges(text, start, end)
        val r = style.monoCornerPx
        val t = if (first) r else 0f
        val b = if (last) r else 0f
        paint.color = style.monoBackgroundColor
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.reset()
        path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
        c.drawPath(path, paint)
    }

    /**
     * The series edges (is the paragraph the first / the last of its series) that the last layout pass baked into the line
     * heights; the corners are drawn from the same facts. `Normalizer.refreshNeighbours` re-sets a span whose edges moved.
     */
    @Volatile internal var bakedSeriesEdges: Pair<Boolean, Boolean>? = null

    internal fun seriesEdges(text: Spanned): Pair<Boolean, Boolean> =
        (previousBlock(text, text.getSpanStart(this))?.first !is MonoSpan) to (nextBlock(text, text.getSpanEnd(this)) !is MonoSpan)

    /** Is the line [start]..[end] the first / the last line of its series of mono paragraphs. */
    private fun edges(text: CharSequence, start: Int, end: Int): Pair<Boolean, Boolean> {
        val spanned = text as? Spanned ?: return true to true
        val s = spanned.getSpanStart(this)
        val e = spanned.getSpanEnd(this)
        val first = start <= s && previousBlock(spanned, s)?.first !is MonoSpan
        val last = end >= e && nextBlock(spanned, e) !is MonoSpan
        return first to last
    }

    override fun restyled(style: EditorStyle) = MonoSpan(style)
}

/** The block span of the paragraph before the one that starts at [start] and where that paragraph starts. */
internal fun previousBlock(text: Spanned, start: Int): Pair<BlockSpan?, Int>? {
    if (start <= 0) return null
    var s = start - 1
    while (s > 0 && text[s - 1] != '\n') s--
    return text.getSpans(s, start, BlockSpan::class.java).firstOrNull { text.getSpanStart(it) == s } to s
}

/** The block span of the paragraph that starts at [end]. */
internal fun nextBlock(text: Spanned, end: Int): BlockSpan? = blockSpanAt(text, end)
