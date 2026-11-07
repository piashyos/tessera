package app.tessera.ui.editor.engine

import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.Inline
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteDocFixtures
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind

/**
 * The texts of Chunk 4c (the user's request: every case in as many variants as possible). [texts] are strings for a paragraph, for typing
 * and for pasting; [documents] are whole notes, as Markdown. The fuzzer starts its sessions from them, the corpus test walks every one.
 */
object EngineTextCorpus {
    /** One grapheme cluster each: what an IME commits at a time. */
    val graphemes: List<String> = listOf(
        "a", "b", "z", "Z", "я", "Ж", "0", "7", "x",
        "*", "_", "\\", "#", "-", "+", ">", "[", "]", "|", "`", "~", "<", "&", "!", ".", ")", "(", "\"", ":",
        "👍🏽", "👨‍👩‍👧‍👦", "🇷🇺", "1️⃣", "❤️", "😀", "é", "ö́",
        "א", "ש", "ب", "م", "‫", "‬",
        " ", "\t", "​", "﻿", " ",
    )

    /** What typing one of them must not remove: no space (auto-replace), no service characters (spec §6.2 rule 5). */
    val keptGraphemes: List<String> = graphemes.filter { it != " " && it != "​" && it != "﻿" && it != " " && it != "‫" && it != "‬" }

    val markers: List<String> = listOf("* ", "- ", "+ ", "1. ", "1) ", "[] ", "[ ] ", "# ", "## ", "### ", "> ", "``` ")

    private val long = "слово word ".repeat(1000) // 11 000 characters

    val texts: List<String> = listOf(
        "", "a", "я", "😀", " ", " ",
        long,
        "Привет, мир", "Hello, world", "Привет, world! Смесь scripts: abc абв 123",
        "👍🏽 тон кожи", "семья 👨‍👩‍👧‍👦 ZWJ", "флаг 🇷🇺 и 🇺🇸 подряд", "keycap 1️⃣2️⃣3️⃣", "сердце ❤️ и 😀😀😀",
        "é комбинируемый", "á̂̃ стопка", "ö́",
        "שלום עולם", "مرحبا بالعالم", "abc שלום def مرحبا 123", "‫право-налево‬", "اللغة العربية 2024 مع numbers",
        "* _ \\ # - + > [ ] | ` ~ < & !", "a*b*c", "**", "~~~", "<u>x</u>", "5 < 3 & 7 > 2", "[link](url)", "![](x)", "\\*\\_\\\\", "snake_case_name", "2 * 3 * 4",
        "1.", "1. x", "2024. год", "3) x", "- [ ]", "- [ ] x", "- [x] y", "---", "***", "___", "```", "``` x", "<!-- -->", "# x", "## y", "> q", "| a | b |", "|", "- ", "+ ", "* ",
        " a b ", "a\tb\t", "\ttab", "  лидирующие", "хвостовые  ", "  both  ", "a  b   c",
        "a​b", "​", "a￼b", "￼", "﻿a", "﻿", "a b", "a\rb",
    )

    private fun body(t: String) = NoteBlock.Text(ParagraphKind.Body, Inline(t))
    private fun kinded(k: ParagraphKind, t: String) = NoteBlock.Text(k, Inline(t))

    /** One of every kind (Body first), with a sample text. */
    val kinds: List<Pair<String, NoteBlock>> = listOf(
        "body" to body("body"),
        "title" to kinded(ParagraphKind.Title, "title"),
        "heading" to kinded(ParagraphKind.Heading, "heading"),
        "subheading" to kinded(ParagraphKind.Subheading, "subheading"),
        "bullet" to kinded(ParagraphKind.ListItem(ListType.Bullet, 0), "bullet"),
        "bullet1" to kinded(ParagraphKind.ListItem(ListType.Bullet, 1), "bullet1"),
        "dash" to kinded(ParagraphKind.ListItem(ListType.Dash, 0), "dash"),
        "dash2" to kinded(ParagraphKind.ListItem(ListType.Dash, 2), "dash2"),
        "number" to kinded(ParagraphKind.ListItem(ListType.Number, 0), "number"),
        "number3" to kinded(ParagraphKind.ListItem(ListType.Number, 3), "number3"),
        "check" to kinded(ParagraphKind.ListItem(ListType.Check, 0), "check"),
        "checked" to kinded(ParagraphKind.ListItem(ListType.Check, 1, true), "checked"),
        "quote" to kinded(ParagraphKind.Quote, "quote"),
        "mono" to kinded(ParagraphKind.Mono, "mono"),
        "empty-bullet" to kinded(ParagraphKind.ListItem(ListType.Bullet, 0), ""),
        "empty-quote" to kinded(ParagraphKind.Quote, ""),
        "empty-mono" to kinded(ParagraphKind.Mono, ""),
        "divider" to NoteBlock.Divider,
        "image" to NoteBlock.Image("tessera-img://p-1?w=50&rot=90"),
        "table" to NoteBlock.Table(TableGrid(listOf("A", "B"), listOf(listOf("1", "2")))),
    )

    /** Every kind next to every kind, both orders, alone and between two Body lines. */
    val matrix: List<NoteDoc> = buildList {
        for ((_, a) in kinds) for ((_, b) in kinds) {
            add(NoteDoc(listOf(a, b)))
            add(NoteDoc(listOf(body("above"), a, b, body("below"))))
        }
    }

    private fun bigNote(paragraphs: Int): NoteDoc {
        val blocks = ArrayList<NoteBlock>()
        var i = 0
        while (blocks.size < paragraphs) {
            blocks += kinds[i % kinds.size].second.let { b -> if (b is NoteBlock.Text) b.copy(content = Inline("${b.content.text} $i")) else b }
            i++
        }
        return NoteDoc(blocks)
    }

    /** Whole notes; [big] ones are for the sessions that check less often. */
    val documents: List<NoteDoc> = buildList {
        add(NoteDoc(listOf(body(""))))
        for (t in texts) {
            add(NoteDoc(listOf(body(t))))
            add(NoteDoc(listOf(body("a"), kinded(ParagraphKind.ListItem(ListType.Bullet, 0), t), kinded(ParagraphKind.Quote, t), kinded(ParagraphKind.Mono, t), kinded(ParagraphKind.Heading, t), body("z"))))
        }
        for (l in NoteDocFixtures.legacyCorpus) add(NoteMarkdown.parse(l))
        addAll(NoteDocFixtures.sampleDocs)
        addAll(NoteDocFixtures.randomDocs(4242, 120))
        addAll(matrix)
    }

    val big: List<NoteDoc> = listOf(
        NoteDoc(listOf(body(long.repeat(1)))), // one paragraph of 11 000 characters
        NoteDoc(listOf(kinded(ParagraphKind.ListItem(ListType.Bullet, 0), long), kinded(ParagraphKind.Quote, long), kinded(ParagraphKind.Mono, long))),
        bigNote(2100), // ≥ 2000 paragraphs, every kind
        NoteDoc(List(2100) { body("line $it") }),
    )

    val markdowns: List<String> by lazy { documents.map { NoteMarkdown.serialize(it) } }
    val bigMarkdowns: List<String> by lazy { big.map { NoteMarkdown.serialize(it) } }
}
