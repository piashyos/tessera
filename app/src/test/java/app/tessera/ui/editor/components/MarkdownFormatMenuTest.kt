package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecTextFormat

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MarkdownFormatMenuTest {
    @get:Rule val composeRule = createComposeRule()

    private val recordedToggles = mutableListOf<AztecTextFormat>()

    private val fakeActions = object : RichBodyActions {
        override fun toggleBold() { recordedToggles += AztecTextFormat.FORMAT_BOLD }
        override fun toggleItalic() { recordedToggles += AztecTextFormat.FORMAT_ITALIC }
        override fun toggleStrikethrough() { recordedToggles += AztecTextFormat.FORMAT_STRIKETHROUGH }
        override fun toggleParagraph() { recordedToggles += AztecTextFormat.FORMAT_PARAGRAPH }
        override fun toggleHeading1() { recordedToggles += AztecTextFormat.FORMAT_HEADING_1 }
        override fun toggleHeading2() { recordedToggles += AztecTextFormat.FORMAT_HEADING_2 }
        override fun toggleHeading3() { recordedToggles += AztecTextFormat.FORMAT_HEADING_3 }
        override fun toggleHeading4() { recordedToggles += AztecTextFormat.FORMAT_HEADING_4 }
        override fun toggleHeading5() { recordedToggles += AztecTextFormat.FORMAT_HEADING_5 }
        override fun toggleHeading6() { recordedToggles += AztecTextFormat.FORMAT_HEADING_6 }
        override fun toggleBulletList() { recordedToggles += AztecTextFormat.FORMAT_UNORDERED_LIST }
        override fun toggleTaskList() { recordedToggles += AztecTextFormat.FORMAT_TASK_LIST }
        override fun focusBody() = Unit
        override fun activeFormats(): Set<AztecTextFormat> = setOf(AztecTextFormat.FORMAT_BOLD)
    }

    @Test fun `menu shows only paragraph and heading entries and dispatches the right callback`() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                MarkdownFormatMenu(
                    visible = true,
                    activeFormats = setOf(AztecTextFormat.FORMAT_HEADING_1),
                    actions = fakeActions,
                    onDismiss = { dismissed = true },
                    hazeState = HazeState(),
                )
            }
        }

        composeRule.onNodeWithText("Paragraph").assertIsDisplayed()
        composeRule.onNodeWithText("Heading 1").assertIsDisplayed()
        composeRule.onNodeWithText("Heading 6").assertIsDisplayed()

        composeRule.onNodeWithText("Heading 3").performClick()
        assert(AztecTextFormat.FORMAT_HEADING_3 in recordedToggles)
        assert(dismissed)
    }
}
