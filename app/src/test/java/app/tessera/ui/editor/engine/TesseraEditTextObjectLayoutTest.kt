package app.tessera.ui.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live layout of the editor ends where the text does after every edit, also a command after typing next to an object (fuzz seed 100098:
 * the line table of the `DynamicLayout` kept the old length and the next key crashed in `bringPointIntoView`). The cause is in the framework's
 * reflow (a plain `EditText` shows the same stale lines under Robolectric); the editor notices a layout that is off and makes a new one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TesseraEditTextObjectLayoutTest {
    private fun lines(h: HostFixture): List<Pair<Int, Int>> {
        val l = h.view.layout!!
        return (0 until l.lineCount).map { l.getLineStart(it) to l.getLineEnd(it) }
    }

    private fun assertLayoutCoversText(label: String, h: HostFixture) {
        val ls = lines(h)
        assertEquals("$label: the layout starts at 0", 0, ls.first().first)
        assertEquals("$label: the layout ends where the text does ($ls)", h.text.length, ls.last().second)
        ls.zipWithNext().forEach { (a, b) -> assertEquals("$label: no gap or overlap in $ls", a.second, b.first) }
    }

    @Test
    fun anImageInsertedAfterTypingBeforeAnObjectIsSeenByTheLayoutAndTheNextKeyWorks() {
        val h = HostFixture("---\n- ")
        h.view.imageSource = FakeImageSource()
        h.layout()
        h.caret(0)
        h.ic().commitText("|", 1)
        h.layout()
        assertLayoutCoversText("after typing", h)
        h.caret(2)
        h.view.insertImage("f1")
        h.layout()
        assertLayoutCoversText("after the image", h)
        h.caret(h.text.length)
        h.ic().commitText("z", 1)
        h.layout()
        assertLayoutCoversText("after the next key", h)
    }
}
