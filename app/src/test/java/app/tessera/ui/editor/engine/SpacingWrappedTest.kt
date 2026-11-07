package app.tessera.ui.editor.engine

import android.text.StaticLayout
import android.text.TextPaint
import app.tessera.core.text.notedoc.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spacing of paragraphs that wrap (native graphics: legacy Robolectric never wraps). The top goes to the first line of a paragraph
 * only, the bottom to the last only; the lines between get nothing, so the line spacing inside a paragraph stays the normal one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpacingWrappedTest {
    private val style = EngineTestStyle.create()
    private val flat = style.copy(
        titleSpaceTopPx = 0f, titleSpaceBottomPx = 0f, headingSpaceTopPx = 0f, headingSpaceBottomPx = 0f,
        blockEdgePx = 0f, listItemPaddingPx = 0f, quotePaddingVerticalPx = 0f, monoPaddingVerticalPx = 0f,
    )

    private fun heights(md: String, st: EditorStyle): List<Int> {
        val text = DocBinder.build(NoteMarkdown.parse(md), st)
        DocBinder.setObjectWidth(text, 400)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = st.bodyTextSizePx }
        val l = StaticLayout.Builder.obtain(text, 0, text.length, paint, 400).setLineSpacing(8f, 1f).build()
        return (0 until l.lineCount).map { l.getLineBottom(it) - l.getLineTop(it) }
    }

    private fun extras(md: String): List<Int> {
        val with = heights(md, style)
        val without = heights(md, flat)
        assertEquals(md, without.size, with.size)
        return with.zip(without) { a, b -> a - b }
    }

    private val long = "word ".repeat(60).trim()


    /** Every kind, wrapped: the top goes to the first line of the paragraph only, the bottom to the last only, inner lines get nothing. */
    @Test
    fun wrappedParagraphsGetSpacingOnlyAtTheirEdges() {
        val kinds = mapOf(
            "title" to "# $long", "heading" to "## $long", "subheading" to "### $long",
            "bullet" to "- $long", "dash" to "* $long", "check" to "- [ ] $long", "number" to "1. $long",
            "quote" to "> $long", "mono" to "```\n$long\n```",
        )
        for ((name, md) in kinds) for (doc in listOf(md, "above\n$md\nbelow", "$md\nbelow", "above\n$md")) {
            val e = extras(doc)
            val lines = e.size
            assertEquals("$name: $doc wraps", true, lines >= 4)
            // paragraph lines: every line but the first and the last of THE PARAGRAPH carries no addition
            val first = if (doc.startsWith("above")) 1 else 0
            val last = if (doc.endsWith("below")) lines - 2 else lines - 1
            for (i in first + 1 until last) assertEquals("$name inner line $i of ${e}", 0, e[i])
            assertEquals("$name above is Body", 0, e.getOrElse(0) { 0 }.takeIf { first == 1 } ?: 0)
            // and the edges carry exactly what Spacing says: the top on the first line of the paragraph, the bottom on its last
            val text = DocBinder.build(NoteMarkdown.parse(doc), style)
            DocBinder.setObjectWidth(text, 400)
            val size = style.bodyTextSizePx
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 400).setLineSpacing(8f, 1f).build()
            val ps = paragraphStart(text, text.toString().indexOf("word"))
            val sp = SpacingRules.of(text, ps, style)
            val firstLine = layout.getLineForOffset(ps)
            val lastLine = layout.getLineForOffset(maxOf(paragraphEnd(text, ps) - 1, ps))
            assertEquals("$name: extraTop of the first line in $doc", sp.extraTop, e[firstLine])
            assertEquals("$name: extraBottom of the last line in $doc", sp.extraBottom, e[lastLine])
        }
    }
}
