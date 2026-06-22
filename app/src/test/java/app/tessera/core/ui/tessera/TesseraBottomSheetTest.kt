package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
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
class TesseraBottomSheetTest {
  @get:Rule val rule = createComposeRule()

  // Note: ModalBottomSheet rendering in Robolectric JVM tests is flaky due to
  // animation/measurement quirks. We test TesseraSheetItem standalone — the sheet
  // shell is a thin ModalBottomSheet wrapper covered by androidTest later.

  @Test fun sheet_item_renders_and_emits_click() {
    var clicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraSheetItem(
          label = "Delete",
          leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
          onClick = { clicks += 1 },
          destructive = true,
        )
      }
    }
    rule.onNodeWithText("Delete").assertIsDisplayed().performClick()
    rule.waitForIdle()
    assert(clicks == 1) { "Expected 1 click, got $clicks" }
  }

  @Test fun non_destructive_item_renders() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraSheetItem(
          label = "Archive",
          leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
          onClick = {},
        )
      }
    }
    rule.onNodeWithText("Archive").assertIsDisplayed()
  }
}
