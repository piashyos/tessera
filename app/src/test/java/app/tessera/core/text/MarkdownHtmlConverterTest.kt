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
    fun `inline emphasis inside list items round-trips`() =
        roundTrip("- **bold** item\n- *italic* item")

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

    // --- HTML special-character handling ------------------------------------------------------

    @Test fun `less-than round-trips`() = roundTrip("5 < 3")
    @Test fun `greater-than round-trips`() = roundTrip("3 > 5")
    @Test fun `ampersand round-trips`() = roundTrip("Tom & Jerry")
    @Test fun `mixed specials round-trip`() = roundTrip("if a < b && c > d")

    @Test
    fun `angle brackets do not produce raw html tags`() {
        val html = converter.markdownToHtml("a <fake> tag")
        assertEquals("<p>a &lt;fake&gt; tag</p>", html)
    }

    @Test
    fun `specials inside emphasis round-trip`() = roundTrip("**a < b**")

    @Test
    fun `specials inside heading round-trip`() = roundTrip("# Q & A < B")

    @Test
    fun `specials inside task item round-trip`() = roundTrip("- [ ] fix a < b & c")

    @Test
    fun `aztec entities decode back to literals`() {
        assertEquals("a < b & c", converter.htmlToMarkdown("<p>a &lt; b &amp; c</p>"))
    }

    @Test
    fun `non-breaking space decodes to plain space`() {
        assertEquals("a b", converter.htmlToMarkdown("<p>a&nbsp;b</p>"))
    }

    @Test
    fun `double-encoded entity decodes to single entity text`() {
        // Literal text "&lt;" stored by Aztec as "&amp;lt;" must decode to "&lt;", not "<".
        assertEquals("&lt;", converter.htmlToMarkdown("<p>&amp;lt;</p>"))
    }

    // --- Aztec paragraph-separator normalization -------------------------------------------
    // Aztec's toHtml() emits plain text with "\n\n" between paragraphs (no <p> tags). Our
    // markdownToHtml uses one "\n" per paragraph, so the import must collapse the doubled
    // breaks or blank lines accumulate on every load→save cycle.

    @Test
    fun `aztec double-newline paragraphs collapse to single`() {
        assertEquals("a\nb\nc", converter.htmlToMarkdown("a\n\nb\n\nc"))
    }

    @Test
    fun `runs of blank lines collapse to a single break`() {
        assertEquals("a\nb", converter.htmlToMarkdown("a\n\n\n\n\n\nb"))
    }

    @Test
    fun `plain multiline body round-trips without growing`() {
        val md = "first\nsecond\nthird"
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        assertEquals(md, once)
        // Simulate Aztec re-doubling the breaks, then importing again — must stay stable.
        val aztecEcho = once.replace("\n", "\n\n")
        assertEquals(md, converter.htmlToMarkdown(aztecEcho))
    }

    @Test
    fun `specials are a fixed point after one html normalization pass`() {
        val md = "5 < 3 & 7 > 2"
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        val twice = converter.htmlToMarkdown(converter.markdownToHtml(once))
        assertEquals(once, twice, "converter must be idempotent on the Markdown side")
        assertEquals(md, once)
    }
}
