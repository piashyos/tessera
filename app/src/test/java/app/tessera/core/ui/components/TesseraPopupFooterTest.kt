package app.tessera.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A popup with a footer keeps its buttons on screen however tall the content is; without one nothing changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
// The card is clickable, which merges its texts into one node: look at the unmerged tree.
class TesseraPopupFooterTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun footerStaysVisibleWhenTheContentIsTallerThanTheCard() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    TesseraPopup(
                        visible = true,
                        onDismiss = {},
                        hazeState = HazeState(),
                        footer = { Button(onClick = {}) { Text("Footer") } },
                    ) {
                        Text("Top")
                        Box(Modifier.height(2000.dp))
                        Text("Bottom")
                    }
                }
            }
        }
        rule.onNodeWithText("Top", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Footer").assertIsDisplayed()
        rule.onNodeWithText("Bottom", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun withoutAFooterTheLastRowIsScrolledOut() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    TesseraPopup(visible = true, onDismiss = {}, hazeState = HazeState()) {
                        Text("Top")
                        Box(Modifier.height(2000.dp))
                        Button(onClick = {}) { Text("Last") }
                    }
                }
            }
        }
        rule.onNodeWithText("Top", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Last", useUnmergedTree = true).assertIsNotDisplayed()
    }
}
