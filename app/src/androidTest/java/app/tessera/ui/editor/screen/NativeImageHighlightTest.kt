package app.tessera.ui.editor.screen

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Finding N4 of the image QA, in pixels: a tapped picture is outlined by the overlay, and the system highlight of the selection paints nothing
 * in the margins of its line (the pink bands above and below the frame); a selection of text is highlighted as before.
 */
@HiltAndroidTest
class NativeImageHighlightTest : NativeAppTest() {
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(4, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    @Inject lateinit var imageDao: NoteImageDao
    private val ctx get() = app.instrumentation.targetContext

    @After
    fun clean() = ImageFixtures.removeApp(ctx, listOf("qa-hl-pic"))

    private fun shot(): Bitmap = app.instrumentation.uiAutomation.takeScreenshot()!!

    /** Pixels of [r] that differ between [a] and [b] by more than 24 in a channel. */
    private fun changed(a: Bitmap, b: Bitmap, r: Rect): Int {
        var n = 0
        for (y in r.top until r.bottom) for (x in r.left until r.right) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            if (maxOf(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q))) > 24) n++
        }
        return n
    }

    @Test
    fun aSelectedPictureHasNoHighlightBandsAndSelectedTextHasItsHighlight() {
        val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, "qa-hl-pic"), 1001, 563)
        runBlocking { imageDao.upsert(ImageFixtures.row("qa-hl-pic", f, 1001, 563)) }
        val md = "# Highlight\nabove one\nabove two\n![](tessera-img://qa-hl-pic)\nbelow the picture\nlast line"
        app.launch()
        app.seed("qa-hl", md, System.currentTimeMillis())
        val e = app.open("qa-hl")
        val until = SystemClock.uptimeMillis() + 15_000
        while (app.ui { shownImages(e).firstOrNull()?.loaded } != true && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        app.settle(500)
        val pic = app.ui { Rect(shownImages(e).single().rect).apply { val loc = IntArray(2).also { e.getLocationInWindow(it) }; offset(loc[0], loc[1]) } }
        val before = shot()
        // the picture's line: its margins above and below the frame, away from the corner handles and the turn button in the middle
        val above = Rect(pic.left + dp(24), pic.top - dp(9), pic.left + pic.width() / 3, pic.top - dp(3))
        val below = Rect(pic.left + dp(24), pic.bottom + dp(3), pic.right - dp(24), pic.bottom + dp(9))
        val t0 = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, pic.exactCenterX(), pic.exactCenterY(), 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t0, t0 + 60, MotionEvent.ACTION_UP, pic.exactCenterX(), pic.exactCenterY(), 0))
        app.settle(800)
        val ps = app.ui { shownImages(e).single().ps }
        assertEquals("the picture is selected", ps to ps + 1, app.ui { e.selectionStart to e.selectionEnd })
        val selected = shot()
        savePng(ctx, "p2-n4-selected-picture", selected)
        val bandAbove = changed(before, selected, above)
        val bandBelow = changed(before, selected, below)

        // a word of text selected: highlighted
        val word = app.ui {
            val t = e.text.toString()
            val at = t.indexOf("below")
            e.requestFocus()
            e.setSelection(at, at + "below".length)
            val l = e.layout
            val line = l.getLineForOffset(at)
            val loc = IntArray(2).also { e.getLocationInWindow(it) }
            Rect(
                loc[0] + e.totalPaddingLeft + l.getPrimaryHorizontal(at).toInt(), loc[1] + e.totalPaddingTop - e.scrollY + l.getLineTop(line),
                loc[0] + e.totalPaddingLeft + l.getPrimaryHorizontal(at + 5).toInt(), loc[1] + e.totalPaddingTop - e.scrollY + l.getLineBottom(line),
            )
        }
        app.settle(800)
        val text = shot()
        savePng(ctx, "p2-n4-selected-text", text)
        val highlighted = changed(selected, text, word)
        Log.i(TAG, "N4: picture $pic; band above $above changed $bandAbove px, band below $below changed $bandBelow px; selected word $word changed $highlighted of ${word.width() * word.height()} px")
        assertEquals("no highlight band above the selected picture", 0, bandAbove)
        assertEquals("no highlight band below the selected picture", 0, bandBelow)
        assertTrue("a selected word is highlighted: $highlighted px of ${word.width() * word.height()}", highlighted > word.width() * word.height() / 3)
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "NativeImageHighlight"
    }
}
