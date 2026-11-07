package app.tessera.ui.editor.components

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.Inline
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.tableZones
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-2 gate (plan E1): in the real host (`NoteBody` + `TesseraEditText`) a table is an overlay on its span, and real touches (injected into the
 * window, no shortcuts) behave: a vertical swipe that starts on the table scrolls the document, a tap in a cell focuses it and brings the
 * keyboard, a sideways swipe scrolls the wide table inside its overlay, and the overlay never lags behind the span while the document scrolls.
 * The numbers go to logcat tag GATE (`adb -s emulator-5556 logcat -d -s GATE`).
 */
class NoteBodyTableGateTest {
    private val instr = InstrumentationRegistry.getInstrumentation()

    private fun log(msg: String) = Log.i("GATE", msg)

    private val grid = TableGrid(
        headers = listOf("Name", "Qty", "Price", "Note"),
        rows = listOf(listOf("apple", "3", "1.20", "red"), listOf("pear", "5", "0.90", "green")),
    )

    private val markdown: String = run {
        fun p(i: Int) = NoteBlock.Text(ParagraphKind.Body, Inline("Paragraph $i lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor."))
        NoteMarkdown.serialize(NoteDoc((1..12).map(::p) + NoteBlock.Table(grid) + (13..24).map(::p)))
    }

    private inner class Rig : AutoCloseable {
        val scenario: ActivityScenario<ComponentActivity> = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var activity: ComponentActivity
        val bars = CopyOnWriteArrayList<TableBarState?>()
        val kb: TableGboard

        init {
            scenario.onActivity { a ->
                activity = a
                WindowCompat.setDecorFitsSystemWindows(a.window, false)
                a.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                a.setContent {
                    TesseraTheme(darkTheme = false, dynamicColor = false) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                            NoteBody(
                                markdown = markdown,
                                onMarkdownChange = {},
                                onActionsReady = {},
                                onFormatStateChanged = {},
                                editable = true,
                                onTableBarChanged = { bars += it },
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            kb = TableGboard(instr)
            val until = SystemClock.uptimeMillis() + 10_000
            while (host() == null && SystemClock.uptimeMillis() < until) Thread.sleep(100)
            settle(1500)
            check(ui { host()!!.editor.tableZones().size } == 1) { "no table in the editor" }
        }

        fun host(): TableOverlayHost? {
            fun find(v: View): TableOverlayHost? =
                if (v is TableOverlayHost) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            return find(activity.window.decorView)
        }

        val editor: TesseraEditText get() = host()!!.editor
        fun <T> ui(block: () -> T): T { var r: Result<T>? = null; instr.runOnMainSync { r = runCatching(block) }; return r!!.getOrThrow() }
        fun settle(ms: Long = 400) { instr.waitForIdleSync(); Thread.sleep(ms); instr.waitForIdleSync() }
        fun dp(v: Float) = (v * activity.resources.displayMetrics.density).toInt()
        fun hostLoc(): IntArray = ui { IntArray(2).also { host()!!.getLocationOnScreen(it) } }

        /** The table's rect on the screen. */
        fun tableOnScreen(): Rect {
            val l = hostLoc()
            val r = ui { editor.tableZones().single().rect }
            return Rect(r.left + l[0], r.top + l[1], r.right + l[0], r.bottom + l[1])
        }

        fun scrollTableTo(yInHost: Int) {
            ui {
                val z = editor.tableZones().single()
                editor.scrollTo(0, (editor.scrollY + z.rect.top - yInHost).coerceAtLeast(0))
            }
            settle(500)
        }

        fun inject(ev: MotionEvent) {
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            instr.uiAutomation.injectInputEvent(ev, true)
            ev.recycle()
        }

        /** A drag with a finger, one move per frame ([frameMs]); returns when the finger is up. */
        fun drag(x0: Int, y0: Int, dx: Int, dy: Int, steps: Int, frameMs: Long = 16) {
            val t0 = SystemClock.uptimeMillis()
            inject(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x0.toFloat(), y0.toFloat(), 0))
            for (i in 1..steps) {
                val at = t0 + i * frameMs
                while (SystemClock.uptimeMillis() < at) Thread.sleep(1)
                inject(MotionEvent.obtain(t0, at, MotionEvent.ACTION_MOVE, (x0 + dx * i / steps).toFloat(), (y0 + dy * i / steps).toFloat(), 0))
            }
            inject(MotionEvent.obtain(t0, t0 + steps * frameMs + frameMs, MotionEvent.ACTION_UP, (x0 + dx).toFloat(), (y0 + dy).toFloat(), 0))
        }

        /** The accessibility nodes of the cells (EditText) of the overlay, left to right. */
        fun cellNodes(within: Rect): List<Rect> {
            val root = kb.ua.rootInActiveWindow ?: return emptyList()
            val out = ArrayList<Rect>()
            fun walk(n: AccessibilityNodeInfo) {
                if (n.className?.toString()?.contains("EditText") == true && n.text != null) {
                    val b = Rect().also { n.getBoundsInScreen(it) }
                    if (b.top >= within.top - 2 && b.bottom <= within.bottom + 2) out += b // the cells, not the editor that holds the whole note
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
            }
            walk(root)
            return out
        }

        override fun close() { runCatching { ui { host()?.commitCells(); editor.dispose() } }; scenario.close() }
    }

    @Test
    fun gate() {
        Rig().use { rig ->
            val out = StringBuilder()
            val editor = rig.editor
            // 1. vertical swipe that starts on the table
            rig.scrollTableTo(rig.dp(300f))
            val table = rig.tableOnScreen()
            val x = table.left + table.width() / 2
            val y = table.top + table.height() / 2
            val s0 = rig.ui { editor.scrollY }
            rig.drag(x, y, 0, -rig.dp(160f), steps = 20)
            rig.settle(400)
            val s1 = rig.ui { editor.scrollY }
            val tableAfter = rig.tableOnScreen()
            rig.drag(x, tableAfter.top + tableAfter.height() / 2, 0, rig.dp(100f), steps = 15)
            rig.settle(400)
            val s2 = rig.ui { editor.scrollY }
            out.append("swipe up 160dp (${rig.dp(160f)}px) from the table: scrollY $s0 -> $s1; swipe down 100dp (${rig.dp(100f)}px): $s1 -> $s2; ")
            log("vertical :: $out")
            assertTrue("a vertical swipe that starts on the table must scroll the document (scrollY $s0 -> $s1)", s1 - s0 >= rig.dp(100f))
            assertTrue("the swipe down must scroll back (scrollY $s1 -> $s2)", s1 - s2 >= rig.dp(60f))

            // 2. tap a cell: focus and keyboard, the editor has no caret there
            rig.scrollTableTo(rig.dp(220f))
            val t2 = rig.tableOnScreen()
            val sel0 = rig.ui { editor.selectionStart }
            rig.kb.tap(t2.left + rig.dp(60f), t2.top + rig.dp(24f))
            rig.kb.awaitShown()
            rig.settle(500)
            val field = rig.kb.focusedField()
            val imeTop = rig.kb.imeTop()
            val editorFocused = rig.ui { editor.isFocused }
            val bar = rig.bars.lastOrNull()
            log("tap :: scrollY=${rig.ui { editor.scrollY }} hostH=${rig.ui { rig.host()!!.height }} overlayTY=${rig.ui { rig.host()!!.getChildAt(1).translationY }} zone=${rig.ui { editor.tableZones().single().rect }} hostLoc=${rig.hostLoc().toList()}")
            log("tap :: focused field=$field in table=$t2, imeTop=$imeTop, editorFocused=$editorFocused, editor selection $sel0 -> ${rig.ui { editor.selectionStart }}, table bar=${bar != null}")
            assertNotNull("a cell must have the input focus after a tap", field)
            val tNow = rig.tableOnScreen()
            assertTrue("the focused field is in the table ($field, table now $tNow)", Rect.intersects(field!!, tNow))
            assertTrue("the table is above the keyboard (table bottom ${tNow.bottom}, keyboard top $imeTop)", tNow.bottom <= imeTop!!)
            assertNotNull("the keyboard must be shown", imeTop)
            assertFalse("the editor lost the focus to the cell", editorFocused)
            assertNotNull("the contextual table bar state is published", bar)
            rig.kb.type("zz")
            rig.settle(900) // the pause commit
            val md = rig.ui { editor.markdown() }
            log("tap :: markdown row = ${md.lines().firstOrNull { it.startsWith("|") }}")
            assertTrue("typing in the cell reaches the note", md.contains("zz"))
            rig.kb.run("input keyevent 4"); rig.settle(300) // keyboard away

            // 3. sideways swipe scrolls the wide table inside the overlay
            rig.scrollTableTo(rig.dp(220f))
            val t3 = rig.tableOnScreen()
            val before = rig.cellNodes(t3).minByOrNull { it.left }
            val sc0 = rig.ui { editor.scrollY }
            rig.drag(t3.right - rig.dp(40f), t3.top + t3.height() / 2, -rig.dp(200f), 0, steps = 20)
            rig.settle(500)
            val after = rig.cellNodes(t3).minByOrNull { it.left }
            val sc1 = rig.ui { editor.scrollY }
            log("horizontal :: first cell left $before -> $after (table ${t3}), document scrollY $sc0 -> $sc1")
            assertTrue("the first cell moved left by the swipe (the node is clipped at the table edge: its right side moves)", before != null && after != null && before.right - after.right >= rig.dp(60f))
            assertEquals("a sideways swipe does not scroll the document", sc0, sc1)

            // 4. the overlay sits on its span while the document scrolls (real drag from the text, one move per frame)
            rig.scrollTableTo(rig.dp(420f))
            val host = rig.host()!!
            val overlay = host.getChildAt(1)
            var frames = 0
            var off = 0
            var maxOff = 0
            val listener = android.view.ViewTreeObserver.OnDrawListener {
                val z = editor.tableZones().firstOrNull() ?: return@OnDrawListener
                frames++
                val d = kotlin.math.abs(overlay.translationY - z.rect.top)
                if (d > 0.5f) { off++; maxOff = maxOf(maxOff, d.toInt()) }
            }
            rig.ui { host.viewTreeObserver.addOnDrawListener(listener) }
            val l = rig.hostLoc()
            rig.drag(l[0] + rig.dp(30f), l[1] + rig.ui { host.height } - rig.dp(60f), 0, -rig.dp(500f), steps = 36)
            rig.settle(600)
            rig.ui { host.viewTreeObserver.removeOnDrawListener(listener) }
            log("lag :: frames drawn=$frames, frames where the overlay is off its span=$off (max $maxOff px)")
            assertTrue("frames were drawn while scrolling", frames > 10)
            assertEquals("the overlay never leaves its span", 0, off)
            rig.kb.ua.takeScreenshot()?.let { savePng(rig.activity, "p2-gate-final", it) }
        }
    }
}
