package app.tessera.ui.editor.screen

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import app.tessera.core.preferences.ThemeMode
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.tableZones
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glass bars on the real app: the notes list with cards behind the bottom bar, and a long note whose text runs under the glass format bar
 * (shots `glass-*` in Downloads/native-editor-qa). The caret of the last line, and of text typed at the end, stays above the bar.
 */
@HiltAndroidTest
class GlassBarShotsTest : NativeAppTest() {
    private val ctx get() = app.instrumentation.targetContext

    private fun shot(name: String) {
        app.settle(900)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, "glass-$name", b)
        Log.i(TAG, "shot $name ${b.width}x${b.height}")
    }

    private fun theme(mode: ThemeMode) = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemeMode(mode)
    }

    private fun long(lines: Int) = (1..lines).joinToString("\n") { i ->
        if (i % 12 == 1) "## Раздел $i" else "Строка $i: достаточно длинный текст, чтобы он занял всю ширину экрана и перенёсся на вторую строку."
    }

    private fun focusEnd(e: TesseraEditText) {
        app.ui {
            e.requestFocus()
            e.setSelection(e.text!!.length)
            e.context.getSystemService(InputMethodManager::class.java).showSoftInput(e, 0)
        }
        Gboard(app).awaitWindow()
        app.settle(1_200)
    }

    /** The caret's bottom, in the editor's own pixels, against the top of the bar's inset. */
    private fun caretClearance(e: TesseraEditText): Int = app.ui {
        val c = e.caretRect() ?: error("no caret")
        (e.height - e.bottomInset) - (c.bottom - e.scrollY).toInt()
    }

    @Test
    fun listAndEditorGlass() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val m = mode.name.lowercase()
            theme(mode)
            db.clearAllTables()
            val now = System.currentTimeMillis()
            (1..9).forEach { app.seed("glass-list-$it", "# Заметка $it\nТекст заметки номер $it, несколько слов для карточки.", now - it * 1000L) }
            app.launch()
            app.awaitCard("glass-list-1")
            shot("list-$m")
            app.close()

            app.seed("glass-long", long(80), now + 1000)
            app.launch()
            val e = app.open("glass-long")
            app.settle(1_500)
            shot("editor-top-$m")
            focusEnd(e)
            val inset = app.ui { e.bottomInset }
            Log.i(TAG, "$m inset $inset px, caret clearance at the end ${caretClearance(e)} px")
            assertTrue("a bar's inset is set", inset > 0)
            assertTrue("caret of the last line above the bar", caretClearance(e) >= 0)
            shot("editor-end-$m")
            // type at the end: the new lines stay above the bar
            repeat(10) {
                app.ime(e) { commitText("\nновая строка $it", 1) }
                app.settle(250)
                assertTrue("caret after typed line $it above the bar (clearance ${caretClearance(e)})", caretClearance(e) >= 0)
            }
            shot("editor-typed-$m")
            // text mid-scroll runs under the bar
            app.ui { e.scrollTo(0, e.scrollY / 2) }
            shot("editor-scrolled-$m")
            app.back()
            app.close()
        }
    }

    /** A table and an image tile whose block sits behind the bar: both are drawn under the glass, and a cell that is focused is lifted above it. */
    @Test
    fun objectsUnderTheGlass() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val m = mode.name.lowercase()
            theme(mode)
            db.clearAllTables()
            val grid = TableGrid(listOf("A", "B", "C"), (1..4).map { r -> listOf("r$r a", "r$r b", "r$r c") })
            val paras = { n: Int, tag: String -> (1..n).map { "$tag $it: достаточно длинный текст, чтобы он занял всю ширину экрана и перенёсся." }.toTypedArray() }
            val md = note(*paras(10, "До"), grid, *paras(3, "Между")) + "\n![](tessera-img://glass-pic)\n" + paras(6, "После").joinToString("\n")
            app.seed("glass-objects", md, System.currentTimeMillis())
            app.launch()
            val e = app.open("glass-objects")
            Tables(app).awaitOverlays(e, 1)
            focusEnd(e)
            val barTop = app.ui { e.height - e.bottomInset }
            val maxScroll = app.ui { e.layout.height + e.totalPaddingTop + e.totalPaddingBottom - e.height }
            // the table straddles the bar's top edge
            val tableTop = app.ui { e.tableZones().first().rect.top }
            app.ui { e.scrollTo(0, (e.scrollY + tableTop - (barTop - 80)).coerceIn(0, maxScroll)) }
            app.settle(600)
            Log.i(TAG, "$m table zone top now ${app.ui { e.tableZones().first().rect.top }} bar top $barTop scroll ${app.ui { e.scrollY }}/$maxScroll")
            shot("table-behind-$m")
            app.ui { e.scrollTo(0, (maxScroll - 260).coerceAtLeast(0)) }
            shot("image-behind-$m")
            app.back()
            app.close()
        }
    }

    /** On the screen: the top of what covers the bottom of the editor — the glass bar (its inset) or the keyboard window, whichever is higher. */
    private fun coverTop(e: TesseraEditText): Int {
        val ime = app.instrumentation.uiAutomation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.let { w -> Rect().also { w.getBoundsInScreen(it) }.top } ?: Int.MAX_VALUE
        return minOf(app.ui { IntArray(2).also(e::getLocationOnScreen)[1] + e.height - e.bottomInset }, ime)
    }

    /** On the screen: the bottom of the caret. */
    private fun caretBottom(e: TesseraEditText): Int = app.ui {
        IntArray(2).also(e::getLocationOnScreen)[1] + (e.caretRect() ?: error("no caret")).bottom.toInt() - e.scrollY
    }

    private fun assertCaretAboveCover(e: TesseraEditText, what: String) {
        val caret = caretBottom(e)
        val cover = coverTop(e)
        Log.i(TAG, "$what: caret bottom $caret, bar/keyboard top $cover, selection ${app.ui { e.selectionStart }}")
        assertTrue("$what: the caret (bottom $caret) is above the bar/keyboard (top $cover)", caret <= cover)
    }

    /**
     * Scrolls a line so that the bar's top edge cuts it in half, taps its visible upper part (a real `input tap`): the caret is put into that
     * line and the note scrolls so that the line is above the bar (and above the keyboard the tap may bring up).
     */
    private fun tapLineHalfUnderTheBar(e: TesseraEditText, label: String) {
        Gboard(app) // lets the keyboard window be seen
        val cover = coverTop(e)
        val (line, range) = app.ui {
            val l = e.layout
            val coverLocal = cover - IntArray(2).also(e::getLocationOnScreen)[1]
            // a line a bit above the bar now; the note scrolls up until the bar's edge cuts it in half
            val line = l.getLineForVertical(e.scrollY + coverLocal - e.totalPaddingTop - 200)
            val mid = (l.getLineTop(line) + l.getLineBottom(line)) / 2
            e.scrollTo(0, e.totalPaddingTop + mid - coverLocal)
            line to (l.getLineStart(line)..l.getLineEnd(line))
        }
        app.settle(600)
        val (x, y) = app.ui {
            val loc = IntArray(2).also(e::getLocationOnScreen)
            val l = e.layout
            val top = loc[1] + e.totalPaddingTop + l.getLineTop(line) - e.scrollY
            Log.i(TAG, "$label: line $line on screen ${top}..${loc[1] + e.totalPaddingTop + l.getLineBottom(line) - e.scrollY}, bar/keyboard top $cover")
            check(top < cover) { "line $line is not partly above the bar" }
            (loc[0] + e.totalPaddingLeft + 60) to (top + cover) / 2
        }
        shot("tap-before-$label")
        Gboard(app).tap(Rect(x, y, x + 1, y + 1))
        app.settle(1_500)
        val sel = app.ui { e.selectionStart }
        Log.i(TAG, "$label: tapped ($x, $y) → selection $sel, line range $range, scrollY ${app.ui { e.scrollY }}")
        shot("tap-after-$label")
        assertTrue("$label: the caret ($sel) is in the tapped line $range", sel in range)
        assertCaretAboveCover(e, "$label, after the tap")
    }

    /** Enter and Backspace of the real Gboard at the end of a long note, and taps on a line the bar half covers: the caret is never under the bar. */
    @Test
    fun caretStaysAboveTheGlassBar() {
        theme(ThemeMode.LIGHT)
        app.seed("glass-caret", long(80), System.currentTimeMillis())
        app.launch()
        val e = app.open("glass-caret")
        app.settle(1_500)
        app.ui { e.scrollTo(0, e.layout.height / 2) }
        tapLineHalfUnderTheBar(e, "keyboard-hidden")

        focusEnd(e)
        assertCaretAboveCover(e, "at the end")
        val gb = Gboard(app)
        gb.awaitShown()
        repeat(5) {
            gb.tap(gb.enterKey())
            app.settle(300)
            assertCaretAboveCover(e, "Enter ${it + 1}")
        }
        gb.type("ab")
        app.settle(300)
        shot("caret-after-enter")
        // "b", "a", then the five empty paragraphs merge one by one, then a character of the last line
        repeat(8) {
            gb.press("Delete")
            app.settle(300)
            assertCaretAboveCover(e, "Backspace ${it + 1}")
        }
        shot("caret-after-backspace")
        tapLineHalfUnderTheBar(e, "keyboard-shown")
        app.back()
    }

    private companion object { const val TAG = "GlassShots" }
}
