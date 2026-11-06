package app.tessera.core.text.notedoc

import app.tessera.core.text.markdownblocks.TableGrid
import kotlin.random.Random

/** Shared by the notedoc tests and (Chunk 3) the editor-engine tests. */
object NoteDocFixtures {

    /**
     * Markdown strings stored by the legacy (Aztec) path: the `roundTrip(...)` arguments, the
     * `markdownToHtml` inputs and the `htmlToMarkdown` expectations of `MarkdownHtmlConverterTest`.
     */
    val legacyCorpus: List<String> = listOf(
        "", "**bold**", "*italic*", "~~strike~~", "# Heading one", "## Heading two",
        "- first\n- second", "- [ ] todo", "- [x] done", "Just some text.",
        "# Title\nSome **bold** and *italic* and ~~strike~~ text.\n## Subhead\n- bullet one\n- bullet two\n" +
            "- [ ] open task\n- [x] closed task",
        "# A **bold** title", "- **bold** item\n- *italic* item",
        "1. first\n2. second", "> quoted", "> line one\n> line two", "`code`", "---",
        "<u>under</u>", "<u>**bold**</u>", "`a < b && c`",
        "```\ncode\n```", "```\nline one\nline two\n```", "```\nline one\n\nline two\n```",
        "```\nif (a < b && c > d) { *x* }\n```", "```\na<b\nc\n```", "```\nval x = 1\nif (x < 2) print(x)\n```",
        "```\na\nb\n```",
        "5 < 3", "3 > 5", "Tom & Jerry", "if a < b && c > d", "**a < b**", "# Q & A < B",
        "- [ ] fix a < b & c", "5 < 3 & 7 > 2", "a <fake> tag", "a < b & c", "&lt;", "a b",
        "- a\n\n1. b\n\n- [ ] c", "- a\n\n- b", "- a\n\n1. b", "- a\n- ", "- [ ] a\n\n- [x] b",
        "# Title\n- [ ] open\n- [x] done\nSome **bold** text.", "- [x] done\n\nTotal: 100",
        "first\n\nsecond\nthird", "a\nb\nc", "a\nb", "a\n\nb", "beforeafter",
        "~~x~~", "**x**", "*x*", "<u>x</u>", "`x`", "1. a\n2. b",
        "before\n![](tessera-img://abc-123)\nafter",
        "![](tessera-img://before-1)\n- item one\n- item two\n![](tessera-img://after-1)",
        "![](tessera-img://before-2)\n| A | B |\n| 1 | 2 |\n![](tessera-img://after-2)",
        "before\n\n![](tessera-img://mid-1)\n\nafter",
        "before\n![](tessera-img://abc-1?w=50&rot=90)\nafter", "![](tessera-img://abc-1)",
        "![](tessera-img://abc-1?w=50&rot=90)", "![](tessera-img://xyz-9)",
        "![](tessera-img://p-1?w=30&rot=180)\n- item\n\n![](tessera-img://p-2?rot=270)",
        // stored emphasis forms of the Aztec era (spec §5.3 legacy pass) and a fence closed by a longer line
        "**bold **text", "** bold**", "~~s ~~x", "**a****b**", "~~a~~~~b~~", "*a**b*",
        "```\na\n````\n*b*\n```\n# T",
    )

    private fun body(text: String, vararg marks: MarkRange) =
        NoteBlock.Text(ParagraphKind.Body, Inline(text, marks.toList()))

    private fun text(kind: ParagraphKind, text: String, vararg marks: MarkRange) =
        NoteBlock.Text(kind, Inline(text, marks.toList()))

    private fun item(type: ListType, level: Int, text: String, checked: Boolean = false) =
        NoteBlock.Text(ParagraphKind.ListItem(type, level, checked), Inline(text))

    private val table = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("x | y", "")))

    /** Every kind, levels 0-4, empty paragraphs at start/middle/end, empty last item, objects, table after table. */
    val sampleDocs: List<NoteDoc> = listOf(
        NoteDoc(listOf(body(""))),
        NoteDoc(listOf(body(""), body(""))),
        NoteDoc(listOf(body("a"), body(""), body("b"), body(""))),
        NoteDoc(listOf(body(""), body("first"), body(""), body(""), body("last"), body(""), body(""))),
        NoteDoc(
            listOf(
                text(ParagraphKind.Title, "Title", MarkRange(0, 3, Mark.Bold)),
                text(ParagraphKind.Heading, "Heading"),
                text(ParagraphKind.Subheading, "Sub"),
                body("plain **text** with \\ and `x`", MarkRange(0, 5, Mark.Italic), MarkRange(6, 12, Mark.Code)),
                text(ParagraphKind.Quote, "quoted", MarkRange(0, 6, Mark.Strike)),
                text(ParagraphKind.Mono, "val x = 1"),
                text(ParagraphKind.Mono, ""),
                text(ParagraphKind.Mono, "```"),
                NoteBlock.Divider,
                body("end", MarkRange(0, 3, Mark.Underline)),
            ),
        ),
        NoteDoc(
            listOf(
                item(ListType.Bullet, 0, "b0"), item(ListType.Bullet, 1, "b1"), item(ListType.Bullet, 2, "b2"),
                item(ListType.Dash, 3, "d3"), item(ListType.Dash, 4, "d4"),
                item(ListType.Number, 0, "n1"), item(ListType.Number, 1, "n1.1"), item(ListType.Number, 0, "n2"),
                item(ListType.Check, 0, "todo"), item(ListType.Check, 1, "done", checked = true),
                item(ListType.Check, 0, ""),
            ),
        ),
        NoteDoc(listOf(body("a"), item(ListType.Bullet, 0, "x"), item(ListType.Bullet, 0, ""))),
        NoteDoc(listOf(item(ListType.Check, 0, "a"), item(ListType.Check, 0, ""))),
        NoteDoc(listOf(NoteBlock.Image("tessera-img://abc-1?w=50&rot=90"), body("x"), NoteBlock.Image("tessera-img://z"))),
        NoteDoc(listOf(NoteBlock.Table(table), NoteBlock.Table(table))),
        NoteDoc(listOf(NoteBlock.Table(table), body("| after table"))),
        NoteDoc(listOf(body("before"), NoteBlock.Table(table), NoteBlock.Divider, NoteBlock.Table(table), body(""))),
    )

    // --- random documents (§5.3) ---

    private val letters = "abcxyzABCXYZабвгяю"
    private val punct = "*_~\\`<>[]#|-+.)!"
    private val tokens = listOf(
        "**", "~~", "~~~", "*", "`", "``", "```", "<u>", "</u>", "<b>", "</b>", "<i>", "<s>", "- ", "+ ", "* ",
        "1. ", "2024. ", "3) ", "> ", "# ", "## ", "![](", "<!--", "---", "***", "___", "|", "[ ] ", "[x] ",
        "\\", "\\*", "**a **b", "** a**", "~~a~~~~b~~", "\n", "\r", "\uD83D\uDE00", "snake_case", "__init__", "<strong>", "a <fake> tag", "\"x\"", "  ", "\t", " ",
    )

    fun randomText(rnd: Random, maxPieces: Int = 8): String = buildString {
        repeat(rnd.nextInt(0, maxPieces + 1)) {
            when (rnd.nextInt(6)) {
                0 -> append(letters[rnd.nextInt(letters.length)])
                1 -> append(rnd.nextInt(10))
                2 -> append(' ')
                3 -> append(punct[rnd.nextInt(punct.length)])
                4 -> append(tokens[rnd.nextInt(tokens.size)])
                else -> repeat(rnd.nextInt(1, 4)) { append(letters[rnd.nextInt(letters.length)]) }
            }
        }
    }

    private val markKinds = listOf(Mark.Bold, Mark.Italic, Mark.Underline, Mark.Strike, Mark.Code)

    private fun randomMarks(rnd: Random, len: Int): List<MarkRange> {
        if (len == 0) return emptyList()
        return List(rnd.nextInt(0, 5)) {
            val a = rnd.nextInt(0, len + 1)
            val b = rnd.nextInt(0, len + 1)
            MarkRange(minOf(a, b), maxOf(a, b), markKinds[rnd.nextInt(markKinds.size)])
        }
    }

    private fun randomInline(rnd: Random): Inline {
        val t = randomText(rnd)
        return Inline(t, randomMarks(rnd, t.length))
    }

    private fun randomKind(rnd: Random): ParagraphKind = when (rnd.nextInt(12)) {
        0 -> ParagraphKind.Title
        1 -> ParagraphKind.Heading
        2 -> ParagraphKind.Subheading
        3 -> ParagraphKind.Quote
        4 -> ParagraphKind.Mono
        5, 6, 7 -> ParagraphKind.ListItem(ListType.entries[rnd.nextInt(4)], rnd.nextInt(-1, 7), rnd.nextBoolean())
        else -> ParagraphKind.Body
    }

    private fun randomTable(rnd: Random): TableGrid {
        val cols = rnd.nextInt(1, 4)
        fun cell() = randomText(rnd, 3)
        val rows = List(rnd.nextInt(0, 4)) { List(cols) { cell() } }
        val widths = if (rnd.nextInt(3) == 0) List(if (rnd.nextInt(4) == 0) cols + 1 else cols) { rnd.nextInt(40, 400) + 0.4f } else null
        val heights = if (rnd.nextInt(3) == 0) List(if (rnd.nextInt(4) == 0) rows.size else rows.size + 1) { rnd.nextInt(30, 200) + 0.6f } else null
        return TableGrid(List(cols) { cell() }, rows, widths, heights)
    }

    private fun randomBlock(rnd: Random, afterTable: Boolean): NoteBlock {
        if (afterTable && rnd.nextInt(3) != 0) {
            val t = if (rnd.nextBoolean()) "| " + randomText(rnd, 3) else randomText(rnd, 3)
            return NoteBlock.Text(ParagraphKind.Body, Inline(t, randomMarks(rnd, t.length)))
        }
        return when (rnd.nextInt(20)) {
            0 -> NoteBlock.Divider
            1 -> NoteBlock.Image(
                listOf(
                    "tessera-img://abc-1", "tessera-img://p-1?w=30&rot=180", "http://x/y", "tessera-img://", "a)b", "",
                )[rnd.nextInt(6)],
            )
            2, 3 -> NoteBlock.Table(randomTable(rnd))
            else -> NoteBlock.Text(randomKind(rnd), randomInline(rnd))
        }
    }

    fun randomDoc(rnd: Random): NoteDoc {
        val blocks = ArrayList<NoteBlock>()
        repeat(rnd.nextInt(1, 9)) { blocks += randomBlock(rnd, blocks.lastOrNull() is NoteBlock.Table) }
        return NoteDoc(blocks)
    }

    fun randomDocs(seed: Int, count: Int): List<NoteDoc> {
        val rnd = Random(seed)
        return List(count) { randomDoc(rnd) }
    }
}
