package app.tessera.core.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Remove animations" (duration scale 0): the blobs must stay at rest instead of jumping to their goals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class GrainOverlayReducedMotionTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test
    fun gestureLeavesBlobsAndScaleAtRest() {
        lateinit var motion: BackdropMotionState
        composeRule.setContent {
            motion = rememberBackdropMotionState()
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = 0.5f, gradientEnabled = true, motion = motion) {
                    Box(Modifier.fillMaxSize().testTag("area")) { Text("body") }
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        fun assertRest(step: String) {
            for (i in 0..2) assertEquals("$step: blob $i", 0f, motion.offset(i).getDistance(), 0f)
            assertEquals("$step: scale", 1f, motion.scale, 0f)
            assertEquals("$step: pulse", 0f, motion.pulse, 0f)
        }
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 40f)) }
        }
        for (t in 1..10) { composeRule.mainClock.advanceTimeBy(50); assertRest("drag $t") }
        composeRule.onNodeWithTag("area").performTouchInput { up() }
        for (t in 1..10) { composeRule.mainClock.advanceTimeBy(100); assertRest("release $t") }
        composeRule.onNodeWithTag("area").performTouchInput { down(center); up() }
        for (t in 1..10) { composeRule.mainClock.advanceTimeBy(50); assertRest("tap $t") }
        // a whole swipe in one go: the fling must not move anything either
        composeRule.onNodeWithTag("area").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 40f)) }
            up()
        }
        for (t in 1..20) { composeRule.mainClock.advanceTimeBy(16); assertRest("fling $t") }
    }
}
