package app.tessera.core.text

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import app.tessera.R
import org.wordpress.aztec.spans.AztecHeadingSpan

/** First-line H1 display size, per spec §6 (Instrument Serif, 26sp). */
private const val SerifH1SizeSp = 26f

/**
 * [MetricAffectingSpan] that swaps the paint typeface to a supplied [Typeface]. Unlike the
 * platform `TypefaceSpan(Typeface)` (API 28+), this works from our minSdk 26. Also serves
 * as a marker so [applySerifH1Range] can find and remove its own previously-applied spans.
 */
class SerifH1TypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
  override fun updateMeasureState(paint: TextPaint) {
    paint.typeface = typeface
  }

  override fun updateDrawState(tp: TextPaint) {
    tp.typeface = typeface
  }
}

/**
 * Render the note's first H1 block in Instrument Serif at display size. Aztec installs an
 * [AztecHeadingSpan] per heading line; we locate the earliest `H1` and overlay our serif
 * typeface + absolute size on it. Safe to call repeatedly (idempotent) — used both after
 * the initial `fromHtml` load and on debounced text changes when the H1 moves.
 *
 * No-op when the note has no H1.
 */
fun applyFirstLineSerifH1(spannable: Spannable, ctx: Context) {
  val serif = ResourcesCompat.getFont(ctx, R.font.instrument_serif_regular) ?: return
  val sizePx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    SerifH1SizeSp,
    ctx.resources.displayMetrics,
  ).toInt()

  val firstH1 = spannable
    .getSpans(0, spannable.length, AztecHeadingSpan::class.java)
    .filter { it.heading == AztecHeadingSpan.Heading.H1 }
    .minByOrNull { spannable.getSpanStart(it) }

  if (firstH1 == null) {
    // H1 was removed since the last apply — strip our markers so stale serif styling
    // doesn't linger on what is now body text.
    removeSerifH1Spans(spannable)
    return
  }

  val start = spannable.getSpanStart(firstH1)
  val end = spannable.getSpanEnd(firstH1)
  if (start < 0 || end < 0 || start >= end) return
  applySerifH1Range(spannable, start, end, serif, sizePx)
}

/** Remove any previously-applied serif-H1 markers across the whole [spannable]. */
internal fun removeSerifH1Spans(spannable: Spannable) {
  spannable.getSpans(0, spannable.length, SerifH1TypefaceSpan::class.java)
    .forEach { spannable.removeSpan(it) }
  spannable.getSpans(0, spannable.length, SerifH1MarkerSizeSpan::class.java)
    .forEach { spannable.removeSpan(it) }
}

/**
 * Apply the serif typeface + [sizePx] absolute size over `[start, end)`, first clearing our
 * own previously-applied spans so repeated calls don't stack. Extracted for unit testing
 * without a live Aztec editor.
 */
internal fun applySerifH1Range(
  spannable: Spannable,
  start: Int,
  end: Int,
  typeface: Typeface,
  sizePx: Int,
) {
  removeSerifH1Spans(spannable)
  spannable.setSpan(SerifH1TypefaceSpan(typeface), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
  spannable.setSpan(SerifH1MarkerSizeSpan(sizePx), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** [AbsoluteSizeSpan] subclass used as a removable marker for the H1 display size. */
class SerifH1MarkerSizeSpan(sizePx: Int) : AbsoluteSizeSpan(sizePx)
