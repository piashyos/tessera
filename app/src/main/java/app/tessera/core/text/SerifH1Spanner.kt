package app.tessera.core.text

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.core.content.res.ResourcesCompat
import app.tessera.R
import org.wordpress.aztec.spans.AztecHeadingSpan

/**
 * [MetricAffectingSpan] that swaps the paint typeface to a supplied [Typeface]. Unlike the
 * platform `TypefaceSpan(Typeface)` (API 28+), this works from our minSdk 26. Also serves
 * as a marker so [applySerifHeadingSpans] can find and remove its own previously-applied spans.
 */
class SerifHeadingTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
  override fun updateMeasureState(paint: TextPaint) {
    paint.typeface = typeface
  }

  override fun updateDrawState(tp: TextPaint) {
    tp.typeface = typeface
  }
}

/**
 * Render every H1/H2 heading block in the Playfair Display serif. Aztec installs an
 * [AztecHeadingSpan] per heading line and sizes it via its own multiplicative scale (H1 ≈ 26sp,
 * H2 ≈ 20sp at the 15sp base); we only overlay the serif typeface so the heading keeps Aztec's
 * native size. Deliberately typeface-only — no absolute-size span — so we never stack on Aztec's
 * size multiplier (a stale absolute size span would render at size × 1.73 ≈ 45sp).
 *
 * Idempotent: clears its own previously-applied spans first, so it's safe to call after every
 * (re)load and on debounced text changes / toolbar toggles when headings move or change. No-op
 * when the note has no heading.
 */
fun applySerifHeadingSpans(spannable: Spannable, ctx: Context) {
  val serif = ResourcesCompat.getFont(ctx, R.font.playfair_display_regular) ?: return

  // The serif typeface lives in a SEPARATE span from Aztec's heading-SIZE span
  // (AztecHeadingSpan). Re-applying it means a remove-then-re-add of MetricAffectingSpans,
  // and every such change forces DynamicLayout to reflow the affected lines. Doing that on
  // every keystroke — especially while the IME is mid-composition on a heading line — churns
  // the layout and, on a fast (release) render pipeline, a half-reflowed frame can reach the
  // screen: lines momentarily draw at the wrong (heading) size/typeface until the next pass
  // settles. So only touch spans when the heading ranges have ACTUALLY changed; when the
  // existing serif ranges already match, this is a no-op and no reflow is triggered.
  val desired = spannable.getSpans(0, spannable.length, AztecHeadingSpan::class.java)
    .filter { it.heading == AztecHeadingSpan.Heading.H1 || it.heading == AztecHeadingSpan.Heading.H2 }
    .map { spannable.getSpanStart(it) to spannable.getSpanEnd(it) }
    // An empty heading line (start == end) has no range to carry a character span; skip it.
    .filter { (start, end) -> start in 0 until end }
    .sortedBy { it.first }

  val existing = spannable.getSpans(0, spannable.length, SerifHeadingTypefaceSpan::class.java)
    .map { spannable.getSpanStart(it) to spannable.getSpanEnd(it) }
    .sortedBy { it.first }

  if (existing == desired) return

  // Ranges changed (heading added/removed/moved): rebuild from scratch so a stale marker can
  // never linger on text that is no longer a heading.
  removeSerifHeadingSpans(spannable)
  desired.forEach { (start, end) -> applySerifTypefaceRange(spannable, start, end, serif) }
}

/** Remove any previously-applied serif-heading markers across the whole [spannable]. */
internal fun removeSerifHeadingSpans(spannable: Spannable) {
  spannable.getSpans(0, spannable.length, SerifHeadingTypefaceSpan::class.java)
    .forEach { spannable.removeSpan(it) }
}

/**
 * Apply the serif typeface over `[start, end)`. Extracted for unit testing without a live
 * Aztec editor. Does NOT clear existing spans — callers that need idempotency clear first.
 */
internal fun applySerifTypefaceRange(
  spannable: Spannable,
  start: Int,
  end: Int,
  typeface: Typeface,
) {
  spannable.setSpan(SerifHeadingTypefaceSpan(typeface), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}
