package app.tessera.ui.editor.engine

import android.content.ClipData
import android.content.ClipboardManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §6.8: inside Tessera the formatting travels, from anywhere else the text is plain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardBridgeTest {
    private val style = EngineTestStyle.create()

    @After
    fun forget() = ClipboardBridge.forget()

    private fun copy(h: HostFixture, a: Int, b: Int) {
        h.select(a, b)
        h.view.onTextContextMenuItem(android.R.id.copy)
    }

    private fun paste(h: HostFixture, at: Int) {
        h.caret(at)
        h.view.onTextContextMenuItem(android.R.id.paste)
        EditFixture.assertSane("paste", h.text, style)
    }

    @Test
    fun theSystemClipboardGetsPlainTextOnly() {
        val h = HostFixture("**ab**cd")
        copy(h, 0, 4)
        val clip = h.view.context.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("abcd", clip.getItemAt(0).coerceToText(h.view.context).toString())
        assertNull("no styled text, no html", clip.getItemAt(0).htmlText)
    }

    @Test
    fun aPartOfAParagraphKeepsItsMarksAndPastesInline() {
        val h = HostFixture("**ab**cd")
        copy(h, 1, 3)
        paste(h, 4)
        assertEquals("**ab**cd**b**c", h.md())
    }

    @Test
    fun theFormatTravelsBetweenEditorsOfTheProcess() {
        val a = HostFixture("- [x] one\n> two")
        copy(a, 0, a.text.length)
        val b = HostFixture("")
        paste(b, 0)
        assertEquals("- [x] one\n> two", b.md())
    }

    @Test
    fun aMultiParagraphFragmentIntoTheMiddleOfABodyParagraph() {
        val h = HostFixture("x\n- a\n- b\ny\nhelloworld")
        copy(h, h.at("a"), h.at("b") + 1)
        paste(h, h.at("hello") + 5)
        assertEquals("x\n- a\n- b\ny\nhelloa\n- bworld", h.md())
        h.view.undo()
        assertEquals("x\n- a\n- b\ny\nhelloworld", h.md())
    }

    @Test
    fun aPartlySelectedFirstLineIsATextNotAnItem() {
        val h = HostFixture("- hello\nx")
        copy(h, h.at("ell"), h.at("ell") + 3)
        paste(h, h.text.length)
        assertEquals("- hello\nxell", h.md())
    }

    @Test
    fun aFragmentOfWholeItemsIntoAnEmptyBodyLineKeepsTheKind() {
        val h = HostFixture("- a\n- b\n\n")
        copy(h, 0, h.at("b") + 1)
        paste(h, h.text.length)
        assertEquals("- a\n- b\n\n- a\n- b", h.md())
    }

    @Test
    fun aFragmentWithAnObject() {
        val h = HostFixture("a\n---\nb")
        copy(h, 0, h.text.length)
        paste(h, h.text.length)
        assertEquals("a\n---\nba\n---\nb", h.md())
    }

    @Test
    fun cutRemovesTheSelectionAndPasteBringsItBack() {
        val h = HostFixture("a **bc** d")
        h.select(2, 4)
        h.view.onTextContextMenuItem(android.R.id.cut)
        assertEquals("a  d", h.md())
        paste(h, 2)
        assertEquals("a **bc** d", h.md())
    }

    @Test
    fun aClipFromAnotherAppIsPastedAsPlainText() {
        val h = HostFixture("a")
        copy(h, 0, 1)
        h.view.context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newHtmlText(null, "bold", "<b>bold</b>"))
        paste(h, 1)
        assertEquals("abold", h.md())
    }

    @Test
    fun aClipThatChangedSinceTheCopyIsPlain() {
        val h = HostFixture("**a**")
        copy(h, 0, 1)
        h.view.setClip("zzz") // another app copied something else
        paste(h, 1)
        assertEquals("azzz", h.text.toString())
    }

    @Test
    fun plainMultiLineTextTakesTheKindOfTheParagraphItStartedIn() {
        val h = HostFixture("- x")
        h.view.setClip("a\nb")
        paste(h, 1)
        assertEquals("- xa\n- b", h.md())
    }

    @Test
    fun aPasteIsOneStepOfHistory() {
        val h = HostFixture("a")
        h.view.setClip("l1\nl2\nl3")
        paste(h, 1)
        h.view.undo()
        assertEquals("a", h.md())
    }

    @Test
    fun readOnlyCopiesButDoesNotCutOrPaste() {
        val h = HostFixture("abc")
        h.view.editable = false
        h.select(0, 2)
        h.view.onTextContextMenuItem(android.R.id.copy)
        assertEquals("ab", ClipboardBridge.plainText(h.view.context))
        h.view.onTextContextMenuItem(android.R.id.cut)
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("abc", h.md())
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anEmptyClipOfAnotherAppDoesNotBringBackACutImage() {
        val h = HostFixture("a\n![](tessera-img://x)\nb")
        val o = h.at("\uFFFC")
        h.select(o, o + 1)
        h.view.onTextContextMenuItem(android.R.id.cut) // the plain text of an image is empty
        assertEquals("a\nb", h.md())
        h.caret(h.text.length)
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("our own clip: the image is back", "a\nb\n![](tessera-img://x)", h.md().trimEnd())
        h.view.setClip("") // another app puts an empty text on the clipboard
        h.caret(0)
        val before = h.md()
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("it is pasted as the plain text it is: nothing", before, h.md())
    }
}
