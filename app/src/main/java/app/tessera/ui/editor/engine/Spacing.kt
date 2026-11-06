package app.tessera.ui.editor.engine

import android.text.Spanned
import app.tessera.core.text.notedoc.ParagraphKind

/**
 * What a paragraph adds to the height of its first and last line (px). Margins and paddings of the mockup CSS in the model of one
 * paragraph per line: [gapTop] / [gapBottom] are the collapsed gap to a neighbour (spec: `max(bottom of the previous, top of the next)`)
 * that this paragraph carries; [padTop] / [padBottom] are inside the block (a list item's `padding: 3px 0`, the quote bar, the mono
 * background). [firstOfSeries] / [lastOfSeries]: the paragraph starts / ends a run of list items, quote lines or mono lines.
 * Baked into the span by the last layout pass; `Normalizer.refreshNeighbours` sets a span again when a neighbour changed it.
 */
internal data class Spacing(
    val gapTop: Int,
    val gapBottom: Int,
    val padTop: Int,
    val padBottom: Int,
    val firstOfSeries: Boolean,
    val lastOfSeries: Boolean,
) {
    val extraTop get() = gapTop + padTop
    val extraBottom get() = gapBottom + padBottom
}

internal object SpacingRules {
    private enum class Family { Body, Title, Heading, List, Quote, Mono, Object }

    private fun familyAt(t: Spanned, ps: Int): Family = when {
        isObjectParagraph(t, ps) -> Family.Object
        else -> when (kindOf(t, ps)) {
            ParagraphKind.Body -> Family.Body
            ParagraphKind.Title -> Family.Title
            ParagraphKind.Heading, ParagraphKind.Subheading -> Family.Heading
            ParagraphKind.Quote -> Family.Quote
            ParagraphKind.Mono -> Family.Mono
            is ParagraphKind.ListItem -> Family.List
        }
    }

    private fun isSeries(f: Family) = f == Family.List || f == Family.Quote || f == Family.Mono

    private fun top(f: Family, s: EditorStyle): Float = when (f) {
        Family.Body -> 0f
        Family.Title -> s.titleSpaceTopPx
        Family.Heading -> s.headingSpaceTopPx
        else -> s.blockEdgePx
    }

    private fun bottom(f: Family, s: EditorStyle): Float = when (f) {
        Family.Body -> 0f
        Family.Title -> s.titleSpaceBottomPx
        Family.Heading -> s.headingSpaceBottomPx
        else -> s.blockEdgePx
    }

    /** The gap between [a] and the paragraph after it [b]: nothing inside a series, otherwise the larger of the two margins. */
    private fun gap(a: Family, b: Family, s: EditorStyle): Int =
        if (a == b && isSeries(a)) 0 else maxOf(bottom(a, s), top(b, s)).toInt()

    /** The spacing of the paragraph that starts at [ps] (a block span or an object — not Body). */
    fun of(t: Spanned, ps: Int, s: EditorStyle): Spacing {
        val pe = paragraphEnd(t, ps)
        val me = familyAt(t, ps)
        val prev = if (ps > 0) familyAt(t, paragraphStart(t, ps - 1)) else null
        val next = if (pe < t.length) familyAt(t, pe + 1) else null
        val first = prev != me || !isSeries(me)
        val last = next != me || !isSeries(me)
        // the gap sits on the bottom of the upper paragraph; only under a Body paragraph (which has no span) the lower one carries it
        val gapTop = if (prev == Family.Body) gap(prev, me, s) else 0
        val gapBottom = if (next != null) gap(me, next, s) else 0
        val padTop: Int
        val padBottom: Int
        when (me) {
            Family.List -> { padTop = s.listItemPaddingPx.toInt(); padBottom = padTop }
            Family.Quote -> { padTop = if (first) s.quotePaddingVerticalPx.toInt() else 0; padBottom = if (last) s.quotePaddingVerticalPx.toInt() else 0 }
            Family.Mono -> { padTop = if (first) s.monoPaddingVerticalPx.toInt() else 0; padBottom = if (last) s.monoPaddingVerticalPx.toInt() else 0 }
            else -> { padTop = 0; padBottom = 0 }
        }
        return Spacing(gapTop, gapBottom, padTop, padBottom, first, last)
    }
}
