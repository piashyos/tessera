package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class TesseraBottomActionBarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun shows_placeholder_when_query_empty_and_emits_click() {
    var searchClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = { searchClicks += 1 },
          onFabClick = {},
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
          searchPlaceholder = "Search notes",
        )
      }
    }
    rule.onNodeWithText("Search notes").assertIsDisplayed().performClick()
    assert(searchClicks == 1)
  }

  @Test fun shows_query_text_when_not_empty() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "android",
          onSearchClick = {},
          onFabClick = {},
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithText("android").assertIsDisplayed()
  }

  @Test fun fab_hidden_when_showFab_false() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = {},
          onFabClick = {},
          showFab = false,
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithContentDescription("Create note").assertDoesNotExist()
  }

  @Test fun fab_click_emits() {
    var fabClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = {},
          onFabClick = { fabClicks += 1 },
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithContentDescription("Create note").performClick()
    assert(fabClicks == 1)
  }
}
