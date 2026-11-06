package app.tessera.core.text.notedoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InlineMarkdownTest {

    private fun marks(vararg m: Triple<Int, Int, Mark>) = m.map { MarkRange(it.first, it.second, it.third) }
    private fun inl(t: String, vararg m: Triple<Int, Int, Mark>) = Inline(t, marks(*m))
    private fun p(s: String) = parseInline(s)
    private fun b(a: Int, e: Int) = Triple(a, e, Mark.Bold)
    private fun i(a: Int, e: Int) = Triple(a, e, Mark.Italic)

    // ---- delimiter runs (CommonMark flanking, §5.3) ----

    @Test fun `simple emphasis`() {
        assertEquals(inl("bar", i(0, 3)), p("*bar*"))
        assertEquals(inl("bar", b(0, 3)), p("**bar**"))
        assertEquals(inl("bar", Triple(0, 3, Mark.Strike)), p("~~bar~~"))
        assertEquals(inl("bar", Triple(0, 3, Mark.Underline)), p("<u>bar</u>"))
    }

    @Test fun `strong plus emphasis share a range`() =
        assertEquals(inl("x", b(0, 1), i(0, 1)), p("***x***"))

    @Test fun `nested emphasis`() = assertEquals(inl("a b c", i(0, 5), b(2, 3)), p("*a **b** c*"))

    @Test fun `intraword star emphasis`() = assertEquals(inl("foobarbaz", i(3, 6)), p("foo*bar*baz"))

    @Test fun `flanking rules keep stray markers literal`() {
        for (s in listOf("a*\"foo\"*", "2 * 3 * 4", "**\"x\"**a", "* a *", "~~ x ~~", "a ~~ b")) {
            assertEquals(Inline(s), p(s), s)
        }
    }

    @Test fun `tildes only in pairs of two`() {
        for (s in listOf("~x~", "~~~x~~~", "a~~~b")) assertEquals(Inline(s), p(s), s)
    }

    @Test fun `underscores are literal`() {
        for (s in listOf("_к_", "__ж__", "snake_case_name", "__init__", "_a_ __b__")) assertEquals(Inline(s), p(s), s)
    }

    @Test fun `rule of three`() {
        // CommonMark: *foo**bar* is <em>foo**bar</em>
        assertEquals(inl("foo**bar", i(0, 8)), p("*foo**bar*"))
    }

    @Test fun `unmatched delimiters stay literal inside a match`() =
        assertEquals(inl("a*b c", b(0, 5)), p("**a\\*b c**"))

    // ---- tags ----

    @Test fun `legacy tags`() {
        assertEquals(inl("x", b(0, 1)), p("<b>x</b>"))
        assertEquals(inl("x", i(0, 1)), p("<i>x</i>"))
        assertEquals(inl("x", Triple(0, 1, Mark.Strike)), p("<s>x</s>"))
        assertEquals(inl("x", b(0, 1), Triple(0, 1, Mark.Underline)), p("<u>**x**</u>"))
    }

    @Test fun `tags may hold whitespace edges that normalize trims`() =
        assertEquals(inl(" x ", b(1, 2)), p("<b> x </b>"))

    @Test fun `other tags are literal`() {
        for (s in listOf("<strong>x</strong>", "<em>x</em>", "<del>x</del>", "<strike>x</strike>", "<U>x</U>", "a <fake> tag", "5 < 3 & 7 > 2", "<b>x", "x</b>")) {
            assertEquals(Inline(s), p(s), s)
        }
    }

    @Test fun `unmatched inner tag is literal`() = assertEquals(inl("<i>x y", b(5, 6)), p("<i>x <b>y</b>"))

    // ---- code spans ----

    @Test fun `code spans`() {
        assertEquals(inl("code", Triple(0, 4, Mark.Code)), p("`code`"))
        assertEquals(inl("a`b", Triple(0, 3, Mark.Code)), p("``a`b``"))
        assertEquals(inl("`a", Triple(0, 2, Mark.Code)), p("`` `a ``"))
        assertEquals(inl("a < b && c", Triple(0, 10, Mark.Code)), p("`a < b && c`"))
    }

    @Test fun `code content is literal`() {
        assertEquals(inl("*a* \\* <b>", Triple(0, 10, Mark.Code)), p("`*a* \\* <b>`"))
    }

    @Test fun `unclosed or mismatched backtick runs are literal`() {
        for (s in listOf("`", "``a`", "a ``` b", "`a``")) assertEquals(Inline(s), p(s), s)
    }

    @Test fun `escaped backtick is literal`() = assertEquals(Inline("`a`"), p("\\`a\\`"))

    @Test fun `all-space code content is not stripped`() = assertEquals(Inline(" "), p("` `"))

    @Test fun `other marks inside code are not recognized`() =
        assertEquals(inl("x**a**y", Triple(1, 6, Mark.Code)), p("x`**a**`y"))

    // ---- escapes ----

    @Test fun `escapes before ascii punctuation only`() {
        assertEquals(Inline("*a*"), p("\\*a\\*"))
        assertEquals(Inline("\\a"), p("\\a"))
        assertEquals(Inline("\\"), p("\\"))
        assertEquals(Inline("\\"), p("\\\\"))
        assertEquals(Inline("\\я"), p("\\я"))
        assertEquals(Inline("<b>x</b>"), p("\\<b>x\\</b>"))
    }

    // ---- writing ----

    private fun ser(i: Inline) = serializeInline(i)

    @Test fun `plain text is written as is`() {
        for (s in listOf("2 * 3 * 4", "snake_case_name", "__init__", "a <fake> tag", "5 < 3 & 7 > 2", "~~~", "C:\\Users", "<strong>x</strong>", "**\"x\"**a")) {
            assertEquals(s, ser(Inline(s)), s)
        }
    }

    @Test fun `marks use delimiters`() {
        assertEquals("**a** b", ser(inl("a b", b(0, 1))))
        assertEquals("*a*", ser(inl("a", i(0, 1))))
        assertEquals("~~a~~", ser(inl("a", Triple(0, 1, Mark.Strike))))
        assertEquals("<u>a</u>", ser(inl("a", Triple(0, 1, Mark.Underline))))
        assertEquals("x`a`", ser(inl("xa", Triple(1, 2, Mark.Code))))
    }

    @Test fun `delimiters failing the re-parse switch to tags and escapes`() {
        assertEquals("<b>\"x\"</b>a", ser(inl("\"x\"a", b(0, 3))))
        assertEquals("\\*\\*x\\*\\* <b>y</b>", ser(inl("**x** y", b(6, 7))))
        assertEquals("a\\\\\\*<i>b</i>", ser(inl("a\\*b", i(3, 4))))
        assertEquals("\\<u>x\\</u> <i>y</i>", ser(inl("<u>x</u> y", i(9, 10))))
    }

    @Test fun `fallback escapes backslash star tilde backtick angle bracket`() {
        val text = "\\*~`<[ x"
        val d = inl(text, b(7, 8))
        val md = ser(d)
        assertEquals("\\\\\\*\\~\\`\\<\\[ <b>x</b>", md)
        assertEquals(d, p(md))
    }

    @Test fun `overlapping marks are written nested and read back`() {
        val d = inl("abcdef", b(0, 4), i(2, 6))
        assertEquals(normalizeInline(d), p(ser(d)))
        val d2 = inl("abc", Triple(0, 3, Mark.Underline), b(0, 1), i(1, 2), Triple(2, 3, Mark.Strike))
        assertEquals(normalizeInline(d2), p(ser(d2)))
    }

    @Test fun `marks are normalized before writing`() {
        assertEquals("**a b**", ser(inl("a b", b(0, 1), b(1, 3))))
        assertEquals("**a** b", ser(inl("a b", b(0, 2))))
    }

    @Test fun `accept callback may reject the primary form`() {
        val md = serializeInline(inl("a", b(0, 1))) { !it.contains("**") }
        assertEquals("<b>a</b>", md)
    }

    // ---- legacy emphasis (Aztec-era stored forms) ----

    @Test fun `blanks on the inner side of a pair move outside`() {
        assertEquals(inl("bold text", b(0, 4)), p("**bold **text"))
        assertEquals(inl(" bold", b(1, 5)), p("** bold**"))
        assertEquals(inl("s x", Triple(0, 1, Mark.Strike)), p("~~s ~~x"))
        assertEquals(inl("a b", i(0, 1)), p("*a *b"))
        assertEquals(inl(" foo bar", b(1, 8)), p("** foo bar**"))
        assertEquals(inl("a  foo bar", i(3, 10)), p("a * foo bar*"))
    }

    @Test fun `adjacent pairs of one kind merge`() {
        assertEquals(inl("ab", b(0, 2)), p("**a****b**"))
        assertEquals(inl("ab", Triple(0, 2, Mark.Strike)), p("~~a~~~~b~~"))
    }

    @Test fun `legacy pass leaves literals and code alone`() {
        for (s in listOf("2 * 3 * 4", "a ** b ** c", "a ~~ b ~~ c", "****", "x ****y")) assertEquals(Inline(s), p(s), s)
        assertEquals(inl("** a **", Triple(0, 7, Mark.Code)), p("`** a **`"))
        assertEquals(Inline("** a**"), p("\\*\\* a\\*\\*"))
    }

    @Test fun `star a double star b star is not touched by the legacy pass`() =
        assertEquals(inl("a**b", i(0, 4)), p("*a**b*"))
}
