package app.tessera.ui.editor.engine

import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Touches off the text (spec §3): the outcomes of the gesture, not the floating menu window (a unit test has none). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraEditTextOutsideTouchTest {
    private fun HostFixture.below() = view.totalPaddingTop + view.layout.height + 40f
    private fun HostFixture.event(t: Long, action: Int, x: Float, y: Float, at: Long = t) =
        view.dispatchTouchEvent(MotionEvent.obtain(t, at, action, x, y, 0))
    private fun HostFixture.sel() = view.selectionStart to view.selectionEnd

    @Test fun aTapUnderTheTextPutsTheCaretAtTheEndOfTheNote() {
        val h = HostFixture("abc\ndef")
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        h.event(t, MotionEvent.ACTION_DOWN, 2f, h.below())
        h.event(t, MotionEvent.ACTION_UP, 2f, h.below(), t + 20)
        assertEquals(h.text.length to h.text.length, h.sel())
    }

    @Test fun aTapUnderANoteThatEndsWithATableIsAsATapInItsMarginBelow() {
        val h = HostFixture("top\n| a | b |\n| --- | --- |\n| 1 | 2 |")
        h.caret(0)
        val ps = h.at("￼")
        val t = SystemClock.uptimeMillis()
        h.event(t, MotionEvent.ACTION_DOWN, 50f, h.below())
        h.event(t, MotionEvent.ACTION_UP, 50f, h.below(), t + 20)
        assertEquals("the caret goes next to the table, as caretNextToTable(above = false)", ps - 1, h.view.selectionStart)
    }

    @Test fun aLongPressUnderTheTextSelectsNothingAndTheCaretGoesToTheEnd() {
        val h = HostFixture("abc\ndef")
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        h.event(t, MotionEvent.ACTION_DOWN, 50f, h.below())
        assertTrue(h.view.performLongClick())
        h.event(t, MotionEvent.ACTION_UP, 50f, h.below(), t + 800)
        assertEquals(h.text.length to h.text.length, h.sel())
    }

    @Test fun aDriftPastTheSlopWithoutScrollKeepsTheLongPressOursAndTheLiftIsNoTap() {
        val h = HostFixture("abc\ndef")
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        val y = h.below()
        h.event(t, MotionEvent.ACTION_DOWN, 50f, y)
        h.event(t, MotionEvent.ACTION_MOVE, 50f, y + 60f, t + 100)
        assertTrue("the long press is ours: no word of the last line is selected", h.view.performLongClick())
        assertEquals(h.text.length to h.text.length, h.sel())
        h.event(t, MotionEvent.ACTION_UP, 50f, y + 60f, t + 800)
        assertEquals(h.text.length to h.text.length, h.sel())
    }

    @Test fun aDragUnderTheTextIsNoTap() {
        val h = HostFixture("abc\ndef")
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        val y = h.below()
        h.event(t, MotionEvent.ACTION_DOWN, 2f, y)
        h.event(t, MotionEvent.ACTION_MOVE, 2f, y + 60f, t + 10)
        h.event(t, MotionEvent.ACTION_UP, 2f, y + 60f, t + 20)
        assertTrue("the platform's own DOWN put the caret at the start of the last line; a tap of ours would move it to the end", h.view.selectionStart < h.text.length)
    }

    @Test fun aLongPressLeftOfAListItemPutsTheCaretAfterTheSentinel() {
        val h = HostFixture("- a\n- b")
        h.caret(0)
        val l = h.view.layout
        val ps = l.getLineStart(1)
        val y = h.view.totalPaddingTop + (l.getLineTop(1) + l.getLineBottom(1)) / 2f
        val t = SystemClock.uptimeMillis()
        h.event(t, MotionEvent.ACTION_DOWN, h.view.totalPaddingLeft + 1f, y)
        assertTrue(h.view.performLongClick())
        assertEquals(contentStart(h.text, ps), h.view.selectionStart)
        assertEquals(h.view.selectionStart, h.view.selectionEnd)
    }

    @Test fun aViewerGetsNoSelectionFromALongPressUnderTheText() {
        val h = HostFixture("abc\ndef")
        h.caret(1)
        h.view.editable = false
        val before = h.sel()
        val t = SystemClock.uptimeMillis()
        h.event(t, MotionEvent.ACTION_DOWN, 50f, h.below())
        assertTrue(h.view.performLongClick())
        h.event(t, MotionEvent.ACTION_UP, 50f, h.below(), t + 800)
        assertEquals(before, h.sel())
    }

    @Test fun aTouchThatCaughtAFlingIsNoLongPressOffTheText() {
        val h = HostFixture((1..200).joinToString("\n") { "ab" })
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        val x = 300f // right of "ab"
        h.event(t, MotionEvent.ACTION_DOWN, x, 700f)
        h.event(t, MotionEvent.ACTION_MOVE, x, 400f, t + 10)
        h.event(t, MotionEvent.ACTION_MOVE, x, 100f, t + 20)
        h.event(t, MotionEvent.ACTION_UP, x, 100f, t + 30)
        val before = h.sel()
        val t2 = SystemClock.uptimeMillis() + 100
        h.event(t2, MotionEvent.ACTION_DOWN, x, 300f) // lands on the moving content
        assertTrue(h.view.performLongClick())
        assertEquals("the long press did nothing", before, h.sel())
    }

    private fun TesseraEditText.field(name: String) = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    @Test fun theLiftOfATouchThatCaughtAFlingEndsTheGestureForALaterLongClick() {
        val h = HostFixture((1..200).joinToString("\n") { "ab" })
        h.caret(0)
        val t = SystemClock.uptimeMillis()
        val x = 300f
        h.event(t, MotionEvent.ACTION_DOWN, x, 700f)
        h.event(t, MotionEvent.ACTION_MOVE, x, 400f, t + 10)
        h.event(t, MotionEvent.ACTION_MOVE, x, 100f, t + 20)
        h.event(t, MotionEvent.ACTION_UP, x, 100f, t + 30)
        assertTrue("the drag started a fling", (h.view.field("flingVelocity") as Float) != 0f)
        val t2 = SystemClock.uptimeMillis() + 100
        h.event(t2, MotionEvent.ACTION_DOWN, x, 300f) // catches the fling
        assertTrue("the touch is off the text", h.view.field("downOutside") != null)
        h.event(t2, MotionEvent.ACTION_UP, x, 300f, t2 + 30) // no drift: the early return of onTouchEvent
        // state, not the caret: Robolectric's text metrics give offset 0 for any point off a line, a moved caret would not show
        assertNull("the gesture is over: a later long click (TalkBack) must not read it", h.view.field("downOutside"))
        assertEquals(false, h.view.field("outsideLongPress"))
        assertEquals(false, h.view.field("outsideMoved"))
    }

    @Test fun aPointInViewCoordinatesIsTranslatedByPaddingAndScroll() {
        val h = HostFixture((1..60).joinToString("\n") { "ab" })
        h.view.setPadding(40, 60, 40, 0)
        h.layout()
        h.view.scrollTo(0, 200)
        val l = h.view.layout
        val line = 20
        val midY = (l.getLineTop(line) + l.getLineBottom(line)) / 2f
        val viewY = midY + h.view.totalPaddingTop - h.view.scrollY
        val onText = h.view.totalPaddingLeft + l.getLineRight(line) / 2
        assertNull(h.view.outsideAt(onText, viewY))
        val pastEnd = h.view.totalPaddingLeft + l.getLineRight(line) + 50f
        assertTrue("right of the line's text", h.view.outsideAt(pastEnd, viewY) is Outside.OffLine)
        assertTrue("in the left padding", h.view.outsideAt(h.view.totalPaddingLeft - 20f, viewY) is Outside.OffLine)
        val bottom = l.height + h.view.totalPaddingTop - h.view.scrollY
        assertTrue("just above the end of the text, scroll taken in", h.view.outsideAt(h.view.totalPaddingLeft + 1f, bottom - 10f) !is Outside.BelowText)
        assertEquals(Outside.BelowText, h.view.outsideAt(50f, bottom + 10f))
    }
}
