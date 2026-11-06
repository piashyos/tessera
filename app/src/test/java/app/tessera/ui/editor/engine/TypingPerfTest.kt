package app.tessera.ui.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec §9 item 5 as a JVM regression (native graphics: the real `DynamicLayout`): one character typed in the middle of a note of 1000
 * paragraphs of every kind costs about the same as in a short note. Found on the device at 270–340 ms. Causes: (1) every span that
 * an insertion moves told the layout to reflow, twice per `UpdateLayout` span (`EngineEditable` no longer delivers that for a span that
 * only moved); (2) `Paint.fontVariationSettings` made a native typeface per measure of a heading / draw of a marker (`Weighted`);
 * (3) the whole note was serialized twice per command (the Markdown is cached per document version). The threshold has a wide
 * margin; the median is printed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TypingPerfTest {

    private fun bigNote(): String {
        val lines = ArrayList<String>()
        var paragraphs = 0
        var i = 0
        while (paragraphs < 1000) {
            val group = when (i % 12) {
                0 -> listOf("# Title $i")
                1 -> listOf("## Heading $i")
                2 -> listOf("Body $i with **bold**, *italic*, <u>under</u> and ~~strike~~ text, long enough to wrap onto a second line of a phone.")
                3 -> listOf("- bullet $i", "    - nested $i")
                4 -> listOf("1. first $i", "2. second $i")
                5 -> listOf("- [ ] todo $i", "- [x] done $i")
                6 -> listOf("> quote $i")
                7 -> listOf("```", "mono $i", "val x = $i", "```")
                8 -> listOf("---")
                9 -> listOf("")
                10 -> listOf("### Subheading $i")
                else -> listOf("+ dash $i")
            }
            lines += group
            paragraphs += group.count { it != "```" }
            i++
        }
        return lines.joinToString("\n")
    }

    private fun median(xs: List<Double>) = xs.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

    @Test
    fun aKeyInTheMiddleOfAThousandParagraphsIsCheap() {
        val h = HostFixture(bigNote(), height = 20_000)
        val middle = h.text.length / 2
        val ic = h.ic()
        val times = ArrayList<Double>()
        val bodyStart = h.text.toString().indexOf("Body ", middle)
        var at = bodyStart + 3
        repeat(25) {
            h.caret(at)
            val t0 = System.nanoTime()
            ic.commitText("x", 1)
            h.layout()
            times += (System.nanoTime() - t0) / 1e6
            at++
        }
        val warm = times.drop(5)
        println("TypingPerf: commitText in the middle of 1000 paragraphs: median %.1f ms, max %.1f ms".format(median(warm), warm.max()))
        assertTrue("median ${median(warm)} ms", median(warm) <= 50.0)
        // not told about the spans that only moved, the layout is still what a fresh one gives
        val fresh = HostFixture(h.md(), height = 20_000)
        assertEquals("line count", fresh.view.layout.lineCount, h.view.layout.lineCount)
        assertEquals("height", fresh.view.layout.height, h.view.layout.height)
        for (line in 0 until fresh.view.layout.lineCount step 7) assertEquals("top of line $line", fresh.view.layout.getLineTop(line), h.view.layout.getLineTop(line))
    }
}
