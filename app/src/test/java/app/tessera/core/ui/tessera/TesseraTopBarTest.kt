package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
class TesseraTopBarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_and_invokes_navigation() {
    var navClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraTopBar(
          title = { Text("All notes") },
          navigationIcon = {
            TesseraTopBarIconButton(
              onClick = { navClicks += 1 },
              contentDescription = "Open drawer",
            ) { Icon(Icons.Outlined.Menu, contentDescription = null) }
          },
        )
      }
    }
    rule.onNodeWithText("All notes").assertIsDisplayed()
    rule.onNodeWithContentDescription("Open drawer").performClick()
    assert(navClicks == 1) { "Expected 1 navigation click, got $navClicks" }
  }

  @Test fun renders_actions_slot() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraTopBar(
          title = { Text("Editor") },
          actions = {
            TesseraTopBarIconButton(onClick = {}, contentDescription = "Star") {
              Icon(Icons.Outlined.Menu, contentDescription = null)
            }
            TesseraTopBarIconButton(onClick = {}, contentDescription = "More") {
              Icon(Icons.Outlined.Menu, contentDescription = null)
            }
          },
        )
      }
    }
    rule.onNodeWithContentDescription("Star").assertIsDisplayed()
    rule.onNodeWithContentDescription("More").assertIsDisplayed()
  }
}
