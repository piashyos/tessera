package app.tessera.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.sync.SyncIndicator
import app.tessera.ui.settings.components.ThemePickerSheet
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for [SettingsScreen] (Plan 07, Task 3): light/dark, every sync indicator
 * state, and the theme picker bottom sheet. Sync states are driven through [SettingsState] and the
 * sheet through [captureScreenRoboImage] (it is a modal window).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/settings/$name.png")
    }

    @Composable
    private fun settings(state: SettingsState) = SettingsScreen(state = state)

    @Test fun okLight() = capture("settings_ok_light", darkTheme = false) { settings(signedIn(SyncIndicator.Ok())) }

    @Test fun okDark() = capture("settings_ok_dark", darkTheme = true) { settings(signedIn(SyncIndicator.Ok())) }

    @Test fun syncing() = capture("settings_syncing_light", darkTheme = false) { settings(signedIn(SyncIndicator.Syncing)) }

    @Test fun offline() = capture("settings_offline_light", darkTheme = false) { settings(signedIn(SyncIndicator.Offline)) }

    @Test fun error() = capture("settings_error_light", darkTheme = false) {
        settings(signedIn(SyncIndicator.Error("Sync failed")))
    }

    @Test fun signedOut() = capture("settings_signed_out_light", darkTheme = false) {
        settings(SettingsState(isSignedIn = false, themeMode = ThemeMode.AUTO))
    }

    @Test fun themeSheetLight() = captureSheet("settings_theme_sheet_light", darkTheme = false)

    @Test fun themeSheetDark() = captureSheet("settings_theme_sheet_dark", darkTheme = true)

    private fun captureSheet(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                ThemePickerSheet(selected = ThemeMode.AUTO, onSelect = {}, onDismiss = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("ui/settings/$name.png")
    }
}

private fun signedIn(sync: SyncIndicator) =
    SettingsState(
        isSignedIn = true,
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        themeMode = ThemeMode.AUTO,
        syncStatus = sync,
    )
