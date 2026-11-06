package app.tessera.ui.editor.engine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.ParagraphStyle
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuggestionSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.SENTINEL_CHAR
import app.tessera.ui.editor.engine.spans.SentinelSpan
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §8 "Instrumented": what a user does with a real keyboard, on the device. The keyboard is played through the editor's own
 * [InputConnection] with the calls real IMEs make (Gboard: `commitText`, `deleteSurroundingText(1, 0)`; Samsung and hardware: key
 * events; AOSP LatinIME over a selection: `setSelection(end, end)` + `deleteSurroundingText(len, 0)`). Every key is a DOWN + UP pair,
 * and every assert is on what the user sees or what is saved, never on how the engine gets there.
 */
class NativeEditorImeTest {

    private fun item(t: ListType, level: Int = 0, checked: Boolean = false) = ParagraphKind.ListItem(t, level, checked)
    private fun content(e: DeviceEditor) = e.blocks().map { (it as NoteBlock.Text).content.text }

    private fun InputConnection.press(code: Int) {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** One Backspace of a keyboard, by one of the two ways keyboards send it. */
    private enum class Backspace {
        /** Samsung, hardware keyboards: a key event (over a selection too). */
        DelKey,

        /** Gboard: `deleteSurroundingText(1, 0)`; over a selection the AOSP LatinIME way: caret to the end, delete the length. */
        DeleteSurrounding,
    }

    private fun backspace(e: DeviceEditor, way: Backspace) = e.ime {
        when (way) {
            Backspace.DelKey -> press(KeyEvent.KEYCODE_DEL)
            Backspace.DeleteSurrounding -> {
                val s = e.view.selectionStart
                val t = e.view.selectionEnd
                beginBatchEdit()
                if (s == t) deleteSurroundingText(1, 0) else { setSelection(t, t); deleteSurroundingText(t - s, 0) }
                endBatchEdit()
            }
        }
    }

    // ------------------------------------------------------------------ Enter (§6.4)

    @Test
    fun enterByCommitTextAndByTheEnterKeySplitsAParagraphAndBothPartsKeepTheKind() {
        DeviceEditor("- ab\n> cd").use { e ->
            e.caret(e.at("ab", 1))
            e.ime { commitText("\n", 1) }
            assertEquals("commitText(\\n) in the middle of a bullet", "- a\n- b\n> cd", e.md())
            assertEquals("the caret starts the second part", e.at("b"), e.ui { e.view.selectionStart })

            e.caret(e.at("cd", 1))
            e.ime { press(KeyEvent.KEYCODE_ENTER) }
            assertEquals("ENTER key (down + up = one Enter) in the middle of a quote", "- a\n- b\n> c\n> d", e.md())
            assertEquals(e.at("d"), e.ui { e.view.selectionStart })
        }
    }

    @Test
    fun enterAfterAWordInCompositionKeepsTheWordAndLeavesAHeadingForBody() {
        DeviceEditor("# Tit").use { e ->
            e.caret(e.text().length)
            e.ime { setComposingText("le", 1); commitText("\n", 1) } // the keyboard still composes "le" when Enter comes
            assertEquals("# Title\n", e.md())
            assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body), e.kinds())
            e.ime { setComposingText("wo", 1); press(KeyEvent.KEYCODE_ENTER) }
            assertEquals("# Title\nwo\n", e.md())
        }
    }

    @Test
    fun enterOnAnEmptyItemFirstOutdentsThenLeavesTheList() {
        DeviceEditor("- a\n        - ").use { e -> // an empty level-2 item at the end
            e.caret(e.text().length)
            e.ime { commitText("\n", 1) }
            assertEquals("level 2 -> 1, no new paragraph", "- a\n    - ", e.md())
            e.ime { press(KeyEvent.KEYCODE_ENTER) }
            assertEquals("level 1 -> 0", "- a\n- ", e.md())
            e.ime { commitText("\n", 1) }
            assertEquals("level 0 -> Body", "- a\n", e.md())
            assertEquals(listOf(item(ListType.Bullet), ParagraphKind.Body), e.kinds())
        }
        DeviceEditor("- [ ] a\n- [ ] \n- [ ] c").use { e -> // an empty item in the middle of a checklist
            e.caret(e.at("a", 2))
            e.ime { press(KeyEvent.KEYCODE_ENTER) }
            assertEquals("- [ ] a\n\n- [ ] c", e.md())
        }
        DeviceEditor("> q\n> ").use { e ->
            e.caret(e.text().length)
            e.ime { commitText("\n", 1) }
            assertEquals("an empty quote line -> Body", "> q\n", e.md())
        }
    }

    @Test
    fun enterAtTheEndOfAChecklistShowsTheMarkerOnTheEmptyLastLine() {
        DeviceEditor("- [x] done\n- [ ] a").use { e ->
            e.caret(e.text().length)
            e.ime { commitText("\n", 1) }
            assertEquals("a new unchecked item", "- [x] done\n- [ ] a\n- [ ] ", e.md())
            assertEquals(item(ListType.Check), e.kinds().last())

            val (lastLine, lineOfA, sentinel) = e.ui {
                val t = e.view.text!!
                val l = e.view.layout
                val sentinelOk = t.last() == SENTINEL_CHAR &&
                    t.getSpans(t.length - 1, t.length, SentinelSpan::class.java).any { t.getSpanStart(it) == t.length - 1 }
                Triple(lineRect(e, l.lineCount - 1), lineRect(e, l.getLineForOffset(t.indexOf("a"))), sentinelOk)
            }
            assertTrue("the empty last item holds the marked U+200B", sentinel)
            assertEquals("the caret is after the sentinel", e.text().length, e.ui { e.view.selectionStart })
            assertEquals("3 lines", 3, e.ui { e.view.layout.lineCount })

            val shot = e.capture()
            val onEmpty = countNear(shot, lastLine, DeviceStyle.ACCENT)
            val onA = countNear(shot, lineOfA, DeviceStyle.ACCENT)
            assertTrue("the check box of the empty last line is painted: $onEmpty accent pixels (the item above: $onA)", onA > 0 && onEmpty * 2 >= onA)

            e.ime { commitText("b", 1) }
            assertEquals("typing replaces the placeholder", "- [x] done\n- [ ] a\n- [ ] b", e.md())
            assertFalse("no U+200B left", e.text().contains(SENTINEL_CHAR))
        }
    }

    /** The marker area of a line (the leading margin of a level-0 item) in view coordinates. */
    private fun lineRect(e: DeviceEditor, line: Int): Rect {
        val l = e.view.layout
        val top = e.view.totalPaddingTop - e.view.scrollY
        val left = e.view.totalPaddingLeft
        return Rect(left, top + l.getLineTop(line), left + e.style.listMarkerAreaPx.toInt(), top + l.getLineBottom(line))
    }

    // ------------------------------------------------------------------ Backspace (§6.4)

    @Test
    fun backspaceByTheDelKey() = backspaceCases(Backspace.DelKey)

    @Test
    fun backspaceByDeleteSurroundingText() = backspaceCases(Backspace.DeleteSurrounding)

    private class Case(val label: String, val md: String, val caretAt: String, val shift: Int, val want: String, val wantCaret: Int)

    private fun backspaceCases(way: Backspace) {
        val cases = listOf(
            Case("one character of a word", "abc", "c", 0, "ac", 1),
            Case("start of a document: nothing", "abc", "abc", 0, "abc", 0),
            Case("start of a level-0 item -> Body in place", "x\n- item", "item", 0, "x\nitem", 2),
            Case("start of a nested item -> one level up", "x\n    - item", "item", 0, "x\n- item", 2),
            Case("start of a checked item -> Body", "- [x] done", "done", 0, "done", 0),
            Case("start of a quote -> Body", "> q", "q", 0, "q", 0),
            Case("Body after Body: joined", "one\ntwo", "two", 0, "onetwo", 3),
            Case("Body under a title: joined, takes the title's kind", "# T\nbody", "body", 0, "# Tbody", 1),
            Case("a skin-tone emoji goes at once", "x👍🏽", "👍🏽", "👍🏽".length, "x", 1),
            Case("a flag goes at once", "x🇷🇺", "🇷🇺", "🇷🇺".length, "x", 1),
            Case("a ZWJ family goes at once", "x👨‍👩‍👧", "👨‍👩‍👧", "👨‍👩‍👧".length, "x", 1),
            Case("a keycap goes at once", "x1️⃣", "1️⃣", "1️⃣".length, "x", 1),
        )
        for (c in cases) DeviceEditor(c.md).use { e ->
            e.caret(e.at(c.caretAt, c.shift))
            backspace(e, way)
            assertEquals("$way, ${c.label}", c.want, e.md())
            assertEquals("$way, ${c.label}: caret", c.wantCaret, e.ui { e.view.selectionStart })
        }

        DeviceEditor("---\nbody").use { e ->
            e.caret(e.at("body"))
            backspace(e, way)
            assertEquals("$way, after an object: nothing deleted yet", "---\nbody", e.md())
            assertEquals("$way, after an object: the object is selected", 0 to 1, e.ui { e.view.selectionStart to e.view.selectionEnd })
            backspace(e, way)
            assertEquals("$way, the second Backspace deletes the object paragraph with its line break", "body", e.md())
            assertEquals("$way, the caret stays at the start of its paragraph", 0, e.ui { e.view.selectionStart })
        }
    }

    @Test
    fun deleteSurroundingTextWhileAWordIsComposedErasesTheCharacterBeforeTheWord() {
        DeviceEditor("ab cd").use { e ->
            e.caret(5)
            e.ime { setComposingRegion(3, 5); deleteSurroundingText(1, 0) } // as BaseInputConnection: before the composing region
            assertEquals("abcd", e.md())
        }
    }

    // ------------------------------------------------------------------ the real keyboard: Gboard of the device

    /** Gboard's own Backspace, twice, after an object (§6.4 rule 2): the first selects it, the second deletes its paragraph. */
    @Test
    fun gboardBackspaceSelectsAnObjectAndTheSecondPressDeletesIt() {
        DeviceEditor("---\nbody").use { e ->
            val ua = e.instrumentation.uiAutomation
            ua.serviceInfo = ua.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }

            val del = showKeyboard(e, e.view)
            e.caret(e.at("body"))
            Thread.sleep(300) // Gboard learns the new caret
            pressKey(e, del)
            assertEquals("Gboard, first Backspace: nothing deleted yet", "---\nbody", e.md())
            assertEquals("Gboard, first Backspace: the object is selected", 0 to 1, e.ui { e.view.selectionStart to e.view.selectionEnd })

            // what the second press does to the text and the selection, seen from outside the engine
            val seen = CopyOnWriteArrayList<String>()
            e.ui {
                val t = e.view.text!!
                t.setSpan(object : SpanWatcher {
                    override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = Unit
                    override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = Unit
                    override fun onSpanChanged(text: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
                        if (what === Selection.SELECTION_START || what === Selection.SELECTION_END) {
                            seen += "selection ${Selection.getSelectionStart(text)}..${Selection.getSelectionEnd(text)}"
                        }
                    }
                }, 0, t.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                e.view.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { seen += "replace $start+$count by $after" }
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable) = Unit
                })
            }
            pressKey(e, del)
            Log.i("NativeEditorGboard", "second Backspace over the selected object: $seen; md: ${e.md()}")
            assertEquals("Gboard, the second Backspace deletes the object paragraph (seen: ${seen.distinct()})", "body", e.md())
        }
    }

    /** Focuses [v], shows the keyboard, returns where its Delete key is on the screen. */
    private fun showKeyboard(e: DeviceEditor, v: android.view.View): Rect {
        e.ui {
            e.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            v.requestFocus()
        }
        val ua = e.instrumentation.uiAutomation
        val until = SystemClock.uptimeMillis() + 15_000
        var last: Rect? = null
        while (SystemClock.uptimeMillis() < until) {
            e.ui { // the keyboard binds to the focused view a moment after the focus: ask again until it is there
                v.windowInsetsController?.show(WindowInsets.Type.ime())
                e.activity.getSystemService(InputMethodManager::class.java).showSoftInput(v, 0)
            }
            Thread.sleep(500)
            val ime = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root
            val key = ime?.let { buildList { collect(it) { n -> n.contentDescription?.toString() == "Delete" } }.firstOrNull() }
            val r = key?.let { Rect().also { key.getBoundsInScreen(it) } }
            if (r != null && r == last) return r // the keyboard has stopped sliding in
            last = r
        }
        error("the keyboard did not show a Delete key; windows: ${ua.windows.map { it.type to it.title }}")
    }

    /** A finger on a key of the keyboard window (another app's window: through the shell). */
    private fun pressKey(e: DeviceEditor, key: Rect) {
        val pfd = e.instrumentation.uiAutomation.executeShellCommand("input tap ${key.centerX()} ${key.centerY()}")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        e.idle()
        Thread.sleep(300)
        e.idle()
    }

    // ------------------------------------------------------------------ selection over paragraphs (§6.4 last line)

    @Test
    fun deletingOrTypingOverASelectionAcrossParagraphsLeavesTheKindOfTheFirst() {
        val md = "# Title text\n- item one\n> quote here"
        for (way in Backspace.entries) DeviceEditor(md).use { e ->
            e.select(e.at("text"), e.at("here"))
            backspace(e, way)
            assertEquals("$way over title..quote", "# Title here", e.md())
        }
        DeviceEditor(md).use { e ->
            e.select(e.at("text"), e.at("here"))
            e.ime { commitText("X", 1) }
            assertEquals("typed over title..quote", "# Title Xhere", e.md())
        }
        DeviceEditor("body start\n# Head\n- [x] item").use { e ->
            e.select(e.at("start"), e.at("item"))
            backspace(e, Backspace.DelKey)
            assertEquals("Body..checklist", "body item", e.md())
            assertEquals(listOf(ParagraphKind.Body), e.kinds())
        }
    }

    // ------------------------------------------------------------------ auto-replace (§6.5)

    @Test
    fun aMarkerTypedCharByCharAtTheStartOfBodyBecomesAKindAndUndoGivesItBack() {
        val markers = listOf(
            "* " to item(ListType.Bullet),
            "- " to item(ListType.Dash),
            "1. " to item(ListType.Number),
            "1) " to item(ListType.Number),
            "[] " to item(ListType.Check),
            "[ ] " to item(ListType.Check),
            "# " to ParagraphKind.Title,
            "## " to ParagraphKind.Heading,
            "### " to ParagraphKind.Subheading,
            "> " to ParagraphKind.Quote,
            "``` " to ParagraphKind.Mono,
        )
        for ((marker, kind) in markers) DeviceEditor("").use { e ->
            for (ch in marker) e.ime { commitText(ch.toString(), 1) }
            assertEquals("\"$marker\": kind", listOf(kind), e.kinds())
            assertEquals("\"$marker\": the marker is gone", listOf(""), content(e))

            e.ui { e.view.undo() }
            assertEquals("\"$marker\": undo -> Body", listOf(ParagraphKind.Body), e.kinds())
            assertEquals("\"$marker\": undo -> the typed characters", listOf(marker), content(e))
            assertEquals("\"$marker\": undo -> caret after them", marker.length, e.ui { e.view.selectionStart })

            e.ui { e.view.redo() }
            e.ime { commitText("x", 1) }
            assertEquals("\"$marker\": redo, then typing goes into the new kind", listOf(kind), e.kinds())
            assertEquals(listOf("x"), content(e))
        }
        DeviceEditor("word").use { e ->
            e.caret(4)
            for (ch in " - ") e.ime { commitText(ch.toString(), 1) }
            assertEquals("not at the start of the paragraph: nothing", listOf(ParagraphKind.Body), e.kinds())
            assertEquals(listOf("word - "), content(e))
        }
    }

    // ------------------------------------------------------------------ check boxes (§6.10)

    /** A real finger on the device (`sendPointerSync`), at a point of the editor. */
    private fun tap(e: DeviceEditor, p: PointF) {
        val loc = e.ui { IntArray(2).also { e.view.getLocationOnScreen(it) } }
        val x = loc[0] + p.x
        val y = loc[1] + p.y
        val t = SystemClock.uptimeMillis()
        e.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        e.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        e.idle()
    }

    /** Where the check box of the item holding [needle] is seen on the screen: the middle of the accent pixels by its first line. */
    private fun checkbox(e: DeviceEditor, needle: String): PointF {
        val r = e.ui { lineRect(e, e.view.layout.getLineForOffset(e.view.text.toString().indexOf(needle))) }
        val box = boundsNear(e.capture(), r, DeviceStyle.ACCENT) ?: error("no check box drawn by \"$needle\" in $r")
        return PointF(box.exactCenterX(), box.exactCenterY())
    }

    /** The first letter of the item holding [needle], on the height of its check box. */
    private fun firstLetter(e: DeviceEditor, needle: String): PointF {
        val y = checkbox(e, needle).y
        return e.ui {
            val at = e.view.text.toString().indexOf(needle)
            PointF(e.view.totalPaddingLeft + e.view.layout.getPrimaryHorizontal(at) + 3 * e.activity.resources.displayMetrics.density, y)
        }
    }

    @Test
    fun aTapOnACheckBoxTogglesItAndInReadOnlyItDoesNot() {
        DeviceEditor("- [ ] a\n- [ ] b").use { e ->
            tap(e, checkbox(e, "b"))
            assertEquals("- [ ] a\n- [x] b", e.md())
            assertEquals("the toggle is saved", "- [ ] a\n- [x] b", e.awaitEmit())
            tap(e, checkbox(e, "b"))
            assertEquals("a second tap unchecks", "- [ ] a\n- [ ] b", e.md())
        }
        DeviceEditor("- [ ] a\n- [ ] b").use { e ->
            e.ui { e.view.editable = false }
            tap(e, checkbox(e, "a"))
            assertEquals("read-only: nothing toggles", "- [ ] a\n- [ ] b", e.md())
            assertEquals("read-only: nothing is saved", null, e.awaitEmit())
        }
    }

    @Test
    fun aTapOnTheFirstLettersPutsTheCaretAndALongPressDoesNotToggle() {
        DeviceEditor("- [ ] abc\n- [ ] def").use { e ->
            tap(e, firstLetter(e, "def"))
            assertEquals("a tap on the text: no toggle", "- [ ] abc\n- [ ] def", e.md())
            val (s, t) = e.ui { e.view.selectionStart to e.view.selectionEnd }
            val at = e.at("def")
            assertTrue("the caret is at the first letters ($s..$t, item at $at)", s == t && s in at..at + 1)
        }
        DeviceEditor("- [ ] abc\n- [ ] def").use { e ->
            val p = checkbox(e, "abc")
            val loc = e.ui { IntArray(2).also { e.view.getLocationOnScreen(it) } }
            val t = SystemClock.uptimeMillis()
            val hold = ViewConfiguration.getLongPressTimeout() + 400L
            e.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, loc[0] + p.x, loc[1] + p.y, 0))
            Thread.sleep(hold)
            e.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + hold, MotionEvent.ACTION_UP, loc[0] + p.x, loc[1] + p.y, 0))
            e.idle()
            assertEquals("a long press on a check box: no toggle", "- [ ] abc\n- [ ] def", e.md())
            assertEquals("nothing is saved", null, e.awaitEmit())
            assertTrue("a long press selects a word", e.ui { e.view.selectionStart != e.view.selectionEnd })
        }
    }

    @Test
    fun theAccessibilityTreeHasACheckableNodePerCheckBoxAndItsClickToggles() {
        DeviceEditor("- [ ] first\n- [x] second\nplain").use { e ->
            val ua = e.instrumentation.uiAutomation
            fun boxes(): List<AccessibilityNodeInfo> {
                ua.waitForIdle(300, 5_000)
                val root = ua.rootInActiveWindow ?: error("no active window")
                return buildList { collect(root) { it.className == "android.widget.CheckBox" } }
            }

            val nodes = boxes()
            assertEquals("one node per check box: ${nodes.map { it.text }}", 2, nodes.size)
            assertTrue(nodes.all { it.isCheckable })
            assertEquals(listOf(false, true), nodes.map { it.isChecked })
            assertEquals(listOf("first", "second"), nodes.map { it.text?.toString() })
            val minPx = 48 * e.activity.resources.displayMetrics.density - 1
            val margin = e.style.listMarkerAreaPx.toInt() // the leading margin of a level-0 item
            for (n in nodes) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                assertTrue("touch area: as wide as the marker ($margin px), ≥ 48dp high: $r", abs(r.width() - margin) <= 1 && r.height() >= minPx)
            }

            val clicked = SystemClock.uptimeMillis()
            assertTrue("click is performed", nodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK))
            e.idle()
            assertEquals("- [x] first\n- [x] second\nplain", e.md())
            // the app asked again at once, and the tree as a screen reader holds it (kept up to date by the app's events):
            // how long the service's copy says "not checked" after the click is logged for the report
            var seen = boxes().map { it.isChecked }
            val firstSeen = seen
            val until = clicked + 3_000
            while (seen != listOf(true, true) && SystemClock.uptimeMillis() < until) { Thread.sleep(50); seen = boxes().map { it.isChecked } }
            val freshAfter = SystemClock.uptimeMillis() - clicked
            val asked = boxes().onEach { it.refresh() }.map { it.isChecked } // refresh() also renews the service's copy: only after the wait
            Log.i("NativeEditorA11y", "after the click: the service's tree first said $firstSeen, then $seen after $freshAfter ms; the app asked again: $asked")
            assertEquals("the app says checked", listOf(true, true), asked)
            assertEquals("the screen reader's tree says checked once the events settled (fresh after $freshAfter ms)", listOf(true, true), firstSeen)

            e.ui { e.view.editable = false }
            boxes()[1].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            e.idle()
            assertEquals("read-only: a click changes nothing", "- [x] first\n- [x] second\nplain", e.md())
        }
    }

    private fun MutableList<AccessibilityNodeInfo>.collect(n: AccessibilityNodeInfo, pick: (AccessibilityNodeInfo) -> Boolean) {
        if (pick(n)) add(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, pick) }
    }

    // ------------------------------------------------------------------ load and echo (§6.9)

    @Test
    fun theOldMarkdownComingAgainDoesNotRollBackWhatWasTyped() {
        DeviceEditor("a").use { e ->
            e.caret(1)
            e.ime { commitText("b", 1) }
            assertEquals("ab", e.awaitEmit())
            e.ui { e.view.setMarkdown("a") } // the screen recomposes with the note it was given before
            assertEquals("the typed text stays", "ab", e.md())
            e.ui { e.view.setMarkdown("ab") } // the echo of the save
            assertEquals("ab", e.md())
            e.ui { e.view.setMarkdown("xyz") } // a real change from outside
            assertEquals("xyz", e.md())
            assertEquals("nothing of the loads is saved back", listOf("ab"), e.emitted.toList())
        }
    }

    // ------------------------------------------------------------------ clipboard (§6.8)

    private fun clipboard(e: DeviceEditor) = e.activity.getSystemService(ClipboardManager::class.java)

    @Test
    fun copyAndPasteInsideTesseraKeepsTheFormatAndTheSystemClipboardGetsPlainText() {
        DeviceEditor("**bold** and *it*\n- item\n- [x] done\n").use { e ->
            ClipboardBridge.forget()
            e.select(0, e.at("done", 4))
            assertTrue(e.ui { e.view.onTextContextMenuItem(android.R.id.copy) })
            assertEquals("the system clipboard: plain text", "bold and it\nitem\ndone", e.ui { clipboard(e).primaryClip?.getItemAt(0)?.coerceToText(e.activity)?.toString() })

            e.caret(e.text().length) // the empty last line
            e.ui { e.view.onTextContextMenuItem(android.R.id.paste) }
            assertEquals("**bold** and *it*\n- item\n- [x] done\n**bold** and *it*\n- item\n- [x] done", e.md())
        }
    }

    @Test
    fun styledTextFromAnotherAppIsPastedAsPlainTextInTheKindOfTheParagraph() {
        DeviceEditor("- x\nplain").use { e ->
            ClipboardBridge.forget()
            val styled = SpannableString("Red big\nsecond").apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(Color.RED), 0, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(2f), 4, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(BackgroundColorSpan(Color.YELLOW), 8, 14, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(UnderlineSpan(), 8, 14, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StrikethroughSpan(), 0, 14, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            e.ui { clipboard(e).setPrimaryClip(ClipData.newPlainText("styled", styled)) }
            e.caret(e.at("x", 1))
            e.ui { e.view.onTextContextMenuItem(android.R.id.paste) }
            assertEquals("plain, both lines take the kind where it was pasted", "- xRed big\n- second\nplain", e.md())
            val foreign = e.ui {
                val t = e.view.text!!
                listOf(CharacterStyle::class.java, ParagraphStyle::class.java, ReplacementSpan::class.java)
                    .flatMap { t.getSpans(0, t.length, it).toList() }
                    .filter { it !is SuggestionSpan && !it.javaClass.name.startsWith("app.tessera.") }
                    .map { it.javaClass.name }
            }
            assertEquals("nothing of the clip's style stays in the text", emptyList<String>(), foreign)
        }
        DeviceEditor("plain").use { e ->
            ClipboardBridge.forget()
            e.ui { clipboard(e).setPrimaryClip(ClipData.newHtmlText("html", "Bold html", "<b>Bold</b> <i>html</i>")) }
            e.caret(5)
            e.ui { e.view.onTextContextMenuItem(android.R.id.paste) }
            assertEquals("an HTML clip: its text only", "plainBold html", e.md())
        }
        DeviceEditor("").use { e ->
            ClipboardBridge.forget()
            e.ui { clipboard(e).setPrimaryClip(ClipData.newPlainText(null, "# not a title")) }
            e.ui { e.view.onTextContextMenuItem(android.R.id.paste) }
            assertEquals("a pasted marker does not auto-replace", listOf(ParagraphKind.Body), e.kinds())
            assertEquals(listOf("# not a title"), content(e))
        }
    }
}
