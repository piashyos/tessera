package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.TagPickerPopup
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TagPickerPopupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun typingTagAndConfirmingAddsTag() {
        var added: String? = null
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TagPickerPopup(
                    visible = true,
                    availableTags = emptyList(),
                    onAddTag = { added = it },
                    onDismiss = {},
                    hazeState = remember { HazeState() },
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performTextInput("urgent")
        composeRule.onNodeWithTag(UiTestTags.AddTagConfirm).performClick()
        composeRule.waitForIdle()

        assertEquals("urgent", added)
    }
}
