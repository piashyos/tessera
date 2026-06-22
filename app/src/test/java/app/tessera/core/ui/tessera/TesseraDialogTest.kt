package app.tessera.core.ui.tessera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraDialogTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_text_and_invokes_confirm() {
    var confirms = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDialog(
          onDismissRequest = {},
          title = "Delete note?",
          text = "This cannot be undone.",
          confirmLabel = "Delete",
          onConfirm = { confirms += 1 },
          dismissLabel = "Cancel",
          destructive = true,
        )
      }
    }
    rule.onNodeWithText("Delete note?").assertIsDisplayed()
    rule.onNodeWithText("This cannot be undone.").assertIsDisplayed()
    rule.onNodeWithText("Cancel").assertIsDisplayed()
    rule.onNodeWithText("Delete").performClick()
    rule.waitForIdle()
    assert(confirms == 1) { "Expected 1 confirm, got $confirms" }
  }

  @Test fun no_dismiss_button_when_label_null() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDialog(
          onDismissRequest = {},
          title = "Info",
          text = "OK",
          confirmLabel = "Got it",
          onConfirm = {},
          dismissLabel = null,
        )
      }
    }
    rule.onNodeWithText("Got it").assertIsDisplayed()
    rule.onNodeWithText("Cancel").assertDoesNotExist()
  }
}
