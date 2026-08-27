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
    fun `ordered list marker is stripped`() {
        assertEquals("first step", markdownToPreviewPlainText("1. first step"))
        assertEquals("later step", markdownToPreviewPlainText("12. later step"))
    }

    @Test
    fun `inline code backticks are stripped`() {
        assertEquals("run gradlew now", markdownToPreviewPlainText("run `gradlew` now"))
    }

    @Test
    fun `code fence lines are skipped, code text shows`() {
        val md = "```kotlin\nval x = 1\n```\nafter"
        assertEquals("val x = 1", markdownToPreviewPlainText(md))
        assertEquals("after", markdownBodyPreviewPlainText(md))
    }

    @Test
    fun `bold containing a lone asterisk is stripped`() {
        // Regression: "**220 000 * 0.2**" leaked raw markers onto note cards because the
        // bold pattern refused any inner asterisk.
        assertEquals(
            "Откладывать ежемесячно 220 000 * 0.2",
            markdownToPreviewPlainText("Откладывать ежемесячно **220 000 * 0.2**"),
        )
    }

    @Test
    fun `strikethrough containing a lone tilde is stripped`() {
        assertEquals("a ~ b", markdownToPreviewPlainText("~~a ~ b~~"))
    }

    @Test
    fun `lone asterisks between spaces are not italic`() {
        assertEquals("2 * 2 * 2", markdownToPreviewPlainText("2 * 2 * 2"))
    }

    @Test
    fun `multiple bold runs strip independently`() {
        assertEquals("a b c", markdownToPreviewPlainText("**a** b **c**"))
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

    @Test
    fun `image-only note falls back to camera emoji`() {
        assertEquals("📷", markdownToPreviewPlainText("![](tessera-img://abc-123)"))
    }

    @Test
    fun `mixed text and image previews the text with image lines dropped`() {
        val md = "Some text\n![](tessera-img://abc-123)\nmore text"
        val preview = markdownToPreviewPlainText(md)
        assertEquals("Some text", preview)
        assert(!preview.contains("tessera-img")) { "preview leaked raw image ref: $preview" }
        assert(!preview.contains("![")) { "preview leaked raw image markup: $preview" }
    }
}
