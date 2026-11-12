package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import dev.chrisbanes.haze.HazeState
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

    private fun setMenu(
        formatState: FormatState = FormatState.Empty,
        expanded: Boolean = true,
        onDismiss: () -> Unit = {},
    ) = composeRule.setContent {
        TesseraTheme(darkTheme = false, dynamicColor = false) {
            MarkdownFormatMenu(
                expanded = expanded,
                actions = fakeActions,
                onDismiss = onDismiss,
                hazeState = HazeState(),
                bottomInset = 80.dp,
                formatState = formatState,
            )
        }
    }

    @Test fun `the sheet shows its title and the six paragraph kinds`() {
        setMenu()
        composeRule.onNodeWithText("Paragraph style").assertIsDisplayed()
        listOf("Title", "Heading", "Subheading", "Body", "Monospaced", "Quote")
            .forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test fun `only the row of the caret paragraph is selected`() {
        setMenu(FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.Title)))
        composeRule.onNodeWithText("Title").assertIsSelected()
        listOf("Heading", "Subheading", "Body", "Monospaced", "Quote")
            .forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsNotSelected() }
    }

    @Test fun `a tap on a row runs its action and closes the sheet`() {
        var dismissed = false
        setMenu(onDismiss = { dismissed = true })
        composeRule.onNodeWithText("Subheading").performScrollTo().performClick()
        assertEquals(listOf("subheading"), calls)
        assertTrue(dismissed)
    }

    @Test fun `a collapsed menu shows nothing`() {
        setMenu(expanded = false)
        composeRule.onNodeWithText("Paragraph style").assertDoesNotExist()
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
