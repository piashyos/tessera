package app.tessera.core.text

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SerifH1SpannerTest {

  private val serif: Typeface = Typeface.SERIF

  @Test fun applies_typeface_and_size_over_range() {
    val sb = SpannableStringBuilder("Title\nbody")
    applySerifH1Range(sb, 0, 5, serif, sizePx = 72)

    val sizeSpans = sb.getSpans(0, sb.length, AbsoluteSizeSpan::class.java)
    assertEquals(1, sizeSpans.size)
    assertEquals(0, sb.getSpanStart(sizeSpans[0]))
    assertEquals(5, sb.getSpanEnd(sizeSpans[0]))
    assertEquals(72, sizeSpans[0].size)

    val faceSpans = sb.getSpans(0, sb.length, SerifH1TypefaceSpan::class.java)
    assertEquals(1, faceSpans.size)
  }

  @Test fun reapply_is_idempotent() {
    val sb = SpannableStringBuilder("Heading\nbody")
    applySerifH1Range(sb, 0, 7, serif, sizePx = 72)
    // H1 grew — re-apply over a different range must not stack duplicate spans.
    applySerifH1Range(sb, 0, 9, serif, sizePx = 72)

    assertEquals(1, sb.getSpans(0, sb.length, AbsoluteSizeSpan::class.java).size)
    assertEquals(1, sb.getSpans(0, sb.length, SerifH1TypefaceSpan::class.java).size)
    val sizeSpan = sb.getSpans(0, sb.length, AbsoluteSizeSpan::class.java)[0]
    assertEquals(9, sb.getSpanEnd(sizeSpan))
  }
}
