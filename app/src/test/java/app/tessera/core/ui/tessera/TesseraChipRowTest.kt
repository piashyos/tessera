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
class TesseraChipRowTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_chips_and_emits_clicks() {
    var clickedKey: String? = null
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(
          chips = listOf(
            TesseraChip(key = "all", label = "All", selected = true),
            TesseraChip(key = "work", label = "#work", selected = false),
            TesseraChip(key = "urgent", label = "#urgent", selected = false),
          ),
          onChipClick = { clickedKey = it },
        )
      }
    }
    rule.onNodeWithText("All").assertIsDisplayed()
    rule.onNodeWithText("#work").assertIsDisplayed().performClick()
    assert(clickedKey == "work") { "Expected 'work', got $clickedKey" }
  }

  @Test fun empty_list_renders_nothing_and_does_not_crash() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(chips = emptyList(), onChipClick = {})
      }
    }
  }
}
