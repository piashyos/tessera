package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownHtmlConverterTest {

    private val converter = MarkdownHtmlConverter()

    private fun roundTrip(markdown: String) {
        val html = converter.markdownToHtml(markdown)
        assertEquals(markdown, converter.htmlToMarkdown(html), "round-trip failed; html was: $html")
    }

    @Test fun `bold round-trips`() = roundTrip("**bold**")
    @Test fun `italic round-trips`() = roundTrip("*italic*")
    @Test fun `strikethrough round-trips`() = roundTrip("~~strike~~")
    @Test fun `h1 round-trips`() = roundTrip("# Heading one")
    @Test fun `h2 round-trips`() = roundTrip("## Heading two")
    @Test fun `bullet list round-trips`() = roundTrip("- first\n- second")
    @Test fun `unchecked task round-trips`() = roundTrip("- [ ] todo")
    @Test fun `checked task round-trips`() = roundTrip("- [x] done")

    @Test
    fun `plain paragraph round-trips`() = roundTrip("Just some text.")

    @Test
    fun `empty content maps to empty`() {
        assertEquals("", converter.markdownToHtml(""))
        assertEquals("", converter.htmlToMarkdown(""))
    }

    @Test
    fun `combined document round-trips`() {
        val md = buildString {
            appendLine("# Title")
            appendLine("Some **bold** and *italic* and ~~strike~~ text.")
            appendLine("## Subhead")
            appendLine("- bullet one")
            appendLine("- bullet two")
            appendLine("- [ ] open task")
            append("- [x] closed task")
        }
        roundTrip(md)
    }

    @Test
    fun `bold inside heading round-trips`() = roundTrip("# A **bold** title")

    @Test
    fun `del tag parses as strikethrough markdown`() {
        assertEquals("~~x~~", converter.htmlToMarkdown("<del>x</del>"))
    }

    @Test
    fun `strong tag parses as bold markdown`() {
        assertEquals("**x**", converter.htmlToMarkdown("<strong>x</strong>"))
    }

    @Test
    fun `em tag parses as italic markdown`() {
        assertEquals("*x*", converter.htmlToMarkdown("<em>x</em>"))
    }

    @Test
    fun `unsupported tag is stripped to plain text`() {
        val out = converter.htmlToMarkdown("<blockquote>quoted</blockquote>")
        assertEquals("quoted", out)
    }

    // HTML-side idempotency: feeding our generated HTML back through the round-trip must be
    // stable. This underpins the editor's echo-loop guard (Chunk 4) — the body decides whether
    // an HTML change is "external" by comparing the DERIVED MARKDOWN, so markdownToHtml must be a
    // fixed point after one normalization pass.
    @Test
    fun `markdown is a fixed point after one html normalization pass`() {
        val md = "# Title\n- [ ] open\n- [x] done\nSome **bold** text."
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        val twice = converter.htmlToMarkdown(converter.markdownToHtml(once))
        assertEquals(once, twice, "converter must be idempotent on the Markdown side")
        assertEquals(md, once)
    }
}
