package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TagChipsExpandableTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `expanded tag row shows all tags`() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                TagChipsExpandable(
                    expanded = true,
                    tags = listOf("alpha", "beta", "gamma"),
                    onRemoveTag = {},
                    onAddTagClick = {},
                )
            }
        }
        composeRule.onNodeWithText("#alpha").assertIsDisplayed()
        composeRule.onNodeWithText("#beta").assertIsDisplayed()
        composeRule.onNodeWithText("#gamma").assertIsDisplayed()
    }
}
