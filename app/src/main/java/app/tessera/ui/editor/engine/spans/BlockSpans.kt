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
import app.tessera.ui.editor.engine.Spacing
import app.tessera.ui.editor.engine.SpacingRules

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
sealed class BlockSpan : MetricAffectingSpan(), LineHeightSpan {
    abstract val kind: ParagraphKind
    abstract val style: EditorStyle

    /** The spacing the last layout pass measured with; `Normalizer.refreshNeighbours` compares it with the neighbours' state. */
    @Volatile internal var baked: Spacing? = null

    /** `(top, ascent)` the first line of the paragraph ended with, to recognise them when StaticLayout carries them into the second line. */
    private var afterFirstLine: Pair<Int, Int>? = null

    internal fun spacing(text: Spanned): Spacing = SpacingRules.of(text, text.getSpanStart(this), style)

    /** Margins and paddings (see [Spacing]) go into the height of the first and the last line of the paragraph. */
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
        val t = text as? Spanned ?: return
        val s = t.getSpanStart(this)
        val e = t.getSpanEnd(this)
        if (s < 0 || e <= s) return // a span collapsed by a deletion, not yet removed by the normalizer, is not a paragraph: it would count twice
        val sp = spacing(t)
        baked = sp
        if (start <= s) {
            fm.ascent -= sp.extraTop; fm.top -= sp.extraTop
            afterFirstLine = fm.top to fm.ascent
        } else if (sp.extraTop != 0 && afterFirstLine == (fm.top to fm.ascent)) {
            // StaticLayout hands the next line of the paragraph the metrics the first line ended with: take the top margin back
            fm.ascent += sp.extraTop; fm.top += sp.extraTop
        }
        if (end >= e) { fm.descent += sp.extraBottom; fm.bottom += sp.extraBottom }
    }

    /** The first line starts, the last line ends, [Spacing] outside the block (not painted by a background). */
    internal fun outside(text: CharSequence, start: Int, end: Int): Pair<Int, Int> {
        val t = text as? Spanned ?: return 0 to 0
        val sp = baked ?: return 0 to 0
        return (if (start <= t.getSpanStart(this)) sp.gapTop else 0) to (if (end >= t.getSpanEnd(this)) sp.gapBottom else 0)
    }

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
        p.typeface = if (serif) style.serifTypeface ?: Typeface.SERIF else Weighted.of(p.typeface, 600)
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
    private val fmScratch = Paint.FontMetrics()

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
        p.getFontMetrics(fmScratch)
        val fm = fmScratch
        val cy = baseline + (fm.ascent + fm.descent) / 2f // the middle of the first text line: margins of the series do not move it
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
        val savedTypeface = p.typeface
        val savedAlign = p.textAlign
        p.color = style.accentColor
        p.typeface = Weighted.of(savedTypeface, 500)
        p.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.RIGHT
        c.drawText(s, anchor, baseline.toFloat(), p)
        p.color = savedColor
        p.typeface = savedTypeface
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
        val (above, below) = outside(text, start, end)
        val t = (top + above).toFloat()
        val b = (bottom - below).toFloat()
        paint.color = style.quoteFillColor
        c.drawRect(left.toFloat(), t, right.toFloat(), b, paint)
        paint.color = style.quoteBarColor
        c.drawRect(left.toFloat(), t, left + style.quoteBarWidthPx, b, paint)
    }

    override fun restyled(style: EditorStyle) = QuoteSpan(style)
}

/** Mono: monospace on a rounded block; the corners are rounded only at the first and last line of a series. */
class MonoSpan(override val style: EditorStyle) : BlockSpan(), LeadingMarginSpan, LineBackgroundSpan {
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

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lnum: Int,
    ) {
        val sp = (text as? Spanned)?.let { spacing(it) }
        val (above, below) = outside(text, start, end)
        val r = style.monoCornerPx
        // the corners are rounded only at the first and the last line of a series
        val first = start <= ((text as? Spanned)?.getSpanStart(this) ?: 0) && sp?.firstOfSeries != false
        val last = end >= ((text as? Spanned)?.getSpanEnd(this) ?: Int.MAX_VALUE) && sp?.lastOfSeries != false
        val t = if (first) r else 0f
        val b = if (last) r else 0f
        paint.color = style.monoBackgroundColor
        rect.set(left.toFloat(), (top + above).toFloat(), right.toFloat(), (bottom - below).toFloat())
        path.reset()
        path.addRoundRect(rect, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
        c.drawPath(path, paint)
    }

    override fun restyled(style: EditorStyle) = MonoSpan(style)
}

/**
 * [base] at a variable-font weight. `Paint.fontVariationSettings` builds a new native typeface on every call, and a heading is
 * measured and a marker drawn over and over (a reflow per keystroke): the typefaces are made once per `(base, weight)`.
 */
internal object Weighted {
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<Typeface?, Int>, Typeface>()

    fun of(base: Typeface?, weight: Int): Typeface? = cache[base to weight]
        ?: Paint().apply { typeface = base; fontVariationSettings = "'wght' $weight" }.typeface?.also { cache[base to weight] = it }
}
