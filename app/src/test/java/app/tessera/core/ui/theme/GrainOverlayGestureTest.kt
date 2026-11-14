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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.sqrt
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

    // Windows until everything is at rest again, from the constants: the fling lasts ln(v0 / threshold) / (4.2 × friction)
    // (v0 ≤ the fastest fling × the largest gain; Robolectric's ViewConfiguration caps a fling at 8000 px/s), the critically
    // damped return needs ~10 / sqrt(stiffness) s to get under the visibility threshold, the pulse rises in 0.4 s and falls
    // within 6 / (damping × sqrt(stiffness)) s.
    private val flingMs = (ln(8000f * BackdropGains[0] / FlingVelocityThreshold) / (4.2f * FlingFriction) * 1000).toLong() + 100
    private val returnMs = (10f / sqrt(BackdropReturnStiffness) * 1000).toLong()
    private val restWindowMs = flingMs + returnMs
    private val pulseWindowMs = 400 + (6f / (PulseFallDamping * sqrt(PulseFallStiffness)) * 1000).toLong()

    private fun setOverlay(gradient: Boolean = true, motionEnabled: Boolean = true, content: @Composable () -> Unit = { Text("body") }) {
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = gradient, motionEnabled = motionEnabled, motion = motion) {
                    Box(Modifier.fillMaxSize().testTag("area")) { content() }
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
    }

    private fun maxOffset() = (0..2).maxOf { motion.offset(it).getDistance() }

    private fun assertAtRest(step: String = "") {
        for (i in 0..2) assertEquals("$step blob $i at rest", 0f, motion.offset(i).getDistance(), 1f)
        assertEquals("$step pulse", 0f, motion.pulse, 0.001f)
        assertEquals("$step scale", 1f, motion.scale, 0.001f)
    }

    /** A finger down at the centre and moved by [steps] × [step] (16 ms apart), still down. */
    private fun dragDown(steps: Int, step: Offset) {
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(steps) { moveBy(step) }
        }
    }

    private fun up() = composeRule.onNodeWithTag("area").performTouchInput { up() }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun limitPx(): Float {
        var side = 0f
        composeRule.onNodeWithTag("area").performTouchInput { side = minOf(width, height).toFloat() }
        return BackdropLimitFraction * side
    }

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
    fun theBlobsFollowTheFingerWhileItKeepsMoving() {
        setOverlay()
        composeRule.onNodeWithTag("area").performTouchInput { down(center) }
        // a move every frame, as on a device: the spring must not restart from standstill each time
        repeat(10) {
            composeRule.onNodeWithTag("area").performTouchInput { moveBy(Offset(0f, 10f)) }
            advance(16)
        }
        // goal: softLimit(0.2 × 100 px) ≈ 20 px; read before any pause or release
        val a = motion.offset(0).y
        assertTrue("A follows the moving finger: $a", a > 5f)
    }

    @Test
    fun tapBreathesWithoutMovingTheBlobsThenRests() {
        setOverlay()
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        var peak = 0f
        var peakScale = 1f
        repeat(30) {
            advance(16)
            peak = maxOf(peak, motion.pulse)
            peakScale = maxOf(peakScale, motion.scale)
            assertEquals("no push from the finger", 0f, maxOffset(), 0f)
        }
        assertTrue("pulsed: $peak", peak > 0.5f)
        assertTrue("scale grows: $peakScale", peakScale > 1f)
        assertTrue("scale stays under PulseScale: $peakScale", peakScale <= PulseScale + 0.001f)
        advance(pulseWindowMs)
        assertAtRest("after tap")
    }

    @Test
    fun everyTapBreathes() {
        setOverlay()
        repeat(2) { n ->
            composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
            var peak = 0f
            repeat(30) { advance(16); peak = maxOf(peak, motion.pulse) }
            assertTrue("tap $n pulsed: $peak", peak > 0.1f)
            advance(pulseWindowMs)
            assertEquals(0f, motion.pulse, 0.001f)
        }
    }

    @Test
    fun dragNeverBreathesAndFansTheBlobsOut() {
        setOverlay()
        dragDown(5, Offset(0f, 40f))
        advance(300)
        assertEquals("no breath while dragging", 0f, motion.pulse, 0f)
        val gesture = Offset(0f, 1f)
        val a = motion.offset(0)
        val b = motion.offset(1)
        val c = motion.offset(2)
        fun angle(v: Offset) = Math.toDegrees(atan2((gesture.x * v.y - gesture.y * v.x).toDouble(), (gesture.x * v.x + gesture.y * v.y).toDouble()))
        assertEquals("A along the gesture", 0.0, angle(a), 0.5)
        assertEquals("B at +120", 120.0, angle(b), 0.5)
        assertEquals("C at -120", -120.0, angle(c), 0.5)
        // 0.2 / 0.15 / 0.1 of the same gesture: B and C are shifted less than A
        assertTrue(a.getDistance() > b.getDistance() && b.getDistance() > c.getDistance())
        val atUp = motion.offset(0).getDistance()
        up() // the finger stood still for 300 ms: no speed, no glide
        repeat(60) {
            advance(16)
            assertEquals("no breath after the swipe", 0f, motion.pulse, 0f)
            assertTrue("A does not grow after a stopped finger lifts", motion.offset(0).getDistance() <= atUp + 0.5f)
        }
        advance(restWindowMs)
        assertAtRest("after drag")
    }

    @Test
    fun shortSwipeGlidesOnAfterReleaseThenRests() {
        setOverlay()
        // 150 px in ~80 ms, far under limit / gain: the shift cannot be at the saturation, so growth after `up` is the fling
        composeRule.onNodeWithTag("area").performTouchInput { down(center) }
        repeat(5) { // a frame between the moves, as on a device
            composeRule.onNodeWithTag("area").performTouchInput { moveBy(Offset(0f, 30f)) }
            advance(16)
        }
        assertTrue(150f < limitPx() / BackdropGains[0])
        up() // straight away: a pause before the release would leave the VelocityTracker with no speed
        val atUp = motion.offset(0).y
        assertTrue("A has already followed the finger at release: $atUp", atUp > 5f)
        advance(300)
        val glided = motion.offset(0).y
        assertTrue("A keeps going after the finger is up: $atUp -> $glided", glided > atUp + 1f)
        assertTrue("and stays inside the limit: $glided", motion.offset(0).getDistance() <= limitPx() + 0.5f)
        advance(restWindowMs)
        assertAtRest("after fling")
    }

    @Test
    fun aPauseOfTenMillisecondsBeforeReleaseDoesNotSlowTheFling() {
        setOverlay()
        // The same swipe twice: released at once, and with a 10 ms hold (a normal lift-off); the glide must be the same.
        fun glidePeak(holdMs: Long): Float {
            dragDown(5, Offset(0f, 30f))
            composeRule.onNodeWithTag("area").performTouchInput {
                if (holdMs > 0) advanceEventTime(holdMs)
                up()
            }
            var peak = 0f
            repeat((restWindowMs / 16).toInt()) { advance(16); peak = maxOf(peak, motion.offset(0).y) }
            assertAtRest("after swipe, hold $holdMs")
            return peak
        }
        val immediate = glidePeak(0)
        val held = glidePeak(10)
        assertTrue("fling exists: $immediate", immediate > 20f)
        assertEquals("glide with a 10 ms hold: $held vs $immediate", immediate, held, immediate * 0.05f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSwipeDeliveredInOneBatchGlidesAsFarAsTheSameSwipeEventByEvent() {
        setOverlay()
        // Under load the moves of a swipe come in one event with `historical` points; the speed must come from all of them.
        fun glidePeak(batched: Boolean): Float {
            composeRule.onNodeWithTag("area").performTouchInput {
                down(center)
                if (batched) {
                    // 80 ms of movement in ONE event: four historical points, then the current position
                    updatePointerTo(0, center + Offset(0f, 150f))
                    moveWithHistory(
                        relativeHistoricalTimes = listOf(-64L, -48L, -32L, -16L),
                        historicalCoordinates = (1..4).map { center + Offset(0f, 30f * it) },
                        delayMillis = 80,
                    )
                } else {
                    repeat(5) { moveBy(Offset(0f, 30f)) }
                }
                up()
            }
            var peak = 0f
            repeat((restWindowMs / 16).toInt()) { advance(16); peak = maxOf(peak, motion.offset(0).y) }
            assertAtRest("after swipe, batched=$batched")
            return peak
        }
        val single = glidePeak(batched = false)
        val batched = glidePeak(batched = true)
        assertTrue("fling exists: $single", single > 20f)
        assertEquals("batched swipe glide: $batched vs $single", single, batched, single * 0.1f)
    }

    @Test
    fun fastSwipeNeverLeavesTheLimitCircle() {
        setOverlay()
        val limit = limitPx()
        dragDown(10, Offset(0f, 150f))
        up()
        var peak = 0f
        repeat((restWindowMs / 16).toInt()) {
            advance(16)
            for (i in 0..2) peak = maxOf(peak, motion.offset(i).getDistance())
        }
        assertTrue("peak shift $peak is reached (the fling is not dead)", peak > 0.5f * limit)
        assertTrue("peak shift $peak is inside the limit $limit", peak <= limit + 0.5f)
        assertAtRest("after fast fling")
    }

    @Test
    fun pressDuringTheFlingCatchesTheBlobsWithoutAJump() {
        setOverlay()
        dragDown(5, Offset(0f, 30f))
        up()
        advance(100)
        val caught = (0..2).map { motion.offset(it) }
        composeRule.onNodeWithTag("area").performTouchInput { down(center) }
        for (i in 0..2) assertEquals("no jump on the press, blob $i", caught[i], motion.offset(i))
        advance(600)
        for (i in 0..2) {
            assertEquals("blob $i x stays caught", caught[i].x, motion.offset(i).x, 0.5f)
            assertEquals("blob $i y stays caught", caught[i].y, motion.offset(i).y, 0.5f)
        }
        // dragging on continues from where the blob was caught, not from rest
        composeRule.onNodeWithTag("area").performTouchInput { moveBy(Offset(0f, 2f)) }
        advance(600)
        for (i in 0..2) assertTrue("blob $i continues, no jump back", (motion.offset(i) - caught[i]).getDistance() < 3f)
        assertTrue("caught away from rest", caught[0].getDistance() > 5f)
    }

    @Test
    fun longPressWithoutMovingDoesNothing() {
        setOverlay()
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
            up()
        }
        repeat(60) {
            advance(16)
            assertEquals("no breath", 0f, motion.pulse, 0f)
            assertEquals("no shift", 0f, maxOffset(), 0f)
        }
    }

    @Test
    fun secondFingerMakesItNotATap() {
        setOverlay()
        composeRule.onNodeWithTag("area").performTouchInput {
            down(0, center)
            down(1, center + Offset(100f, 0f))
            up(1)
            up(0)
        }
        repeat(60) {
            advance(16)
            assertEquals("no breath", 0f, motion.pulse, 0f)
            assertEquals("no shift", 0f, maxOffset(), 0f)
        }
    }

    @Test
    fun disabledGradientNeverMoves() {
        setOverlay(gradient = false)
        dragDown(5, Offset(0f, 40f))
        advance(300)
        assertEquals(0f, maxOffset(), 0f)
        up()
        advance(300)
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        advance(300)
        assertEquals(0f, maxOffset(), 0f)
        assertEquals(1f, motion.scale, 0f)
        assertEquals(0f, motion.pulse, 0f)
    }

    @Test
    fun motionDisabledMeansNoListenerSoNothingMoves() {
        setOverlay(motionEnabled = false)
        dragDown(5, Offset(0f, 40f))
        advance(300)
        up()
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        advance(300)
        assertEquals(0f, maxOffset(), 0f)
        assertEquals(1f, motion.scale, 0f)
        assertEquals(0f, motion.pulse, 0f)
    }

    @Test
    fun cancelAfterDragReturnsEverythingToRestWithoutAGlide() {
        setOverlay()
        dragDown(4, Offset(0f, 30f))
        advance(200)
        assertTrue(maxOffset() > 0f)
        composeRule.onNodeWithTag("area").performTouchInput { cancel() }
        var peak = maxOffset()
        repeat((restWindowMs / 16).toInt()) {
            advance(16)
            assertTrue("no glide after a cancel", maxOffset() <= peak + 0.01f)
            peak = maxOffset()
            assertEquals("no breath", 0f, motion.pulse, 0f)
        }
        assertAtRest("after cancel")
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
        dragDown(5, Offset(0f, 40f))
        advance(200)
        assertTrue(maxOffset() > 0f)
        gradient = false // the listener goes away while the finger is down: it must count as a cancel
        advance(restWindowMs)
        assertAtRest("after switch-off")
        up()
    }

    @Test
    fun switchingMotionOffMidBreathEndsTheBreath() {
        var motionOn by mutableStateOf(true)
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = true, motionEnabled = motionOn, motion = motion) {
                    Box(Modifier.fillMaxSize().testTag("area"))
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        advance(150)
        assertTrue(motion.pulse > 0f)
        motionOn = false
        advance(100)
        assertEquals(0f, motion.pulse, 0f)
    }
}
