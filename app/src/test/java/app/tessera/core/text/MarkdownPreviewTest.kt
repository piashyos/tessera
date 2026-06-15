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
}
