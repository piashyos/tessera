package app.tessera.ui.editor.screen

import android.util.Log
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

    private companion object { const val TAG = "GlassShots" }
}
