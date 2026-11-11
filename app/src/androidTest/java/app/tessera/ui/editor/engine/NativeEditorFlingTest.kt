package app.tessera.ui.editor.engine

import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inertial scrolling of the editor, on the device: after a fast drag the content keeps moving on its own, slows down and
 * stops at the end of the note; a touch on the moving content stops it and is not a tap (the caret stays where it was).
 */
class NativeEditorFlingTest {
    private val note = (1..300).joinToString("\n\n") { "Paragraph $it: long enough text to take a line of the editor" }

    private fun event(down: Long, action: Int, y: Float): MotionEvent =
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 300f, y, 0)

    /** A fast upward drag (the content scrolls down) that ends with the finger lifting. Returns the time of the lift. */
    private fun flick(e: DeviceEditor) {
        val down = SystemClock.uptimeMillis()
        e.ui { e.view.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, 1200f)) }
        var y = 1200f
        repeat(12) {
            y -= 60f
            Thread.sleep(8)
            e.ui { e.view.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, y)) }
        }
        e.ui { e.view.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, y)) }
    }

    @Test
    fun aFastDragKeepsScrollingAfterTheFingerLifts() {
        DeviceEditor(note).use { e ->
            e.settleFrames()
            flick(e)
            val atLift = e.ui { e.view.scrollY }
            Thread.sleep(150)
            val soon = e.ui { e.view.scrollY }
            Thread.sleep(1500)
            val later = e.ui { e.view.scrollY }
            Thread.sleep(3000) // the speed dies out by 0.998 per millisecond: a few seconds
            val settled = e.ui { e.view.scrollY }
            assertTrue("it scrolled while the finger was down: $atLift", atLift > 0)
            assertTrue("it went on after the lift: $atLift -> $soon", soon > atLift)
            assertTrue("it went on further: $soon -> $later", later >= soon)
            Thread.sleep(500)
            val rest = e.ui { e.view.scrollY }
            assertEquals("it came to rest", settled, rest)
        }
    }

    @Test
    fun aTouchOnTheMovingContentStopsItWithoutPlacingTheCaret() {
        DeviceEditor(note).use { e ->
            e.settleFrames()
            flick(e)
            Thread.sleep(120)
            val caretBefore = e.ui { e.view.selectionStart } // where the first touch left it (a touch that focuses the editor puts it under the finger)
            val down = SystemClock.uptimeMillis()
            e.ui { e.view.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, 800f)) }
            val caught = e.ui { e.view.scrollY }
            Thread.sleep(40)
            e.ui { e.view.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, 800f)) }
            Thread.sleep(600)
            val after = e.ui { e.view.scrollY }
            assertTrue("it stopped where it was touched: $caught -> $after", after - caught < 30)
            assertEquals("the touch was not a tap: the caret stays", caretBefore, e.ui { e.view.selectionStart })
        }
    }
}
