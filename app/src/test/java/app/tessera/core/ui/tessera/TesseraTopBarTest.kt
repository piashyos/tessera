package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private fun centered(title: String) {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraTopBar(
          modifier = Modifier.testTag("bar"),
          centerTitle = true,
          title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("title")) },
          navigationIcon = {
            TesseraTopBarIconButton(onClick = {}, contentDescription = "Back") { Icon(Icons.Outlined.Menu, contentDescription = null) }
          },
          actions = {
            TesseraTopBarIconButton(onClick = {}, contentDescription = "Pin") { Icon(Icons.Outlined.Menu, contentDescription = null) }
            TesseraTopBarIconButton(onClick = {}, contentDescription = "More") { Icon(Icons.Outlined.Menu, contentDescription = null) }
          },
        )
      }
    }
  }

  @Test fun centered_title_sits_on_the_center_of_the_bar_with_one_button_left_and_two_right() {
    centered("Work")
    val bar = rule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
    val title = rule.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot
    assertEquals(bar.center.x, title.center.x, 1f)
  }

  @Test fun long_centered_title_stays_clear_of_the_buttons() {
    centered("A very long folder path that does not fit between the buttons at all — ".repeat(4))
    val title = rule.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot
    val back = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
    val pin = rule.onNodeWithContentDescription("Pin").fetchSemanticsNode().boundsInRoot
    assertTrue("title ${title.right} reaches the first action ${pin.left}", title.right <= pin.left)
    assertTrue("title ${title.left} reaches the navigation ${back.right}", title.left >= back.right)
  }
}
