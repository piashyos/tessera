package app.tessera.core.ui.tessera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class TesseraNoteCardTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_preview_date() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Lunch ideas",
          preview = "Tacos, sushi, ramen…",
          dateLabel = "2 days ago",
          tags = emptyList(),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("Lunch ideas").assertIsDisplayed()
    rule.onNodeWithText("Tacos, sushi, ramen…").assertIsDisplayed()
    rule.onNodeWithText("2 days ago").assertIsDisplayed()
  }

  @Test fun pinned_indicator_present_when_pinned() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Pinned",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = true,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithTag(TesseraNoteCardTags.PinnedBar, useUnmergedTree = true).assertExists()
  }

  @Test fun pinned_indicator_absent_when_not_pinned() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Not pinned",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithTag(TesseraNoteCardTags.PinnedBar).assertDoesNotExist()
  }

  @Test fun click_invokes_handler() {
    var clicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Tappable",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = false,
          onClick = { clicks += 1 },
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("Tappable").performClick()
    assert(clicks == 1) { "Expected 1 click, got $clicks" }
  }

  @Test fun tags_overflow_renders_plus_count() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Many tags",
          preview = "p",
          dateLabel = "now",
          tags = listOf("a", "b", "c", "d", "e"),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("+2").assertIsDisplayed()
  }
}
