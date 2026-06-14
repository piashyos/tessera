package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraTextFieldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsPlaceholderWhenEmpty() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraTextField(value = "", onValueChange = {}, placeholder = "Search notes")
            }
        }
        composeRule.onNodeWithText("Search notes").assertIsDisplayed()
    }

    @Test
    fun typingInvokesOnValueChange() {
        var captured = ""
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                var text by remember { mutableStateOf("") }
                TesseraTextField(
                    value = text,
                    onValueChange = { text = it; captured = it },
                    placeholder = "hint",
                    modifier = Modifier.testTag(UiTestTags.ReusableTextField),
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performTextInput("hello")
        assertEquals("hello", captured)
    }

    @Test
    fun imeActionInvokesCallback() {
        var fired = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraTextField(
                    value = "x",
                    onValueChange = {},
                    placeholder = "hint",
                    imeAction = ImeAction.Done,
                    onImeAction = { fired = true },
                    modifier = Modifier.testTag(UiTestTags.ReusableTextField),
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performImeAction()
        assertTrue(fired)
    }
}
