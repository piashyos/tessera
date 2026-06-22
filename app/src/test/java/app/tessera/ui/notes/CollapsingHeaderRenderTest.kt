package app.tessera.ui.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class CollapsingHeaderRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val title = "All Notes"

    @Test
    fun headerFullShowsTitleAndSettings() {
        var settingsClicked = false
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                CollapsingHeader(
                    title = title,
                    scrollPx = 0,
                    onOpenSettings = { settingsClicked = true },
                    viewMode = NotesViewMode.Tiles,
                    onToggleViewMode = {},
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.OpenSettings).assertExists()
        composeRule.onRoot().captureRoboImage("ui/collapsing-header-full.png")

        composeRule.onNodeWithTag(UiTestTags.OpenSettings).performClick()
        assertTrue("settings button should be clickable", settingsClicked)
    }

    @Test
    fun headerCollapsedShowsTitleAndSettings() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                CollapsingHeader(
                    title = title,
                    scrollPx = 400,
                    onOpenSettings = {},
                    viewMode = NotesViewMode.Tiles,
                    onToggleViewMode = {},
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.OpenSettings).assertExists()
        composeRule.onRoot().captureRoboImage("ui/collapsing-header-collapsed.png")
    }
}
