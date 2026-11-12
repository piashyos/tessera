package app.tessera.ui.editor.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Log
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.filters.SdkSuppress
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Shots of the ⋯ sheet for the visual check (plan §7.3–7.4) and its fit under load, on the real screen with the real keyboard: the reference
 * `SearchOptionPopup` (search → "Period"), the sheet in light and dark and in every palette, the "Paste / Select all" menu under the text; and
 * the card's fit — above the bar, at least 144 dp, its last row reachable by a drag — in whatever font scale and orientation the device is set to
 * (the runner sets them: `font_scale 1.3`, landscape). Shots go to Downloads/native-editor-qa as `qa-*` / `ref-*`.
 */
@SdkSuppress(minSdkVersion = 29)
@HiltAndroidTest
class FormatSheetShotsTest : NativeAppTest() {
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(12, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private val ctx get() = app.instrumentation.targetContext
    private val t by lazy { Tables(app) }
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun log(msg: String) = Log.i(TAG, msg)

    private val more get() = ctx.getString(R.string.toolbar_more)
    private val undo get() = ctx.getString(R.string.toolbar_undo)
    private val title get() = ctx.getString(R.string.editor_style_menu_title)
    private val quote get() = ctx.getString(R.string.toolbar_quote)

    private val body = (1..24).joinToString("\n") { "Line $it: a paragraph of the note, long enough to wrap onto a second line on a phone." }

    private fun theme(mode: ThemeMode, palette: ThemePalette = ThemePalette.WARM) = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemePalette(palette)
        prefs.setThemeMode(mode)
    }

    private fun open(id: String, md: String = body): TesseraEditText {
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

    private fun focusEnd(e: TesseraEditText) {
        app.ui {
            e.requestFocus()
            e.setSelection(e.text!!.length)
            e.context.getSystemService(InputMethodManager::class.java).showSoftInput(e, 0)
        }
        t.awaitIme(e)
        app.settle(800)
        assertTrue("no on-screen keyboard (see EditorTouchOutsideTest)", imeInset(e) > 0)
    }

    private fun imeInset(e: TesseraEditText): Int = app.ui { e.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0 }

    private fun origin(): IntArray = app.ui { IntArray(2).also(app.activity().window.decorView::getLocationOnScreen) }
    private fun bounds(nodes: List<SemanticsNode>): Rect? = nodes.firstOrNull()?.boundsInWindow?.let {
        val o = origin()
        Rect(it.left.toInt() + o[0], it.top.toInt() + o[1], it.right.toInt() + o[0], it.bottom.toInt() + o[1])
    }
    private fun byDesc(d: String) = bounds(compose.onAllNodesWithContentDescription(d).fetchSemanticsNodes())
    private fun byText(s: String) = bounds(compose.onAllNodesWithText(s).fetchSemanticsNodes())
    private fun card() = bounds(compose.onAllNodesWithTag(UiTestTags.PopupCard).fetchSemanticsNodes())

    private fun openSheet() {
        val b = byDesc(more) ?: error("no \"$more\" on the bar")
        t.tap(b.centerX(), b.centerY())
        compose.waitUntil(5_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        app.settle(900) // the slide and the fade
    }

    private fun shot(name: String) {
        app.settle(600)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, name, b)
        log("shot $name ${b.width}x${b.height}")
    }

    private fun modeName(m: ThemeMode) = m.name.lowercase()

    // ------------------------------------------------------------------ the reference and the sheet

    /** The reference modal: the search screen's "Period" filter (`SearchOptionPopup` on `TesseraPopup`). */
    @Test
    fun searchOptionPopupReference() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            db.clearAllTables()
            app.seed("ref-note", "# A note\nfor the list to have a card", System.currentTimeMillis())
            app.launch()
            compose.waitUntil(60_000) { compose.onAllNodesWithTag(UiTestTags.noteCard("ref-note")).fetchSemanticsNodes().isNotEmpty() }
            app.settle(800)
            val search = byText(ctx.getString(R.string.search_hint)) ?: error("no search capsule")
            t.tap(search.centerX(), search.centerY())
            val period = ctx.getString(R.string.search_filter_period)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(period).fetchSemanticsNodes().isNotEmpty() }
            app.settle(800)
            val row = byText(period) ?: error("no \"$period\" filter")
            t.tap(row.centerX(), row.centerY())
            compose.waitUntil(5_000) { compose.onAllNodesWithTag(UiTestTags.PopupCard).fetchSemanticsNodes().isNotEmpty() }
            app.settle(900)
            log("reference ${modeName(mode)}: card ${card()}")
            shot("ref-search-option-${modeName(mode)}")
            app.close()
        }
    }

    /** The ⋯ sheet over a note with the keyboard up, light and dark. */
    @Test
    fun sheetWithTheKeyboard() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            db.clearAllTables()
            val e = open("qa-sheet-${modeName(mode)}")
            focusEnd(e)
            openSheet()
            log("sheet ${modeName(mode)}: card ${card()}, undo ${byDesc(undo)}")
            shot("qa-sheet-${modeName(mode)}")
            app.close()
        }
    }

    /** The floating "Paste / Select all" after a long press under the text (§3), light. */
    @Test
    fun pasteMenuUnderTheText() {
        theme(ThemeMode.LIGHT)
        app.ui { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("qa", "pasted text")) }
        val e = open("qa-paste", "Alpha bravo charlie delta 1\nAlpha bravo charlie delta 2\nAlpha bravo charlie delta 3")
        val (x, y) = app.ui {
            val loc = IntArray(2).also(e::getLocationOnScreen)
            (loc[0] + e.totalPaddingLeft + dp(24)) to (loc[1] + e.totalPaddingTop - e.scrollY + e.layout.height + dp(48))
        }
        t.longPress(x, y)
        t.awaitIme(e)
        compose.waitForIdle()
        app.settle(1_000)
        shot("qa-paste-menu")
    }

    /** Every palette in light and dark: the sheet and the bar under it. */
    @Test
    fun everyPaletteLightAndDark() {
        for (palette in ThemePalette.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode, palette)
            db.clearAllTables()
            val e = open("qa-palette-${palette.name.lowercase()}-${modeName(mode)}")
            focusEnd(e)
            openSheet()
            shot("qa-palette-${palette.name.lowercase()}-${modeName(mode)}")
            app.close()
        }
        theme(ThemeMode.LIGHT)
    }

    // ------------------------------------------------------------------ the fit under load

    /**
     * In the device's font scale and orientation: the card is above the bar's pill (its buttons are 36 dp in a 48 dp row: the pill's top is 6 dp
     * above them), it is at least 144 dp tall when its rows need that, and its last row ("Quote") is reached by a real drag inside the card.
     */
    @Test
    fun theCardFitsAboveTheBarAndItsLastRowIsReachable() {
        theme(ThemeMode.LIGHT)
        val cfg = ctx.resources.configuration
        val tag = "fs${cfg.fontScale}-${if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) "land" else "port"}"
        val e = open("qa-fit")
        focusEnd(e)
        openSheet()
        val c = card() ?: error("no card")
        val pillTop = (byDesc(undo) ?: error("no \"$undo\"")).top - dp(6)
        shot("qa-fit-$tag-open")
        log("fit $tag: card $c (${c.height() / ctx.resources.displayMetrics.density} dp), pill top $pillTop, IME inset ${imeInset(e)}")
        assertTrue("$tag: the card ($c) is above the bar's pill (top $pillTop)", c.bottom <= pillTop)
        assertTrue("$tag: the card is at least 144 dp tall: ${c.height()} px", c.height() >= dp(144) - 2)
        var last = byText(quote)
        var drags = 0
        while ((last == null || last.height() <= 0 || last.bottom > c.bottom) && drags < 6) {
            t.drag(c.centerX(), c.bottom - c.height() / 5, 0, -c.height() * 3 / 5)
            app.settle(500)
            drags++
            last = byText(quote)
        }
        shot("qa-fit-$tag-scrolled")
        log("fit $tag: after $drags drags \"$quote\" at $last")
        assertNotNull("$tag: \"$quote\" is reachable", last)
        assertTrue("$tag: \"$quote\" ($last) is inside the card ($c) after $drags drags", last!!.height() > 0 && last.top >= c.top && last.bottom <= c.bottom)
        assertNotNull("$tag: the sheet is still open after the drags", card())
    }

    private companion object { const val TAG = "FormatSheetShots" }
}
