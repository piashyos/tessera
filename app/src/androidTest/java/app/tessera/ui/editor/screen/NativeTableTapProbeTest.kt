package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
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
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.components.TableOverlayHost
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Investigation of T2 (a tap in a cell leaves the caret at 0 in the app, not in the bare `NoteBody`): the same taps in the app's editor screen and
 * in the gate's rig (`NoteBody` alone in an activity), logged — the touch at the window (and whether it was handled), whether the table host took
 * it (its own touch listener runs only when it intercepts), whether the editor under the overlay got it, then the focused cell and its selection
 * at moments after the finger is up (the accessibility tree read again each time). Logcat tag `TableTapProbe`. It asserts nothing: it records.
 */
@HiltAndroidTest
class NativeTableTapProbeTest : NativeAppTest() {
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(4, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private val ctx get() = app.instrumentation.targetContext
    private val events = CopyOnWriteArrayList<String>()
    private var t0 = 0L
    private fun log(msg: String) = Log.i(TAG, msg)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun act(ev: MotionEvent) = MotionEvent.actionToString(ev.actionMasked)
    private fun now() = SystemClock.uptimeMillis() - t0

    private val grid = TableGrid(
        listOf("Fruit", "Note"),
        listOf(listOf("watermelon", "big"), listOf("apple", "red")),
    )
    private val md = note("Probe", grid, "after the table")

    /** The window's touches (with the result of their dispatch), the host's own touch handling (an intercept), the editor's touches. */
    private fun install() = app.ui {
        val a = app.activity()
        val w = a.window
        val cb = w.callback
        if (cb !is Probe) w.callback = Probe(cb)
        val host = findView(w.decorView, TableOverlayHost::class.java)!!
        host.setOnTouchListener { _, ev -> events += "${now()} host took ${act(ev)}"; false }
        host.editor.setOnTouchListener { _, ev -> events += "${now()} editor got ${act(ev)}"; false }
    }

    private inner class Probe(private val cb: Window.Callback) : Window.Callback by cb {
        val inner get() = cb

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val r = cb.dispatchTouchEvent(ev)
            events += "${now()} window ${act(ev)} (%.0f, %.0f) handled $r".format(ev.rawX, ev.rawY)
            return r
        }
    }

    /** The focused cell and its selection now, and the keyboard. */
    private fun state(t: Tables, e: TesseraEditText) = "${now()} focused ${t.focused()?.first} sel ${t.focusedSelection()} ime ${t.imeShown(e)} editorFocused ${app.ui { e.isFocused }}"

    /** A finger on [x], [y]: down, [hold] ms, up; then the state at moments after it. */
    private var readFrom = 0L

    private fun press(t: Tables, e: TesseraEditText, label: String, x: Int, y: Int, hold: Long) {
        events.clear()
        t0 = SystemClock.uptimeMillis()
        val d = SystemClock.uptimeMillis()
        t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
        if (hold > 0) Thread.sleep(hold)
        val stateDown = state(t, e)
        t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
        val after = ArrayList<String>()
        after += "while down: $stateDown"
        var prev = 0L
        for (wait in listOf(0L, 50L, 150L, 300L, 600L, 1_200L).filter { it >= readFrom }) {
            Thread.sleep(wait - prev)
            prev = wait
            after += state(t, e)
        }
        log("$label: touches ${events.toList()}")
        log("$label: after ${after.joinToString(" | ")}")
    }

    /** The middle of the word "watermelon" in its cell: the caret should stand there after a tap, not at the start or the end. */
    private fun middleOfWatermelon(t: Tables): Pair<Int, Int> {
        val c = t.cell("watermelon")
        return c.left + dp(8) + dp(38) to c.centerY() // padding 8dp, the word is ~76dp at bodyMedium: its middle
    }

    private fun probeAll(where: String, e: TesseraEditText, listeners: Boolean = true) {
        val t = Tables(app)
        t.awaitOverlays(e, 1)
        if (listeners) install()
        val (x, y) = middleOfWatermelon(t)
        press(t, e, "$where, keyboard down, tap in the middle of watermelon", x, y, 0)
        app.settle(800)
        // the keyboard up (the focus in another cell), then a tap in the middle of the word
        val big = t.cell("big")
        t.tap(big.right - dp(20), big.centerY())
        t.awaitIme(e)
        val (x2, y2) = middleOfWatermelon(t)
        press(t, e, "$where, keyboard up, tap in the middle of watermelon", x2, y2, 0)
        app.settle(800)
        val apple = t.cell("apple")
        press(t, e, "$where, keyboard up, long press in the middle of apple", apple.left + dp(8) + dp(18), apple.centerY(), 900)
        t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-probe-$where", it) }
    }

    /** Where the first key goes after a tap in the middle of a word: nothing focused before, the note's text focused before, another cell focused before. */
    private fun keys(where: String, e: TesseraEditText) {
        val t = Tables(app)
        t.awaitOverlays(e, 1)
        fun tapMiddleAndType(word: String, half: Int) {
            val c = t.cell(word)
            t.tap(c.left + dp(8) + dp(half), c.centerY())
            val sel = t.focusedSelection()
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(300)
            log("$where: tap in the middle of \"$word\" -> selection $sel, a Z typed -> cells ${t.cells(t.onScreen(e)).map { it.first }}")
        }
        tapMiddleAndType("watermelon", 38) // nothing focused
        t.tapText(e, "after the table") // the note's text focused
        t.awaitIme(e)
        tapMiddleAndType("apple", 18)
        tapMiddleAndType("big", 10) // another cell of the table focused
    }

    @Test
    fun firstKeyInTheApp() {
        app.launch()
        app.seed("qa-probe-keys", md, System.currentTimeMillis())
        keys("app", app.open("qa-probe-keys"))
    }

    @Test
    fun firstKeyInTheBareNoteBody() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { a ->
                WindowCompat.setDecorFitsSystemWindows(a.window, false)
                a.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                a.setContent {
                    TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.WARM) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                            NoteBody(
                                markdown = note(*(1..6).map(::lorem).toTypedArray(), grid, "after the table", *(7..12).map(::lorem).toTypedArray()),
                                onMarkdownChange = {}, onActionsReady = {}, onFormatStateChanged = {}, editable = true,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { app.editorOrNull() != null }
            val e = app.editorOrNull()!!
            Tables(app).scrollTableTo(e, dp(160))
            keys("rig", e)
        } finally {
            scenario.close()
        }
    }

    /**
     * The time between the finger's down and up decides where the caret goes in a cell that had no focus: each tap on a note opened afresh (no
     * focus anywhere), in the middle of "watermelon", nothing read between down and up; then a Z typed.
     */
    @Test
    fun theTimeOfATapDecidesTheCaret() {
        app.launch()
        val rows = ArrayList<String>()
        for ((k, gap) in listOf(0L, 30L, 60L, 100L, 150L, 250L).withIndex()) for (spot in listOf("middle", "past the end")) {
            val id = "qa-probe-gap-$k-${spot.replace(' ', '-')}"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            val c = t.cell("watermelon")
            val x = if (spot == "middle") c.left + dp(8) + dp(38) else c.right - dp(20)
            val d = SystemClock.uptimeMillis()
            t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), c.centerY().toFloat(), 0))
            if (gap > 0) Thread.sleep(gap)
            t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), c.centerY().toFloat(), 0))
            app.settle(600)
            val sel = t.focusedSelection()
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(300)
            val cell = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
            rows += "down->up ${gap} ms, $spot: selection $sel, Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("tap timing: $it") }
    }

    /**
     * The same 100 ms tap with and without a read of the accessibility tree while the finger is down (what a screen reader or the probe does), and
     * a long press without any read; a Z typed after each shows where the caret or the selection is.
     */
    @Test
    fun aReadOfTheTreeWhileTheFingerIsDownAndALongPress() {
        app.launch()
        val rows = ArrayList<String>()
        for ((k, case) in listOf("tap 100 ms, no read", "tap 100 ms, a read while down", "long press 900 ms, no read", "long press 900 ms, a read while down").withIndex()) {
            val id = "qa-probe-read-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            val c = t.cell("watermelon")
            val x = c.left + dp(8) + dp(38)
            val d = SystemClock.uptimeMillis()
            t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), c.centerY().toFloat(), 0))
            val hold = if (case.startsWith("long")) 900L else 100L
            if (case.endsWith("read while down")) { Thread.sleep(hold / 2); t.focused(); Thread.sleep(hold / 2) } else Thread.sleep(hold)
            t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), c.centerY().toFloat(), 0))
            app.settle(600)
            val sel = t.focusedSelection()
            t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-probe-read-$k", it) }
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(300)
            val cell = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") || it.contains("Z") }
            rows += "$case: selection $sel, Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("read while down: $it") }
    }

    /**
     * T9 on 8fc6aa2: the drop seen under the table is the cell's own insertion handle (its x is the cell's caret), not the editor's. Does it stay
     * where the tap was when the note moves after the tap (the keyboard coming up, a scroll)? Shots p2t-handle-<k>, the cell's rect logged.
     */
    @Test
    fun theCellsHandleAndTheNoteMoving() {
        app.launch()
        val long = note("Probe", grid, *(1..20).map(::lorem).toTypedArray())
        val rows = ArrayList<String>()
        fun shot(k: String, t: Tables, what: String) {
            app.settle(400)
            val c = t.cells(t.onScreen(app.editorOrNull()!!)).firstOrNull { it.first.startsWith("waterm") }?.second
            t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-handle-$k", it) }
            rows += "$k $what: cell watermelon at $c, selection ${t.focusedSelection()}"
        }
        // (a) keyboard down: tap, the keyboard comes up and the note moves
        app.seed("qa-probe-handle-a", long, System.currentTimeMillis())
        var e = app.open("qa-probe-handle-a")
        var t = Tables(app)
        t.awaitOverlays(e, 1)
        var c = t.cell("watermelon")
        rows += "a tap at (${c.left + dp(46)}, ${c.centerY()}), cell at $c"
        t.tap(c.left + dp(46), c.centerY())
        t.awaitIme(e)
        shot("a", t, "keyboard came up after the tap")
        app.back()
        // (b) keyboard up already (focus in big), tap, then the note scrolls under a swipe on the text below
        app.seed("qa-probe-handle-b", long, System.currentTimeMillis())
        e = app.open("qa-probe-handle-b")
        t = Tables(app)
        t.awaitOverlays(e, 1)
        t.tapCell("big")
        t.awaitIme(e)
        app.settle(800)
        c = t.cell("watermelon")
        rows += "b tap at (${c.left + dp(46)}, ${c.centerY()}), cell at $c"
        t.tap(c.left + dp(46), c.centerY())
        shot("b0", t, "tap with the keyboard up")
        t.shell("input swipe 360 ${c.bottom + dp(150)} 360 ${c.bottom + dp(90)} 400")
        shot("b1", t, "after a swipe of 60dp up on the text")
        app.settle(1500)
        shot("b2", t, "1.5 s later")
        app.back()
        rows.forEach { log("handle: $it") }
    }

    /** T9/T12: the hold of the tap (60, 100, 300 ms) and the note (short, long) against where the cell's handle ends after the keyboard came up. */
    @Test
    fun theHoldOfATapAndTheCellsHandle() {
        app.launch()
        val notes = listOf("short" to md, "long" to note("Probe", grid, *(1..20).map(::lorem).toTypedArray()))
        val rows = ArrayList<String>()
        var k = 0
        for ((name, body) in notes) for (hold in listOf(60L, 100L, 300L)) {
            val id = "qa-probe-hold-${k}"
            app.seed(id, body, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            val c = t.cell("watermelon")
            val x = c.left + dp(46)
            val d = SystemClock.uptimeMillis()
            t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), c.centerY().toFloat(), 0))
            Thread.sleep(hold)
            t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), c.centerY().toFloat(), 0))
            t.awaitIme(e)
            app.settle(1000)
            val after = t.cells(t.onScreen(e)).firstOrNull { it.first == "watermelon" }?.second
            t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-hold-$k", it) }
            rows += "$k $name note, hold $hold ms: cell at $c -> $after, selection ${t.focusedSelection()}"
            app.back()
            k++
        }
        rows.forEach { log("hold: $it") }
    }

    /**
     * The guard of 8fc6aa2 (a cell focused with the selection (0, 0) and a text puts the caret at its end) on the one path that focuses a cell
     * with a fresh field: Enter at the end of "watermelon" moves the focus to "big" (its `TextFieldValue` starts at `TextRange.Zero`), then Z.
     * And T12: shots of the cell's handle 0.6, 1.5 and 3 s after a tap that brings the keyboard up.
     */
    @Test
    fun theGuardAndTheHandleOverTime() {
        app.launch()
        app.seed("qa-probe-guard", md, System.currentTimeMillis())
        var e = app.open("qa-probe-guard")
        var t = Tables(app)
        t.awaitOverlays(e, 1)
        t.tapCell("watermelon")
        t.awaitIme(e)
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MOVE_END)
        app.settle(300)
        val before = "${t.focused()?.first} ${t.focusedSelection()}"
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
        app.settle(600)
        val after = "${t.focused()?.first} ${t.focusedSelection()}"
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
        app.settle(300)
        val typed = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
        log("guard: before Enter $before, after Enter $after, Z -> \"$typed\"")
        app.back()
        app.seed("qa-probe-handle-time", md, System.currentTimeMillis())
        e = app.open("qa-probe-handle-time")
        t = Tables(app)
        t.awaitOverlays(e, 1)
        val c = t.cell("watermelon")
        val d = SystemClock.uptimeMillis()
        t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, (c.left + dp(46)).toFloat(), c.centerY().toFloat(), 0))
        Thread.sleep(100)
        t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, (c.left + dp(46)).toFloat(), c.centerY().toFloat(), 0))
        val up = SystemClock.uptimeMillis()
        for ((k, at) in listOf(600L, 1500L, 3000L).withIndex()) {
            SystemClock.sleep((up + at - SystemClock.uptimeMillis()).coerceAtLeast(0))
            t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-handle-time-$k", it) }
        }
        log("handle over time: tap at y ${c.centerY()}, cell now ${t.cells(t.onScreen(e)).firstOrNull { it.first == "watermelon" }?.second}")
        app.back()
    }

    /** Which part of the probe brings the (0, 0): its touch listeners on the host and the editor (and the window callback), or its reads right after UP. */
    @Test
    fun inTheAppWithoutTheListeners() {
        app.launch()
        app.seed("qa-probe-nolisten", md, System.currentTimeMillis())
        probeAll("app without listeners", app.open("qa-probe-nolisten"), listeners = false)
    }

    @Test
    fun inTheAppReadingFrom300Ms() {
        app.launch()
        app.seed("qa-probe-late", md, System.currentTimeMillis())
        readFrom = 300L
        probeAll("app reading from 300 ms", app.open("qa-probe-late"))
    }

    /** The first tap (keyboard down) with one kind of read while the finger is down, a fresh note each; Z after it shows where the caret is. */
    @Test
    fun whatIsReadWhileTheFingerIsDown() {
        app.launch()
        val rows = ArrayList<String>()
        val reads: List<Pair<String, (Tables, TesseraEditText) -> Unit>> = listOf(
            "nothing" to { _, _ -> },
            "e.isFocused on the main thread" to { _, e -> app.ui { e.isFocused } },
            "the ime insets on the main thread" to { t, e -> t.imeShown(e) },
            "the focused cell (a11y)" to { t, _ -> t.focused() },
            "the focused cell and its selection (a11y twice)" to { t, _ -> t.focused(); t.focusedSelection() },
            "all of it (as press())" to { t, e -> state(t, e) },
        )
        for ((k, read) in reads.withIndex()) {
            val id = "qa-probe-what-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            val (x, y) = middleOfWatermelon(t)
            val d = SystemClock.uptimeMillis()
            t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
            read.second(t, e)
            val gap = SystemClock.uptimeMillis() - d
            t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
            t.awaitIme(e)
            app.settle(500)
            val sel = t.focusedSelection()
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(300)
            val typed = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
            rows += "${read.first} (down to up $gap ms): selection $sel, Z -> \"$typed\""
            app.back()
        }
        rows.forEach { log("while down: $it") }
    }

    /** Is the (0, 0) that press() logs the caret itself? A Z after the first tap (keyboard down) and a Q after the long press on apple. */
    @Test
    fun pressThenZ() {
        app.launch()
        app.seed("qa-probe-pressz", md, System.currentTimeMillis())
        val e = app.open("qa-probe-pressz")
        val t = Tables(app)
        t.awaitOverlays(e, 1)
        val (x, y) = middleOfWatermelon(t)
        press(t, e, "press then Z, keyboard down, tap in the middle of watermelon", x, y, 0)
        app.settle(500)
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
        app.settle(300)
        val first = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
        val apple = t.cell("apple")
        press(t, e, "press then Z, keyboard up, long press in the middle of apple", apple.left + dp(8) + dp(18), apple.centerY(), 900)
        app.settle(500)
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Q)
        app.settle(300)
        val second = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("q") }
        log("press then key: after the tap Z -> \"$first\", after the long press Q -> \"$second\"")
    }

    /**
     * T2, the long press (P2-3d): is "the word is not selected" the harness? The same press on the middle of "watermelon", nothing focused before,
     * three ways: (a) `input swipe x y x y 1000` from the shell, as adb does it; (b) what `UiDevice.swipe(x, y, x, y, 200)` injects (DOWN, a MOVE
     * on the same point every 5 ms, UP; UiAutomator is not a dependency here); (c) the tests' way so far: DOWN, sleep 1000 ms, UP. After each: the
     * cell's selection from the tree, a shot, then Z: a selected word is replaced ("z"), a caret takes it in ("watermzelon"). The window's touches
     * are logged (action, ms from DOWN, position).
     */
    @Test
    fun aLongPressThreeWays() {
        app.launch()
        val rows = ArrayList<String>()
        val ways = listOf("a: input swipe x y x y 1000", "b: DOWN, MOVE every 5 ms x200, UP", "c: DOWN, sleep 1000, UP")
        for ((k, way) in ways.withIndex()) {
            val id = "qa-probe-lp-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            install()
            val (x, y) = middleOfWatermelon(t)
            events.clear()
            t0 = SystemClock.uptimeMillis()
            when (k) {
                0 -> t.shell("input swipe $x $y $x $y 1000")
                1 -> {
                    val d = SystemClock.uptimeMillis()
                    t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
                    repeat(200) {
                        SystemClock.sleep(5)
                        t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x.toFloat(), y.toFloat(), 0))
                    }
                    t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
                }
                else -> {
                    val d = SystemClock.uptimeMillis()
                    t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
                    SystemClock.sleep(1000)
                    t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
                }
            }
            app.settle(1200)
            val sel = t.focusedSelection()
            t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-lp-$k", it) }
            val touches = events.toList().let { ev -> ev.filter { "MOVE" !in it } + "${ev.count { "MOVE" in it }} moves" }
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(400)
            val cell = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
            rows += "$way at ($x, $y): selection $sel, Z -> \"$cell\"; touches $touches"
            app.back()
        }
        rows.forEach { log("long press: $it") }
    }

    /**
     * T2, the long press, the pointer side: the touches that reach the overlay's AndroidComposeView (a listener that returns false: Compose still
     * gets them), a CANCEL if one comes, the cell's state 100, 300, 600, 1200 ms after UP and shots at 100 and 600 ms (a selection that shows and
     * goes). Twice: nothing focused before, and the keyboard up with another cell focused. The press is `input swipe x y x y 1000`.
     */
    @Test
    fun aLongPressPointerLog() = pointerLog(innerListener = true)

    /** The same without the listener on the AndroidComposeView (it overrides dispatchTouchEvent: the listener never runs, but is it harmless?). */
    @Test
    fun aLongPressPointerLogWithoutTheComposeListener() = pointerLog(innerListener = false)

    private fun pointerLog(innerListener: Boolean) {
        app.launch()
        val rows = ArrayList<String>()
        for ((k, case) in listOf("nothing focused", "another cell focused").withIndex()) {
            val id = "qa-probe-lpp-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            if (k == 1) { t.tapCell("big"); t.awaitIme(e); app.settle(600) }
            install()
            app.ui {
                val host = findView(app.activity().window.decorView, TableOverlayHost::class.java)!!
                val inner = findView(host.getChildAt(1), androidx.compose.ui.platform.AbstractComposeView::class.java)?.getChildAt(0)
                if (innerListener) inner?.setOnTouchListener { _, ev -> events += "${now()} compose ${act(ev)} (%.0f, %.0f)".format(ev.rawX, ev.rawY); false }
                log("pointer: compose view ${inner?.javaClass?.simpleName}")
            }
            val (x, y) = middleOfWatermelon(t)
            events.clear()
            t0 = SystemClock.uptimeMillis()
            Log.d("TableBar", "QA long press $k: down at ($x, $y)") // the press in the host's log (logcat -s TableBar)
            t.shell("input swipe $x $y $x $y 1000")
            val up = SystemClock.uptimeMillis()
            Log.d("TableBar", "QA long press $k: up")
            val states = ArrayList<String>()
            for ((i, at) in listOf(100L, 300L, 600L, 1200L).withIndex()) {
                SystemClock.sleep((up + at - SystemClock.uptimeMillis()).coerceAtLeast(0))
                if (at == 100L || at == 600L) t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-lpp-$k-$at", it) }
                states += "$at ms: ${t.focused()?.first} ${t.focusedSelection()}"
                Log.d("TableBar", "QA long press $k: read at $at ms: ${states.last()}")
            }
            Log.d("TableBar", "QA long press $k: Z")
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(400)
            val cell = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("z") }
            val ev = events.toList()
            rows += "listener $innerListener, $case at ($x, $y): ${ev.filter { "MOVE" !in it }} + ${ev.count { "window ACTION_MOVE" in it }} window moves, ${ev.count { "compose ACTION_MOVE" in it }} compose moves; after UP ${states.joinToString(" | ")}; Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("pointer: $it") }
    }

    /**
     * P2-3f, the caret at 0 after a long press: what of the pointer probe brings it — the reads of the tree 100 to 1200 ms after UP, the shots at
     * 100 and 600 ms, or both — on the native path. `input swipe x y x y 1000` on the middle of "watermelon", nothing focused, a fresh
     * note each time, Z after it.
     */
    @Test
    fun theCaretAfterALongPressReadsAgainstShots() {
        val rows = ArrayList<String>()
        app.launch()
        for ((k, case) in listOf("reads only", "shots only", "reads and shots", "nothing").withIndex()) {
            val id = "qa-probe-split-n-$k"
            app.seed(id, md, System.currentTimeMillis())
            app.tapCard(id)
            compose.waitUntil(15_000) { app.editorOrNull() != null }
            app.settle(1_000)
            val t = Tables(app)
            t.awaitCells(5_000) { "watermelon" in it }
            val (x, y) = middleOfWatermelon(t)
            Log.d("TableBar", "QA split native $case: long press")
            t.shell("input swipe $x $y $x $y 1000")
            val up = SystemClock.uptimeMillis()
            val reads = ArrayList<String>()
            for (at in listOf(100L, 300L, 600L, 1200L)) {
                SystemClock.sleep((up + at - SystemClock.uptimeMillis()).coerceAtLeast(0))
                if (k != 0 && k != 3 && (at == 100L || at == 600L)) t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-split-n-$k-$at", it) }
                if (k == 0 || k == 2) reads += "$at: ${t.focusedSelection()}"
            }
            app.settle(600)
            Log.d("TableBar", "QA split native $case: Z")
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(400)
            val cell = t.cells().map { it.first }.firstOrNull { it.contains("z") }
            rows += "native, $case: ${if (reads.isEmpty()) "" else "reads ${reads.joinToString(" ")}; "}Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("split: $it") }
    }

    /**
     * P2-3f: is the caret at 0 of the pointer probe its own harness? The same long press (`input swipe`, no reads) after: nothing installed; only
     * the window-callback wrapper of [install]; only the touch listeners on the host and the editor; both (= [install]).
     */
    @Test
    fun theCaretAfterALongPressAndTheProbesHooks() {
        app.launch()
        val rows = ArrayList<String>()
        for ((k, case) in listOf("nothing", "window callback only", "listeners only", "both (install)").withIndex()) {
            val id = "qa-probe-hooks-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            app.ui {
                val w = app.activity().window
                if ((k == 1 || k == 3) && w.callback !is Probe) w.callback = Probe(w.callback)
                if (k == 2 || k == 3) {
                    val host = findView(w.decorView, TableOverlayHost::class.java)!!
                    host.setOnTouchListener { _, ev -> events += "${now()} host took ${act(ev)}"; false }
                    host.editor.setOnTouchListener { _, ev -> events += "${now()} editor got ${act(ev)}"; false }
                }
            }
            val (x, y) = middleOfWatermelon(t)
            Log.d("TableBar", "QA hooks $case: long press")
            t.shell("input swipe $x $y $x $y 1000")
            app.settle(1_200)
            val sel = t.focusedSelection()
            Log.d("TableBar", "QA hooks $case: Z")
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(400)
            val cell = t.cells().map { it.first }.firstOrNull { it.contains("z") }
            rows += "$case: selection $sel, Z -> \"$cell\""
            app.ui { // the activity is the same for the next note: take the hooks off
                val w = app.activity().window
                (w.callback as? Probe)?.let { w.callback = it.inner }
            }
            app.back()
        }
        rows.forEach { log("hooks: $it") }
    }

    /**
     * P2-3f: the caret at 0 of the pointer probe came with a Z typed right after its last read (~1.25 s after UP); every probe that waited 0.6 s
     * more before Z had the caret at the press point. Here: `input swipe x y x y 1000` on the middle of "watermelon", Z at 0.3, 1.3 and 2.5 s
     * after UP, with the tree read every 150 ms until then or not, on the native path. A fresh note each time.
     */
    @Test
    fun theCaretAfterALongPressAndTheTimeOfTheKey() {
        val rows = ArrayList<String>()
        app.launch()
        var k = 0
        for (reads in listOf(false, true)) for (keyAt in listOf(300L, 1300L, 2500L)) {
            val path = "native"
            val id = "qa-probe-keyat-$path-${k++}"
            app.seed(id, md, System.currentTimeMillis())
            app.tapCard(id)
            compose.waitUntil(15_000) { app.editorOrNull() != null }
            app.settle(1_000)
            val t = Tables(app)
            t.awaitCells(5_000) { "watermelon" in it }
            val (x, y) = middleOfWatermelon(t)
            t.shell("input swipe $x $y $x $y 1000")
            val up = SystemClock.uptimeMillis()
            val seen = ArrayList<String>()
            if (reads) {
                var at = 100L
                while (at < keyAt - 50) {
                    SystemClock.sleep((up + at - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    seen += "${SystemClock.uptimeMillis() - up}:${t.focusedSelection()?.first}"
                    at += 150
                }
            }
            SystemClock.sleep((up + keyAt - SystemClock.uptimeMillis()).coerceAtLeast(0))
            val zAt = SystemClock.uptimeMillis() - up
            Log.d("TableBar", "QA key time $path reads=$reads: Z at $zAt ms after UP")
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(600)
            val cell = t.cells().map { it.first }.firstOrNull { it.contains("z") }
            rows += "$path, reads $reads, Z at $zAt ms: ${if (seen.isEmpty()) "" else "caret read ${seen.joinToString(" ")}; "}Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("key time: $it") }
    }

    /**
     * P2-3f: the probes that waited for the main thread to be idle (`app.settle`) before Z had the caret at the press point, the ones that only
     * slept had it at 0 even 2.5 s after UP. Here, 2.5 s after the long press (`input swipe x y x y 1000`): only a sleep; a runnable on the main
     * thread (`runOnMainSync`); `waitForIdleSync` (timed). Then the cell's selection from the tree and Z. A fresh note each.
     */
    @Test
    fun theCaretAfterALongPressAndAnIdleMainThread() {
        val rows = ArrayList<String>()
        app.launch()
        for ((k, how) in listOf("sleep only", "runOnMainSync", "waitForIdleSync").withIndex()) {
            val path = "native"
            val id = "qa-probe-idle-$path-$k"
            app.seed(id, md, System.currentTimeMillis())
            app.tapCard(id)
            compose.waitUntil(15_000) { app.editorOrNull() != null }
            app.settle(1_000)
            val t = Tables(app)
            t.awaitCells(5_000) { "watermelon" in it }
            val (x, y) = middleOfWatermelon(t)
            t.shell("input swipe $x $y $x $y 1000")
            SystemClock.sleep(2_500)
            val t0 = SystemClock.uptimeMillis()
            when (k) {
                1 -> app.instrumentation.runOnMainSync { }
                2 -> app.instrumentation.waitForIdleSync()
            }
            val took = SystemClock.uptimeMillis() - t0
            Log.d("TableBar", "QA idle $path $how: took $took ms, Z")
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(600)
            val cell = t.cells().map { it.first }.firstOrNull { it.contains("z") }
            rows += "$path, $how (took $took ms): Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("idle: $it") }
    }

    /**
     * P2-3f: the user's case of T2 — a tap in the middle of "watermelon" and a key right away, with no wait of the test between them (every
     * earlier tap probe went through `Tables.tap`, which settles 500 ms). Z at 150, 400 and 1000 ms after UP; the tap 60 ms (injected) and
     * `input tap` (shell). A fresh note each time.
     */
    @Test
    fun aTapAndAKeyRightAway() {
        val rows = ArrayList<String>()
        app.launch()
        var n = 0
        for (how in listOf("inject 60 ms", "input tap")) for (keyAt in listOf(150L, 400L, 1000L)) {
            val path = "native"
            val id = "qa-probe-tapnow-$path-${n++}"
            app.seed(id, md, System.currentTimeMillis())
            app.tapCard(id)
            compose.waitUntil(15_000) { app.editorOrNull() != null }
            app.settle(1_000)
            val t = Tables(app)
            t.awaitCells(5_000) { "watermelon" in it }
            val (x, y) = middleOfWatermelon(t)
            if (how == "input tap") t.shell("input tap $x $y") else {
                val d = SystemClock.uptimeMillis()
                t.inject(MotionEvent.obtain(d, d, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
                SystemClock.sleep(60)
                t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
            }
            val up = SystemClock.uptimeMillis()
            SystemClock.sleep((up + keyAt - SystemClock.uptimeMillis()).coerceAtLeast(0))
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Z)
            app.settle(600)
            val cell = t.cells().map { it.first }.firstOrNull { it.contains("z") }
            rows += "$path, $how, Z at $keyAt ms: Z -> \"$cell\""
            app.back()
        }
        rows.forEach { log("tap now: $it") }
    }

    /**
     * P2-3g: a long press (and a tap, for T13) with the Compose rule's clock driven while the finger is down — does the field then do what the app
     * does without instrumentation (the word selected; the node's selection where the caret is)? Q after the long press: "q" = the word was selected.
     */
    @Test
    fun aLongPressWithTheRulesClockDriven() {
        app.launch()
        val rows = ArrayList<String>()
        val ways = listOf("control: DOWN, sleep 1000, UP", "waitForIdle every 50 ms while down", "advanceTimeBy 100 ms x10 while down", "tap, then waitForIdle (T13)", "tap, no sync (T13)")
        for ((k, way) in ways.withIndex()) {
            val id = "qa-probe-clock-$k"
            app.seed(id, md, System.currentTimeMillis())
            val e = app.open(id)
            val t = Tables(app)
            t.awaitOverlays(e, 1)
            val (x, y) = middleOfWatermelon(t)
            val d = SystemClock.uptimeMillis()
            fun ev(a: Int) = t.inject(MotionEvent.obtain(d, SystemClock.uptimeMillis(), a, x.toFloat(), y.toFloat(), 0))
            ev(MotionEvent.ACTION_DOWN)
            val reads = ArrayList<String>()
            when (k) {
                0 -> SystemClock.sleep(1000)
                1 -> repeat(20) { SystemClock.sleep(50); compose.waitForIdle() }
                2 -> repeat(10) { compose.mainClock.advanceTimeBy(100); SystemClock.sleep(100) }
                else -> SystemClock.sleep(60)
            }
            ev(MotionEvent.ACTION_UP)
            when (k) {
                3 -> { compose.waitForIdle(); reads += "after waitForIdle ${t.focusedSelection()}"; SystemClock.sleep(300); compose.waitForIdle(); reads += "300 ms ${t.focusedSelection()}" }
                4 -> { reads += "right away ${t.focusedSelection()}"; SystemClock.sleep(300); reads += "300 ms ${t.focusedSelection()}" }
                else -> compose.waitForIdle()
            }
            app.settle(800)
            val sel = t.focusedSelection()
            app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_Q)
            app.settle(400)
            val cell = t.cells(t.onScreen(e)).map { it.first }.firstOrNull { it.contains("q") }
            rows += "$way: ${if (reads.isEmpty()) "" else "${reads.joinToString(", ")}; "}selection $sel, Q -> \"$cell\""
            app.back()
        }
        rows.forEach { log("clock: $it") }
    }

    @Test
    fun inTheApp() {
        app.launch()
        app.seed("qa-probe", md, System.currentTimeMillis())
        val e = app.open("qa-probe")
        probeAll("app", e)
    }

    @Test
    fun inTheBareNoteBody() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { a ->
                WindowCompat.setDecorFitsSystemWindows(a.window, false)
                a.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                a.setContent {
                    TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.WARM) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                            NoteBody(
                                markdown = note(*(1..6).map(::lorem).toTypedArray(), grid, *(7..12).map(::lorem).toTypedArray()),
                                onMarkdownChange = {}, onActionsReady = {}, onFormatStateChanged = {}, editable = true,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { app.editorOrNull() != null }
            val e = app.editorOrNull()!!
            Tables(app).scrollTableTo(e, dp(160))
            probeAll("rig", e)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val TAG = "TableTapProbe"
    }
}
