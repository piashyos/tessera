package app.tessera.core.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Frame-by-frame motion of the swipe-down dismissal on a host that behaves like production:
 * `onDismiss` flips `visible` to false.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TesseraPopupSwipeMotionTest {
    @get:Rule
    val rule = createComposeRule()

    private var visible by mutableStateOf(true)
    private var dismissCalls = 0
    private var taps = 0
    private var handleColor = Color.Unspecified
    private val focus = FocusRequester()
    private var focused = false
    private var hostBacks = 0
    private lateinit var back: OnBackPressedDispatcher

    /** [tall]: a card of ~360dp, so that the 120dp threshold (not half of a short card) decides and leaving takes a few frames. */
    private fun host(tall: Boolean = false, withField: Boolean = false) {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                // The screen under the popup: a tappable area and a Back handler of its own (the popup's is registered later, so it comes first).
                BackHandler { hostBacks++ }
                Box(Modifier.fillMaxSize().testTag("under").clickable { taps++ })
                TesseraPopup(
                    visible = visible,
                    onDismiss = { dismissCalls++; visible = false },
                    hazeState = remember { HazeState() },
                ) {
                    handleColor = LocalTextSelectionColors.current.handleColor
                    Text("Content")
                    if (tall) Box(Modifier.height(300.dp))
                    if (withField) BasicTextField("", {}, Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused })
                }
            }
        }
        if (withField) rule.runOnIdle { focus.requestFocus() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.mainClock.autoAdvance = false
    }

    private fun cardTop(): Float? =
        rule.onAllNodes(hasTestTag(UiTestTags.PopupCard)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

    private fun swipeDown(distanceDp: Int, durationMs: Long) {
        rule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput {
            val dy = distanceDp.dp.toPx()
            swipe(Offset(centerX, top + 20f), Offset(centerX, top + 20f + dy), durationMs)
        }
    }

    /** Card tops, one per frame, from the release until the card is gone (or [maxFrames]). */
    private fun topsUntilGone(maxFrames: Int = 120): List<Float> {
        val tops = mutableListOf<Float>()
        repeat(maxFrames) {
            cardTop()?.let { tops += it } ?: return tops
            rule.mainClock.advanceTimeByFrame()
        }
        return tops
    }

    @Test
    fun cardTopNeverRisesAfterReleaseBeyondTheThreshold() {
        host()
        val restTop = cardTop()!!
        swipeDown(distanceDp = 220, durationMs = 700)
        val tops = topsUntilGone()
        val rises = tops.zipWithNext().withIndex().filter { (_, p) -> p.second < p.first - 0.5f }
        assertTrue("card top rose after release: first rise at frame ${rises.firstOrNull()?.index}, tops=$tops", rises.isEmpty())
        assertTrue("card never moved down from $restTop: $tops", (tops.maxOrNull() ?: 0f) > restTop)
    }

    /** Clock time from the release (the swipe has just returned) until onDismiss. */
    private fun msToDismiss(distanceDp: Int, durationMs: Long): Long {
        swipeDown(distanceDp, durationMs)
        val releasedAt = rule.mainClock.currentTime
        var frames = 0
        while (dismissCalls == 0 && frames++ < 120) rule.mainClock.advanceTimeByFrame()
        val ms = rule.mainClock.currentTime - releasedAt
        repeat(60) { rule.mainClock.advanceTimeByFrame() } // a late second call would show up here
        assertEquals("onDismiss calls", 1, dismissCalls)
        return ms
    }

    @Test
    fun slowSwipeBeyondTheThresholdCallsOnDismissWithoutWaitingForTheSpringTail() {
        host(tall = true)
        val ms = msToDismiss(distanceDp = 220, durationMs = 1_000)
        assertTrue("onDismiss came $ms ms after the release", ms <= 350)
    }

    @Test
    fun fastSwipeBeyondTheThresholdCallsOnDismissWithoutWaitingForTheSpringTail() {
        host(tall = true)
        val ms = msToDismiss(distanceDp = 220, durationMs = 80)
        assertTrue("onDismiss came $ms ms after the release", ms <= 350)
    }

    @Test
    fun shortSlowPullSpringsBackAndKeepsThePopupOpen() {
        host(tall = true)
        val restTop = cardTop()!!
        swipeDown(distanceDp = 60, durationMs = 800)
        repeat(90) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(0, dismissCalls)
        assertTrue(visible)
        assertEquals(restTop, cardTop()!!, 1f)
    }

    @Test
    fun reopeningWhileTheCardLeavesDoesNotDismissTheNewOpening() {
        host(tall = true)
        val restTop = cardTop()!!
        swipeDown(distanceDp = 220, durationMs = 700)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        visible = false
        rule.mainClock.advanceTimeByFrame()
        visible = true
        repeat(120) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(0, dismissCalls)
        assertTrue(visible)
        assertEquals(restTop, cardTop()!!, 1f)
    }

    @Test
    fun afterTheSwipeLeavesNothingInvisibleCatchesTheTouchesOnTheScreenUnder() {
        host(tall = true)
        swipeDown(distanceDp = 220, durationMs = 700)
        var frames = 0
        while (dismissCalls == 0 && frames++ < 120) rule.mainClock.advanceTimeByFrame()
        assertEquals(1, dismissCalls)
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("under").performClick()
        assertEquals("the tap right after onDismiss must reach the screen", 1, taps)
    }

    @Test
    fun backWhileTheCardLeavesIsSwallowed() {
        host(tall = true)
        swipeDown(distanceDp = 220, durationMs = 700)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        rule.runOnUiThread { back.onBackPressed() }
        assertEquals("Back went through to the screen", 0, hostBacks)
        repeat(120) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(1, dismissCalls)
    }

    @Test
    fun closingWhileTheCardSpringsBackDoesNotPullItUp() {
        host(tall = true)
        swipeDown(distanceDp = 60, durationMs = 800)
        repeat(2) { rule.mainClock.advanceTimeByFrame() }
        val tops = mutableListOf(cardTop()!!)
        visible = false
        tops += topsUntilGone()
        // The frame in which `visible` flips still advances the spring once (the animation runs before the recomposition
        // that sees the new value): that one step is allowed, a spring pulling the card up for the rest of the exit is not.
        val rises = tops.drop(2).zipWithNext().filter { it.second < it.first - 0.5f }
        assertTrue("card top rose while closing: $tops", rises.isEmpty())
    }

    @Test
    fun focusIsClearedAfterTheCardLeftNotBefore() {
        host(tall = true, withField = true)
        assertTrue("the field must hold the focus", focused)
        swipeDown(distanceDp = 220, durationMs = 700)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        assertTrue("focus cleared at the start of the exit", focused)
        repeat(120) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(1, dismissCalls)
        assertTrue("focus must be cleared after onDismiss", !focused)
    }

    @Test
    fun reopeningWhileTheCardLeavesKeepsTheFocus() {
        host(tall = true, withField = true)
        swipeDown(distanceDp = 220, durationMs = 700)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        visible = false
        rule.mainClock.advanceTimeByFrame()
        visible = true
        repeat(120) { rule.mainClock.advanceTimeByFrame() }
        assertEquals(0, dismissCalls)
        assertTrue("focus must stay on reopening", focused)
    }

    @Test
    fun selectionHandlesAreHiddenInsideTheCardOnlyWhileItLeaves() {
        host(tall = true)
        rule.mainClock.advanceTimeByFrame()
        val themed = handleColor
        assertTrue("at rest the handle keeps the theme colour", themed != Color.Transparent)
        swipeDown(distanceDp = 220, durationMs = 700)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("the card must still be leaving", 0, dismissCalls)
        assertEquals(Color.Transparent, handleColor)
    }
}
