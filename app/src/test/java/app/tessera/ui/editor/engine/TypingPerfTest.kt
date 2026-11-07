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

    /**
     * The first key of a typing run (after a pause of more than 500 ms) used to serialize the whole note for the history step, and a debounced
     * emit did it again on the main thread. Each run here is a new editor, opened and left alone for 600 ms, then one character in the middle;
     * then the debounce of the emit goes by and a second run starts: the note's Markdown came from the emit, the step costs nothing.
     */
    @Test
    fun theFirstKeyAfterAPauseOnAThousandParagraphsIsCheap() {
        val md = bigNote()
        val first = ArrayList<Double>()
        val second = ArrayList<Double>()
        val frame = ArrayList<Double>()
        repeat(7) { run ->
            val h = HostFixture(md, height = 20_000)
            val jobs = ArrayList<Runnable>()
            h.view.emitExecutor = java.util.concurrent.Executor { jobs += it } // the background thread: its work is not on the main thread
            val ic = h.ic()
            h.caret(h.text.toString().indexOf("Body ", h.text.length / 2) + 3)
            h.pass(600)
            var t0 = System.nanoTime()
            ic.commitText("x", 1)
            h.layout()
            if (run > 1) first += (System.nanoTime() - t0) / 1e6
            t0 = System.nanoTime()
            h.pass(300) // the debounce: a copy of the note goes to the background
            if (run > 1) frame += (System.nanoTime() - t0) / 1e6
            assertEquals("nothing is emitted before the background has written the Markdown", 0, h.emitted.size)
            jobs.forEach { it.run() }
            h.pass(10) // the result comes back to the main thread
            assertEquals("the emit is the note", h.md(), h.emitted.single())
            h.pass(600)
            t0 = System.nanoTime()
            ic.commitText("y", 1)
            h.layout()
            if (run > 1) second += (System.nanoTime() - t0) / 1e6
            h.view.dispose()
        }
        println("TypingPerf: first key after a pause on 1000 paragraphs: median %.1f ms (max %.1f); the debounce on the main thread: median %.1f ms (max %.1f); first key after an emit: median %.1f ms"
            .format(median(first), first.max(), median(frame), frame.max(), median(second)))
        assertTrue("first key ${median(first)} ms", median(first) <= 40.0)
        assertTrue("first key after an emit ${median(second)} ms", median(second) <= 40.0)
    }

    /** Backspace in a paragraph of 11 000 characters: the platform lays the paragraph out again; what is ours must stay small. */
    @Test
    fun backspaceInAParagraphOfElevenThousandCharactersIsCheap() {
        val h = HostFixture("слово word ".repeat(1000) + "\nsecond line", height = 20_000)
        val ic = h.ic()
        h.caret(5_500)
        val times = ArrayList<Double>()
        repeat(40) {
            val t0 = System.nanoTime()
            ic.deleteSurroundingText(1, 0)
            h.layout()
            times += (System.nanoTime() - t0) / 1e6
        }
        val warm = times.drop(5)
        println("TypingPerf: Backspace in an 11 000-character paragraph: median %.1f ms, max %.1f ms".format(median(warm), warm.max()))
        assertTrue("median ${median(warm)} ms", median(warm) <= 40.0)
    }
}
