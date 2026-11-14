package app.tessera.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot baselines for [AppearanceScreen]: light/dark, and with the gradient off (no "Background animation" row). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppearanceScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, state: SettingsState) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { AppearanceScreen(state = state) }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/settings/$name.png")
    }

    @Test fun light() = capture("appearance_light", darkTheme = false, state = SettingsState())

    @Test fun dark() = capture("appearance_dark", darkTheme = true, state = SettingsState())

    @Test fun gradientOffLight() =
        capture("appearance_gradient_off_light", darkTheme = false, state = SettingsState(gradientEnabled = false))
}
