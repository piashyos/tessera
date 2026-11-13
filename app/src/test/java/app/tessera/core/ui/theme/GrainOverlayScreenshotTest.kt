package app.tessera.core.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app background at rest with the gradient on, a pair of cards over it (light / dark), and grain size 1 / 4.
 * New, no goldens yet: the orchestrator records them after looking at the `_actual` images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class GrainOverlayScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean, grainSize: Int = 1, intensity: Float = 0.5f) {
        composeRule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                GrainOverlay(enabled = true, intensity = intensity, grainSize = grainSize, gradientEnabled = true) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        repeat(2) { i ->
                            Surface(
                                Modifier.fillMaxWidth().height(96.dp).padding(bottom = 12.dp),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) { Text("Card ${i + 1}", Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("core/ui/theme/$name.png")
    }

    @Test fun gradientLight() = capture("grain-overlay-gradient-light", dark = false)

    @Test fun gradientDark() = capture("grain-overlay-gradient-dark", dark = true)

    @Test fun grainSize1() = capture("grain-overlay-grain-x1", dark = false, grainSize = 1, intensity = 1f)

    @Test fun grainSize4() = capture("grain-overlay-grain-x4", dark = false, grainSize = 4, intensity = 1f)
}
