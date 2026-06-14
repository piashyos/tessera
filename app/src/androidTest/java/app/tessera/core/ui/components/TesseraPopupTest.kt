package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraPopupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hiddenWhenNotVisible() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = false, onDismiss = {}, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithText("popup body").assertDoesNotExist()
    }

    @Test
    fun showsContentWhenVisible() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = true, onDismiss = {}, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithText("popup body").assertIsDisplayed()
    }

    @Test
    fun scrimTapInvokesDismiss() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = true, onDismiss = { dismissed = true }, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithTag(UiTestTags.PopupScrim).performClick()
        assertTrue(dismissed)
    }
}
