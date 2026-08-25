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

  @Test fun applies_typeface_over_range() {
    val sb = SpannableStringBuilder("Title\nbody")
    applySerifTypefaceRange(sb, 0, 5, serif)

    val faceSpans = sb.getSpans(0, sb.length, SerifHeadingTypefaceSpan::class.java)
    assertEquals(1, faceSpans.size)
    assertEquals(0, sb.getSpanStart(faceSpans[0]))
    assertEquals(5, sb.getSpanEnd(faceSpans[0]))
  }

  @Test fun does_not_apply_an_absolute_size_span() {
    // Typeface-only by design: an absolute-size span would stack on Aztec's multiplicative
    // heading scale and blow the line up to ~45sp.
    val sb = SpannableStringBuilder("Heading\nbody")
    applySerifTypefaceRange(sb, 0, 7, serif)

    assertEquals(0, sb.getSpans(0, sb.length, AbsoluteSizeSpan::class.java).size)
  }

  @Test fun remove_strips_all_markers() {
    val sb = SpannableStringBuilder("Heading\nbody")
    applySerifTypefaceRange(sb, 0, 7, serif)
    removeSerifHeadingSpans(sb)

    assertEquals(0, sb.getSpans(0, sb.length, SerifHeadingTypefaceSpan::class.java).size)
  }
}
