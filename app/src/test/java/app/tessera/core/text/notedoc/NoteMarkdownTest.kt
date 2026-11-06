package app.tessera.core.text.notedoc

import app.tessera.core.text.markdownblocks.TableGrid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteMarkdownTest {

    private fun parse(md: String) = NoteMarkdown.parse(md)
    private fun ser(doc: NoteDoc) = NoteMarkdown.serialize(doc)

    private fun body(t: String) = NoteBlock.Text(ParagraphKind.Body, Inline(t))
    private fun text(k: ParagraphKind, t: String) = NoteBlock.Text(k, Inline(t))
    private fun item(type: ListType, level: Int, t: String, checked: Boolean = false) =
        NoteBlock.Text(ParagraphKind.ListItem(type, level, checked), Inline(t))
    private fun doc(vararg b: NoteBlock) = NoteDoc(b.toList())

    /** parse(md) == expected and the canonical record of it is [canonical] (md itself by default). */
    private fun check(md: String, expected: NoteDoc, canonical: String = md) {
        assertEquals(expected, parse(md), "parse of <$md>")
        assertEquals(canonical, ser(expected), "serialize of $expected")
        assertEquals(expected, parse(canonical), "parse of canonical <$canonical>")
    }

    private fun literal(md: String) = assertEquals(md, ser(parse(md)), "literal must round-trip unchanged: <$md>")

    // ---- §5.1 one line = one paragraph ----

    @Test fun `empty string is one empty body`() = check("", doc(body("")))
    @Test fun `single newline is two empty bodies`() = check("\n", doc(body(""), body("")))
    @Test fun `trailing empty paragraphs are kept`() = check("a\n\n", doc(body("a"), body(""), body("")))
    @Test fun `leading and middle empty paragraphs are kept`() =
        check("\n\na\n\nb", doc(body(""), body(""), body("a"), body(""), body("b")))
    @Test fun `empty document normalizes to one empty body`() = assertEquals("", ser(NoteDoc(emptyList())))

    // ---- §5.2 blocks ----

    @Test fun `title heading subheading`() =
        check("# T\n## H\n### S", doc(text(ParagraphKind.Title, "T"), text(ParagraphKind.Heading, "H"), text(ParagraphKind.Subheading, "S")))

    @Test fun `h4 to h6 read as subheading and are written as h3`() {
        for (n in 4..6) check("${"#".repeat(n)} x", doc(text(ParagraphKind.Subheading, "x")), "### x")
    }

    @Test fun `seven hashes and hash without space are body`() {
        check("####### x", doc(body("####### x")))
        check("#x", doc(body("#x")))
        check("#", doc(body("#")))
    }

    @Test fun `only one space after the marker is eaten`() {
        check("#  x", doc(text(ParagraphKind.Title, " x")))
        check("# x #", doc(text(ParagraphKind.Title, "x #")))
        check("-  x", doc(item(ListType.Bullet, 0, " x")))
        check("> ", doc(text(ParagraphKind.Quote, "")))
        check("# ", doc(text(ParagraphKind.Title, "")))
    }

    @Test fun `leading and trailing spaces of body are its text`() {
        check("  text  ", doc(body("  text  ")))
        check("\ttext", doc(body("\ttext")))
    }

    // fence

    @Test fun `fence gives mono paragraphs`() =
        check("```\na\nb\n```", doc(text(ParagraphKind.Mono, "a"), text(ParagraphKind.Mono, "b")))

    @Test fun `blank line inside fence is an empty mono`() =
        check("```\na\n\nb\n```", doc(text(ParagraphKind.Mono, "a"), text(ParagraphKind.Mono, ""), text(ParagraphKind.Mono, "b")))

    @Test fun `fence language is dropped`() =
        check("```kotlin\nx\n```", doc(text(ParagraphKind.Mono, "x")), "```\nx\n```")

    @Test fun `unclosed fence runs to the end`() =
        check("```\na\nb", doc(text(ParagraphKind.Mono, "a"), text(ParagraphKind.Mono, "b")), "```\na\nb\n```")

    @Test fun `closing fence may be longer`() =
        check("```\na\n`````", doc(text(ParagraphKind.Mono, "a")), "```\na\n```")

    @Test fun `mono line starting with backticks lengthens the fence`() {
        check("````\n```\n````", doc(text(ParagraphKind.Mono, "```")))
        check("`````\n```\n````\n`````", doc(text(ParagraphKind.Mono, "```"), text(ParagraphKind.Mono, "````")))
    }

    @Test fun `mono keeps escapes and markup literal`() {
        check("```\n\\*a* **b** `c`\n```", doc(text(ParagraphKind.Mono, "\\*a* **b** `c`")))
    }

    @Test fun `tilde fence is literal`() = check("~~~\na\n~~~", doc(body("~~~"), body("a"), body("~~~")))

    @Test fun `fence text after the document stays after it`() =
        check("```\na\n```\nb", doc(text(ParagraphKind.Mono, "a"), body("b")))

    @Test fun `backtick line with a closing backtick later is not a fence`() {
        val d = parse("```a``b```")
        assertEquals(listOf(NoteBlock.Text(ParagraphKind.Body, Inline("a``b", listOf(MarkRange(0, 4, Mark.Code))))), d.blocks)
        assertEquals("```a``b```", ser(d))
    }

    // lists

    @Test fun `list markers`() {
        check("- a", doc(item(ListType.Bullet, 0, "a")))
        check("* a", doc(item(ListType.Bullet, 0, "a")), "- a")
        check("+ a", doc(item(ListType.Dash, 0, "a")))
        check("1. a", doc(item(ListType.Number, 0, "a")))
        check("1) a", doc(item(ListType.Number, 0, "a")), "1. a")
    }

    @Test fun `check items`() {
        check("- [ ] a", doc(item(ListType.Check, 0, "a")))
        check("- [x] a", doc(item(ListType.Check, 0, "a", true)))
        check("* [ ] a", doc(item(ListType.Check, 0, "a")), "- [ ] a")
        check("+ [ ] a", doc(item(ListType.Check, 0, "a")), "- [ ] a")
        check("- [X] a", doc(item(ListType.Check, 0, "a", true)), "- [x] a")
        check("- [ ] ", doc(item(ListType.Check, 0, "")))
    }

    @Test fun `bracket without trailing space is bullet text`() {
        check("- [ ]", doc(item(ListType.Bullet, 0, "[ ]")))
        check("- [x]", doc(item(ListType.Bullet, 0, "[x]")))
    }

    @Test fun `empty trailing item`() = check("- a\n- ", doc(item(ListType.Bullet, 0, "a"), item(ListType.Bullet, 0, "")))

    @Test fun `item text starting with a checkbox is escaped`() {
        check("- \\[ ] x", doc(item(ListType.Bullet, 0, "[ ] x")))
        check("1. \\[x] x", doc(item(ListType.Number, 0, "[x] x")))
    }

    @Test fun `nesting by four spaces tab and ceil`() {
        check("    - a", doc(item(ListType.Bullet, 1, "a")))
        check("\t- a", doc(item(ListType.Bullet, 1, "a")), "    - a")
        check("  - a", doc(item(ListType.Bullet, 1, "a")), "    - a")
        check("        - a", doc(item(ListType.Bullet, 2, "a")))
        check("\t\t- a", doc(item(ListType.Bullet, 2, "a")), "        - a")
        check("     - a", doc(item(ListType.Bullet, 2, "a")), "        - a")
        check(" ".repeat(40) + "- a", doc(item(ListType.Bullet, 4, "a")), " ".repeat(16) + "- a")
    }

    @Test fun `nested checklist item keeps its marker`() =
        check("    - [x] a", doc(item(ListType.Check, 1, "a", true)))

    @Test fun `numbering is recomputed`() {
        check("1. a\n1. b\n1. c", doc(item(ListType.Number, 0, "a"), item(ListType.Number, 0, "b"), item(ListType.Number, 0, "c")), "1. a\n2. b\n3. c")
        check("5. a\n9) b", doc(item(ListType.Number, 0, "a"), item(ListType.Number, 0, "b")), "1. a\n2. b")
    }

    @Test fun `numbering with nesting`() {
        val d = doc(
            item(ListType.Number, 0, "a"), item(ListType.Number, 1, "a1"), item(ListType.Number, 1, "a2"),
            item(ListType.Number, 0, "b"), item(ListType.Number, 1, "b1"),
        )
        assertEquals("1. a\n    1. a1\n    2. a2\n2. b\n    1. b1", ser(d))
    }

    @Test fun `deeper item does not reset the outer counter`() {
        val d = doc(item(ListType.Number, 0, "a"), item(ListType.Bullet, 1, "x"), item(ListType.Number, 0, "b"))
        assertEquals("1. a\n    - x\n2. b", ser(d))
    }

    @Test fun `non-list paragraph other type and shallower item reset numbering`() {
        assertEquals("1. a\nx\n1. b", ser(doc(item(ListType.Number, 0, "a"), body("x"), item(ListType.Number, 0, "b"))))
        assertEquals("1. a\n- x\n1. b", ser(doc(item(ListType.Number, 0, "a"), item(ListType.Bullet, 0, "x"), item(ListType.Number, 0, "b"))))
        assertEquals("1. a\n2. b\n    1. c\n    2. d\n3. e\n    1. f", ser(doc(
            item(ListType.Number, 0, "a"), item(ListType.Number, 0, "b"), item(ListType.Number, 1, "c"),
            item(ListType.Number, 1, "d"), item(ListType.Number, 0, "e"), item(ListType.Number, 1, "f"),
        )))
        assertEquals("1. a\n\n1. b", ser(doc(item(ListType.Number, 0, "a"), body(""), item(ListType.Number, 0, "b"))))
        assertEquals("1. a\n- [ ] c\n1. b", ser(doc(item(ListType.Number, 0, "a"), item(ListType.Check, 0, "c"), item(ListType.Number, 0, "b"))))
    }

    @Test fun `only inline inside quote heading and item`() {
        check("> - x", doc(text(ParagraphKind.Quote, "- x")))
        check("- - x", doc(item(ListType.Bullet, 0, "- x")))
        check("# # x", doc(text(ParagraphKind.Title, "# x")))
        check("> > x", doc(text(ParagraphKind.Quote, "> x")))
    }

    // quote

    @Test fun `quote needs a space`() {
        check("> a", doc(text(ParagraphKind.Quote, "a")))
        check(">текст", doc(body(">текст")))
    }

    // divider

    @Test fun `divider forms`() {
        check("---", doc(NoteBlock.Divider))
        check("***", doc(NoteBlock.Divider), "---")
        check("___", doc(NoteBlock.Divider), "---")
        check("-----", doc(NoteBlock.Divider), "---")
    }

    // image / table

    @Test fun `image line`() {
        check("![](tessera-img://abc-1?w=50&rot=90)", doc(NoteBlock.Image("tessera-img://abc-1?w=50&rot=90")))
        check("a\n![](tessera-img://x)\nb", doc(body("a"), NoteBlock.Image("tessera-img://x"), body("b")))
    }

    @Test fun `image with an invalid source is body text`() {
        assertEquals("\\![](http://x)", ser(doc(NoteBlock.Image("http://x"))))
        assertEquals(doc(body("![](http://x)")), parse("![](http://x)"))
    }

    private val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2")))

    @Test fun `table round trip`() =
        check("| A | B |\n|---|---|\n| 1 | 2 |", doc(NoteBlock.Table(grid)))

    @Test fun `table with sizing comment`() {
        val sized = grid.copy(colWidths = listOf(140f, 80f), rowHeights = listOf(44f, 30f))
        check("<!-- tessera:cols=140,80;rows=44,30 -->\n| A | B |\n|---|---|\n| 1 | 2 |", doc(NoteBlock.Table(sized)))
    }

    @Test fun `table sizes with a wrong count are dropped`() {
        val d = parse("<!-- tessera:cols=140,80,10 -->\n| A | B |\n|---|---|\n| 1 | 2 |")
        assertEquals(doc(NoteBlock.Table(grid)), d)
    }

    @Test fun `table between paragraphs`() =
        check("a\n| A | B |\n|---|---|\n| 1 | 2 |\nb", doc(body("a"), NoteBlock.Table(grid), body("b")))

    @Test fun `pipe line without a separator is body and is escaped`() {
        check("| A | B |\n| 1 | 2 |", doc(body("| A | B |"), body("| 1 | 2 |")), "\\| A | B |\n\\| 1 | 2 |")
    }

    @Test fun `body after a table starting with a pipe stays body`() {
        val d = doc(NoteBlock.Table(grid), body("| x"))
        val md = ser(d)
        assertEquals("| A | B |\n|---|---|\n| 1 | 2 |\n\\| x", md)
        assertEquals(d, parse(md))
    }

    @Test fun `escaped pipe also after leading whitespace`() {
        val d = doc(body("a"), body("  | x"))
        assertEquals("a\n  \\| x", ser(d))
        assertEquals(d, parse(ser(d)))
    }

    @Test fun `two tables in a row get an empty body between`() {
        val d = doc(NoteBlock.Table(grid), NoteBlock.Table(grid))
        val md = ser(d)
        assertEquals(doc(NoteBlock.Table(grid), body(""), NoteBlock.Table(grid)), parse(md))
        assertEquals(md, ser(parse(md)))
    }

    @Test fun `table cell pipe is escaped`() {
        val g = TableGrid(listOf("a|b"), listOf(listOf("x")))
        assertEquals(doc(NoteBlock.Table(g)), parse(ser(doc(NoteBlock.Table(g)))))
    }

    // ---- block escaping §5.3 ----

    private fun escaped(text: String, expectedMd: String) {
        val d = doc(body(text))
        assertEquals(expectedMd, ser(d), "serialize body <$text>")
        assertEquals(d, parse(expectedMd), "parse <$expectedMd>")
    }

    @Test fun `body text that looks like a block is escaped on punctuation`() {
        escaped("# x", "\\# x")
        escaped("###### x", "\\###### x")
        escaped("- text", "\\- text")
        escaped("+ x", "\\+ x")
        escaped("* x", "\\* x")
        escaped("2024. год", "2024\\. год")
        escaped("3) x", "3\\) x")
        escaped("> x", "\\> x")
        escaped("```x", "\\```x")
        escaped("![](tessera-img://a)", "\\![](tessera-img://a)")
        escaped("<!-- x -->", "\\<!-- x -->")
        escaped("| x", "\\| x")
        escaped("---", "\\---")
        escaped("***", "\\***")
        escaped("___", "\\___")
        escaped("- - -", "\\- - -")
        escaped("  - x", "  \\- x")
    }

    @Test fun `text that only resembles a block is not escaped`() {
        for (s in listOf("#x", "-x", "2024.x", ">x", "``x", "a - b", "1.5", "--", "|", "x | y")) {
            if (s == "|") escaped(s, "\\|") else escaped(s, s)
        }
    }

    // ---- terse reading ----

    @Test fun `html tags are read`() {
        val bold = NoteBlock.Text(ParagraphKind.Body, Inline("x", listOf(MarkRange(0, 1, Mark.Bold))))
        check("<b>x</b>", doc(bold), "**x**")
        check("<i>x</i>", doc(NoteBlock.Text(ParagraphKind.Body, Inline("x", listOf(MarkRange(0, 1, Mark.Italic))))), "*x*")
        check("<s>x</s>", doc(NoteBlock.Text(ParagraphKind.Body, Inline("x", listOf(MarkRange(0, 1, Mark.Strike))))), "~~x~~")
        check("<u>x</u>", doc(NoteBlock.Text(ParagraphKind.Body, Inline("x", listOf(MarkRange(0, 1, Mark.Underline))))))
    }

    @Test fun `other tags and unbalanced markup stay literal`() {
        for (s in listOf(
            "<strong>x</strong>", "<em>x</em>", "<del>x</del>", "<strike>x</strike>", "~~~x~~~", "_k_", "__ж__",
            "snake_case_name", "__init__", "a <fake> tag", "5 < 3 & 7 > 2", "2 * 3 * 4", "**\"x\"**a", "**x", "x**",
            "<b>x", "</b>", "a ~~ b", "if (a < b && c > d) { x }",
        )) literal(s)
        assertEquals(doc(body("<strong>x</strong>")), parse("<strong>x</strong>"))
        assertEquals(doc(body("~~~x~~~")), parse("~~~x~~~"))
        assertEquals(doc(body("__init__")), parse("__init__"))
    }

    @Test fun `literal markers that would pair up force the fallback form`() {
        val d = doc(NoteBlock.Text(ParagraphKind.Body, Inline("**x** y", listOf(MarkRange(6, 7, Mark.Bold)))))
        val md = ser(d)
        assertEquals("\\*\\*x\\*\\* <b>y</b>", md)
        assertEquals(d, parse(md))
    }

    @Test fun `bold over a quoted word followed by a letter needs the fallback`() {
        val d = doc(NoteBlock.Text(ParagraphKind.Body, Inline("\"x\"a", listOf(MarkRange(0, 3, Mark.Bold)))))
        assertEquals("<b>\"x\"</b>a", ser(d))
        assertEquals(d, parse(ser(d)))
    }

    @Test fun `backslash before punctuation is an escape and before a letter is literal`() {
        assertEquals(doc(body("*a*")), parse("\\*a\\*"))
        assertEquals(doc(body("C:\\Users")), parse("C:\\Users"))
        assertEquals(doc(body("a\\b")), parse("a\\\\b"))
        assertEquals(doc(body("end\\")), parse("end\\"))
        literal("C:\\Users")
    }

    @Test fun `code span keeps backslashes`() {
        val d = parse("`\\d+\\.`")
        assertEquals(doc(NoteBlock.Text(ParagraphKind.Body, Inline("\\d+\\.", listOf(MarkRange(0, 5, Mark.Code))))), d)
        assertEquals("`\\d+\\.`", ser(d))
    }

    @Test fun `code spans with backticks`() {
        fun code(content: String) = doc(NoteBlock.Text(ParagraphKind.Body, Inline(content, listOf(MarkRange(0, content.length, Mark.Code)))))
        assertEquals("``a`b``", ser(code("a`b")))
        assertEquals("```a``b```", ser(code("a``b")))
        assertEquals("`` `a ``", ser(code("`a")))
        assertEquals("`` a` ``", ser(code("a`")))
        assertEquals("`` ` ``", ser(code("`")))
        for (c in listOf("a`b", "a``b", "`a", "a`", "`", "``", "`a`", "a ` b")) assertEquals(code(c), parse(ser(code(c))), c)
    }

    @Test fun `code span with a single padding space is stripped`() {
        assertEquals(doc(NoteBlock.Text(ParagraphKind.Body, Inline("a", listOf(MarkRange(0, 1, Mark.Code))))), parse("` a `"))
    }

    @Test fun `literal backtick text`() {
        literal("`")
        literal("a `b")
        val d = doc(body("`a`"))
        assertEquals("\\`a\\`", ser(d))
        assertEquals(d, parse(ser(d)))
    }

    @Test fun `markup spans around code are nested and read back`() {
        val d = doc(NoteBlock.Text(ParagraphKind.Body, Inline("ab", listOf(MarkRange(0, 2, Mark.Bold), MarkRange(1, 2, Mark.Code)))))
        assertEquals("**a`b`**", ser(d))
        assertEquals(d, parse(ser(d)))
    }

    @Test fun `nesting order is underline strike bold italic code`() {
        val all = listOf(Mark.Bold, Mark.Italic, Mark.Underline, Mark.Strike).map { MarkRange(0, 1, it) }
        val d = doc(NoteBlock.Text(ParagraphKind.Body, Inline("x", all)))
        assertEquals("<u>~~***x***~~</u>", ser(d))
        assertEquals(d, parse(ser(d)))
    }

    // ---- legacy tolerance ----

    @Test fun `legacy forms are tolerated`() {
        assertEquals(doc(item(ListType.Number, 0, "a"), item(ListType.Number, 0, "b"), item(ListType.Number, 0, "c")), parse("1. a\n1. b\n1. c"))
        assertEquals(doc(item(ListType.Bullet, 0, "x")), parse("* x"))
        assertEquals("- a\n\n1. b\n\n- [ ] c", ser(parse("- a\n\n1. b\n\n- [ ] c")))
    }

    @Test fun `parse never yields an empty document`() {
        assertEquals(1, parse("").blocks.size)
        assertEquals(1, parse("```\n```").blocks.size)
    }

    @Test fun `fence has no indent`() {
        check("  ```\na", doc(body("  ```"), body("a")), "  \\```\na")
        check("```\n  ```\n```", doc(text(ParagraphKind.Mono, "  ```")))
    }

    @Test fun `a longer closing line ends the fence and the rest is parsed again`() {
        val d = parse("```\na\n````\n*b*\n```\n# T")
        assertEquals(
            listOf(
                text(ParagraphKind.Mono, "a"),
                NoteBlock.Text(ParagraphKind.Body, Inline("b", listOf(MarkRange(0, 1, Mark.Italic)))),
                text(ParagraphKind.Mono, "# T"),
            ),
            d.blocks,
        )
        assertEquals(d, parse(ser(d)))
    }

    @Test fun `legacy emphasis forms are read as marked text`() {
        fun bodyOf(md: String) = (parse(md).blocks.single() as NoteBlock.Text).content
        assertEquals(Inline("bold text", listOf(MarkRange(0, 4, Mark.Bold))), bodyOf("**bold **text"))
        assertEquals("**bold** text", ser(parse("**bold **text")))
        assertEquals("**ab**", ser(parse("**a****b**")))
    }
}
