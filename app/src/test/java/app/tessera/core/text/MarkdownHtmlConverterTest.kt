package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    // --- New formats (Plan 05): ordered list, blockquote, inline code, hr, underline ---

    @Test fun `ordered list round-trips`() = roundTrip("1. first\n2. second")
    @Test fun `blockquote round-trips`() = roundTrip("> quoted")
    @Test fun `multiline blockquote round-trips`() = roundTrip("> line one\n> line two")
    @Test fun `inline code round-trips`() = roundTrip("`code`")
    @Test fun `horizontal rule round-trips`() = roundTrip("---")
    @Test fun `underline round-trips`() = roundTrip("<u>under</u>")
    @Test fun `underline with bold round-trips`() = roundTrip("<u>**bold**</u>")
    @Test fun `code preserves markdown specials`() = roundTrip("`a < b && c`")

    @Test
    fun `aztec ordered list parses to numbered markdown`() {
        assertEquals("1. a\n2. b", converter.htmlToMarkdown("<ol><li>a</li><li>b</li></ol>"))
    }

    @Test
    fun `aztec underline tag is preserved`() {
        assertEquals("<u>x</u>", converter.htmlToMarkdown("<u>x</u>"))
    }

    @Test
    fun `aztec code tag parses to backticks`() {
        assertEquals("`x`", converter.htmlToMarkdown("<code>x</code>"))
    }

    // --- Fenced code blocks (preformat) ---

    @Test fun `single-line code block round-trips`() = roundTrip("```\ncode\n```")

    @Test fun `multi-line code block round-trips`() = roundTrip("```\nline one\nline two\n```")

    @Test fun `code block with a blank line round-trips`() = roundTrip("```\nline one\n\nline two\n```")

    @Test
    fun `code block preserves markdown specials verbatim`() =
        roundTrip("```\nif (a < b && c > d) { *x* }\n```")

    @Test
    fun `markdown code block maps to pre`() {
        assertEquals("<pre>a&lt;b<br>c</pre>", converter.markdownToHtml("```\na<b\nc\n```"))
    }

    @Test
    fun `aztec pre parses to fenced block`() {
        assertEquals("```\na\nb\n```", converter.htmlToMarkdown("<pre>a<br>b</pre>"))
    }

    @Test
    fun `aztec pre with newlines parses to fenced block`() {
        assertEquals("```\na\nb\n```", converter.htmlToMarkdown("<pre>a\nb</pre>"))
    }

    @Test
    fun `code block is a fixed point after one html normalization pass`() {
        val md = "```\nval x = 1\nif (x < 2) print(x)\n```"
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        assertEquals(once, converter.htmlToMarkdown(converter.markdownToHtml(once)))
    }

    @Test
    fun `aztec self-closing hr parses to rule`() {
        assertEquals("---", converter.htmlToMarkdown("<hr />"))
    }

    @Test
    fun `blockquote parses to quote markdown`() {
        assertEquals("> quoted", converter.htmlToMarkdown("<blockquote>quoted</blockquote>"))
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
        assertEquals("a &lt;fake&gt; tag", html)
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

    // --- Aztec newline handling (verified on-device/Robolectric) ----------------------------
    // Aztec's toHtml() serializes every plain-text paragraph break as a DOUBLE bare "\n" (editable
    // "a\nb" -> "a\n\nb"; "a\n\nb" -> "a\n\n\n\nb"), i.e. bare-newline runs are 2x the line breaks
    // the user sees. htmlToMarkdown HALVES plain-text runs to recover the visible structure: one
    // typed Enter -> one line break, two Enters -> one preserved blank line. A run touching a block
    // boundary (after "</…>" / before "<…>") is structural and kept 1:1, so a blank placed right
    // after a heading/list is preserved too. Our own markdownToHtml emits "<br>" / a placeholder
    // (never bare "\n"), so its breaks are read 1:1.

    @Test
    fun `adjacent plain lines stay on consecutive lines`() {
        // Single bare newlines (e.g. from our own <br> markup) are 1:1 line breaks, no blank.
        assertEquals("a\nb\nc", converter.htmlToMarkdown("a\nb\nc"))
    }

    @Test
    fun `a typed plain break (doubled) maps to a single line break`() {
        // Aztec emits a typed plain-text break as "\n\n"; halving -> one break, NOT a stored blank.
        assertEquals("a\nb", converter.htmlToMarkdown("a\n\nb"))
    }

    @Test
    fun `a typed blank line between plain paragraphs is preserved`() {
        // Two typed Enters (one blank line) serialize as "\n\n\n\n"; halving -> "\n\n" (one blank).
        assertEquals("a\n\nb", converter.htmlToMarkdown("a\n\n\n\nb"))
    }

    @Test
    fun `one blank line after a block element is preserved`() {
        // One blank line after a block (here a task list) is intentional and kept. (A toggled
        // heading's spurious doubled break is normalized separately so it does NOT add a blank;
        // 2+ blanks after a block are capped at one for round-trip stability.)
        assertEquals(
            "- [x] done\n\nTotal: 100",
            converter.htmlToMarkdown(
                "<ul type=\"task-list\"><li><input type=\"checkbox\" checked />done</li></ul>\n\nTotal: 100",
            ),
        )
    }

    @Test
    fun `paragraph right after a list gets a blank line`() {
        // A single newline between a list close and following text is Aztec's serialization of a
        // typed list-exit (and a lazy continuation in Markdown), so it must become a blank line —
        // otherwise the blank the user typed after the list is lost.
        assertEquals(
            "- [x] done\n\nTotal: 100",
            converter.htmlToMarkdown(
                "<ul type=\"task-list\"><li><input type=\"checkbox\" checked />done</li></ul>\nTotal: 100",
            ),
        )
    }

    @Test
    fun `a blank line round-trips and stays stable`() {
        // A deliberate blank line must survive markdown -> html -> markdown unchanged, and remain a
        // fixed point on a second pass — the editor's echo-loop guard relies on this.
        val md = "first\n\nsecond\nthird"
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        assertEquals("first\n\nsecond\nthird", once)
        val twice = converter.htmlToMarkdown(converter.markdownToHtml(once))
        assertEquals(once, twice)
    }

    // --- Blank lines between list items (Aztec absorbs a typed blank as an empty <li>) -----------
    // When the user inserts a blank line between two list items / between a list and a neighbour,
    // Aztec doesn't create a paragraph — it absorbs the break as an EMPTY list item (<li></li>).
    // The converter turns a non-terminal empty item (or a trailing one when content follows the
    // list) into a real blank line. A trailing empty item of a list that ENDS the note is kept as a
    // genuine not-yet-typed item (checklist being built).

    @Test
    fun `empty bullet item between two items becomes a blank line`() {
        assertEquals("- a\n\n- b", converter.htmlToMarkdown("<ul><li>a</li><li></li><li>b</li></ul>"))
    }

    @Test
    fun `trailing empty bullet item becomes a blank when content follows the list`() {
        assertEquals(
            "- a\n\n1. b",
            converter.htmlToMarkdown("<ul><li>a</li><li></li></ul><ol><li>b</li></ol>"),
        )
    }

    @Test
    fun `trailing empty bullet item is kept when the list ends the note`() {
        assertEquals("- a\n- ", converter.htmlToMarkdown("<ul><li>a</li><li></li></ul>"))
    }

    @Test
    fun `empty task item between two tasks becomes a blank line`() {
        assertEquals(
            "- [ ] a\n\n- [x] b",
            converter.htmlToMarkdown(
                "<ul type=\"task-list\"><li><input type=\"checkbox\" />a</li>" +
                    "<li></li>" +
                    "<li><input type=\"checkbox\" checked />b</li></ul>",
            ),
        )
    }

    @Test
    fun `mixed list-to-list blanks round-trip`() {
        roundTrip("- a\n\n1. b\n\n- [ ] c")
    }

    @Test
    fun `specials are a fixed point after one html normalization pass`() {
        val md = "5 < 3 & 7 > 2"
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        val twice = converter.htmlToMarkdown(converter.markdownToHtml(once))
        assertEquals(once, twice, "converter must be idempotent on the Markdown side")
        assertEquals(md, once)
    }

    // --- tessera-img round-trip (Drive-backed images) ---------------------------------------

    @Test fun `image line round-trips`() {
        val md = "before\n![](tessera-img://abc-123)\nafter"
        val html = converter.markdownToHtml(md)
        assertTrue(html.contains("<img src=\"tessera-img://abc-123\" />"))
        assertEquals(md, converter.htmlToMarkdown(html))
    }

    @Test fun `image adjacent to list round-trips`() {
        val md = "![](tessera-img://before-1)\n- item one\n- item two\n![](tessera-img://after-1)"
        val html = converter.markdownToHtml(md)
        assertTrue(html.contains("<img src=\"tessera-img://before-1\" />"))
        assertTrue(html.contains("<img src=\"tessera-img://after-1\" />"))
        assertEquals(md, converter.htmlToMarkdown(html))
    }

    @Test fun `image adjacent to table round-trips`() {
        val md = "![](tessera-img://before-2)\n| A | B |\n| 1 | 2 |\n![](tessera-img://after-2)"
        val html = converter.markdownToHtml(md)
        assertTrue(html.contains("<img src=\"tessera-img://before-2\" />"))
        assertTrue(html.contains("<img src=\"tessera-img://after-2\" />"))
        assertEquals(md, converter.htmlToMarkdown(html))
    }

    @Test fun `image between blank lines round-trips`() {
        val md = "before\n\n![](tessera-img://mid-1)\n\nafter"
        val html = converter.markdownToHtml(md)
        assertTrue(html.contains("<img src=\"tessera-img://mid-1\" />"))
        assertEquals(md, converter.htmlToMarkdown(html))
    }

    @Test fun `aztec img tag with extra attributes parses to image markdown`() {
        assertEquals(
            "![](tessera-img://xyz-9)",
            converter.htmlToMarkdown("<img id=\"1\" class=\"foo\" src=\"tessera-img://xyz-9\" />"),
        )
    }

    @Test fun `bare object-replacement char is never stored`() {
        // Simulates an image span that serialized WITHOUT its src (the destructive-corruption path):
        // Aztec leaves the raw U+FFFC placeholder. It must not survive into stored markdown.
        val md = converter.htmlToMarkdown("before￼after")
        assertTrue(!md.contains('￼'), "stored markdown must not contain U+FFFC; got: $md")
        assertEquals("beforeafter", md)
    }
}
