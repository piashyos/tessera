package app.tessera.ui.components

import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.notes.components.AppearancePopup
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * Swipe-down dismissal of [TesseraPopup] as the user meets it (spec 2026-11-12-editor-header-and-popup-gestures §3, §4.2):
 * - let go past the threshold, the card only goes down — it never jumps back up before leaving;
 * - a short confident flick closes it, a short slow pull springs it back to where it was;
 * - `onDismiss` comes while the card leaves (≤ 350 ms after the release), not after the tail of the spring;
 * - the same from the footer row (the `draggable` path) as from the content (the nested-scroll path);
 * - the inertia of the scrolled content never leaves the card hanging half way;
 * - reopening while the card leaves (a double tap on the format bar's ⋯) is not dismissed by the old swipe;
 * - with the keyboard up: closed by the backdrop or Back, the card keeps its height while the keyboard leaves; swiped away, the caret and
 *   the keyboard stay until the card is gone (spec §3.4 as of Chunk 1.2: the focus is cleared after the card left).
 *
 * Hosts behave like production: `onDismiss` turns `visible` to false — the jump back lived on that path.
 * Every card except the first test's is tall (≥ 300 dp), so the 120 dp threshold decides, not half of a short card.
 */
class TesseraPopupSwipeTest {
    @get:Rule(order = 0)
    val timeout: Timeout = Timeout.builder().withTimeout(3, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var visible by mutableStateOf(true)
    private var dismissCalls = 0
    private var footerClicks = 0
    private var imePx = 0
    private var navPx = 0

    @Test
    fun swipeDownDismissesPopup() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(
                    visible = true,
                    onDismiss = { dismissed = true },
                    hazeState = remember { HazeState() },
                ) {
                    Text("Content")
                }
            }
        }

        // Swipe down a distance well beyond the ~120dp dismiss threshold. The card
        // rendered here is short, so the default swipeDown() would not travel far enough.
        composeRule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput {
            swipeDown(startY = top, endY = top + 600f)
        }
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }

    // ------------------------------------------------------------------ hosts

    /**
     * A ~360 dp card; [footer]: a Cancel button pinned under the content, as in `AppearancePopup`; [rows]: tall scrolling content;
     * [field]: a text field over content taller than the card (the shape of `AppearancePopup`: name field, footer, more than fits).
     */
    private fun host(footer: Boolean = false, rows: Int = 0, frameByFrame: Boolean = true, field: Boolean = false) {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                TrackInsets()
                TesseraPopup(
                    visible = visible,
                    onDismiss = { dismissCalls++; visible = false },
                    hazeState = remember { HazeState() },
                    footer = if (footer) {
                        {
                            Button(onClick = { footerClicks++ }, modifier = Modifier.weight(1f).testTag(FOOTER)) { Text("Cancel") }
                        }
                    } else {
                        null
                    },
                ) {
                    Text("Content")
                    if (field) {
                        var name by remember { mutableStateOf("Work") }
                        BasicTextField(name, { name = it }, Modifier.fillMaxWidth())
                    }
                    if (rows == 0) Box(Modifier.height(if (footer && !field) 220.dp else 300.dp))
                    repeat(rows) { Text("Row $it", Modifier.height(48.dp).testTag("row-$it")) }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000) // the enter animation
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = !frameByFrame
    }

    /** Unmerged: the card is clickable, so its content (the rows) merges into the card's own node. */
    private fun bounds(tag: String): Rect? =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun card(): Rect? = bounds(UiTestTags.PopupCard)

    private fun dpPx(dp: Int): Float = dp * composeRule.activity.resources.displayMetrics.density

    /** A straight swipe down by [distanceDp] in [durationMs], starting [fromTopPx] below the top of the node [tag] (its centre when null). */
    private fun swipeDown(distanceDp: Int, durationMs: Long, tag: String = UiTestTags.PopupCard, fromTopPx: Float? = 40f) {
        composeRule.onNodeWithTag(tag).performTouchInput {
            val start = Offset(centerX, fromTopPx?.let { top + it } ?: centerY)
            swipe(start, start + Offset(0f, distanceDp.dp.toPx()), durationMs)
        }
    }

    /** Card bounds, one per frame from the release, until the card is gone (or [maxFrames]); [eachFrame] runs after every frame. */
    private fun framesUntilGone(maxFrames: Int = 150, eachFrame: () -> Unit = {}): List<Rect> {
        val frames = mutableListOf<Rect>()
        repeat(maxFrames) {
            frames += card() ?: return frames
            composeRule.mainClock.advanceTimeByFrame()
            eachFrame()
        }
        return frames
    }

    /** Test-clock time from the release (the swipe has just returned) until onDismiss. */
    private fun msToDismiss(): Long {
        val releasedAt = composeRule.mainClock.currentTime
        var frames = 0
        while (dismissCalls == 0 && frames++ < 150) composeRule.mainClock.advanceTimeByFrame()
        assertEquals("onDismiss calls", 1, dismissCalls)
        return composeRule.mainClock.currentTime - releasedAt
    }

    /** Frames until the card is at rest again (no animation left), then its top. */
    private fun settledTop(frames: Int = 120): Float? {
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
        return card()?.top
    }

    // ------------------------------------------------------------------ past the threshold: down only, onDismiss on the way

    @Test
    fun theCardOnlyGoesDownAfterAReleasePastTheThreshold() {
        host()
        val restTop = card()!!.top
        swipeDown(distanceDp = 220, durationMs = 700)
        val tops = framesUntilGone().map { it.top }
        val rise = tops.zipWithNext().indexOfFirst { (a, b) -> b < a - 0.5f }
        assertTrue("the card top rose after the release at frame $rise: $tops", rise == -1)
        assertTrue("the card was let go below its rest top $restTop: $tops", tops.first() > restTop + dpPx(100))
        assertTrue("the card left the screen: $tops", card() == null || card()!!.top >= composeRule.activity.window.decorView.height)
        assertEquals("onDismiss calls", 1, dismissCalls)
    }

    @Test
    fun aSlowSwipePastTheThresholdCallsOnDismissWithin350Ms() {
        host()
        swipeDown(distanceDp = 220, durationMs = 1_000)
        val ms = msToDismiss()
        assertTrue("onDismiss came $ms ms after the release", ms <= 350)
    }

    @Test
    fun aFastSwipePastTheThresholdCallsOnDismissWithin350Ms() {
        host()
        swipeDown(distanceDp = 220, durationMs = 80)
        val ms = msToDismiss()
        assertTrue("onDismiss came $ms ms after the release", ms <= 350)
    }

    // ------------------------------------------------------------------ short gestures

    @Test
    fun aShortFastFlickCloses() {
        host()
        swipeDown(distanceDp = 60, durationMs = 50)
        val ms = msToDismiss()
        assertTrue("onDismiss came $ms ms after the flick", ms <= 350)
        repeat(60) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("onDismiss calls", 1, dismissCalls)
    }

    @Test
    fun aShortSlowPullSpringsBackToWhereTheCardWas() {
        host()
        val restTop = card()!!.top
        swipeDown(distanceDp = 60, durationMs = 800)
        assertTrue("the card follows the finger", card()!!.top > restTop + dpPx(30))
        assertEquals(restTop, settledTop()!!, 1f)
        assertEquals(0, dismissCalls)
        assertTrue(visible)
    }

    // ------------------------------------------------------------------ the footer row: the draggable path

    @Test
    fun fromTheFooterASlowSwipePastTheThresholdCloses() {
        host(footer = true)
        swipeDown(distanceDp = 200, durationMs = 1_000, tag = FOOTER, fromTopPx = null)
        val ms = msToDismiss()
        assertTrue("onDismiss came $ms ms after the release", ms <= 350)
        repeat(60) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("onDismiss calls", 1, dismissCalls)
        assertEquals("the swipe pressed the footer button", 0, footerClicks)
    }

    @Test
    fun fromTheFooterAShortFastFlickCloses() {
        host(footer = true)
        swipeDown(distanceDp = 60, durationMs = 50, tag = FOOTER, fromTopPx = null)
        val ms = msToDismiss()
        assertTrue("onDismiss came $ms ms after the flick", ms <= 350)
        repeat(60) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("onDismiss calls", 1, dismissCalls)
        assertEquals("the flick pressed the footer button", 0, footerClicks)
    }

    @Test
    fun fromTheFooterAShortSlowPullSpringsBack() {
        host(footer = true)
        val restTop = card()!!.top
        swipeDown(distanceDp = 60, durationMs = 800, tag = FOOTER, fromTopPx = null)
        assertTrue("the card follows the finger", card()!!.top > restTop + dpPx(30))
        assertEquals(restTop, settledTop()!!, 1f)
        assertEquals(0, dismissCalls)
        assertEquals("the pull pressed the footer button", 0, footerClicks)
    }

    // ------------------------------------------------------------------ inertia of the content

    @Test
    fun theInertiaOfScrolledContentNeverLeavesTheCardHalfWay() {
        host(rows = 20, frameByFrame = false)
        val restTop = card()!!.top
        fun rowInCard() = bounds("row-0")!!.top - card()!!.top
        val topRowInCard = rowInCard()
        // the user scrolls the content to its middle
        composeRule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput {
            swipe(center, center - Offset(0f, 200.dp.toPx()), 900)
        }
        composeRule.waitForIdle()
        assertTrue("the content scrolled: row 0 at ${rowInCard()} in the card, was $topRowInCard", rowInCard() < topRowInCard - dpPx(150))
        assertEquals("scrolling up left the card in place", restTop, card()!!.top, 1f)

        // a fast swipe down on the content: less than it is scrolled, so the finger never reaches the card, the fling does
        composeRule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput {
            swipe(center, center + Offset(0f, 120.dp.toPx()), 60)
        }
        composeRule.waitForIdle()
        Log.i(TAG, "inertia: onDismiss $dismissCalls, card ${card()?.top} (rest $restTop), row 0 in the card ${card()?.let { rowInCard() }} (top $topRowInCard)")
        if (dismissCalls == 0) {
            assertEquals("the card is back at rest, not hanging half way", restTop, card()!!.top, 1f)
            assertEquals("the fling reached the top of the content (else nothing was checked)", topRowInCard, rowInCard(), 1f)
        }
        assertTrue("onDismiss calls $dismissCalls", dismissCalls <= 1)
    }

    // ------------------------------------------------------------------ reopening

    @Test
    fun reopeningWhileTheCardLeavesIsNotDismissedByTheOldSwipe() {
        host()
        val restTop = card()!!.top
        swipeDown(distanceDp = 220, durationMs = 700)
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        visible = false
        composeRule.mainClock.advanceTimeByFrame()
        visible = true
        assertEquals(restTop, settledTop()!!, 1f)
        assertEquals(0, dismissCalls)
        assertTrue(visible)
    }

    // ------------------------------------------------------------------ the keyboard

    /** The folder popup (name field, footer, more than fits), as the app shows it. */
    private fun folderPopup() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                TrackInsets()
                AppearancePopup(
                    visible = visible,
                    title = "Folder",
                    name = "Work",
                    initialColor = null,
                    initialIcon = null,
                    onSave = { _, _, _ -> },
                    onDismiss = { dismissCalls++; visible = false },
                    hazeState = remember { HazeState() },
                )
            }
        }
    }

    private fun tapTheBackdrop() {
        composeRule.onNodeWithTag(UiTestTags.PopupScrim).performTouchInput { click(Offset(centerX, 8.dp.toPx())) }
    }

    private fun pressBack() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    /*
     * Closing by the backdrop or Back clears the focus at once (`dismiss`): the keyboard leaves while the card does, and the card keeps
     * its height. Back is sent to the activity's dispatcher, as the popup's BackHandler gets it (a Back key would hide the keyboard first).
     */

    @Test
    fun withTheKeyboardUpTheFolderPopupKeepsItsHeightWhenTheBackdropIsTapped() =
        cardKeepsItsHeightWhileTheKeyboardLeaves(::folderPopup, ::tapTheBackdrop)

    @Test
    fun withTheKeyboardUpTheFolderPopupKeepsItsHeightOnBack() = cardKeepsItsHeightWhileTheKeyboardLeaves(::folderPopup, ::pressBack)

    /** The same on a bare card of that shape (field, footer, more than fits). */
    @Test
    fun withTheKeyboardUpACardWithAFooterKeepsItsHeightWhenTheBackdropIsTapped() =
        cardKeepsItsHeightWhileTheKeyboardLeaves({ host(footer = true, field = true, frameByFrame = false) }, ::tapTheBackdrop)

    /**
     * A swipe with the keyboard up: the caret and the keyboard stay while the card leaves (no keyboard animation in the middle of the
     * card's), and both go once the card is gone and `onDismiss` came.
     */
    @Test
    fun withTheKeyboardUpASwipeKeepsTheCaretAndTheKeyboardUntilTheCardIsGone() {
        val (_, heightWithKeyboard) = raiseTheKeyboard(::folderPopup)
        val keyboardPx = imePx
        composeRule.mainClock.autoAdvance = false
        swipeDown(distanceDp = 200, durationMs = 600, tag = UiTestTags.PopupCard, fromTopPx = dpPx(20))
        val imeEachFrame = mutableListOf<Int>()
        val heights = mutableListOf<Float>()
        var frames = 0
        while (dismissCalls == 0 && frames++ < 150) {
            assertTrue("the name field lost the caret at frame $frames, before the card was gone", nameFieldFocused())
            card()?.let { heights += it.height }
            imeEachFrame += imePx
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(16) // the keyboard would move on the device's own clock
        }
        Log.i(TAG, "swipe with the keyboard: $frames frames, heights $heights, ime $imeEachFrame")
        assertEquals("onDismiss calls", 1, dismissCalls)
        assertTrue("the card left in $frames frames: too few to check anything", heights.size >= 3 && imeEachFrame.size >= 3)
        assertTrue("the keyboard moved while the card left: $imeEachFrame", imeEachFrame.all { it == keyboardPx })
        assertTrue("the card height changed while it left: $heights", heights.all { kotlin.math.abs(it - heightWithKeyboard) <= 1f })
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(10_000) { imePx <= navPx }
    }

    private fun nameFieldFocused(): Boolean =
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Focused) == true }

    /** [setUp] shows the popup; its first text field gets the caret and the real keyboard. Returns the card height without / with it. */
    private fun raiseTheKeyboard(setUp: () -> Unit): Pair<Float, Float> {
        composeRule.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
            // as MainActivity: windowSoftInputMode="adjustResize"
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        setUp()
        composeRule.waitForIdle()
        val heightNoKeyboard = card()!!.height

        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        val deadline = System.currentTimeMillis() + 15_000
        while (imePx <= navPx && System.currentTimeMillis() < deadline) {
            composeRule.runOnUiThread {
                val w = composeRule.activity.window
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.ime())
            }
            Thread.sleep(300)
            composeRule.waitForIdle()
        }
        assertTrue("no on-screen keyboard (ime $imePx px, nav $navPx px): show_ime_with_hard_keyboard 1?", imePx > navPx)
        Thread.sleep(800) // the keyboard's slide-in
        composeRule.waitForIdle()
        val heightWithKeyboard = card()!!.height
        assertTrue(
            "the keyboard makes the card shorter ($heightWithKeyboard px vs $heightNoKeyboard px)",
            heightWithKeyboard < heightNoKeyboard - dpPx(8),
        )
        return heightNoKeyboard to heightWithKeyboard
    }

    /** [setUp] shows the popup, the keyboard comes up, [close] closes it: frame by frame the card keeps its height until it is gone. */
    private fun cardKeepsItsHeightWhileTheKeyboardLeaves(setUp: () -> Unit, close: () -> Unit) {
        val (_, heightWithKeyboard) = raiseTheKeyboard(setUp)
        val keyboardPx = imePx
        composeRule.mainClock.autoAdvance = false
        close()
        val imeEachFrame = mutableListOf(imePx)
        // the keyboard leaves on the device's own clock: let it move between the test's frames
        val heights = framesUntilGone { Thread.sleep(16); imeEachFrame += imePx }.map { it.height }
        Log.i(TAG, "keyboard: heights $heights, ime $imeEachFrame")
        assertEquals("onDismiss calls", 1, dismissCalls)
        assertTrue(
            "the keyboard did not move while the card left ($imeEachFrame): nothing was checked",
            imeEachFrame.min() < keyboardPx - dpPx(40),
        )
        val changed = heights.indexOfFirst { kotlin.math.abs(it - heightWithKeyboard) > 1f }
        assertTrue("the card height changed at frame $changed while it left: $heights (keyboard $imeEachFrame)", changed == -1)
    }

    @Composable
    private fun TrackInsets() {
        val density = LocalDensity.current
        val ime = WindowInsets.ime.getBottom(density)
        val nav = WindowInsets.navigationBars.getBottom(density)
        SideEffect {
            imePx = ime
            navPx = nav
        }
    }

    private companion object {
        const val FOOTER = "test-popup-footer"
        const val TAG = "TesseraPopupSwipeTest"
    }
}
