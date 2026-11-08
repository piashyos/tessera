package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MarkdownFormatMenuTest {
    @get:Rule val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private val fakeActions = object : RichBodyActions {
        override fun toggleBold() { calls += "bold" }
        override fun toggleItalic() { calls += "italic" }
        override fun toggleStrikethrough() { calls += "strike" }
        override fun toggleParagraph() { calls += "paragraph" }
        override fun toggleHeading1() { calls += "h1" }
        override fun toggleHeading2() { calls += "h2" }
        override fun toggleHeading3() {}
        override fun toggleHeading4() {}
        override fun toggleHeading5() {}
        override fun toggleHeading6() {}
        override fun toggleBulletList() { calls += "bullet" }
        override fun toggleTaskList() { calls += "task" }
        override fun toggleSubheading() { calls += "subheading" }
        override fun toggleQuote() { calls += "quote" }
        override fun toggleCode() { calls += "code" }
        override fun focusBody() = Unit
    }

    @Test fun `panel shows the paragraph kinds and dispatches the right callback`() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                MarkdownFormatMenu(
                    expanded = true,
                    actions = fakeActions,
                    onDismiss = { dismissed = true },
                    formatState = FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.Title)),
                )
            }
        }

        listOf("Title", "Heading", "Subheading", "Body", "Monospaced", "Quote")
            .forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }

        composeRule.onNodeWithText("Subheading").performScrollTo().performClick()
        assertEquals(listOf("subheading"), calls)
        assertTrue(dismissed)
    }

    @Test fun `the catalogue maps every entry to its paragraph kind`() {
        assertEquals(
            listOf(
                ParagraphKind.Title, ParagraphKind.Heading, ParagraphKind.Subheading,
                ParagraphKind.Body, ParagraphKind.Mono, ParagraphKind.Quote,
            ),
            formatMenuEntries(fakeActions).map { it.kind },
        )
    }
}
