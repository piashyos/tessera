package app.tessera.ui.editor.engine

import android.graphics.Paint
import android.text.DynamicLayout
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What [EngineEditable] tells the `DynamicLayout`: nothing about a span that an insertion only moved, everything about a span that code sets
 * on purpose (also from the `afterTextChanged` of that same insertion, where the normalizer and the auto-replace work). A probe span counts
 * how often the layout measures its paragraph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineEditableTest {
    private class Probe : MetricAffectingSpan(), LineHeightSpan {
        var measured = 0
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) { measured++ }
        override fun updateMeasureState(p: TextPaint) = Unit
        override fun updateDrawState(tp: TextPaint) = Unit
    }

    private fun setup(text: Editable): Probe {
        val probe = Probe()
        val d = text.indexOf("d")
        text.setSpan(probe, d, text.length, Spanned.SPAN_PARAGRAPH)
        DynamicLayout.Builder.obtain(text, TextPaint(), 400).build() // watches the text, as a TextView's does
        probe.measured = 0
        return probe
    }

    @Test
    fun aPlainEditableReflowsEveryMovedSpanAndOursDoesNot() {
        val plain = SpannableStringBuilder("a\nb\nc\nd")
        val p1 = setup(plain)
        plain.insert(0, "xy")
        assertTrue("the platform does re-measure a span that only moved (${p1.measured})", p1.measured > 0)

        val ours = EngineEditable("a\nb\nc\nd")
        val p2 = setup(ours)
        ours.insert(0, "xy")
        assertEquals("a span that only moved is not measured again", 0, p2.measured)
        ours.delete(0, 2)
        assertEquals("nor after a deletion", 0, p2.measured)
    }

    @Test
    fun aSpanSetOnPurposeFromAfterTextChangedReachesTheLayout() {
        val ours = EngineEditable("a\nb\nc\nd")
        val probe = setup(ours)
        ours.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                val sp = s as Spanned
                if (sp.getSpanStart(probe) < 0) return
                s.setSpan(probe, sp.getSpanStart(probe), sp.getSpanEnd(probe), sp.getSpanFlags(probe)) // like refreshNeighbours
            }
        })
        ours.insert(0, "xy")
        assertTrue("the layout measures the span that was set again (${probe.measured})", probe.measured > 0)
    }

    private fun Editable.addTextChangedListener(w: TextWatcher) = setSpan(w, 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
}
