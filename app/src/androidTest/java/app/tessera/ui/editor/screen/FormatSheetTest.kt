package app.tessera.ui.editor.screen

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.filters.SdkSuppress
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * The ⋯ sheet (spec §4; tests §6.2-7, §6.2-8) on the real editor screen with the real keyboard up, touched with real taps injected into the
 * window (`UiAutomation`): the old menu was a `Popup` window, and a tap on ⋯ was "outside" it — only a real tap shows that.
 * - ⋯ opens the sheet (the card "Paragraph style"), ⋯ again closes it; a tap on the blurred backdrop closes it, the keyboard and the caret stay;
 * - Bold on the bar with the sheet open makes the selection bold and closes the sheet;
 * - "Heading" turns the caret's paragraph into a heading, the sheet closes, the keyboard stays, the caret too;
 * - geometry: the card is above the bar's pill, the bar above the keyboard; the sheet is no window of its own (nothing of the app over the IME);
 * - Back with the sheet open hides the keyboard and closes the sheet, the note stays; the next Back leaves the note.
 *
 * The device needs a real on-screen keyboard: on an AVD with a hardware keyboard set `show_ime_with_hard_keyboard 1` and
 * `stylus_handwriting_enabled 0` (see `EditorTouchOutsideTest`).
 */
@SdkSuppress(minSdkVersion = 29) // WindowInspector
@HiltAndroidTest
class FormatSheetTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(4, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private val ctx get() = app.instrumentation.targetContext
    private val t by lazy { Tables(app) }
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun log(msg: String) = Log.i(TAG, msg)

    private val more get() = ctx.getString(R.string.toolbar_more)
    private val bold get() = ctx.getString(R.string.toolbar_bold)
    private val undo get() = ctx.getString(R.string.toolbar_undo)
    private val title get() = ctx.getString(R.string.editor_style_menu_title)
    private val heading get() = ctx.getString(R.string.editor_style_heading)

    private val three = "Alpha bravo charlie delta 1\nAlpha bravo charlie delta 2\nAlpha bravo charlie delta 3"

    // ------------------------------------------------------------------ the note, the keyboard

    private fun open(id: String, md: String = three): TesseraEditText {
        runBlocking {
            prefs.setDynamicColor(false)
            prefs.setThemeMode(ThemeMode.LIGHT)
        }
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

    /** The caret at [offset] (or the selection [offset]..[end]), the keyboard up and settled. */
    private fun focus(e: TesseraEditText, offset: Int, end: Int = offset) {
        app.ui {
            e.requestFocus()
            e.setSelection(offset, end)
            e.context.getSystemService(InputMethodManager::class.java).showSoftInput(e, 0)
        }
        t.awaitIme(e)
        app.settle(800)
        requireKeyboard(e)
    }

    private fun imeInset(e: TesseraEditText): Int = app.ui { e.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0 }

    private fun requireKeyboard(e: TesseraEditText) = assertTrue(
        "no on-screen keyboard (IME inset ${imeInset(e)} px). On an AVD with a hardware keyboard: settings put secure show_ime_with_hard_keyboard 1 " +
            "and settings put secure stylus_handwriting_enabled 0",
        imeInset(e) > 0,
    )

    /** On the screen: the top of the keyboard as the window lays itself out (its IME insets). */
    private fun imeTop(e: TesseraEditText): Int = app.ui {
        val root = e.rootView
        IntArray(2).also(root::getLocationOnScreen)[1] + root.height - imeInset(e)
    }

    private fun sel(e: TesseraEditText) = app.ui { e.selectionStart to e.selectionEnd }

    // ------------------------------------------------------------------ the screen's nodes

    /** The window's place on the screen: Compose bounds are in the window. */
    private fun windowOrigin(): IntArray = app.ui { IntArray(2).also(app.activity().window.decorView::getLocationOnScreen) }

    private fun Rect.onScreen(): Rect = Rect(this).apply { windowOrigin().let { offset(it[0], it[1]) } }

    private fun boundsOf(nodes: List<androidx.compose.ui.semantics.SemanticsNode>): Rect? = nodes.firstOrNull()?.boundsInWindow?.let {
        Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()).onScreen()
    }

    private fun byDesc(desc: String): Rect? = boundsOf(compose.onAllNodesWithContentDescription(desc).fetchSemanticsNodes())
    private fun byText(text: String): Rect? = boundsOf(compose.onAllNodesWithText(text).fetchSemanticsNodes())
    private fun card(): Rect? = boundsOf(compose.onAllNodesWithTag(UiTestTags.PopupCard).fetchSemanticsNodes())
    private fun sheetOpen() = compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun awaitSheet(open: Boolean, what: String) {
        runCatching { compose.waitUntil(5_000) { sheetOpen() == open } }
        app.settle(600) // the card's slide and the backdrop's fade
        assertEquals("$what: the sheet is ${if (open) "open" else "closed"}", open, sheetOpen())
    }

    /** A real tap in the middle of [r] (screen coordinates). */
    private fun tap(r: Rect) = t.tap(r.centerX(), r.centerY())

    private fun tapMore() = tap(byDesc(more) ?: error("no \"$more\" on the bar"))

    private fun screen(name: String) {
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, "sheet-$name", b)
    }

    // ------------------------------------------------------------------ §6.2-7

    @Test
    fun moreOpensTheSheetAndMoreAgainClosesIt() {
        val e = open("sheet-toggle")
        focus(e, app.text(e).length)
        tapMore()
        awaitSheet(true, "after ⋯")
        assertNotNull("the card is shown", card())
        screen("open")
        tapMore()
        awaitSheet(false, "after ⋯ again")
        requireKeyboard(e)
        tapMore()
        awaitSheet(true, "after ⋯ the third time (the toggle is not stuck)")
    }

    @Test
    fun aTapOnTheBackdropClosesTheSheetAndKeepsTheKeyboardAndTheCaret() {
        val e = open("sheet-backdrop")
        val at = app.text(e).indexOf("charlie delta 2")
        focus(e, at)
        tapMore()
        awaitSheet(true, "after ⋯")
        val c = card() ?: error("no card")
        val statusBottom = app.ui { e.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0 } + windowOrigin()[1]
        // a small phone with the keyboard up gives the card all the room up to the status bar (§4.3): the backdrop is then its 12 dp side margins
        val above = c.top - statusBottom > dp(48)
        check(above || c.left >= dp(8)) { "no backdrop to tap: card $c, status bar bottom $statusBottom" }
        val (x, y) = if (above) c.centerX() to (statusBottom + c.top) / 2 else c.left / 2 to c.centerY()
        log("backdrop: card $c, status bar bottom $statusBottom, tap ($x, $y) ${if (above) "above the card" else "in its side margin"}")
        t.tap(x, y)
        awaitSheet(false, "after a tap on the backdrop")
        requireKeyboard(e)
        assertEquals("the caret is where it was", at to at, sel(e))
        assertTrue("the editor keeps the focus", app.ui { e.isFocused })
    }

    @Test
    fun boldOnTheBarWithTheSheetOpenMakesTheSelectionBoldAndClosesTheSheet() {
        val e = open("sheet-bold")
        val start = app.text(e).indexOf("bravo")
        focus(e, start, start + "bravo".length)
        tapMore()
        awaitSheet(true, "after ⋯")
        tap(byDesc(bold) ?: error("no \"$bold\" on the bar"))
        awaitSheet(false, "after Bold")
        app.settle(800)
        val md = app.md(e)
        log("bold: $md")
        assertTrue("the selection is bold: \"${md.lines().first()}\"", md.lines().first().contains("**bravo**"))
    }

    @Test
    fun headingFromTheSheetMakesTheCaretParagraphAHeadingAndKeepsTheKeyboardAndTheCaret() {
        val e = open("sheet-heading")
        val at = app.text(e).indexOf("charlie delta 2") + 3
        focus(e, at)
        tapMore()
        awaitSheet(true, "after ⋯")
        tap(byText(heading) ?: error("no \"$heading\" row"))
        awaitSheet(false, "after \"$heading\"")
        app.settle(800)
        val kinds = app.kinds(e)
        log("heading: kinds $kinds, selection ${sel(e)}")
        assertEquals("the caret's paragraph is a heading, the others are not", listOf(ParagraphKind.Body, ParagraphKind.Heading, ParagraphKind.Body), kinds)
        requireKeyboard(e)
        assertEquals("the caret is where it was", at to at, sel(e))
        screen("after-heading")
    }

    // ------------------------------------------------------------------ §6.2-8

    /**
     * The card is above the bar's pill, the pill above the keyboard. The pill is the bar's 48 dp row: its buttons are 36 dp, centred, so its top is
     * 6 dp above them (EditorFormatBar). The sheet is drawn in the activity's own window: the app has no other window (no Popup over the IME).
     */
    @Test
    fun theCardIsAboveTheBarAndTheBarAboveTheKeyboardAndTheSheetIsNoWindow() {
        val e = open("sheet-geometry")
        focus(e, app.text(e).length)
        tapMore()
        awaitSheet(true, "after ⋯")
        val c = card() ?: error("no card")
        val button = byDesc(undo) ?: error("no \"$undo\" on the bar")
        val pillTop = button.top - dp(6)
        val pillBottom = button.bottom + dp(6)
        val ime = imeTop(e)
        val windows = app.ui { WindowInspector.getGlobalWindowViews().map { "${it.javaClass.name.substringAfterLast('.')} ${it.width}x${it.height}" } }
        screen("geometry")
        log("geometry: card $c, pill $pillTop..$pillBottom, keyboard top $ime, app windows $windows")
        assertTrue("the card ($c) is above the bar's pill (top $pillTop)", c.bottom <= pillTop)
        assertTrue("the bar (bottom $pillBottom) is above the keyboard (top $ime)", pillBottom <= ime)
        assertEquals("the app has one window, the activity's: no Popup over the keyboard ($windows)", 1, windows.size)
    }

    /** Spec §4.5: Back with the sheet open and the keyboard up hides the keyboard (the system) and so closes the sheet; the note stays. */
    @Test
    fun backWithTheSheetOpenKeepsTheNoteAndTheNextBackLeavesIt() {
        val e = open("sheet-back")
        focus(e, app.text(e).length)
        tapMore()
        awaitSheet(true, "after ⋯")
        back()
        awaitSheet(false, "after Back")
        assertNotNull("the note is still open after Back", app.editorOrNull())
        assertTrue("the editor screen is there", compose.onAllNodesWithTag(UiTestTags.EditorRoot).fetchSemanticsNodes().isNotEmpty())
        assertEquals("Back hid the keyboard", 0, imeInset(app.editorOrNull()!!))
        screen("after-back")
        back()
        runCatching { compose.waitUntil(5_000) { app.editorOrNull() == null } }
        assertTrue("the next Back leaves the note, as without the sheet", app.editorOrNull() == null)
    }

    /** The system Back as a key from the shell (what the gesture sends to an app that has not opted into predictive back the same way). */
    private fun back() {
        ParcelFileDescriptor.AutoCloseInputStream(app.instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
        app.settle(900)
    }

    private companion object { const val TAG = "FormatSheet" }
}
