package app.tessera.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraPopupSwipeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeDownDismissesPopup() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(
                    visible = true,
                    onDismiss = { dismissed = true },
                    hazeState = remember { HazeState() },
                ) {
                    Text("Content")
                }
            }
        }

        // Swipe down a distance well beyond the ~120dp dismiss threshold. The card
        // rendered here is short, so the default swipeDown() would not travel far enough.
        composeRule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput {
            swipeDown(startY = top, endY = top + 600f)
        }
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }
}
