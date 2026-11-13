package app.tessera.core.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.test.center
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class GrainOverlayGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var motion: BackdropMotionState

    private fun setOverlay(gradient: Boolean, content: @Composable () -> Unit = { Text("body") }) {
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = gradient, motion = motion) {
                    Box(Modifier.fillMaxSize().testTag("area")) { content() }
                }
            }
        }
    }

    private fun maxOffset() = (0..2).maxOf { motion.offset(it).getDistance() }

    @Test
    fun listStillScrollsAndItemsStillClick() {
        var clicked = 0
        lateinit var state: LazyListState
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            state = rememberLazyListState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = true, motion = motion) {
                    LazyColumn(Modifier.testTag("list"), state = state) {
                        items((0 until 60).toList()) { i -> Text("Item $i", Modifier.clickable { clicked++ }) }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("list scrolled", state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        composeRule.onNodeWithText("Item ${state.firstVisibleItemIndex + 1}").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun dragMovesBlobAAlongTheGestureAndTheyRestAfterRelease() {
        setOverlay(gradient = true)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 40f)) }
        }
        composeRule.mainClock.advanceTimeBy(200)
        assertTrue("A moved down: ${motion.offset(0)}", motion.offset(0).y > 0f)
        assertTrue("A kept its x", kotlin.math.abs(motion.offset(0).x) < motion.offset(0).y)
        composeRule.onNodeWithTag("area").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(3000)
        for (i in 0..2) assertEquals("blob $i at rest", 0f, motion.offset(i).getDistance(), 1f)
    }

    @Test
    fun disabledGradientNeverMoves() {
        setOverlay(gradient = false)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 40f)) }
        }
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(0f, maxOffset(), 0f)
        composeRule.onNodeWithTag("area").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(0f, maxOffset(), 0f)
        assertEquals(1f, motion.scale, 0f)
        assertEquals(0f, motion.pulse, 0f)
    }

    @Test
    fun cancelAfterDragReturnsEverythingToRest() {
        setOverlay(gradient = true)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(4) { moveBy(Offset(0f, 30f)) }
        }
        composeRule.mainClock.advanceTimeBy(200)
        assertTrue(maxOffset() > 0f)
        composeRule.onNodeWithTag("area").performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(3000)
        for (i in 0..2) assertEquals(0f, motion.offset(i).getDistance(), 1f)
        assertEquals(0f, motion.pulse, 0.001f)
        assertEquals(1f, motion.scale, 0.001f)
    }

    @Test
    fun pressPushesBlobAAwayFromTheTouchPointThenRests() {
        setOverlay(gradient = true)
        composeRule.mainClock.autoAdvance = false
        var press = Offset.Zero
        var size = Offset.Zero
        composeRule.onNodeWithTag("area").performTouchInput {
            size = Offset(width.toFloat(), height.toFloat())
            // up-left of blob A's resting centre (0.15, 0.12)
            press = Offset(0.05f * width, 0.03f * height)
            down(press)
        }
        composeRule.mainClock.advanceTimeBy(150)
        assertTrue("pulse rises: ${motion.pulse}", motion.pulse > 0f)
        assertTrue("scale grows: ${motion.scale}", motion.scale > 1f)
        assertEquals(press, motion.pulseOrigin)
        val centerA = Offset(0.15f * size.x, 0.12f * size.y)
        // the very shift the blob layer draws with (no drag yet, so it is the pulse alone)
        val push = motion.blobShift(0, centerA, BackdropLimitFraction * minOf(size.x, size.y))
        val dot = (centerA - press).x * push.x + (centerA - press).y * push.y
        assertTrue("push goes away from the finger: dot=$dot", dot > 0f)
        composeRule.onNodeWithTag("area").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(2500)
        assertEquals(0f, motion.pulse, 0.001f)
        assertEquals(1f, motion.scale, 0.001f)
        for (i in 0..2) assertEquals(0f, motion.offset(i).getDistance(), 1f)
    }

    @Test
    fun everyTapPulses() {
        setOverlay(gradient = true)
        composeRule.mainClock.autoAdvance = false
        repeat(2) { n ->
            composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
            var peak = 0f
            repeat(30) {
                composeRule.mainClock.advanceTimeBy(16)
                peak = maxOf(peak, motion.pulse)
            }
            assertTrue("tap $n pulsed: $peak", peak > 0.1f)
            composeRule.mainClock.advanceTimeBy(3000)
            assertEquals(0f, motion.pulse, 0.001f)
        }
    }

    @Test
    fun switchingTheGradientOffMidDragReturnsTheBlobsToRest() {
        var gradient by mutableStateOf(true)
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = gradient, motion = motion) {
                    Box(Modifier.fillMaxSize().testTag("area"))
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput { down(center); repeat(5) { moveBy(Offset(0f, 40f)) } }
        composeRule.mainClock.advanceTimeBy(200)
        assertTrue(maxOffset() > 0f)
        gradient = false // the listener goes away while the finger is down: it must count as a cancel
        composeRule.mainClock.advanceTimeBy(3000)
        for (i in 0..2) assertEquals("blob $i at rest", 0f, motion.offset(i).getDistance(), 1f)
        composeRule.onNodeWithTag("area").performTouchInput { up() }
    }

    @Test
    fun disabledGradientDoesNotPulse() {
        setOverlay(gradient = false)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(0f, motion.pulse, 0f)
    }
}
