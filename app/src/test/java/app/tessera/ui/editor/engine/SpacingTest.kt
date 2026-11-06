package app.tessera.ui.editor.engine

import android.text.StaticLayout
import android.text.TextPaint
import app.tessera.core.text.notedoc.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vertical spacing of the blocks (the mockup CSS in the model of one paragraph per line): for every pair of neighbours the height that
 * the spacing adds, measured as the difference between a layout with the spacing and one of the same text without it. Margins collapse
 * (the larger counts), a series has margins at its edges only, a list item has its own 3 above and below, the first paragraph has no margin
 * above and the last none below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpacingTest {
    private val style = EngineTestStyle.create()
    private val flat = style.copy(
        titleSpaceTopPx = 0f, titleSpaceBottomPx = 0f, headingSpaceTopPx = 0f, headingSpaceBottomPx = 0f,
        blockEdgePx = 0f, listItemPaddingPx = 0f, quotePaddingVerticalPx = 0f, monoPaddingVerticalPx = 0f,
    )

    /** Height each line gains from the spacing. */
    private fun extras(md: String): List<Int> {
        fun heights(st: EditorStyle): List<Int> {
            val text = DocBinder.build(NoteMarkdown.parse(md), st)
            DocBinder.setObjectWidth(text, 400)
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = st.bodyTextSizePx }
            val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, 400).build()
            return (0 until l.lineCount).map { l.getLineBottom(it) - l.getLineTop(it) }
        }
        val with = heights(style)
        val without = heights(flat)
        assertEquals(md, without.size, with.size)
        return with.zip(without) { a, b -> a - b }
    }

    private val body = "a"
    private val title = "# a"
    private val heading = "## a"
    private val sub = "### a"
    private val item = "- a"
    private val check = "- [ ] a"
    private val number = "1. a"
    private val quote = "> a"
    private val mono = "```\na\n```"
    private val obj = "---"

    private fun pair(a: String, b: String) = "$a\n$b"

    /** The added height of the whole two-paragraph document: both inner paddings, the gap between, nothing outside. */
    private fun total(md: String) = extras(md).sum()

    @Test
    fun bodyAndHeadings() {
        assertEquals("Body/Body", 0, total(pair(body, "b")))
        assertEquals("Body/Title", 12, total(pair(body, title)))
        assertEquals("Title/Body", 8, total(pair(title, body)))
        assertEquals("Title/Heading: the larger of 8 and 12", 12, total(pair(title, heading)))
        assertEquals("Heading/Title: the larger of 6 and 12", 12, total(pair(heading, title)))
        assertEquals("Heading/Body", 6, total(pair(heading, body)))
        assertEquals("Body/Heading", 12, total(pair(body, heading)))
        assertEquals("Subheading/Heading", 12, total(pair(sub, heading)))
        assertEquals("Heading/Subheading", 12, total(pair(heading, sub)))
        assertEquals("Subheading/Body", 6, total(pair(sub, body)))
    }

    @Test
    fun lists() {
        assertEquals("Body/List: 10 outside + 3 + 3 inside", 16, total(pair(body, item)))
        assertEquals("List/Body", 16, total(pair(item, body)))
        assertEquals("List/List: between the items 3 + 3", 12, total(pair(item, "- b")))
        assertEquals("a series of any types is one series", 12, total(pair(check, number)))
        assertEquals("List/Heading: 6 inside + max(10, 12)", 18, total(pair(item, heading)))
        assertEquals("Heading/List: max(6, 10) + 6 inside", 16, total(pair(heading, item)))
        assertEquals("three items: 3 + 3 each", 18, total("- a\n- b\n- c"))
    }

    @Test
    fun quotes() {
        assertEquals("Body/Quote: 10 outside + 4 + 4 inside the bar", 18, total(pair(body, quote)))
        assertEquals("Quote/Quote: inside the series only the ends", 8, total(pair(quote, "> b")))
        assertEquals("Quote/Body", 18, total(pair(quote, body)))
        assertEquals("Quote/List: 4 + 4 inside, 10 between, 6 for the item", 24, total(pair(quote, item)))
    }

    @Test
    fun monos() {
        assertEquals("Body/Mono: 10 outside + the background padding 10 + 10", 30, total(pair(body, mono)))
        assertEquals("Mono/Mono (one series): the padding at the ends, no gap", 20, total("```\na\nb\n```"))
        assertEquals("Mono/Body", 30, total(pair(mono, body)))
        assertEquals("two series with a Body line between", 60, total("```\na\n```\nb\n```\nc\n```"))
    }

    @Test
    fun objects() {
        assertEquals("Body/Object", 10, total(pair(body, obj)))
        assertEquals("Object/Body", 10, total(pair(obj, body)))
        assertEquals("Object/Object", 10, total("---\n---"))
        assertEquals("Object/List", 16, total(pair(obj, item)))
        assertEquals("List/Object", 16, total(pair(item, obj)))
    }

    @Test
    fun theFirstParagraphHasNoMarginAboveAndTheLastNoneBelow() {
        assertEquals("a Title alone", listOf(0), extras(title))
        assertEquals("a Heading alone", listOf(0), extras(heading))
        assertEquals("an object alone", listOf(0), extras(obj))
        assertEquals("a list item alone keeps its own padding", listOf(6), extras(item))
        assertEquals("Title first: nothing above, 8 below", listOf(8, 0), extras(pair(title, body)))
        assertEquals("Title last: 12 above, nothing below", listOf(0, 12), extras(pair(body, title)))
        assertEquals("List first: 3 + 3, then 10 to the Body", listOf(16, 0), extras(pair(item, body)))
    }
}
