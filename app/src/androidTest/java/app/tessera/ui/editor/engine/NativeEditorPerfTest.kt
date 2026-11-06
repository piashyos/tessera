package app.tessera.ui.editor.engine

import android.text.SpannableStringBuilder
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.spans.HeadingSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.MonoSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.QuoteSpan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Spec §9 item 5: a note of 1000 paragraphs (all kinds) opens in ≤ 300 ms, typing has no visible lag. Measured on the device
 * (`adb logcat -s NativeEditorPerf` has every number):
 * - open: from `setMarkdown` (parse + bind) through the first layout of the window, and on to the first frame committed; 5 runs,
 *   each a new editor, the median is checked;
 * - a key: `commitText` of one character in a Body paragraph in the middle, the first of a typing run (after a pause > 500 ms the
 *   history takes a snapshot of the whole note) and one inside a run, each also up to the next committed frame.
 */
class NativeEditorPerfTest {

    /** ≥ 1000 paragraphs: every kind, marks, nesting, mono series, dividers, empty lines. */
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

    private fun ms(ns: Long) = ns / 1_000_000.0
    private fun median(xs: List<Double>) = xs.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
    private fun fmt(xs: List<Double>) = xs.joinToString(", ") { "%.1f".format(it) }
    private fun log(msg: String) = Log.i(TAG, msg)

    @Test
    fun aNoteOf1000ParagraphsOpensFastAndAKeyIsCheap() {
        val md = bigNote()
        DeviceEditor("").use { e ->
            val blocks = NoteMarkdown.parse(md).blocks.size
            log("note: $blocks paragraphs, ${md.length} chars of Markdown")

            val parse = ArrayList<Double>()
            val toLayout = ArrayList<Double>()
            val toFrame = ArrayList<Double>()
            var current: TesseraEditText = e.view
            repeat(RUNS) { run ->
                val laidOut = CountDownLatch(1)
                val drawn = CountDownLatch(1)
                var tLayout = 0L
                var tFrame = 0L
                val (t0, t1) = e.ui {
                    e.container.removeView(current)
                    val v = e.newEditor(null)
                    val t0 = System.nanoTime()
                    v.setMarkdown(md)
                    val t1 = System.nanoTime()
                    e.container.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    v.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            if (v.layout == null) return
                            tLayout = System.nanoTime()
                            v.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            laidOut.countDown()
                        }
                    })
                    v.viewTreeObserver.registerFrameCommitCallback { tFrame = System.nanoTime(); drawn.countDown() }
                    current = v
                    t0 to t1
                }
                check(laidOut.await(10, TimeUnit.SECONDS)) { "no layout" }
                check(drawn.await(10, TimeUnit.SECONDS)) { "no frame" }
                parse += ms(t1 - t0)
                toLayout += ms(tLayout - t0)
                toFrame += ms(tFrame - t0)
                log("open run ${run + 1}: setMarkdown %.1f ms, first layout %.1f ms, first frame %.1f ms".format(parse.last(), toLayout.last(), toFrame.last()))
                e.idle()
            }
            val lines = e.ui { current.layout.lineCount }
            log("open, $RUNS runs: setMarkdown [${fmt(parse)}] median %.1f ms".format(median(parse)))
            log("open, $RUNS runs: first layout [${fmt(toLayout)}] median %.1f ms ($lines lines)".format(median(toLayout)))
            log("open, $RUNS runs: first frame [${fmt(toFrame)}] median %.1f ms".format(median(toFrame)))

            // a key in a Body paragraph in the middle of the note
            val v = current
            e.ui {
                val t = v.text.toString()
                val middle = Regex("Body \\d+ ").findAll(t).minBy { abs(it.range.first - t.length / 2) }
                v.setSelection(middle.range.first + 4) // after "Body"
            }
            /** One key: the time of `commitText`, and (when [frame]) up to the next committed frame. */
            fun key(frame: Boolean): Pair<Double, Double> {
                val drawn = CountDownLatch(1)
                var tFrame = 0L
                val (t0, t1) = e.ui {
                    val ic = v.onCreateInputConnection(EditorInfo())!!
                    val t0 = System.nanoTime()
                    ic.commitText("a", 1)
                    val t1 = System.nanoTime()
                    if (frame) v.viewTreeObserver.registerFrameCommitCallback { tFrame = System.nanoTime(); drawn.countDown() }
                    t0 to t1
                }
                if (!frame) return ms(t1 - t0) to 0.0
                check(drawn.await(5, TimeUnit.SECONDS)) { "no frame after a key" }
                return ms(t1 - t0) to ms(tFrame - t0)
            }
            val first = (1..KEYS).map { Thread.sleep(600); key(frame = true) } // each one starts a typing run
            Thread.sleep(600)
            val inRun = (1..KEYS).map { Thread.sleep(50); key(frame = false) } // well inside the 500 ms of a run
            log("key, first of a run (x$KEYS): commitText [${fmt(first.map { it.first })}] median %.1f ms, max %.1f ms".format(median(first.map { it.first }), first.maxOf { it.first }))
            log("key, first of a run (x$KEYS): to frame [${fmt(first.map { it.second })}] median %.1f ms".format(median(first.map { it.second })))
            log("key, inside a run (x$KEYS): commitText [${fmt(inRun.map { it.first })}] median %.1f ms, max %.1f ms".format(median(inRun.map { it.first }), inRun.maxOf { it.first }))

            // where the time of a key goes: the whole-note passes a key may run
            val (serialize, format) = e.ui {
                val a = System.nanoTime(); v.markdown(); val b = System.nanoTime(); v.formatState(); val c = System.nanoTime()
                ms(b - a) to ms(c - b)
            }
            log("whole note: markdown() %.1f ms, formatState() %.1f ms".format(serialize, format))

            // the same key without the view: the engine's own pass over a detached Editable
            val detached = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), e.style))
            val normalizer = Normalizer(e.style)
            val at = Regex("Body \\d+ ").findAll(detached).minBy { abs(it.range.first - detached.length / 2) }.range.first + 4
            val engineOnly = (1..KEYS).map {
                val t0 = System.nanoTime()
                val origin = normalizer.originKind(detached, at)
                detached.replace(at, at, "a")
                normalizer.normalize(detached, at, at + 1, origin)
                ms(System.nanoTime() - t0)
            }
            log("key, Normalizer on a detached Editable (x$KEYS): [${fmt(engineOnly)}] median %.1f ms".format(median(engineOnly)))

            // and a plain EditText holding the same text without the engine, with all / none / all but one kind of its spans:
            // what the platform's layout of this text costs per key, and which spans make it so
            e.ui { e.container.removeView(v) }
            val variants = listOf<Pair<String, (Any) -> Boolean>>(
                "all spans" to { _ -> false },
                "no spans" to { _ -> true },
                "without ListItemSpan" to { it is ListItemSpan },
                "without HeadingSpan" to { it is HeadingSpan },
                "without QuoteSpan" to { it is QuoteSpan },
                "without MonoSpan" to { it is MonoSpan },
                "without MarkSpan" to { it is MarkSpan },
                "without ObjectSpan" to { it is ObjectSpan },
            )
            for ((name, drop) in variants) {
                val text = DocBinder.build(NoteMarkdown.parse(md), e.style).also { t -> t.getSpans(0, t.length, Any::class.java).filter(drop).forEach(t::removeSpan) }
                val plain = e.ui {
                    AppCompatEditText(ContextThemeWrapper(e.activity, androidx.appcompat.R.style.Theme_AppCompat_Light)).also {
                        it.setText(text, TextView.BufferType.EDITABLE)
                        e.container.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                }
                e.idle()
                e.ui { plain.setSelection(at) }
                val platform = (1..5).map {
                    Thread.sleep(50)
                    e.ui {
                        val ic = plain.onCreateInputConnection(EditorInfo())!!
                        val t0 = System.nanoTime()
                        ic.commitText("a", 1)
                        ms(System.nanoTime() - t0)
                    }
                }
                e.ui { e.container.removeView(plain) }
                log("key, plain EditText, $name (x5): commitText [${fmt(platform)}] median %.1f ms".format(median(platform)))
            }

            assertTrue("a note of $blocks paragraphs opens (to the first layout) in ≤ 300 ms: median %.1f ms of [${fmt(toLayout)}]".format(median(toLayout)), median(toLayout) <= 300.0)
        }
    }

    private companion object {
        const val TAG = "NativeEditorPerf"
        const val RUNS = 5
        const val KEYS = 10
    }
}
