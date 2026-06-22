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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorMetaRenderTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_breadcrumb_and_date() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        EditorMeta(
          folderBreadcrumb = listOf("Work", "Specs"),
          relativeDate = "Today",
        )
      }
    }
    rule.onNodeWithText("Work › Specs").assertIsDisplayed()
    rule.onNodeWithText("Today").assertIsDisplayed()
  }

  @Test fun single_segment_breadcrumb() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        EditorMeta(folderBreadcrumb = listOf("All Notes"), relativeDate = "15 Jun")
      }
    }
    rule.onNodeWithText("All Notes").assertIsDisplayed()
    rule.onNodeWithText("15 Jun").assertIsDisplayed()
  }
}
