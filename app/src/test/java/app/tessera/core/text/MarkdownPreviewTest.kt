package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownPreviewTest {

    @Test
    fun `empty markdown maps to empty`() {
        assertEquals("", markdownToPreviewPlainText(""))
    }

    @Test
    fun `plain paragraph survives unchanged`() {
        assertEquals("Just some text.", markdownToPreviewPlainText("Just some text."))
    }

    @Test
    fun `h1 marker is stripped`() {
        assertEquals("Title", markdownToPreviewPlainText("# Title"))
    }

    @Test
    fun `h2 marker is stripped`() {
        assertEquals("Subtitle", markdownToPreviewPlainText("## Subtitle"))
    }

    @Test
    fun `bullet marker is stripped`() {
        assertEquals("item one", markdownToPreviewPlainText("- item one"))
    }

    @Test
    fun `unchecked task marker is stripped`() {
        assertEquals("buy milk", markdownToPreviewPlainText("- [ ] buy milk"))
    }

    @Test
    fun `checked task marker is stripped`() {
        assertEquals("call mom", markdownToPreviewPlainText("- [x] call mom"))
    }

    @Test
    fun `bold inline markers are stripped`() {
        assertEquals("important text", markdownToPreviewPlainText("**important** text"))
    }

    @Test
    fun `italic inline markers are stripped`() {
        assertEquals("hint text", markdownToPreviewPlainText("*hint* text"))
    }

    @Test
    fun `strikethrough inline markers are stripped`() {
        assertEquals("old text", markdownToPreviewPlainText("~~old~~ text"))
    }

    @Test
    fun `html tags are stripped`() {
        assertEquals("bold text", markdownToPreviewPlainText("<b>bold</b> text"))
    }

    @Test
    fun `first non-blank line wins, leading blanks skipped`() {
        val md = "\n\n# First heading\nignored second line"
        assertEquals("First heading", markdownToPreviewPlainText(md))
    }

    @Test
    fun `whitespace inside the line is preserved as a single space`() {
        assertEquals("a b c", markdownToPreviewPlainText("a   b\tc"))
    }

    @Test
    fun `combined markers strip cleanly`() {
        assertEquals("bold heading", markdownToPreviewPlainText("# **bold** heading"))
    }

    @Test
    fun `table markup flattens to cell text in title and body previews`() {
        val md = "| Task | Owner |\n| --- | --- |\n| Design | Ann |\nnotes below"
        assertEquals("Task Owner", markdownToPreviewPlainText(md))
        // Body preview: the separator row is dropped, the next content row is cell text.
        assertEquals("Design Ann", markdownBodyPreviewPlainText(md))
    }

    @Test
    fun `table sizing metadata comment is invisible to previews`() {
        val md = "<!-- tessera:cols=115,264;rows=44,44 -->\n| Task | Owner |\n| --- | --- |\n| Design | Ann |"
        assertEquals("Task Owner", markdownToPreviewPlainText(md))
        assertEquals("Design Ann", markdownBodyPreviewPlainText(md))
    }
}
