package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four rows of the title block's table: path + date, path alone, date alone, nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorMetaTest {
  @get:Rule val rule = createComposeRule()

  private fun show(path: List<String>, date: String) = rule.setContent {
    TesseraTheme(darkTheme = false, dynamicColor = false) { EditorMeta(folderBreadcrumb = path, relativeDate = date) }
  }

  @Test fun path_and_date() {
    show(listOf("Work", "Projects"), "12 Jun")
    rule.onNodeWithText("Work › Projects").assertIsDisplayed()
    rule.onNodeWithText("12 Jun").assertIsDisplayed()
  }

  @Test fun path_without_date() {
    show(listOf("Work", "Projects"), "")
    rule.onNodeWithText("Work › Projects").assertIsDisplayed()
  }

  @Test fun date_without_folder() {
    show(emptyList(), "12 Jun")
    rule.onNodeWithText("12 Jun").assertIsDisplayed()
  }

  @Test fun nothing_is_drawn_without_folder_and_date() {
    show(emptyList(), "")
    rule.onRoot().assertHeightIsEqualTo(0.dp)
  }
}
