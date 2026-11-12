package app.tessera.ui.notes.components

import androidx.compose.material3.Surface
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a 360 dp screen the icon grid has 6 columns, and the short last row keeps to the same columns as the rows above. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-port-xxhdpi")
class AppearancePopupNarrowTest {
    @get:Rule
    val rule = createComposeRule()

    // Unclipped: the lower rows are scrolled out of the card's viewport.
    private fun left(description: String) = rule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot().left.value

    @Test
    fun lastRowOfTheIconGridStandsInTheFirstColumn() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    AppearancePopup(
                        visible = true, title = "#work", name = null, initialColor = null, initialIcon = null,
                        onSave = { _, _, _ -> }, onDismiss = {}, hazeState = HazeState(),
                    )
                }
            }
        }
        // 31 cells in 6 columns: "No icon" opens the first row, "Star" the second, "Gift" is alone on the sixth.
        val first = left("No icon")
        assertEquals(first, left("Star"), 0.5f)
        assertEquals(first, left("Gift"), 0.5f)
    }
}
