package app.tessera.ui.editor.engine

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.customview.widget.ExploreByTouchHelper
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.ObjectSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraEditTextTest {

    private fun kind(h: HostFixture, i: Int) = (app.tessera.ui.editor.engine.DocBinder.read(h.text).blocks[i] as NoteBlock.Text).kind

    // ------------------------------------------------------------ hardware keys

    @Test
    fun enterAndDelKeys() {
        val h = HostFixture("- [x] a")
        h.caret(1)
        assertTrue(h.key(KeyEvent.KEYCODE_ENTER))
        assertEquals("- [x] a\n- [ ] ", h.md())
        assertTrue(h.key(KeyEvent.KEYCODE_DEL)) // the empty new item -> Body
        assertEquals("- [x] a\n", h.md())
    }

    @Test
    fun tabAndShiftTabMoveListItemsAndLeaveOtherParagraphsAlone() {
        val h = HostFixture("- a\nplain")
        h.caret(1)
        assertTrue(h.key(KeyEvent.KEYCODE_TAB))
        assertEquals("    - a\nplain", h.md())
        assertTrue(h.key(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON))
        assertEquals("- a\nplain", h.md())
        h.caret(h.at("plain"))
        assertFalse("Tab on Body keeps its default", h.key(KeyEvent.KEYCODE_TAB))
        assertEquals("- a\nplain", h.md())
    }

    @Test
    fun markShortcutsOverASelectionAndAsTypingAttributes() {
        val h = HostFixture("abc")
        h.select(0, 3)
        assertTrue(h.key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON))
        assertEquals("**abc**", h.md())
        h.key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)
        assertEquals("abc", h.md())
        assertTrue(h.shortcut(KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON)) // the path of a real device
        assertEquals("*abc*", h.md())
        h.shortcut(KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON)
        h.shortcut(KeyEvent.KEYCODE_U, KeyEvent.META_CTRL_ON)
        assertEquals("<u>abc</u>", h.md())
        h.shortcut(KeyEvent.KEYCODE_U, KeyEvent.META_CTRL_ON)
        h.caret(3)
        h.key(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON) // no selection: the next typed text
        h.text.insert(3, "d")
        assertEquals("abc**d**", h.md())
    }

    @Test
    fun ctrlZAndCtrlShiftZ() {
        val h = HostFixture("a")
        h.caret(1)
        h.view.toggleKind(ParagraphKind.Heading)
        assertEquals("## a", h.md())
        assertTrue(h.shortcut(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON))
        assertEquals("a", h.md())
        assertTrue(h.shortcut(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        assertEquals("## a", h.md())
        h.key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
        assertEquals("a", h.md())
    }

    // ------------------------------------------------------------ typing: normalizer, auto-replace, history

    @Test
    fun aTypedMarkerAndSpaceBecomeAKindAndUndoGivesTheTypedCharactersBack() {
        val h = HostFixture("")
        h.text.insert(0, "-")
        h.text.insert(1, " ")
        assertEquals(ParagraphKind.ListItem(ListType.Dash, 0), kind(h, 0))
        assertEquals(0, h.text.length.coerceAtMost(0))
        h.view.undo()
        assertEquals(ParagraphKind.Body, kind(h, 0))
        assertEquals("- ", h.text.toString())
        h.view.undo()
        assertEquals("", h.text.toString())
        h.view.redo()
        h.view.redo()
        assertEquals(ParagraphKind.ListItem(ListType.Dash, 0), kind(h, 0))
    }

    @Test
    fun aPastedMarkerDoesNotAutoReplace() {
        val h = HostFixture("")
        h.view.setClip("- ")
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals(ParagraphKind.Body, kind(h, 0))
        assertEquals("- ", h.text.toString())
    }

    @Test
    fun anImeThatRecommitsAnUndoneMarkerDoesNotTriggerAgain() {
        val h = HostFixture("\\- ")
        h.caret(2)
        h.text.replace(0, 2, "- ")
        assertEquals(ParagraphKind.Body, kind(h, 0))
    }

    @Test
    fun undoKeepsTheMarksOfMarkedText() {
        val h = HostFixture("**bold** and *it*")
        h.caret(h.text.length)
        h.view.toggleKind(ParagraphKind.Quote)
        h.view.undo()
        assertEquals("**bold** and *it*", h.md())
        h.text.insert(h.text.length, "!")
        h.pass(600)
        h.text.insert(0, "x")
        h.view.undo()
        assertEquals("**bold** and *it!*", h.md())
        h.view.undo()
        assertEquals("**bold** and *it*", h.md())
    }

    @Test
    fun typingRunsCoalesceByPauseAndCommandsAreOwnSteps() {
        val h = HostFixture("")
        h.text.insert(0, "a"); h.pass(100); h.text.insert(1, "b")
        h.pass(600)
        h.text.insert(2, "c")
        h.view.undo()
        assertEquals("ab", h.md())
        h.view.undo()
        assertEquals("", h.md())
    }

    @Test
    fun aTypedNewlineInTheMiddleOfAListItemMakesAnItemViaTheNormalizer() {
        val h = HostFixture("- ab")
        h.text.insert(1, "\n")
        assertEquals("- a\n- b", h.md())
    }

    @Test
    fun theFormatStateReachesTheCallbackWithUndoFlags() {
        val h = HostFixture("a")
        assertFalse(h.states.last().canUndo)
        h.caret(1)
        h.view.toggleKind(ParagraphKind.Heading)
        assertEquals(ParagraphFormat.Single(ParagraphKind.Heading), h.states.last().paragraph)
        assertTrue(h.states.last().canUndo)
        h.view.toggleMark(Mark.Bold) // no selection: the typing attribute
        assertEquals(setOf(Mark.Bold), h.states.last().marks)
    }

    // ------------------------------------------------------------ sentinel

    @Test
    fun aCaretNeverStandsBeforeASentinel() {
        val h = HostFixture("- a\n- ")
        val sentinel = h.text.length - 1
        h.caret(sentinel)
        assertEquals(sentinel + 1, h.view.selectionStart)
    }

    // ------------------------------------------------------------ check boxes

    private fun tap(h: HostFixture, x: Float, y: Float, moveBy: Float = 0f): Boolean {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        var handled = h.view.dispatchTouchEvent(down)
        if (moveBy != 0f) handled = h.view.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, x, y + moveBy, 0)) || handled
        handled = h.view.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y + moveBy, 0)) || handled
        return handled
    }

    @Test
    fun aTapOnACheckBoxTogglesItAndAnEmitFollows() {
        val h = HostFixture("- [ ] a\n- [ ] b")
        val z = h.view.checkboxZones()
        assertEquals(2, z.size)
        assertTrue("the zone is the marker area wide and at least 48dp high", z.all { it.rect.width() == 30 && it.rect.height() >= 48 })
        tap(h, z[1].rect.exactCenterX(), z[1].rect.exactCenterY())
        assertEquals("- [ ] a\n- [x] b", h.md())
        h.pass(300)
        assertEquals(listOf("- [ ] a\n- [x] b"), h.emitted)
        h.view.undo()
        assertEquals("- [ ] a\n- [ ] b", h.md())
    }

    @Test
    fun aDragStartingOnACheckBoxDoesNotToggle() {
        val h = HostFixture("- [ ] a")
        val z = h.view.checkboxZones().single()
        tap(h, z.rect.exactCenterX(), z.rect.exactCenterY(), moveBy = 200f)
        assertEquals("- [ ] a", h.md())
    }

    @Test
    fun aTapInTheTextOfAnItemDoesNotToggle() {
        val h = HostFixture("- [ ] a")
        val z = h.view.checkboxZones().single()
        tap(h, z.rect.right + 100f, z.rect.exactCenterY())
        assertEquals("- [ ] a", h.md())
    }

    @Test
    fun readOnlyDoesNotToggleAndKeepsTheTextSelectable() {
        val h = HostFixture("- [ ] a")
        h.view.editable = false
        val z = h.view.checkboxZones().single()
        tap(h, z.rect.exactCenterX(), z.rect.exactCenterY())
        assertEquals("- [ ] a", h.md())
        assertNull(h.view.keyListener)
        assertTrue(h.view.isTextSelectable)
        h.view.performBackspace(); h.view.performEnter(); h.view.toggleKind(ParagraphKind.Quote)
        assertEquals("- [ ] a", h.md())
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun editableComesBackWithInputAndMovement() {
        val h = HostFixture("a")
        h.view.editable = false
        h.view.editable = true
        assertNotNull(h.view.keyListener)
        assertFalse(h.view.isTextSelectable)
        assertNotNull(h.view.movementMethod)
        h.caret(1)
        h.view.performEnter()
        assertEquals("a\n", h.md())
    }

    // ------------------------------------------------------------ accessibility

    @Test
    fun everyVisibleCheckBoxIsAVirtualNodeWithItsState() {
        val h = HostFixture("text\n- [x] done\n- [ ] todo")
        val helper = ViewCompat.getAccessibilityDelegate(h.view) as ExploreByTouchHelper
        val provider: AccessibilityNodeProviderCompat = helper.getAccessibilityNodeProvider(h.view)!!
        val host = provider.createAccessibilityNodeInfo(ExploreByTouchHelper.HOST_ID)!!
        assertEquals("the host node keeps the text", true, host.text.toString().contains("done"))
        assertEquals(2, host.childCount)
        val zones = h.view.checkboxZones()
        val done = provider.createAccessibilityNodeInfo(zones[0].ps)!!
        val todo = provider.createAccessibilityNodeInfo(zones[1].ps)!!
        assertTrue(done.isCheckable && done.isChecked)
        assertTrue(todo.isCheckable && !todo.isChecked)
        assertEquals("done", done.text.toString())
        assertEquals("android.widget.CheckBox", done.className.toString())
        assertTrue(provider.performAction(zones[1].ps, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals("text\n- [x] done\n- [x] todo", h.md())
        assertTrue(provider.createAccessibilityNodeInfo(zones[1].ps)!!.isChecked)
    }

    @Test
    fun readOnlyNodesAreStateOnly() {
        val h = HostFixture("- [ ] a")
        h.view.editable = false
        val helper = ViewCompat.getAccessibilityDelegate(h.view) as ExploreByTouchHelper
        val provider = helper.getAccessibilityNodeProvider(h.view)!!
        val id = h.view.checkboxZones().single().ps
        assertFalse(provider.performAction(id, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals("- [ ] a", h.md())
        assertFalse(provider.createAccessibilityNodeInfo(id)!!.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
    }

    // ------------------------------------------------------------ objects

    @Test
    fun objectsAreAsWideAsTheTextAreaAlsoNewOnesAndAfterAResize() {
        val h = HostFixture("a\n---\nb")
        h.view.setPadding(20, 0, 20, 0)
        h.layout()
        fun widths() = h.text.getSpans(0, h.text.length, ObjectSpan::class.java).map { it.widthPx }
        assertEquals(listOf(360), widths())
        h.caret(1)
        h.view.insertHorizontalRule()
        assertEquals(listOf(360, 360), widths())
        h.view.layout(0, 0, 300, 800)
        h.view.measure(android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY))
        h.view.layout(0, 0, 300, 800)
        assertEquals(listOf(260, 260), widths())
    }

    @Test
    fun aLongPressOnACheckBoxDoesNotToggle() {
        val h = HostFixture("- [ ] a")
        val z = h.view.checkboxZones().single()
        val t = SystemClock.uptimeMillis()
        val x = z.rect.exactCenterX()
        val y = z.rect.exactCenterY()
        h.view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        h.view.dispatchTouchEvent(MotionEvent.obtain(t, t + 1500, MotionEvent.ACTION_UP, x, y, 0))
        assertEquals("- [ ] a", h.md())
    }

    @Test
    fun aTapOnTheFirstLettersOfTheItemPlacesTheCaretAndDoesNotToggle() {
        val h = HostFixture("- [ ] abc")
        val z = h.view.checkboxZones().single()
        tap(h, z.rect.right + 2f, z.rect.exactCenterY())
        assertEquals("- [ ] abc", h.md())
    }

    @Test
    fun plainPasteShortcutNeverPastesTheFragment() {
        val h = HostFixture("**ab**cd")
        h.select(0, 2)
        h.view.onTextContextMenuItem(android.R.id.copy)
        h.caret(h.text.length)
        h.view.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        assertEquals("**ab**cdab", h.md())
        h.caret(h.text.length)
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("the ordinary paste gets the fragment", "**ab**cdab**ab**", h.md())
    }
}
