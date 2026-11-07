package app.tessera.ui.editor.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.util.Log
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.Inline
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.components.TableOverlayHost
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.tableZones
import java.util.concurrent.CopyOnWriteArrayList

/** A note in the canonical Markdown of the native editor: Body paragraphs (a `String`) and tables (a [TableGrid]). */
internal fun note(vararg parts: Any): String = NoteMarkdown.serialize(
    NoteDoc(parts.map { p -> if (p is TableGrid) NoteBlock.Table(p) else NoteBlock.Text(ParagraphKind.Body, Inline(p as String)) }),
)

/** The button with [desc] (a button of the format bar or of the table bar: its click, as TalkBack would, wherever the row is scrolled). */
internal fun ComposeTestRule.press(desc: String, timeout: Long = 5_000) {
    waitUntil(timeout) { onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }
    onAllNodesWithContentDescription(desc)[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

internal fun ComposeTestRule.has(desc: String) = onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()

internal fun lorem(i: Int) = "Paragraph $i lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor."

/** The tables of a Markdown text, parsed. */
internal fun tablesOf(md: String): List<TableGrid> = NoteMarkdown.parse(md).blocks.filterIsInstance<NoteBlock.Table>().map { it.grid }

/**
 * The tables of the open note on the device, the way a finger and an eye meet them: the overlay of each table over its span, its cells as the
 * accessibility tree shows them (the bounds of each cell field on the screen, its text, the focus), touches injected into the window (one move
 * per frame), the keyboard's window.
 */
internal class Tables(private val app: AppDriver) {
    val ua = app.instrumentation.uiAutomation.also {
        it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    fun host(): TableOverlayHost? = app.ui { findView(app.activity().window.decorView, TableOverlayHost::class.java) }

    /** The table [i] (document order) on the screen. */
    fun onScreen(e: TesseraEditText, i: Int = 0): Rect = app.ui {
        val loc = IntArray(2).also { e.getLocationOnScreen(it) }
        Rect(e.tableZones()[i].rect).apply { offset(loc[0], loc[1]) }
    }

    fun count(e: TesseraEditText) = app.ui { e.tableZones().size }

    /** Every table has its overlay, measured, on its span, and its cells are in the accessibility tree. */
    fun awaitOverlays(e: TesseraEditText, n: Int) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            val ok = app.ui {
                val h = host()
                val zones = (app.editorOrNull() ?: e).tableZones() // the current editor: an activity re-created meanwhile has a new one
                h != null && zones.size == n && h.childCount == n + 1 &&
                    zones.withIndex().all { (i, z) -> h.getChildAt(i + 1).let { o -> o.height > 0 && o.height == z.rect.height() } }
            }
            if (ok && (n == 0 || cells().isNotEmpty())) return
            Thread.sleep(100)
        }
        val h = app.ui { host()?.let { h -> (1 until h.childCount).map { h.getChildAt(it).height } } }
        val nodes = appNodes().filter { it.className?.toString()?.contains("Edit") == true || it.text != null }.take(30)
            .map { "${it.className}/${it.packageName}/\"${it.text?.toString()?.take(20)}\"/${bounds(it)}" }
        error("overlays not placed: zones ${app.ui { (app.editorOrNull() ?: e).tableZones().map { it.rect } }}, overlay heights $h, editor ${editorRect()}, nodes $nodes, windows ${ua.windows.map { "${it.type}:${it.title}" }}")
    }

    /** The table [i] scrolled so that its top is at [y] px of the editor (view coordinates). */
    fun scrollTableTo(e: TesseraEditText, y: Int, i: Int = 0) {
        app.ui { e.scrollTo(0, (e.scrollY + e.tableZones()[i].rect.top - y).coerceAtLeast(0)) }
        app.settle(500)
    }

    // ------------------------------------------------------------------ touches

    fun inject(ev: MotionEvent) {
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        ua.injectInputEvent(ev, true)
        ev.recycle()
    }

    fun tap(x: Int, y: Int) {
        val t = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
        inject(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
        app.settle(500)
    }

    fun longPress(x: Int, y: Int, ms: Long = 900) {
        val t = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0))
        Thread.sleep(ms)
        inject(MotionEvent.obtain(t, t + ms, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0))
        app.settle(600)
    }

    /** A drag with a finger, one move per frame; returns when the finger is up. */
    fun drag(x0: Int, y0: Int, dx: Int, dy: Int, steps: Int = 20, frameMs: Long = 16) {
        val t0 = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x0.toFloat(), y0.toFloat(), 0))
        for (i in 1..steps) {
            val at = t0 + i * frameMs
            while (SystemClock.uptimeMillis() < at) Thread.sleep(1)
            inject(MotionEvent.obtain(t0, at, MotionEvent.ACTION_MOVE, (x0 + dx * i / steps).toFloat(), (y0 + dy * i / steps).toFloat(), 0))
        }
        inject(MotionEvent.obtain(t0, t0 + steps * frameMs + frameMs, MotionEvent.ACTION_UP, (x0 + dx).toFloat(), (y0 + dy).toFloat(), 0))
        app.settle(500)
    }

    fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }.also {
        app.instrumentation.waitForIdleSync()
    }

    /** Characters as a hardware keyboard sends them (fast: several within the 500 ms pause of a cell). */
    fun typeKeys(s: String) {
        shell("input text " + s.replace(" ", "%s"))
        Thread.sleep(200)
    }

    // ------------------------------------------------------------------ the accessibility tree

    private fun appNodes(): List<AccessibilityNodeInfo> = buildList {
        fun walk(n: AccessibilityNodeInfo) { add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::walk) }
        ua.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.forEach { w -> w.root?.let(::walk) }
    }

    private fun bounds(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    /** The editor's own node on the screen (it is an EditText too): what the cells are told apart from. */
    private fun editorRect(): Rect? = app.ui { app.editorOrNull()?.let { e -> Rect().also { e.getGlobalVisibleRect(it) } } }

    /**
     * The cell nodes. [fresh]: the tree is read again (the cache of this accessibility client dropped first), what the screen holds now; without
     * it, what a screen reader that keeps its tree from the events it got would know.
     */
    private fun cellNodes(area: Rect? = null, fresh: Boolean = true): List<AccessibilityNodeInfo> {
        if (fresh) ua.clearCache()
        val editor = editorRect()
        val pkg = app.instrumentation.targetContext.packageName
        return appNodes()
            .filter { it.className?.toString()?.contains("EditText") == true && it.packageName?.toString() == pkg }
            .filter { n ->
                val b = bounds(n)
                val text = n.text?.toString() ?: ""
                // the editor holds the whole note (its lines); a cell is one line of GFM
                b != editor && '\n' !in text && b.height() in 1..600 && (area == null || Rect.intersects(area, b))
            }
            .sortedWith(compareBy({ bounds(it).top }, { bounds(it).left }))
    }

    /** The cell fields (text, bounds on the screen) of the tables in reading order; within [area] when given. */
    fun cells(area: Rect? = null, fresh: Boolean = true): List<Pair<String, Rect>> = cellNodes(area, fresh).map { (it.text?.toString() ?: "") to bounds(it) }

    /** The cells until [ok] holds for their texts (the overlay recomposes on the next frame, the tree follows), at most [ms]. */
    fun awaitCells(ms: Long = 3_000, ok: (List<String>) -> Boolean): List<String> {
        val until = SystemClock.uptimeMillis() + ms
        var c = cells().map { it.first }
        while (!ok(c) && SystemClock.uptimeMillis() < until) { Thread.sleep(100); c = cells().map { it.first } }
        return c
    }

    fun cell(text: String): Rect = cells().firstOrNull { it.first == text }?.second ?: error("no cell \"$text\"; cells ${cells().map { it.first }}")

    /** The cell that has the focus (its text field is focused: the view that holds the input focus is the overlay's compose view). */
    fun focused(): Pair<String, Rect>? = cellNodes().firstOrNull { it.isFocused }?.let { (it.text?.toString() ?: "") to bounds(it) }

    fun focusedSelection(): Pair<Int, Int>? = cellNodes().firstOrNull { it.isFocused }?.let { it.textSelectionStart to it.textSelectionEnd }

    /** The keyboard window's top on the screen; `null` when it is not there. */
    fun imeTop(): Int? = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { w -> Rect().also { w.getBoundsInScreen(it) } }?.takeIf { !it.isEmpty }?.top

    fun imeShown(e: TesseraEditText) = app.ui { e.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }

    fun awaitIme(e: TesseraEditText, shown: Boolean = true) {
        val until = SystemClock.uptimeMillis() + 10_000
        var stable = 0
        var last: Int? = -1
        while (SystemClock.uptimeMillis() < until) {
            val top = imeTop()
            if (imeShown(e) == shown && (!shown || (top != null && top == last))) { if (++stable >= 3) break } else stable = 0
            last = top
            Thread.sleep(150)
        }
        check(imeShown(e) == shown) { "the keyboard is ${if (shown) "not shown" else "still shown"}" }
        app.settle(400)
    }

    /** A finger at the end ([atEnd]) or the start of the line of [needle] in the editor's text. */
    fun tapText(e: TesseraEditText, needle: String, atEnd: Boolean = true) {
        fun point() = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = l.getLineForOffset(e.text.toString().indexOf(needle).also { check(it >= 0) { "\"$needle\" not in the note" } })
            val x = loc[0] + e.totalPaddingLeft + (if (atEnd) l.getLineRight(line) - 4f else l.getLineLeft(line) + 4f)
            val y = loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
            Triple(x.toInt(), y.toInt(), l.getLineTop(line))
        }
        // as a user does: the line is tapped where it is seen — not under the keyboard or the floating bar (a table that grew pushes it there;
        // with the keyboard up the editor ends above the bar, so its own bottom is the limit)
        val density = app.instrumentation.targetContext.resources.displayMetrics.density
        val top = app.ui { IntArray(2).also { e.getLocationOnScreen(it) }[1] }
        val bottom = minOf(imeTop() ?: Int.MAX_VALUE, app.ui { IntArray(2).also { e.getLocationOnScreen(it) }[1] + e.height }) - 8
        var (x, y, lineTop) = point()
        if (y !in (top + 8)..bottom) {
            Log.i("TableFixtures", "tapText \"$needle\": y $y not in the seen area $top..$bottom, the note scrolled to it")
            app.ui { e.scrollTo(0, (lineTop - (40 * density).toInt()).coerceAtLeast(0)) }
            app.settle(400)
            point().let { x = it.first; y = it.second }
        }
        Log.i("TableFixtures", "tapText \"$needle\" at ($x, $y), keyboard top ${imeTop()}")
        tap(x, y)
    }

    /** A finger on the cell with [text]: inside it, clear of the resize strip at its right edge; past its text, so the caret goes to the end. */
    fun tapCell(text: String) {
        val r = cell(text)
        val strip = (20 * app.instrumentation.targetContext.resources.displayMetrics.density).toInt()
        tap(r.right - strip, r.centerY())
    }

    /** What the window shows in each frame while it is recorded. */
    inner class Frames<T>(private val sample: () -> T?) {
        val list = CopyOnWriteArrayList<T>()
        private val listener = ViewTreeObserver.OnPreDrawListener { sample()?.let { list += it }; true }
        fun start() = apply { app.ui { app.activity().window.decorView.viewTreeObserver.addOnPreDrawListener(listener) } }
        fun stop() = app.ui { app.activity().window.decorView.viewTreeObserver.removeOnPreDrawListener(listener) }
    }
}
