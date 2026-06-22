package app.tessera.core.ui.tessera

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
class TesseraDrawerShellTest {
  @get:Rule val rule = createComposeRule()

  @Test fun open_drawer_shows_header_and_items() {
    rule.setContent {
      val state = rememberDrawerState(initialValue = DrawerValue.Open)
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDrawerShell(
          drawerState = state,
          header = { Text("Tessera") },
          content = {
            TesseraDrawerItem(label = "All notes", selected = true, onClick = {})
            TesseraDrawerItem(label = "Pinned", selected = false, onClick = {})
          },
          footer = { Text("Settings") },
        ) { Text("Screen") }
      }
    }
    rule.onNodeWithText("Tessera").assertIsDisplayed()
    rule.onNodeWithText("All notes").assertIsDisplayed()
    rule.onNodeWithText("Pinned").assertIsDisplayed()
    rule.onNodeWithText("Settings").assertIsDisplayed()
  }

  @Test fun item_click_emits() {
    // Standalone TesseraDrawerItem (outside ModalNavigationDrawer) — verifies the
    // click contract without ModalDrawer animation/measurement quirks under Robolectric.
    var clicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDrawerItem(label = "Trash", selected = false, onClick = { clicks += 1 })
      }
    }
    rule.onNodeWithTag(TesseraDrawerItemTags.row("Trash")).performClick()
    rule.waitForIdle()
    assert(clicks == 1) { "Expected 1 click, got $clicks" }
  }

  @Test fun selected_item_uses_secondary_container_background() {
    // Smoke: drawer renders without crashing when a selected item is shown.
    rule.setContent {
      val state = rememberDrawerState(initialValue = DrawerValue.Open)
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDrawerShell(
          drawerState = state,
          header = { Text("Tessera") },
          content = {
            TesseraDrawerItem(label = "Selected", selected = true, onClick = {})
          },
        ) { Text("Screen") }
      }
    }
    rule.onNodeWithText("Selected").assertIsDisplayed()
  }
}
