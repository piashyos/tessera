package app.tessera.ui.editor.screen

/**
 * Markdown as older builds stored it: the legacy corpus of the unit tests (`NoteDocFixtures.legacyCorpus`, what the Aztec path
 * wrote) and forms the native dialect reads but never writes — non-canonical markers, numbering, tabs, stray tags, odd whitespace,
 * scripts and emoji. Opening such a note must not rewrite it (spec §9 item 2).
 */
internal object LegacyNotes {
    /** The note of the "разметка сходит с ума" video. */
    const val VIDEO_NOTE =
        "# Tessera\n" +
            "Что нужна добавить в приложение или исправить\n" +
            "- [x] Эффект бумаги на фон\n" +
            "- [x] Исправить сумасшествие разметки\n" +
            "- [x] Переделать теги на списке\n" +
            "- [x] При открытии доп меню в из нижнего бара весь лэйаут перестраивается, меню не открывается как дропдаун вне основного лэйаута\n" +
            "- [ ] Исправить инпут в модалке Шеринга\n" +
            "- [ ] Прозрачный отступ под навигационной полоской"

    private val unitCorpus = listOf(
        "**bold**", "*italic*", "~~strike~~", "# Heading one", "## Heading two",
        "- first\n- second", "- [ ] todo", "- [x] done", "Just some text.",
        "# Title\nSome **bold** and *italic* and ~~strike~~ text.\n## Subhead\n- bullet one\n- bullet two\n- [ ] open task\n- [x] closed task",
        "# A **bold** title", "- **bold** item\n- *italic* item",
        "1. first\n2. second", "> quoted", "> line one\n> line two", "`code`", "---",
        "<u>under</u>", "<u>**bold**</u>", "`a < b && c`",
        "```\ncode\n```", "```\nline one\nline two\n```", "```\nline one\n\nline two\n```",
        "```\nif (a < b && c > d) { *x* }\n```", "```\na<b\nc\n```", "```\nval x = 1\nif (x < 2) print(x)\n```",
        "5 < 3", "3 > 5", "Tom & Jerry", "if a < b && c > d", "**a < b**", "# Q & A < B",
        "- [ ] fix a < b & c", "a <fake> tag", "&lt;", "a b",
        "- a\n\n1. b\n\n- [ ] c", "- a\n\n- b", "- a\n- ", "- [ ] a\n\n- [x] b",
        "# Title\n- [ ] open\n- [x] done\nSome **bold** text.", "- [x] done\n\nTotal: 100",
        "first\n\nsecond\nthird", "a\nb\nc", "a\n\nb",
        "before\n![](tessera-img://abc-123)\nafter",
        "![](tessera-img://before-1)\n- item one\n- item two\n![](tessera-img://after-1)",
        "![](tessera-img://before-2)\n| A | B |\n| 1 | 2 |\n![](tessera-img://after-2)",
        "before\n\n![](tessera-img://mid-1)\n\nafter", "before\n![](tessera-img://abc-1?w=50&rot=90)\nafter",
        "![](tessera-img://p-1?w=30&rot=180)\n- item\n\n![](tessera-img://p-2?rot=270)",
        "**bold **text", "** bold**", "~~s ~~x", "**a****b**", "~~a~~~~b~~", "*a**b*",
        "```\na\n````\n*b*\n```\n# T",
    )

    private val nonCanonical = listOf(
        "* x\n* y", "1. 1. x", "1. a\n1. b\n1. c", "3. a\n7. b", "1) a\n2) b", "+ a\n+ b", "* [ ] t\n* [X] d", "- [X] big X",
        "#### four\n##### five\n###### six", "## closing hashes ##", "***", "___", "---\n---",
        "<b>b</b> <i>i</i> <s>s</s>", "<strong>s</strong> <em>e</em> <del>d</del>", "<u>open only",
        "\ttabbed body", "    - nested four\n        - nested eight\n                    - nested twenty", "\t- tab nested\n- a\n\t- b",
        "trailing spaces  ", "  leading spaces", "a\n\n\n", "\n\nleading blank lines", "a\r\nb", "a b nbsp",
        "```kotlin\nval a = 1\n```", "```\nunclosed fence\nline", "~~~\ntilde\n~~~",
        "| A | B |\n|---|---|\n| 1 | 2 |", "| not | a table", "2024. год", "\\- escaped\n\\# escaped",
        "snake_case_name __init__", "2 * 3 * 4", ">no space", "# ", "- [ ]", "1.", "**", "[link](https://example.com) <https://example.com>",
        "![](https://not-tessera/x.png)", "a​b zero width﻿bom ￼ object",
        "👍🏽 👨‍👩‍👧 🇷🇺 1️⃣ é", "שלום עולם and English مرحبا", "<!-- comment -->", "x".repeat(3_000),
        VIDEO_NOTE,
    )

    val all: List<String> = unitCorpus + nonCanonical
}
