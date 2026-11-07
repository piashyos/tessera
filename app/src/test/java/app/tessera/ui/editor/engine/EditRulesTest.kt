package app.tessera.ui.editor.engine

import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec §6.4 (Enter, Backspace, selection delete) and §6.6 (levels, kind, marks) as tables. A row is
 * `name | input | action | expected | inSel | outSel`; the markers [CARET] / [SEL_START]..[SEL_END] are in the text of the
 * Markdown, `inSel` / `outSel` are absolute offsets for the places next to an object (it has no text to put a marker in).
 * Every row also checks invariant 1, the text and the list numbers against a fresh build of what the editable holds.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class EditRulesTest(
    private val name: String,
    private val input: String,
    private val action: String,
    private val expected: String,
    private val inSel: String,
    private val outSel: String,
) {
    private val style = EngineTestStyle.create()

    @Test
    fun cell() {
        val f = EditFixture(input, inSel, style)
        val rules = EditRules(style)
        val sel = run(rules, f)
        EditFixture.assertSane(name, f.e, style)
        assertEquals("$name: document", EditFixture.doc(expected), DocBinder.read(f.e))
        if (outSel.isNotEmpty()) {
            val p = outSel.split("-").map { it.toInt() }
            assertEquals("$name: selection", p.first() to p.last(), sel.start to sel.end)
        } else {
            val (a, b) = EditFixture.markerPositions(expected)
            assertEquals("$name: selection", a to b, EditFixture.visible(f.e, sel.start) to EditFixture.visible(f.e, sel.end))
        }
    }

    private fun run(rules: EditRules, f: EditFixture): Sel = when {
        action == "enter" -> rules.enter(f.e, f.selStart, f.selEnd)
        action == "backspace" -> rules.backspace(f.e, f.selStart, f.selEnd)
        action == "indent" -> rules.changeLevel(f.e, f.selStart, f.selEnd, +1)
        action == "outdent" -> rules.changeLevel(f.e, f.selStart, f.selEnd, -1)
        action == "hr" -> rules.insertHorizontalRule(f.e, f.selStart, f.selEnd)
        action == "image" -> rules.insertObject(f.e, f.selStart, f.selEnd, NoteBlock.Image(IMG))
        action == "forwarddelete" -> rules.forwardDelete(f.e, f.selStart, f.selEnd) ?: Sel(f.selStart, f.selEnd)
        action.startsWith("kind:") -> rules.toggleKind(f.e, f.selStart, f.selEnd, kindOf(action.removePrefix("kind:")))
        action.startsWith("mark:") -> rules.toggleMark(f.e, f.selStart, f.selEnd, markOf(action.removePrefix("mark:")))
        else -> error(action)
    }

    private fun kindOf(n: String): ParagraphKind = when (n) {
        "body" -> ParagraphKind.Body
        "title" -> ParagraphKind.Title
        "heading" -> ParagraphKind.Heading
        "subheading" -> ParagraphKind.Subheading
        "mono" -> ParagraphKind.Mono
        "quote" -> ParagraphKind.Quote
        "bullet" -> ParagraphKind.ListItem(ListType.Bullet, 0)
        "dash" -> ParagraphKind.ListItem(ListType.Dash, 0)
        "number" -> ParagraphKind.ListItem(ListType.Number, 0)
        "check" -> ParagraphKind.ListItem(ListType.Check, 0)
        else -> error(n)
    }

    private fun markOf(n: String): Mark = when (n) {
        "bold" -> Mark.Bold
        "italic" -> Mark.Italic
        "underline" -> Mark.Underline
        "strike" -> Mark.Strike
        else -> error(n)
    }

    companion object {
        private const val IMG = "tessera-img://x"
        private const val MD = "![](tessera-img://x)"

        private fun row(name: String, input: String, action: String, expected: String, inSel: String = "", outSel: String = "") =
            arrayOf<Any>(name, input, action, expected, inSel, outSel)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cells(): List<Array<Any>> = listOf(
            // ---- Enter: start / middle of a paragraph — both parts keep the kind ----
            row("6.4 Enter split | body, middle", "ab‸cd", "enter", "ab\n‸cd"),
            row("6.4 Enter split | body, start", "‸ab", "enter", "\n‸ab"),
            row("6.4 Enter split | title, middle", "# ab‸cd", "enter", "# ab\n# ‸cd"),
            row("6.4 Enter split | heading, middle", "## ab‸cd", "enter", "## ab\n## ‸cd"),
            row("6.4 Enter split | heading, start", "# ‸ab", "enter", "# \n# ‸ab"),
            row("6.4 Enter split | bullet, middle", "- ab‸cd", "enter", "- ab\n- ‸cd"),
            row("6.4 Enter split | number, middle", "1. ab‸cd", "enter", "1. ab\n2. ‸cd"),
            row("6.4 Enter split | check checked, middle: second part unchecked", "- [x] ab‸cd", "enter", "- [x] ab\n- [ ] ‸cd"),
            row("6.4 Enter split | check checked, start: empty unchecked item above", "- [x] ‸ab", "enter", "- [ ] \n- [x] ‸ab"),
            row("6.4 Enter split | quote, middle", "> ab‸cd", "enter", "> ab\n> ‸cd"),
            row("6.4 Enter split | mono, middle", "```\nab‸cd\n```", "enter", "```\nab\n‸cd\n```"),
            // ---- Enter at the end, non-empty paragraph ----
            row("6.4 Enter end non-empty | bullet", "- ab‸", "enter", "- ab\n- ‸"),
            row("6.4 Enter end non-empty | number", "1. ab‸", "enter", "1. ab\n2. ‸"),
            row("6.4 Enter end non-empty | dash level 2", "        + ab‸", "enter", "        + ab\n        + ‸"),
            row("6.4 Enter end non-empty | check unchecked", "- [ ] ab‸", "enter", "- [ ] ab\n- [ ] ‸"),
            row("6.4 Enter end non-empty | check checked: new item is unchecked", "- [x] ab‸", "enter", "- [x] ab\n- [ ] ‸"),
            row("6.4 Enter end non-empty | list item in the middle of the document", "- ab‸\nz", "enter", "- ab\n- ‸\nz"),
            row("6.4 Enter end non-empty | quote", "> ab‸", "enter", "> ab\n> ‸"),
            row("6.4 Enter end non-empty | title: new Body", "# ab‸", "enter", "# ab\n‸"),
            row("6.4 Enter end non-empty | heading: new Body", "## ab‸", "enter", "## ab\n‸"),
            row("6.4 Enter end non-empty | subheading: new Body", "### ab‸", "enter", "### ab\n‸"),
            row("6.4 Enter end non-empty | mono: new Mono line", "```\nab‸\n```", "enter", "```\nab\n‸\n```"),
            row("6.4 Enter end non-empty | body: new Body", "ab‸", "enter", "ab\n‸"),
            // ---- Enter in an empty paragraph ----
            row("6.4 Enter empty | bullet level 1 -> level 0", "- a\n    - ‸", "enter", "- a\n- ‸"),
            row("6.4 Enter empty | bullet level 0 -> Body", "- a\n- ‸", "enter", "- a\n‸"),
            row("6.4 Enter empty | number level 0 -> Body", "1. a\n2. ‸", "enter", "1. a\n‸"),
            row("6.4 Enter empty | check level 0 -> Body", "- [ ] a\n- [ ] ‸", "enter", "- [ ] a\n‸"),
            row("6.4 Enter empty | check level 2 -> level 1", "- [ ] a\n        - [ ] ‸", "enter", "- [ ] a\n    - [ ] ‸"),
            row("6.4 Enter empty | number level 2 -> level 1, type kept", "1. a\n        1. ‸", "enter", "1. a\n    1. ‸"),
            row("6.4 Enter empty | list item in the middle, numbers below restart", "1. a\n2. ‸\n3. b", "enter", "1. a\n‸\n3. b"),
            row("6.4 Enter empty | quote -> Body", "> a\n> ‸", "enter", "> a\n‸"),
            row("6.4 Enter empty | title: stays, new Body below", "# a\n# ‸", "enter", "# a\n# \n‸"),
            row("6.4 Enter empty | heading: stays, new Body below", "## a\n## ‸", "enter", "## a\n## \n‸"),
            row("6.4 Enter empty | subheading: stays, new Body below", "### a\n### ‸", "enter", "### a\n### \n‸"),
            row("6.4 Enter empty | mono: new Mono line", "```\na\n‸\n```", "enter", "```\na\n\n‸\n```"),
            row("6.4 Enter empty | body: new Body", "a\n‸", "enter", "a\n\n‸"),
            row("6.4 Enter empty | sentinel paragraph, caret before the sentinel", "- a\n- ", "enter", "- a\n", "2", "2"),
            // ---- Enter with a selection, next to objects ----
            row("6.4 Enter | selection inside a paragraph is replaced", "ab«cd»ef", "enter", "ab\n‸ef"),
            row("6.4 Enter | selection across paragraphs takes the kind of the first", "# ab«c\n- d»ef", "enter", "# ab\n# ‸ef"),
            row("6.4 Enter | caret after an object: new Body after it, nothing goes into the object paragraph", "a\n---", "enter", "a\n---\n", "3", "4"),
            row("6.4 Enter | caret before an object: new empty Body before it", "a\n---", "enter", "a\n\n---", "2", "3"),
            row("6.4 Enter | caret after an object in the middle of the document", "---\nb", "enter", "---\n\nb", "1", "2"),
            // ---- Backspace in the middle of a paragraph ----
            row("6.4 Backspace | a character", "ab‸cd", "backspace", "a‸cd"),
            row("6.4 Backspace | a surrogate pair goes whole", "a😀‸", "backspace", "a‸"),
            row("6.4 Backspace | last character of a last list item keeps the item (sentinel)", "- a\n- b‸", "backspace", "- a\n- ‸"),
            // ---- Backspace at the start of a paragraph: 1 — a kind other than Body ----
            row("6.4 Backspace start | bullet level 0 -> Body, text stays", "- ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | bullet level 2 -> level 1", "        - ‸ab", "backspace", "    - ‸ab"),
            row("6.4 Backspace start | check -> Body", "- [x] ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | number -> Body, numbers below restart", "1. a\n2. ‸b\n3. c", "backspace", "1. a\n‸b\n3. c"),
            row("6.4 Backspace start | quote -> Body", "> ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | title -> Body", "# ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | heading -> Body", "## ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | subheading -> Body", "### ‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | mono line -> Body in place", "```\na\n‸b\n```", "backspace", "```\na\n```\n‸b"),
            row("6.4 Backspace start | empty last list item (caret after the sentinel) -> Body", "- a\n- ‸", "backspace", "- a\n‸"),
            row("6.4 Backspace start | empty last list item (caret before the sentinel) -> Body", "- a\n- ", "backspace", "- a\n", "2", "2"),
            row("6.4 Backspace start | empty last list item level 1 -> level 0", "- a\n    - ‸", "backspace", "- a\n- ‸"),
            row("6.4 Backspace start | list item right under an object: kind first", "---\n- b", "backspace", "---\nb", "2", "2"),
            // ---- 3 — Body: merge with the previous paragraph ----
            row("6.4 Backspace start | Body merges into Body", "ab\n‸cd", "backspace", "ab‸cd"),
            row("6.4 Backspace start | merge takes the kind of the previous: title", "# ab\n‸cd", "backspace", "# ab‸cd"),
            row("6.4 Backspace start | merge takes the kind of the previous: list item", "- ab\n‸cd", "backspace", "- ab‸cd"),
            row("6.4 Backspace start | merge takes the kind of the previous: check checked", "- [x] ab\n‸cd", "backspace", "- [x] ab‸cd"),
            row("6.4 Backspace start | merge takes the kind of the previous: quote", "> ab\n‸cd", "backspace", "> ab‸cd"),
            row("6.4 Backspace start | merge takes the kind of the previous: mono", "```\nab\n```\n‸cd", "backspace", "```\nab‸cd\n```"),
            row("6.4 Backspace start | merge keeps the marks of both parts", "**ab**\n‸cd", "backspace", "**ab**‸cd"),
            row("6.4 Backspace start | empty Body at the end merges into the text", "ab\n‸", "backspace", "ab‸"),
            row("6.4 Backspace start | empty Body after an empty list item: the item stays last (sentinel)", "- a\n- \n‸", "backspace", "- a\n- ‸"),
            row("6.4 Backspace start | at the start of the document: nothing", "‸ab", "backspace", "‸ab"),
            row("6.4 Backspace start | empty document: nothing", "‸", "backspace", "‸"),
            // ---- 2 — Body after an object: select it, the next Backspace deletes it ----
            row("6.4 Backspace start | divider before a Body paragraph: select it", "---\nb", "backspace", "---\nb", "2", "0-1"),
            row("6.4 Backspace start | image before a Body paragraph: select it", "a\n![](tessera-img://1)\nb", "backspace", "a\n![](tessera-img://1)\nb", "4", "2-3"),
            row("6.4 Backspace start | caret right after the object: select it", "a\n---", "backspace", "a\n---", "3", "2-3"),
            row("6.4 Backspace | selected object in the middle: its paragraph goes whole", "a\n---\nb", "backspace", "a\nb", "2-3", "2"),
            row("6.4 Backspace | selected object, last paragraph: the break before it goes too", "a\n---", "backspace", "a", "2-3", "1"),
            row("6.4 Backspace | selected object, the only paragraph: nothing is left but an empty Body", "---", "backspace", "", "0-1", "0"),
            row("6.4 Backspace | no empty Body is left where the object was, the next one keeps its kind", "# a\n---\n- b", "backspace", "# a\n- b", "2-3", "2"),
            row("6.4 Backspace start | caret before an object, text above: the caret goes to its end", "ab\n---", "backspace", "ab\n---", "3", "2"),
            row("6.4 Backspace start | caret before an object, empty line above: the line goes", "ab\n\n---", "backspace", "ab\n---", "4", "3"),
            row("6.4 Backspace start | caret before an object at the start of the document: nothing", "---", "backspace", "---", "0", "0"),
            // ---- selection across paragraphs: the result takes the kind of the first ----
            row("6.4 Delete selection | Body paragraphs", "ab«c\nde»f", "backspace", "ab‸f"),
            row("6.4 Delete selection | first is a title", "# ab«c\nde»f", "backspace", "# ab‸f"),
            row("6.4 Delete selection | first is Body, last is a heading (starts at the start of the Body)", "«ab\n## c»d", "backspace", "‸d"),
            row("6.4 Delete selection | from the middle of Body into a heading", "a«b\n## c»d", "backspace", "a‸d"),
            row("6.4 Delete selection | list item then quote", "- a«b\n> c»d", "backspace", "- a‸d"),
            row("6.4 Delete selection | all of the text keeps the kind of the first (sentinel)", "# «ab\ncd»", "backspace", "# ‸"),
            row("6.4 Delete selection | up to the end of a last list item", "- a«\n- b»", "backspace", "- a‸"),
            row("6.4 Delete selection | across an object", "a\n---\nb", "backspace", "ab", "1-4", "1"),
            row("6.4 Delete selection | from an object into the next paragraph", "a\n---\nb", "backspace", "a\n", "2-5", "2"),
            row("6.4 Delete selection | up to an object: the object stays alone in its paragraph", "ab\n---", "backspace", "a\n---", "1-3", "1"),
            row("6.4 Delete selection | the empty line between two objects", "---\n\n---", "backspace", "---\n---", "2-3", "2"),
            row("6.4 Delete selection | kept numbers below", "1. a«\n2. b\n3. »c\n4. d", "backspace", "1. a‸c\n2. d"),
            // ---- indent (§6.6, 0..4) ----
            row("6.6 Indent | bullet 0 -> 1", "- a‸", "indent", "    - a‸"),
            row("6.6 Indent | level 4 stays", "                - a‸", "indent", "                - a‸"),
            row("6.6 Indent | checked state kept", "- [x] a‸", "indent", "    - [x] a‸"),
            row("6.6 Indent | numbers around the item", "1. a\n2. b‸\n3. c", "indent", "1. a\n    1. b‸\n2. c"),
            row("6.6 Indent | a selection over several items", "- «a\n- b»", "indent", "    - «a\n    - b»"),
            row("6.6 Indent | a selection ending at the start of a paragraph leaves it", "- «a\n»- b", "indent", "    - «a\n»- b"),
            row("6.6 Indent | only list items change", "«a\n- b»", "indent", "«a\n    - b»"),
            row("6.6 Indent | Body: nothing", "a‸", "indent", "a‸"),
            row("6.6 Outdent | level 3 -> 2", "            - a‸", "outdent", "        - a‸"),
            row("6.6 Outdent | level 0 stays", "- a‸", "outdent", "- a‸"),
            row("6.6 Outdent | numbers around the item", "1. a\n    1. b‸\n2. c", "outdent", "1. a\n2. b‸\n3. c"),
            row("6.6 Outdent | the empty last item keeps its marker (sentinel)", "- a\n    - ‸", "outdent", "- a\n- ‸"),
            // ---- paragraph kind (§6.6) ----
            row("6.6 Kind | Body -> heading", "ab‸", "kind:heading", "## ab‸"),
            row("6.6 Kind | heading -> Body when it already is", "## ab‸", "kind:heading", "ab‸"),
            row("6.6 Kind | title -> heading", "# ab‸", "kind:heading", "## ab‸"),
            row("6.6 Kind | Body -> subheading", "ab‸", "kind:subheading", "### ab‸"),
            row("6.6 Kind | to Body", "## ab‸", "kind:body", "ab‸"),
            row("6.6 Kind | several paragraphs", "«a\nb»", "kind:heading", "## «a\n## b»"),
            row("6.6 Kind | mixed -> all get the kind", "## «a\nb»", "kind:heading", "## «a\n## b»"),
            row("6.6 Kind | all already -> Body", "## «a\n## b»", "kind:heading", "«a\nb»"),
            row("6.6 Kind | Body -> bullet", "ab‸", "kind:bullet", "- ab‸"),
            row("6.6 Kind | bullet -> Body", "- ab‸", "kind:bullet", "ab‸"),
            row("6.6 Kind | leaving a list resets the level", "    - ab‸", "kind:bullet", "ab‸"),
            row("6.6 Kind | heading -> list keeps the text, level 0", "# ab‸", "kind:bullet", "- ab‸"),
            row("6.6 Kind | list -> list of another type keeps the level", "        - ab‸", "kind:number", "        1. ab‸"),
            row("6.6 Kind | checked item -> bullet", "- [x] a‸", "kind:bullet", "- a‸"),
            row("6.6 Kind | checked item toggled as a check -> Body", "- [x] a‸", "kind:check", "a‸"),
            row("6.6 Kind | Body -> check, unchecked", "a‸", "kind:check", "- [ ] a‸"),
            row("6.6 Kind | Body -> dash", "a‸", "kind:dash", "+ a‸"),
            row("6.6 Kind | Body -> number", "a‸", "kind:number", "1. a‸"),
            row("6.6 Kind | numbers around a changed item", "1. a\n2. b‸\n3. c", "kind:bullet", "1. a\n- b‸\n1. c"),
            row("6.6 Kind | Body -> quote", "a‸", "kind:quote", "> a‸"),
            row("6.6 Kind | Body -> mono", "a‸", "kind:mono", "```\na‸\n```"),
            row("6.6 Kind | to mono drops the marks (a mono line has none)", "**a**‸", "kind:mono", "```\na‸\n```"),
            row("6.6 Kind | the empty last paragraph -> list (sentinel)", "a\n‸", "kind:bullet", "a\n- ‸"),
            row("6.6 Kind | the empty last list item -> Body (sentinel goes)", "a\n- ‸", "kind:bullet", "a\n‸"),
            row("6.6 Kind | the middle line of a mono series -> Body splits the series", "```\na\nb‸\nc\n```", "kind:mono", "```\na\n```\nb‸\n```\nc\n```"),
            row("6.6 Kind | objects are skipped", "a\n---\nb", "kind:heading", "## a\n---\n## b", "0-5", "0-5"),
            row("6.6 Kind | only an object: nothing", "---", "kind:heading", "---", "0", "0"),
            // ---- inline marks over a selection (§6.6) ----
            row("6.6 Mark | add bold", "«ab»", "mark:bold", "«**ab**»"),
            row("6.6 Mark | all marked -> remove", "«**ab**»", "mark:bold", "«ab»"),
            row("6.6 Mark | partly marked -> mark the whole", "«**a**b»", "mark:bold", "«**ab**»"),
            row("6.6 Mark | a part of a word", "a«b»c", "mark:bold", "a«**b**»c"),
            row("6.6 Mark | italic", "«ab»", "mark:italic", "«*ab*»"),
            row("6.6 Mark | underline", "«ab»", "mark:underline", "«<u>ab</u>»"),
            row("6.6 Mark | strike", "«ab»", "mark:strike", "«~~ab~~»"),
            row("6.6 Mark | across paragraphs the line breaks stay unmarked", "«a\nb»", "mark:bold", "«**a**\n**b**»"),
            row("6.6 Mark | across paragraphs, all marked -> remove", "«**a**\n**b**»", "mark:bold", "«a\nb»"),
            row("6.6 Mark | a mono line is skipped", "«a\n```\nb»\n```", "mark:bold", "«**a**\n```\nb»\n```"),
            row("6.6 Mark | only a line break selected: nothing", "a«\n»b", "mark:bold", "a«\n»b"),
            row("6.6 Mark | no selection: nothing here (typing attributes)", "ab‸", "mark:bold", "ab‸"),
            // ---- a divider after the paragraph with the caret (§7) ----
            row("7 Divider | after the paragraph, caret in the Body after it", "a‸\nb", "hr", "a\n---\n‸b"),
            row("7 Divider | in the middle of the paragraph: it is split", "a‸b\nc", "hr", "a\n---\n‸b\nc"),
            row("7 Divider | after the last paragraph: an empty Body follows", "a‸", "hr", "a\n---\n‸"),
            row("7 Divider | after a list item: the numbers below restart", "1. a‸\n2. b", "hr", "1. a\n---\n1. ‸b"),
            row("7 Divider | after the empty last item: the sentinel goes, the item stays", "- a\n- ‸", "hr", "- a\n- \n---\n‸"),
            row("7 Divider | after an object", "a\n---", "hr", "a\n---\n---\n", "3", "6"),
            row("7 Divider | after a title", "# a‸\nb", "hr", "# a\n---\n‸b"),
            // ---- an object as a paragraph of its own (phase 2 §2) ----
            row("P2 insertObject | end of a paragraph", "a‸\nb", "image", "a\n$MD\n‸b"),
            row("P2 insertObject | middle of a paragraph: split", "a‸b\nc", "image", "a\n$MD\n‸b\nc"),
            row("P2 insertObject | start of a text: before it", "‸ab", "image", "$MD\n‸ab"),
            row("P2 insertObject | start of a title: before it, the kind stays", "# ‸ab\nc", "image", "$MD\n# ‸ab\nc"),
            row("P2 insertObject | end of the last paragraph: an empty Body follows", "a‸", "image", "a\n$MD\n‸"),
            row("P2 insertObject | empty document", "‸", "image", "$MD\n‸"),
            row("P2 insertObject | empty Body between paragraphs: no empty line above", "a\n‸\nb", "image", "a\n$MD\n‸\nb"),
            row("P2 insertObject | empty first paragraph", "‸\na", "image", "$MD\n‸\na"),
            row("P2 insertObject | middle of a list item: both halves keep the item", "- ab‸cd", "image", "- ab\n$MD\n- ‸cd"),
            row("P2 insertObject | middle of a checked item: the second half is unchecked", "- [x] ab‸cd", "image", "- [x] ab\n$MD\n- [ ] ‸cd"),
            row("P2 insertObject | end of a list item: the numbers below restart", "1. a‸\n2. b", "image", "1. a\n$MD\n1. ‸b"),
            row("P2 insertObject | start of a list item", "- ‸ab", "image", "$MD\n- ‸ab"),
            row("P2 insertObject | after the empty last item: the sentinel goes, the item stays", "- a\n- ‸", "image", "- a\n- \n$MD\n‸"),
            row("P2 insertObject | middle of a marked word: the marks are cut at the object", "**a‸b**", "image", "**a**\n$MD\n**‸b**"),
            row("P2 insertObject | selection inside a paragraph is deleted first", "a«bc»d", "image", "a\n$MD\n‸d"),
            row("P2 insertObject | selection across paragraphs", "# a«b\n- c»d", "image", "# a\n$MD\n# ‸d"),
            row("P2 insertObject | selected object is replaced", "a\n---\nb", "image", "a\n$MD\n‸b", "2-3", "4"),
            row("P2 insertObject | selected object, an empty last item after it: replaced in place", "a\n---\n- ", "image", "a\n$MD\n- ", "2-3", "5"),
            row("P2 insertObject | selected object, a list item after it: replaced in place", "a\n---\n- b", "image", "a\n$MD\n- b", "2-3", "4"),
            row("P2 insertObject | selected last object: replaced, an empty Body after it", "a\n---", "image", "a\n$MD\n", "2-3", "4"),
            row("P2 hr | a divider over a selected image", "a\n$MD\nb", "hr", "a\n---\nb", "2-3", "4"),
            row("P2 insertObject | after an object (caret at its end)", "a\n---", "image", "a\n---\n$MD\n", "3", "6"),
            row("P2 insertObject | before an object (caret at its start)", "---", "image", "$MD\n---", "0", "2"),
            row("P2 insertObject | between two objects", "---\n---", "image", "---\n$MD\n---", "1", "4"),
            row("P2 insertObject | after the last object, document ends there", "a\n$MD", "image", "a\n$MD\n$MD\n", "3", "6"),
            // ---- the delete forms over a selected object ----
            row("P2 Backspace | selected object: its paragraph goes", "a\n---\nb", "backspace", "a\nb", "2-3", "2"),
            row("P2 Backspace | selected last object: the line break before it goes too", "a\n---", "backspace", "a", "2-3", "1"),
            row("P2 Backspace | selected only object", "---", "backspace", "", "0-1", "0"),
            row("P2 Cut | the cut of a selected object is the same deletion", "a\n$MD\nb", "backspace", "a\nb", "2-3", "2"),
            row("P2 Delete | selected object: its paragraph goes", "a\n---\nb", "forwarddelete", "a\nb", "2-3", "2"),
            row("P2 Delete | selected last object", "a\n---", "forwarddelete", "a", "2-3", "1"),
            row("P2 Delete | selected object under a list: the next paragraph keeps its kind", "---\n- b", "forwarddelete", "- b", "0-1", "0"),
            row("P2 Delete | a caret is not handled here: nothing", "a‸\n---", "forwarddelete", "a‸\n---"),
        )
    }
}
