package app.tessera.ui.editor.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.paragraphEnd
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.tableZones
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Touches off the text and the insertion handle (spec §2, §3; tests §6.2, 1–6) in the real app, with real touches injected into the window
 * (`UiAutomation`), not through the Compose rule (memory `compose-test-rule-gesture-artifact`):
 * - no insertion handle (the drop) after a tap on the text, under the text, on opening a note, and a tap just under the caret moves it;
 * - a long press under the text: no selection, the caret at the end, the keyboard, the menu "Paste / Select all", a vibration; "Select all" gives
 *   the selection handles and "Copy";
 * - a long press right of a short line: the caret at the end of that line, the menu;
 * - a long press on a word still selects it;
 * - a tap under the text: the caret at the end of the note (next to a table that ends it);
 * - a viewer of a shared note: a long press under the text does nothing;
 * - cold: a long press under the text of an unfocused note whose end the keyboard covers — the menu outlives the keyboard coming up and stands at
 *   the caret after the note scrolled to it.
 *
 * The drop is a separate popup window: PixelCopy of the activity window does not see it (Chunk 0b, probe/report.md). It is found as the window that
 * holds `Editor$InsertionHandleView` ([WindowInspector]) and, as a check, as accent pixels under the caret on a screenshot of the whole screen.
 *
 * The device needs a real on-screen keyboard. An AVD with a hardware keyboard (`hw.keyboard = yes`, a touchscreen that is also a stylus) shows
 * Gboard's narrow panel instead: set `settings put secure show_ime_with_hard_keyboard 1` and `settings put secure stylus_handwriting_enabled 0`
 * before the run (and back after it).
 */
@SdkSuppress(minSdkVersion = 29) // WindowInspector
@HiltAndroidTest
class EditorTouchOutsideTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(4, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private val ctx get() = app.instrumentation.targetContext
    private val ua by lazy {
        app.instrumentation.uiAutomation.also { it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS } }
    }
    private val t by lazy { Tables(app) }
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun log(msg: String) = Log.i(TAG, msg)

    private val paste get() = ctx.getString(android.R.string.paste)
    private val selectAll get() = ctx.getString(android.R.string.selectAll)
    private val copy get() = ctx.getString(android.R.string.copy)

    private val three = "Alpha bravo charlie delta 1\nAlpha bravo charlie delta 2\nAlpha bravo charlie delta 3"

    // ------------------------------------------------------------------ the note

    private fun lightTheme() = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemeMode(ThemeMode.LIGHT)
    }

    /** A note opened from the list, light theme; not focused, no keyboard. */
    private fun open(id: String, md: String): TesseraEditText {
        lightTheme()
        app.seed(id, md, System.currentTimeMillis())
        app.launch()
        // On a loaded host a cold start takes longer than AppDriver's 15 s to show the card, and the editor longer than its 5 s to open after the tap
        // (AppDriver.open then taps a card that is gone): one tap, generous waits.
        compose.waitUntil(60_000) { compose.onAllNodesWithTag(UiTestTags.noteCard(id)).fetchSemanticsNodes().isNotEmpty() }
        app.tapCard(id)
        compose.waitUntil(60_000) { app.editorOrNull() != null }
        val e = app.awaitEditor()
        app.settle(1_200)
        return e
    }

    /**
     * [s] on the clipboard. The system then shows its clipboard chip in the bottom-left corner for a few seconds: a touch there goes to it,
     * not to the note (a long press under the last line's first word landed on it once) — wait until it is gone.
     */
    private fun setClip(s: String) {
        app.ui { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", s)) }
        val until = SystemClock.uptimeMillis() + 15_000
        Thread.sleep(500) // the chip comes up
        while (shell("dumpsys window windows").contains("ClipboardOverlay") && SystemClock.uptimeMillis() < until) Thread.sleep(300)
    }

    private fun sel(e: TesseraEditText) = app.ui { e.selectionStart to e.selectionEnd }
    private fun len(e: TesseraEditText) = app.ui { e.text!!.length }

    /** On the screen: the middle of the character at [offset], on its line. */
    private fun pointAt(e: TesseraEditText, offset: Int): Pair<Int, Int> = app.ui {
        val loc = IntArray(2).also(e::getLocationOnScreen)
        val l = e.layout
        val line = l.getLineForOffset(offset)
        val x = loc[0] + e.totalPaddingLeft + ((l.getPrimaryHorizontal(offset) + l.getPrimaryHorizontal(offset + 1)) / 2).toInt()
        val y = loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2
        x to y
    }

    /** The bottom of the IME insets of the window (0: no keyboard, or a floating one that covers no line). */
    private fun imeInset(e: TesseraEditText): Int = app.ui { e.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0 }

    /** On the screen: the top of what covers the bottom of the editor — the glass bar (its inset) or the keyboard (the window's IME insets). */
    private fun coverTop(e: TesseraEditText): Int = app.ui {
        val root = e.rootView
        val rootTop = IntArray(2).also(root::getLocationOnScreen)[1]
        minOf(IntArray(2).also(e::getLocationOnScreen)[1] + e.height - e.bottomInset, rootTop + root.height - imeInset(e))
    }

    /** On the screen: the bottom of the last line. */
    private fun textBottom(e: TesseraEditText): Int = app.ui {
        IntArray(2).also(e::getLocationOnScreen)[1] + e.totalPaddingTop - e.scrollY + e.layout.height
    }

    /**
     * On the screen: a point under the last line, halfway down to what covers the editor (no deeper than 120 dp under the text). With the keyboard
     * up a three-line note on a small phone leaves ~65 dp there: at least 40 dp, or the note does not fit the test.
     *
     * The x is under a word of the last line ([underAWord]), the user's complaint 1: right of the line's end the platform already put the caret at
     * the end before §3, so a point there could not tell the new behaviour from the old. A note that ends with an object passes `false`.
     */
    private fun belowText(e: TesseraEditText, underAWord: Boolean = true): Pair<Int, Int> {
        val cover = coverTop(e)
        val bottom = textBottom(e)
        check(cover - bottom > dp(40)) { "no room under the text: its bottom $bottom, the bar/keyboard top $cover" }
        val y = minOf((bottom + cover) / 2, bottom + dp(120))
        val x = app.ui {
            val l = e.layout
            val last = l.lineCount - 1
            val inLayout = dp(24).toFloat()
            if (underAWord) check(inLayout >= l.getLineLeft(last) && inLayout <= l.getLineRight(last)) {
                "the point is not under a word of the last line: x $inLayout, the line ${l.getLineLeft(last)}..${l.getLineRight(last)}"
            }
            IntArray(2).also(e::getLocationOnScreen)[0] + e.totalPaddingLeft + inLayout.toInt()
        }
        return x to y
    }

    /** On the screen: where the engine draws its caret; `null` when it draws none. */
    private fun caretOnScreen(e: TesseraEditText): Rect? = app.ui {
        val c = e.caretRect() ?: return@ui null
        val loc = IntArray(2).also(e::getLocationOnScreen)
        Rect((loc[0] + c.left - e.scrollX).toInt(), (loc[1] + c.top - e.scrollY).toInt(), (loc[0] + c.right - e.scrollX).toInt() + 1, (loc[1] + c.bottom - e.scrollY).toInt())
    }

    // ------------------------------------------------------------------ what is on the screen

    private fun classes(v: View): List<String> = buildList {
        add(v.javaClass.name.substringAfterLast('.'))
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(classes(v.getChildAt(i)))
    }.distinct()

    private fun describe(root: View): String {
        val loc = IntArray(2).also(root::getLocationOnScreen)
        return "${root.javaClass.name.substringAfterLast('.')} at ${loc[0]},${loc[1]} ${root.width}x${root.height} [${classes(root).joinToString(",")}]"
    }

    /**
     * The shown windows of the app that hold a platform text handle whose class contains [kind]: `InsertionHandleView` (the drop under the
     * caret), `SelectionHandleView` (the two handles of a selection).
     */
    private fun handles(kind: String): List<String> = app.ui {
        WindowInspector.getGlobalWindowViews().filter { r -> r.isShown && r.width > 0 && classes(r).any { it.contains(kind) } }.map(::describe)
    }

    /** On the screen: a view of the app's windows (the floating toolbar is one) or an accessibility node of any window but the keyboard showing [text]. */
    private fun textOnScreen(text: String): Rect? {
        fun View.matches() = isShown && width > 0 && listOf((this as? TextView)?.text, contentDescription).any { it?.toString().equals(text, ignoreCase = true) }
        fun find(v: View): View? {
            if (v.matches()) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        app.ui {
            WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)?.let { v ->
                val loc = IntArray(2).also(v::getLocationOnScreen)
                Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
            }
        }?.let { return it }
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (listOf(n.text, n.contentDescription).any { it?.toString().equals(text, ignoreCase = true) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)?.let { return it }
            return null
        }
        return ua.windows.filter { it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }.firstNotNullOfOrNull { w -> w.root?.let(::walk) }
            ?.let { n -> Rect().also { n.getBoundsInScreen(it) } }
    }

    private fun awaitText(text: String, ms: Long = 3_000): Rect? {
        val until = SystemClock.uptimeMillis() + ms
        while (true) {
            textOnScreen(text)?.let { return it }
            if (SystemClock.uptimeMillis() > until) return null
            Thread.sleep(150)
        }
    }

    /** The screen as the user sees it (all windows), kept as `touch-[name]` in Downloads/native-editor-qa. */
    private fun screen(name: String): Bitmap {
        val b = ua.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, "touch-$name", b)
        return b.copy(Bitmap.Config.ARGB_8888, false) // the screenshot is a hardware bitmap: no getPixel
    }

    /** Accent pixels (close to the accent and at least half as colorful) in a 48×48 dp box hanging from the caret: where the drop would be. */
    private fun accentUnderCaret(e: TesseraEditText, shot: Bitmap): Int {
        val c = caretOnScreen(e) ?: return 0
        val accent = app.ui { e.editorStyle.accentColor }
        fun chroma(p: Int) = maxOf(Color.red(p), Color.green(p), Color.blue(p)) - minOf(Color.red(p), Color.green(p), Color.blue(p))
        val box = Rect(c.centerX() - dp(24), c.bottom + 1, c.centerX() + dp(24), c.bottom + dp(48))
        if (!box.intersect(0, 0, shot.width, shot.height)) return 0
        var n = 0
        for (y in box.top until box.bottom) for (x in box.left until box.right) {
            val p = shot.getPixel(x, y)
            val d = Math.abs(Color.red(p) - Color.red(accent)) + Math.abs(Color.green(p) - Color.green(accent)) + Math.abs(Color.blue(p) - Color.blue(accent))
            if (d < 60 && chroma(p) * 2 >= chroma(accent)) n++
        }
        return n
    }

    /** No drop now: no window with `Editor$InsertionHandleView`, no accent under the caret on the screen. Observed within a second of the touch (the platform hides its drop after ~4 s). */
    private fun assertNoDrop(e: TesseraEditText, what: String) {
        val windows = handles("InsertionHandleView")
        val px = accentUnderCaret(e, screen(what))
        log("$what: insertion handle windows $windows, accent px under the caret $px, caret ${caretOnScreen(e)}, selection ${sel(e)}")
        assertTrue("$what: a drop window $windows", windows.isEmpty())
        assertEquals("$what: accent pixels of a drop under the caret", 0, px)
    }

    private fun shell(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }

    /** The app's `LONG_PRESS` haptic feedbacks the vibrator service remembers (`dumpsys vibrator_manager`, recent vibrations; each line has its time). */
    private fun longPressHaptics(): Set<String> = shell("dumpsys vibrator_manager").lines().map { it.trim() }
        .filter { it.contains("| ${ctx.packageName} (uid=") && it.contains("performHapticFeedback(constant=${HapticFeedbackConstants.LONG_PRESS})") }
        .toSet()

    private fun requireKeyboard(e: TesseraEditText, what: String) {
        assertTrue(
            "$what: no on-screen keyboard (IME inset ${imeInset(e)} px). On an AVD with a hardware keyboard: " +
                "settings put secure show_ime_with_hard_keyboard 1 and settings put secure stylus_handwriting_enabled 0",
            imeInset(e) > 0,
        )
    }

    // ------------------------------------------------------------------ §6.2-1: no insertion handle

    /** The detector is not blind: with the platform cursor turned back on, a tap on the text shows the drop (the app before §2). */
    @Test
    fun controlTheDropIsSeenWhenThePlatformCursorIsOn() {
        val e = open("touch-control", three)
        app.ui { e.isCursorVisible = true }
        val (x, y) = pointAt(e, app.text(e).indexOf("charlie delta 2") + 2)
        t.tap(x, y)
        val windows = handles("InsertionHandleView")
        val px = accentUnderCaret(e, screen("control-drop"))
        log("control: insertion handle windows $windows, accent px $px")
        assertTrue("the control shows no drop window: the detector is blind", windows.isNotEmpty())
        assertTrue("the control shows no drop pixels: the detector is blind", px > 0)
    }

    @Test
    fun noInsertionHandleOnOpeningOnATapOnTheTextOrUnderItAndATapUnderTheCaretMovesIt() {
        setClip(PASTED) // the platform's "Paste" bubble on a tap at the caret shows only with something to paste
        val e = open("touch-no-drop", three)
        assertTrue("opening: a drop window ${handles("InsertionHandleView")}", handles("InsertionHandleView").isEmpty())
        assertTrue("the platform cursor is off (spec §2)", !app.ui { e.isCursorVisible })

        val text = app.text(e)
        val word = pointAt(e, text.indexOf("charlie delta 2") + 2)
        t.tap(word.first, word.second)
        assertNoDrop(e, "tap-on-text")
        val line2 = app.ui { e.layout.getLineForOffset(text.indexOf("charlie delta 2")) }
        assertEquals("the tap put the caret on its line", line2, app.ui { e.layout.getLineForOffset(e.selectionStart) })

        app.settle(4_500) // were there a drop, it would be gone by now: the next tap starts clean
        val caret = caretOnScreen(e) ?: error("no caret after the tap")
        t.tap(caret.centerX(), caret.bottom + dp(20))
        val line3 = app.ui { e.layout.getLineForOffset(text.indexOf("charlie delta 3")) }
        assertEquals("a tap 20 dp under the caret moves the caret to the line there", line3, app.ui { e.layout.getLineForOffset(e.selectionStart) })
        assertTrue("…and is no selection", app.ui { e.selectionStart == e.selectionEnd })
        assertNull("…and opens no menu: no \"$paste\"", textOnScreen(paste))
        assertNull("…no \"$selectAll\"", textOnScreen(selectAll))
        assertNoDrop(e, "tap-under-the-caret")

        app.settle(4_500)
        val (bx, by) = belowText(e)
        t.tap(bx, by)
        assertNoDrop(e, "tap-under-the-text")
        assertTrue("the platform cursor is still off", !app.ui { e.isCursorVisible })
    }

    // ------------------------------------------------------------------ §6.2-2: a long press under the text

    @Test
    fun aLongPressUnderTheTextPutsTheCaretAtTheEndWithTheKeyboardAndPasteThenSelectAllGivesHandlesAndCopy() {
        setClip(PASTED)
        val e = open("touch-long-below", three)
        val hapticsBefore = longPressHaptics()
        val (x, y) = belowText(e)
        t.longPress(x, y)
        t.awaitIme(e)
        val n = len(e)
        assertEquals("a long press under the text selects nothing and puts the caret at the end", n to n, sel(e))
        requireKeyboard(e, "after the long press")
        val pasteItem = awaitText(paste)
        screen("long-below-menu")
        assertNotNull("the menu has \"$paste\"", pasteItem)
        assertNotNull("the menu has \"$selectAll\" (the note is not empty)", textOnScreen(selectAll))
        assertTrue("no word selection handles", handles("SelectionHandleView").isEmpty())
        val haptics = longPressHaptics() - hapticsBefore
        log("long press under the text: new LONG_PRESS haptics $haptics")
        assertTrue("the long press gives the LONG_PRESS vibration (dumpsys vibrator_manager)", haptics.isNotEmpty())

        t.tap(pasteItem!!.centerX(), pasteItem.centerY())
        app.settle(800)
        val pasted = app.text(e)
        assertTrue("\"$paste\" puts the clip at the end: \"${pasted.takeLast(40)}\"", pasted.endsWith(PASTED))
        assertNull("the menu is gone after \"$paste\"", textOnScreen(selectAll))

        // 2b: again, then "Select all"
        val (x2, y2) = belowText(e)
        t.longPress(x2, y2)
        val all = awaitText(selectAll) ?: error("no \"$selectAll\" on the second long press")
        t.tap(all.centerX(), all.centerY())
        val copyItem = awaitText(copy)
        val n2 = len(e)
        val handles = handles("SelectionHandleView")
        screen("long-below-select-all")
        assertEquals("\"$selectAll\" selects the whole text", 0 to n2, sel(e))
        assertEquals("both selection handles are shown: $handles", 2, handles.size)
        assertNotNull("the selection toolbar has \"$copy\"", copyItem)
    }

    // ------------------------------------------------------------------ §6.2-3: right of a short line

    @Test
    fun aLongPressRightOfAShortLinePutsTheCaretAtItsEndWithTheMenu() {
        setClip(PASTED)
        val e = open("touch-long-right", "Alpha bravo charlie delta 1\nShort\nAlpha bravo charlie delta 3")
        val end = app.text(e).indexOf("Short") + "Short".length
        val (x, y) = app.ui {
            val loc = IntArray(2).also(e::getLocationOnScreen)
            val l = e.layout
            val line = l.getLineForOffset(end)
            (loc[0] + e.totalPaddingLeft + l.getLineRight(line).toInt() + dp(60)) to (loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2)
        }
        check(x < app.ui { IntArray(2).also(e::getLocationOnScreen)[0] + e.width - e.totalPaddingRight }) { "the point right of the line is off the text area" }
        t.longPress(x, y)
        val menu = awaitText(paste) ?: textOnScreen(selectAll)
        screen("long-right-of-short-line")
        assertEquals("the caret at the end of the short line, nothing selected", end to end, sel(e))
        assertNotNull("the menu is shown", menu)
        assertTrue("no word selection handles", handles("SelectionHandleView").isEmpty())
    }

    // ------------------------------------------------------------------ §6.2-4: on a word

    @Test
    fun aLongPressOnAWordSelectsIt() {
        val e = open("touch-long-word", three)
        val text = app.text(e)
        for ((what, word, at) in listOf(
            Triple("a middle line", "bravo", text.indexOf("bravo charlie delta 2")),
            Triple("the last line", "delta", text.indexOf("delta 3")),
        )) {
            val (x, y) = pointAt(e, at + 2)
            t.longPress(x, y)
            val copyItem = awaitText(copy)
            val s = sel(e)
            val selected = app.ui { e.text!!.substring(s.first, s.second) }
            val handles = handles("SelectionHandleView")
            screen("long-word-${word}")
            assertEquals("$what: the long press selects the word", word, selected)
            assertEquals("$what: both selection handles: $handles", 2, handles.size)
            assertNotNull("$what: the selection toolbar has \"$copy\"", copyItem)
            app.ui { e.setSelection(e.text!!.length) } // the selection mode ends with the selection
            app.settle(800)
        }
    }

    // ------------------------------------------------------------------ §6.2-5: a tap under the text

    @Test
    fun aTapUnderTheTextPutsTheCaretAtTheEndOfTheNote() {
        val e = open("touch-tap-below", three)
        app.ui { e.requestFocus(); e.setSelection(0) }
        app.settle(600)
        val (x, y) = belowText(e)
        t.tap(x, y)
        val n = len(e)
        assertEquals("the caret at the end of the note", n to n, sel(e))
        assertTrue("the editor has the focus", app.ui { e.isFocused })
    }

    @Test
    fun aTapUnderANoteThatEndsWithATablePutsTheCaretNextToTheTable() {
        val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4")))
        val e = open("touch-tap-below-table", note("Before the table", grid))
        t.awaitOverlays(e, 1)
        val expected = app.ui {
            val ps = e.tableZones().last().ps
            val pe = paragraphEnd(e.text!!, ps)
            if (pe < e.text!!.length) pe + 1 else ps - 1 // caretNextToTable(ps, above = false)
        }
        val (x, y) = belowText(e, underAWord = false)
        t.tap(x, y)
        screen("tap-below-table")
        assertEquals("the caret next to the table, as a tap in its margin below", expected to expected, sel(e))
        assertNull("no cell took the focus", t.focused())
    }

    // ------------------------------------------------------------------ §6.2-6: a viewer

    /**
     * A viewer of a shared note (the app needs a signed-in viewer, which the device has not got: the body is the same `NoteBody(editable = false)`
     * the screen draws, in the rig of `NativeTableReadOnlyTest`): a long press under the text selects nothing and shows no menu.
     */
    @Test
    fun aViewersLongPressUnderTheTextDoesNothing() {
        setClip(PASTED)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { a ->
                WindowCompat.setDecorFitsSystemWindows(a.window, false)
                a.setContent {
                    TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.WARM) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                            NoteBody(
                                markdown = three, onMarkdownChange = {}, onActionsReady = {}, onFormatStateChanged = {}, editable = false,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { app.editorOrNull() != null }
            app.settle(800)
            val e = app.editorOrNull()!!
            val before = sel(e)
            val (x, y) = belowText(e)
            t.longPress(x, y)
            Thread.sleep(1_000) // a menu or a selection toolbar would be up by now
            val after = sel(e)
            screen("viewer-long-below")
            assertTrue("a viewer's long press under the text selects nothing: $before → $after", after.first == after.second)
            assertNull("no \"$paste\"", textOnScreen(paste))
            assertNull("no \"$selectAll\"", textOnScreen(selectAll))
            assertNull("no \"$copy\"", textOnScreen(copy))
            assertTrue("no selection handles", handles("SelectionHandleView").isEmpty())

            // the rest is as before (§3): a long press on a word still selects it — and it shows the rig delivers long presses at all
            val (wx, wy) = pointAt(e, three.indexOf("charlie delta 2") + 3)
            t.longPress(wx, wy)
            val copyItem = awaitText(copy)
            val s = sel(e)
            val word = app.ui { e.text!!.substring(s.first, s.second) }
            screen("viewer-long-word")
            assertEquals("a viewer's long press on a word selects it", "charlie", word)
            assertNotNull("…with \"$copy\"", copyItem)
        } finally {
            scenario.close()
        }
    }

    // ------------------------------------------------------------------ cold: the keyboard comes up under the menu

    /**
     * No focus, no keyboard; the note ends at about 70 % of the screen. A long press under it puts the caret at the end and shows the menu; the
     * keyboard comes up over the end of the note, the note scrolls the caret above the bar — and the menu moves with the caret
     * (`invalidateContentRect` on the scroll): it outlives the keyboard and stands right above (or below) the caret, clear of the bar.
     */
    @Test
    fun coldALongPressUnderTheTextKeepsTheMenuAtTheCaretWhenTheKeyboardScrollsTheNote() {
        setClip(PASTED)
        val m = ctx.resources.displayMetrics
        val lines = ((0.70f * m.heightPixels / m.density - 140f) / 22f).toInt().coerceAtLeast(8) // ~22 dp a line, ~140 dp of bars above the text
        val e = open("touch-cold", (1..lines).joinToString("\n") { "Line $it" })
        app.ui {
            e.clearFocus()
            ctx.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(e.windowToken, 0)
        }
        t.awaitIme(e, shown = false)
        val bottomShare = textBottom(e).toFloat() / m.heightPixels
        log("cold: $lines lines, the text ends at ${"%.2f".format(bottomShare)} of the screen")
        check(bottomShare in 0.5f..0.85f) { "the note must end at 50–85 % of the screen to be covered by the keyboard: ${"%.2f".format(bottomShare)}" }
        val scrollBefore = app.ui { e.scrollY }

        val (x, y) = belowText(e)
        val hapticsBefore = longPressHaptics()
        t.longPress(x, y)
        log("cold: long press at ($x, $y), editor ${app.ui { Rect().also(e::getGlobalVisibleRect) }}; then focus ${app.ui { e.isFocused }}, selection ${sel(e)}, " +
            "IME inset ${imeInset(e)}, new LONG_PRESS haptics ${(longPressHaptics() - hapticsBefore).size}")
        screen("cold-after-long-press")
        t.awaitIme(e)
        app.settle(800)
        requireKeyboard(e, "cold, after the long press")
        val n = len(e)
        val scrolled = app.ui { e.scrollY } - scrollBefore
        val caret = caretOnScreen(e) ?: error("no caret after the long press")
        val cover = coverTop(e)
        val item = awaitText(paste) ?: textOnScreen(selectAll)
        screen("cold-menu-after-keyboard")
        log("cold: scrolled $scrolled px, caret $caret, bar/keyboard top $cover, menu item $item")
        assertEquals("the caret at the end, nothing selected", n to n, sel(e))
        check(scrolled > dp(120)) { "the keyboard scrolled the note by $scrolled px only: the menu's move is not exercised (it must be more than its 64 dp tolerance)" }
        assertTrue("the caret is above the bar/keyboard: caret $caret, cover $cover", caret.bottom <= cover)
        assertNotNull("the menu outlived the keyboard coming up", item)
        val above = item!!.bottom <= caret.top + dp(2) && item.bottom >= caret.top - dp(64)
        val below = item.top >= caret.bottom - dp(2) && item.top <= caret.bottom + dp(64)
        assertTrue("the menu stands at the caret after the scroll: item $item, caret $caret", above || below)
        assertTrue("the menu is clear of the bar/keyboard: item $item, cover $cover", item.bottom <= cover)
    }

    // ------------------------------------------------------------------ the menu gives way to the screen's own controls

    /** On the screen: the first Compose node of [nodes] (Compose bounds are in the window). */
    private fun boundsOf(nodes: List<SemanticsNode>): Rect? = nodes.firstOrNull()?.boundsInWindow?.let { b ->
        val o = app.ui { IntArray(2).also(app.activity().window.decorView::getLocationOnScreen) }
        Rect(b.left.toInt() + o[0], b.top.toInt() + o[1], b.right.toInt() + o[0], b.bottom.toInt() + o[1])
    }

    /** A real tap in the middle of the Compose node with [desc] (a button of the format bar). */
    private fun tapDesc(desc: String) {
        val r = boundsOf(compose.onAllNodesWithContentDescription(desc).fetchSemanticsNodes()) ?: error("no \"$desc\" on the screen")
        t.tap(r.centerX(), r.centerY())
    }

    /** A long press under the text with something to paste: the caret at the end, the keyboard, the menu "Paste / Select all" — where the cases below start. */
    private fun longPressMenu(id: String): TesseraEditText {
        setClip(PASTED)
        val e = open(id, three)
        val (x, y) = belowText(e)
        t.longPress(x, y)
        t.awaitIme(e)
        requireKeyboard(e, "after the long press")
        assertNotNull("the menu is up before the screen's control is touched", awaitText(paste))
        return e
    }

    /** The menu is gone: no "Paste", no "Select all", no floating toolbar window (an ActionMode of the platform) in the app. */
    private fun assertMenuGone(what: String) {
        val until = SystemClock.uptimeMillis() + 2_000
        while ((textOnScreen(paste) != null || handles("FloatingToolbar").isNotEmpty()) && SystemClock.uptimeMillis() < until) Thread.sleep(150)
        screen(what)
        assertNull("$what: \"$paste\" is still on the screen", textOnScreen(paste))
        assertNull("$what: \"$selectAll\" is still on the screen", textOnScreen(selectAll))
        assertTrue("$what: a floating toolbar window is still there: ${handles("FloatingToolbar")}", handles("FloatingToolbar").isEmpty())
    }

    /** The ⋯ sheet opens over the note: the menu of the long press must not stay on top of it. */
    @Test
    fun theMenuGoesWhenTheParagraphStyleSheetOpens() {
        longPressMenu("touch-menu-sheet")
        tapDesc(ctx.getString(R.string.toolbar_more))
        val title = ctx.getString(R.string.editor_style_menu_title)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        app.settle(600)
        assertMenuGone("menu-under-sheet")
        assertTrue("the sheet \"$title\" is open", compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
    }

    /** The tag picker opens from "+" under the title: the menu goes. */
    @Test
    fun theMenuGoesWhenTheTagPickerOpens() {
        longPressMenu("touch-menu-tag")
        tapDesc(ctx.getString(R.string.editor_add_tag)) // the "+" itself: EditorTagToggle is the whole row of tags
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(UiTestTags.AddTagConfirm).fetchSemanticsNodes().isNotEmpty() }
        app.settle(600)
        assertMenuGone("menu-under-tag-picker")
    }

    /** Any button of the bar does its job and takes the menu away (Bold here). */
    @Test
    fun theMenuGoesWithAButtonOfTheBar() {
        longPressMenu("touch-menu-bold")
        tapDesc(ctx.getString(R.string.toolbar_bold))
        app.settle(600)
        assertMenuGone("menu-after-bold")
    }

    private companion object {
        const val TAG = "EditorTouchOutside"
        const val PASTED = "PASTEDXYZ"
    }
}
