package app.tessera.ui.editor.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorFormatBarRenderTest {
  @get:Rule val rule = createComposeRule()

  private class RecordingActions : RichBodyActions {
    val calls = mutableListOf<String>()
    override fun toggleBold() { calls += "bold" }
    override fun toggleItalic() { calls += "italic" }
    override fun toggleStrikethrough() { calls += "strike" }
    override fun toggleParagraph() {}
    override fun toggleHeading1() { calls += "h1" }
    override fun toggleHeading2() { calls += "h2" }
    override fun toggleHeading3() {}
    override fun toggleHeading4() {}
    override fun toggleHeading5() {}
    override fun toggleHeading6() {}
    override fun toggleBulletList() { calls += "bullet" }
    override fun toggleTaskList() { calls += "task" }
    override fun toggleUnderline() { calls += "underline" }
    override fun toggleOrderedList() { calls += "ordered" }
    override fun toggleQuote() { calls += "quote" }
    override fun toggleCode() { calls += "code" }
    override fun insertHorizontalRule() { calls += "divider" }
    override fun focusBody() {}
    override fun activeFormats() = emptySet<org.wordpress.aztec.AztecTextFormat>()
  }

  @Test fun renders_all_groups() {
    val actions = RecordingActions()
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        EditorFormatBar(actions = actions, activeFormats = emptySet())
      }
    }
    listOf("Bold", "Italic", "Underline", "Strikethrough", "Heading 1", "Heading 2",
      "Bullet list", "Numbered list", "Checklist item", "Quote", "Code", "Divider", "More formats")
      .forEach { rule.onNodeWithContentDescription(it).assertExists() }
  }

  @Test fun click_invokes_actions() {
    val actions = RecordingActions()
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        EditorFormatBar(actions = actions, activeFormats = emptySet())
      }
    }
    rule.onNodeWithContentDescription("Bold").performScrollTo().performClick()
    rule.onNodeWithContentDescription("Quote").performScrollTo().performClick()
    rule.onNodeWithContentDescription("Divider").performScrollTo().performClick()
    assert(actions.calls == listOf("bold", "quote", "divider")) { "got ${actions.calls}" }
  }

  @Test fun overflow_toggle_reports_to_caller() {
    val actions = RecordingActions()
    var toggled = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        EditorFormatBar(
          actions = actions,
          activeFormats = emptySet(),
          menuExpanded = false,
          onToggleMenu = { toggled++ },
        )
      }
    }
    // The ⋯ overflow is pinned outside the scrollable group, so no scroll is needed (or possible).
    // The panel itself is rendered by the caller; the button just reports the toggle.
    rule.onNodeWithContentDescription("More formats").performClick()
    assert(toggled == 1) { "expected one toggle callback, got $toggled" }
  }
}
