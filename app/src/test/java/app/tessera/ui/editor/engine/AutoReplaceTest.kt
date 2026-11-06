package app.tessera.ui.editor.engine

import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §6.5: every row of the table, and what must not trigger it. A row: `name | text with the caret before the space | typed | result ("=" — none)`. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class AutoReplaceTest(
    private val name: String,
    private val input: String,
    private val typed: String,
    private val expected: String,
) {
    private val style = EngineTestStyle.create()

    @Test
    fun cell() {
        val f = EditFixture(input, "", style)
        val c = f.selStart
        val before = f.e.toString()
        f.e.insert(c, " ")
        val r = AutoReplace(EditRules(style)).tryApply(f.e, c until c + 1, typed == "typed")
        if (expected == "=") {
            assertNull("$name: nothing happens", r)
            assertEquals("$name: the space stays", before.substring(0, c) + " " + before.substring(c), f.e.toString())
            return
        }
        assertNotNull("$name: replaced", r)
        EditFixture.assertSane(name, f.e, style)
        assertEquals("$name: document", EditFixture.doc(expected), DocBinder.read(f.e))
        assertEquals("$name: caret", EditFixture.markerPositions(expected).first, EditFixture.visible(f.e, r!!.caret))
    }

    companion object {
        private fun row(name: String, input: String, expected: String, typed: String = "typed") = arrayOf<Any>(name, input, typed, expected)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cells(): List<Array<Any>> = listOf(
            row("6.5 `* ` -> bullet", "\\*‸", "- ‸"),
            row("6.5 `- ` -> dash", "\\-‸", "+ ‸"),
            row("6.5 `1. ` -> number", "1\\.‸", "1. ‸"),
            row("6.5 `1) ` -> number", "1\\)‸", "1. ‸"),
            row("6.5 `[] ` -> check", "[]‸", "- [ ] ‸"),
            row("6.5 `[ ] ` -> check", "[ ]‸", "- [ ] ‸"),
            row("6.5 `# ` -> title", "\\#‸", "# ‸"),
            row("6.5 `## ` -> heading", "\\##‸", "## ‸"),
            row("6.5 `### ` -> subheading", "\\###‸", "### ‸"),
            row("6.5 `> ` -> quote", "\\>‸", "> ‸"),
            row("6.5 ``` + space -> mono", "\\```‸", "```\n‸\n```"),
            row("6.5 | in the middle of the document", "a\n\\-‸\nb", "a\n+ ‸\nb"),
            row("6.5 | text after the marker stays", "\\-‸text", "+ ‸text"),
            row("6.5 | the next list item numbers on", "1. a\n1\\.‸", "1. a\n2. ‸"),
            row("6.5 no | not at the start of the paragraph", "a-‸", "="),
            row("6.5 no | a leading space before the marker", " \\-‸", "="),
            row("6.5 no | a list item is not Body", "- \\*‸", "="),
            row("6.5 no | a heading is not Body", "## \\-‸", "="),
            row("6.5 no | a quote is not Body", "> \\-‸", "="),
            row("6.5 no | a mono line is not Body", "```\n-‸\n```", "="),
            row("6.5 no | #### is not a marker", "\\####‸", "="),
            row("6.5 no | 2. is not a marker", "2\\.‸", "="),
            row("6.5 no | [x] is not a marker", "[x]‸", "="),
            row("6.5 no | 1.5 is not a marker", "1\\.5‸", "="),
            row("6.5 no | a code fence with a language", "\\```kotlin‸", "="),
            row("6.5 no | a paste", "\\-‸", "=", "pasted"),
        )
    }

    @RunWith(RobolectricTestRunner::class)
    @Config(sdk = [34])
    class Shapes {
        private val style = EngineTestStyle.create()

        @Test
        fun aComposingRegionReplacedWithTheMarkerAndTheSpaceTriggers() {
            val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse("\\-"), style))
            e.replace(0, 1, "- ") // the keyboard re-commits the word with the space
            val r = AutoReplace(EditRules(style)).tryApply(e, 1 until 2, true)
            assertEquals(ParagraphKind.ListItem(ListType.Dash, 0), r?.kind)
            assertEquals("- ", r?.typed)
        }

        @Test
        fun deletingTextThatLeavesTheMarkerAndASpaceDoesNotTrigger() {
            val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse("\\- x"), style))
            e.delete(2, 3) // "- " left over
            assertNull(AutoReplace(EditRules(style)).tryApply(e, 2 until 2, true))
        }

        @Test
        fun anImeThatRecommitsTheSameMarkerAndSpaceInsertsNothingAndDoesNotTrigger() {
            // Body "- " (an undone auto-replace); the keyboard replaces the whole region by the same text
            val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse("\\- "), style))
            val tp = TypingAttributes(style)
            tp.beforeChange(e, 0, 2)
            e.replace(0, 2, "- ")
            val inserted = tp.afterChange(e, 2)
            assertEquals(true, inserted.isEmpty())
            assertNull(AutoReplace(EditRules(style)).tryApply(e, inserted, true))
            assertEquals("- ", e.toString())
        }

        @Test
        fun aLongLineIsNotLookedAtOnASpace() {
            val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse("a long paragraph of text "), style))
            val rules = EditRules(style)
            assertEquals(false, AutoReplace(rules).wouldApply(e, e.length - 1 until e.length, true))
        }
    }
}
