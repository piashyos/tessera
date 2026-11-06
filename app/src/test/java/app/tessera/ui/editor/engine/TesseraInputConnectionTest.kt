package app.tessera.ui.editor.engine

import android.text.TextUtils
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §6.3 (1): every way an IME says Enter / Backspace reaches the same `EditRules` as the hardware key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraInputConnectionTest {

    @Test
    fun commitTextNewlineIsEnter() {
        val h = HostFixture("- a")
        h.caret(1)
        assertEquals(true, h.ic().commitText("\n", 1))
        assertEquals("- a\n- ", h.md())
    }

    @Test
    fun commitTextOfSeveralCharactersIsNotIntercepted() {
        val h = HostFixture("- a")
        h.caret(1)
        h.ic().commitText("b\n", 1)
        assertEquals("- ab\n- ", h.md()) // the normalizer's job: the new paragraph takes the kind of the paragraph it started in
    }

    @Test
    fun enterKeyEventIsEnterOnceNotTwice() {
        val h = HostFixture("- a")
        h.caret(1)
        val ic = h.ic()
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        assertEquals("- a\n- ", h.md())
    }

    @Test
    fun delKeyEventAtTheStartOfANestedItemOutdentsIt() {
        val h = HostFixture("- a\n    - b")
        h.caret(h.at("b"))
        val ic = h.ic()
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        assertEquals("- a\n- b", h.md())
    }

    @Test
    fun deleteSurroundingTextOneBeforeIsBackspaceAtTheStartOfAHeading() {
        val h = HostFixture("## h")
        h.caret(0)
        assertEquals(true, h.ic().deleteSurroundingText(1, 0))
        assertEquals("h", h.md())
    }

    @Test
    fun delKeyAndDeleteSurroundingTextGiveTheSameResultEverywhere() {
        for (md in listOf("# t", "> q", "- [x] c", "1. n\n2. m", "a\n---\nb", "x\n\ny", "```\nm\n```", "a\n- b")) {
            val n = HostFixture(md).text.length
            for (p in 0..n) {
                val viaKey = HostFixture(md)
                val viaSurrounding = HostFixture(md)
                viaKey.caret(p); viaSurrounding.caret(p)
                viaKey.ic().sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                viaSurrounding.ic().deleteSurroundingText(1, 0)
                assertEquals("$md at $p", viaKey.md(), viaSurrounding.md())
                assertEquals("$md at $p selection", viaKey.view.selectionStart, viaSurrounding.view.selectionStart)
            }
        }
    }

    @Test
    fun backspaceInTheMiddleOfAParagraphDeletesACharacter() {
        val h = HostFixture("abc")
        h.caret(2)
        h.ic().deleteSurroundingText(1, 0)
        assertEquals("ac", h.md())
        assertEquals(1, h.view.selectionStart)
    }

    @Test
    fun otherDeleteSurroundingTextGoesThrough() {
        val h = HostFixture("abcd")
        h.caret(3)
        h.ic().deleteSurroundingText(2, 0)
        assertEquals("ad", h.md())
    }

    @Test
    fun enterEndsTheWordBeingComposed() {
        val h = HostFixture("")
        val ic = h.ic()
        ic.setComposingText("word", 1)
        ic.commitText("\n", 1)
        assertEquals("word\n", h.md())
    }

    @Test
    fun capsModeAfterASentinelIsTheStartOfAParagraph() {
        val h = HostFixture("- a\n- ") // the last item is empty: its paragraph is a sentinel
        h.caret(h.text.length)
        assertEquals("the plain framework answer for the sentinel", 0, TextUtils.getCapsMode(h.text, h.text.length, TextUtils.CAP_MODE_SENTENCES))
        assertEquals("what an ordinary empty line gets", TextUtils.getCapsMode("a\n", 2, TextUtils.CAP_MODE_SENTENCES), h.ic().getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES))
    }

    @Test
    fun capsModeElsewhereIsTheFrameworks() {
        val h = HostFixture("hello. ")
        h.caret(h.text.length)
        assertEquals(TextUtils.getCapsMode(h.text, h.text.length, TextUtils.CAP_MODE_SENTENCES), h.ic().getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES))
    }

    @Test
    fun aReadOnlyEditorHasNoInputConnection() {
        val h = HostFixture("a")
        h.view.editable = false
        assertNull(h.view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
    }

    @Test
    fun deleteSurroundingTextWithAComposingRegionDeletesBeforeTheRegion() {
        val h = HostFixture("abc")
        h.caret(3)
        val ic = h.ic()
        ic.setComposingText("de", 1) // abc[de]
        assertEquals("abcde", h.text.toString())
        ic.deleteSurroundingText(1, 0)
        assertEquals("the character before the composing region", "abde", h.text.toString())
        assertEquals("the caret keeps its place after the composing text", 4, h.view.selectionStart)
        assertEquals("the composing region follows", 2, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(h.text))
    }

    @Test
    fun deleteSurroundingTextWithAComposingRegionAtTheStartOfAnItemIsBackspaceThere() {
        val h = HostFixture("- a\n- ")
        h.caret(h.text.length)
        val ic = h.ic()
        ic.setComposingText("bc", 1)
        h.select(h.text.length - 1, h.text.length - 1) // the caret in the middle of the word: before the region is the item start
        ic.deleteSurroundingText(1, 0)
        assertEquals("the item became Body, the text stayed", "- a\nbc", h.md())
    }

    @Test
    fun backspaceDeletesWholeGraphemeClusters() {
        for ((name, cluster) in listOf(
            "skin tone" to "\uD83D\uDC4D\uD83C\uDFFD",
            "flag" to "\uD83C\uDDF7\uD83C\uDDFA",
            "ZWJ family" to "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
            "keycap" to "1\uFE0F\u20E3",
            "combining accent" to "e\u0301",
            "single emoji" to "\uD83D\uDE00",
        )) {
            val viaKey = HostFixture("a$cluster")
            viaKey.caret(viaKey.text.length)
            viaKey.key(KeyEvent.KEYCODE_DEL)
            assertEquals("$name via DEL", "a", viaKey.text.toString())
            val viaIc = HostFixture("a$cluster")
            viaIc.caret(viaIc.text.length)
            viaIc.ic().deleteSurroundingText(1, 0)
            assertEquals("$name via deleteSurroundingText", "a", viaIc.text.toString())
        }
    }

    @Test
    fun backspaceInTheMiddleOfTheTextStopsAtTheClusterBeforeTheCaret() {
        val h = HostFixture("x\uD83C\uDDF7\uD83C\uDDFAy")
        h.caret(h.text.length - 1) // between the flag and y
        h.key(KeyEvent.KEYCODE_DEL)
        assertEquals("xy", h.text.toString())
        assertEquals(1, h.view.selectionStart)
    }
}
