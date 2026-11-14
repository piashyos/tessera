package app.tessera.ui.settings.licenses

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot baselines for [LicensesScreen]: the list (light/dark) and the license-text sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class LicensesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sample = listOf(
        LicenseEntry("Activity Compose", "Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/"),
        LicenseEntry("Coil", "Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/"),
        LicenseEntry("Dagger", "Apache License\nVersion 2.0, January 2004\nhttp://www.apache.org/licenses/"),
    )

    private fun show(darkTheme: Boolean) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { LicensesScreen(licenses = sample) }
            }
        }
    }

    @Test fun listLight() {
        show(darkTheme = false)
        composeRule.onRoot().captureRoboImage("ui/settings/licenses_list_light.png")
    }

    @Test fun listDark() {
        show(darkTheme = true)
        composeRule.onRoot().captureRoboImage("ui/settings/licenses_list_dark.png")
    }

    @Test fun textSheetLight() {
        show(darkTheme = false)
        composeRule.onNodeWithText("Coil").performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("ui/settings/licenses_text_sheet_light.png")
    }
}
