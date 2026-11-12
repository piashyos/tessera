package app.tessera.ui.editor.engine

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What counts as "not on the text" for a touch (spec §3): under the last line, off the end of a line, an empty line, left of an indent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OutsideTextTest {
    private val paint = TextPaint().apply { textSize = 20f }

    private fun layout(text: CharSequence, width: Int = 400): Layout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()

    private fun midY(l: Layout, line: Int) = (l.getLineTop(line) + l.getLineBottom(line)) / 2f

    @Test fun belowTheLastLineIsBelowText() {
        val l = layout("one\ntwo")
        assertEquals(Outside.BelowText, outsideTextAt(l, 5f, l.height + 30f))
        assertEquals(Outside.BelowText, outsideTextAt(l, 380f, l.height + 1f))
    }

    @Test fun rightOfAShortLineIsOffLineAtItsEnd() {
        val l = layout("short\nsecond line")
        assertEquals(Outside.OffLine(5), outsideTextAt(l, 350f, midY(l, 0)))
    }

    @Test fun anEmptyLineIsOffLineAtItsStart() {
        val l = layout("a\n\nb")
        assertEquals(Outside.OffLine(2), outsideTextAt(l, 40f, midY(l, 1)))
    }

    @Test fun onAWordIsNull() {
        val l = layout("charlie delta")
        val x = (l.getLineLeft(0) + l.getLineRight(0)) / 2
        assertNull(outsideTextAt(l, x, midY(l, 0)))
    }

    @Test fun leftOfAnIndentedLineIsOffLineAtItsStart() {
        val s = SpannableStringBuilder("plain\nindented text")
        s.setSpan(LeadingMarginSpan.Standard(60), 6, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val l = layout(s)
        assertEquals(Outside.OffLine(6), outsideTextAt(l, 10f, midY(l, 1)))
    }
}
