package app.tessera.ui.editor.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import app.tessera.core.preferences.ThemeMode
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glass format bar on the real app: the margins around its pill belong to the bar (a tap there must not reach the note under it), and the
 * text of the note is drawn under the bar (a `TextView` clips to its padding unless the editor says otherwise).
 */
@HiltAndroidTest
class GlassBarGuardsTest : NativeAppTest() {
    private fun lightTheme() = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemeMode(ThemeMode.LIGHT)
    }

    private fun checklist(n: Int) = (1..n).joinToString("\n") { "- [ ] item $it of the list" }

    private fun focusEnd(e: TesseraEditText) {
        app.ui {
            e.requestFocus()
            e.setSelection(e.text!!.length)
            e.context.getSystemService(InputMethodManager::class.java).showSoftInput(e, 0)
        }
        Gboard(app).awaitWindow()
        app.settle(1_200)
    }

    @Test
    fun aTapInTheMarginAroundThePillDoesNotReachTheNote() {
        lightTheme()
        app.seed("guard-tap", checklist(60), System.currentTimeMillis())
        app.launch()
        val e = app.open("guard-tap")
        app.settle(1_500)
        focusEnd(e)
        val d = app.ui { e.resources.displayMetrics.density }
        // the bar's top margin (8 dp) is the first strip of its column; a checklist line is scrolled under it
        val stripMid = app.ui { e.height - e.bottomInset + (4 * d).toInt() }
        app.ui {
            val l = e.layout
            val line = l.getLineForVertical(e.scrollY + stripMid - e.totalPaddingTop - 400)
            e.scrollTo(0, e.totalPaddingTop + (l.getLineTop(line) + l.getLineBottom(line)) / 2 - stripMid)
        }
        app.settle(600)
        val mdBefore = app.md(e)
        val selBefore = app.ui { e.selectionStart }
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            (loc[0] + e.totalPaddingLeft + 12 * d) to (loc[1] + stripMid).toFloat() // where a checkbox of the line is
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        app.settle(900)
        assertEquals("the checkbox under the bar's margin was toggled", mdBefore, app.md(e))
        assertEquals("the caret moved under the bar's margin", selBefore, app.ui { e.selectionStart })
    }

    @Test
    fun textIsDrawnUnderTheBar() {
        lightTheme()
        app.seed("guard-draw", (1..80).joinToString("\n") { "Line $it: long enough text to fill the width of the screen and wrap onto a second line." }, System.currentTimeMillis())
        app.launch()
        val e = app.open("guard-draw")
        app.settle(1_500)
        focusEnd(e)
        app.ui { e.scrollTo(0, e.scrollY / 2) } // mid-note: text goes on below the view
        app.settle(500)
        val (ink, strip) = app.ui {
            val bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { it.drawColor(Color.WHITE); it.translate(0f, -e.scrollY.toFloat()); e.draw(it) } // `View.draw` leaves the scroll to the parent
            val d = e.resources.displayMetrics.density
            val rows = (e.height - e.bottomInset) until (e.height - (16 * d).toInt()) // the bar's strip, above the plain bottom padding
            var n = 0
            for (y in rows) for (x in 0 until bmp.width) if (Color.luminance(bmp.getPixel(x, y)) < 0.6f) n++
            n to rows
        }
        assertTrue("ink in the strip the bar covers ($strip): $ink dark pixels — the text must run under the bar", ink > 0)
    }

    /** No keyboard, no bar: the note runs under the transparent navigation bar (the gesture strip) — the strip is not an empty band. */
    @Test
    fun textRunsUnderTheNavigationBarWithoutTheKeyboard() {
        lightTheme()
        app.seed("guard-nav", (1..80).joinToString("\n") { "Line $it: long enough text to fill the width of the screen and wrap onto a second line." }, System.currentTimeMillis())
        app.launch()
        val e = app.open("guard-nav")
        app.settle(1_500)
        app.ui { e.clearFocus(); e.scrollTo(0, e.layout.height / 3) }
        app.settle(500)
        val (inset, ink) = app.ui {
            val bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { it.drawColor(Color.WHITE); it.translate(0f, -e.scrollY.toFloat()); e.draw(it) }
            val d = e.resources.displayMetrics.density
            var n = 0
            for (y in (e.height - e.bottomInset) until (e.height - (16 * d).toInt())) for (x in 0 until bmp.width) if (Color.luminance(bmp.getPixel(x, y)) < 0.6f) n++
            e.bottomInset to n
        }
        assertTrue("the navigation bar's height is the inset ($inset px)", inset > 0)
        assertTrue("text in the strip under the navigation bar: $ink dark pixels", ink > 0)
    }
}
