package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText

/**
 * End-to-end guard for the "blank lines disappear on exit" bug. Exercises the real editor flow —
 * stored Markdown -> [MarkdownHtmlConverter.markdownToHtml] -> Aztec.fromHtml -> Aztec.toHtml ->
 * [MarkdownHtmlConverter.htmlToMarkdown] -> stored Markdown — through an actual AztecText so the
 * assertions reflect Aztec's true serialization rather than an assumed one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AztecBlankLineRoundTripTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    /** Round-trips [markdown] through markdownToHtml -> Aztec -> htmlToMarkdown. */
    private fun roundTripThroughAztec(markdown: String): String {
        var editor: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = converter.markdownToHtml(markdown),
                    markdownKey = markdown,
                    htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {},
                    onActionsReady = {},
                )
            }
        }
        rule.waitForIdle()
        val view = rule.activity.findViewById<AztecText>(R.id.aztec)
        editor = view
        var result = ""
        rule.runOnUiThread { result = converter.htmlToMarkdown(editor!!.toHtml()) }
        return result
    }

    @Test
    fun typedAdjacentLinesStayAdjacent() {
        // A single blank-less break: loading "a\nb" then re-saving must NOT gain a blank line even
        // though Aztec serializes the paragraph break as a doubled "\n\n" internally.
        assertEquals("a\nb", roundTripThroughAztec("a\nb"))
    }

    @Test
    fun adjacentLinesStayAdjacent() {
        assertEquals("a\nb\nc", roundTripThroughAztec("a\nb\nc"))
    }

    @Test
    fun blankLineBetweenPlainParagraphsIsPreserved() {
        // A deliberate blank line between two plain paragraphs must survive the round-trip.
        assertEquals("a\n\nb", roundTripThroughAztec("a\n\nb"))
    }

    @Test
    fun oneBlankLineAfterBlockIsPreserved() {
        assertEquals("- [x] done\n\nTotal: 100", roundTripThroughAztec("- [x] done\n\nTotal: 100"))
    }

    @Test
    fun headingThenAdjacentTextSurvives() {
        assertEquals("# Title\nbody", roundTripThroughAztec("# Title\nbody"))
    }

    @Test
    fun oneBlankLineAfterHeadingIsPreserved() {
        // An intentional blank line after a heading is kept (a toggled heading adds none — that's
        // guarded separately).
        assertEquals("# Title\n\nbody", roundTripThroughAztec("# Title\n\nbody"))
    }

    @Test
    fun blankLineBeforeABlockIsPreserved() {
        // A blank BEFORE a block (plain text -> blank -> list) IS kept: plain-left runs are stable.
        val md = "Свадьба\n\n- [x] Кольца 56000"
        assertEquals(md, roundTripThroughAztec(md))
    }

    @Test
    fun blankLineBetweenHeadingAndChecklistIsPreserved() {
        // The reported case: a blank line between a heading and a following checkbox list. The NBSP
        // blank marker survives between blocks, so it round-trips.
        assertEquals("# H\n\n- [ ] task", roundTripThroughAztec("# H\n\n- [ ] task"))
    }

    @Test
    fun blankLineBetweenHeadingAndQuoteIsPreserved() {
        assertEquals("# H\n\n> quote", roundTripThroughAztec("# H\n\n> quote"))
    }

    @Test
    fun blankLineBetweenHeadingAndCodeIsPreserved() {
        assertEquals("# H\n\n```\ncode\n```", roundTripThroughAztec("# H\n\n```\ncode\n```"))
    }

    @Test
    fun blankLineBetweenListAndHeadingIsPreserved() {
        assertEquals("- [ ] task\n\n# H", roundTripThroughAztec("- [ ] task\n\n# H"))
    }

    @Test
    fun blankLinesBetweenTwoHeadingsArePreservedAndStable() {
        // The NBSP blank marker survives between two block elements, so multiple blank lines between
        // two headings round-trip EXACTLY and stay stable over many save/reload cycles — neither
        // collapsing to adjacency nor multiplying (the historical "blank lines multiply" bug).
        var editable: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = "", markdownKey = "seed", htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        editable = rule.activity.findViewById(R.id.aztec)
        var md = "## A\n\n\n\n# B"
        rule.runOnUiThread {
            repeat(5) {
                editable!!.fromHtml(converter.markdownToHtml(md))
                md = converter.htmlToMarkdown(editable!!.toHtml())
            }
        }
        assertEquals("## A\n\n\n\n# B", md)
    }

    @Test
    fun multipleBlankLinesAfterAHeadingArePreservedAndStable() {
        // Multiple blank lines after a block round-trip exactly and never grow (NBSP marker).
        var editor: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = "", markdownKey = "seed", htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        editor = rule.activity.findViewById(R.id.aztec)
        var md = "# A\n\n\nbody"
        rule.runOnUiThread {
            repeat(5) {
                editor!!.fromHtml(converter.markdownToHtml(md))
                md = converter.htmlToMarkdown(editor!!.toHtml())
            }
        }
        // Multiple blanks after a heading are preserved exactly and never grow.
        assertEquals("# A\n\n\nbody", md)
    }

    @Test
    fun blankLineBetweenPlainAndFollowingHeadingIsPreserved() {
        // The reported note's exact shape: heading, plain line, blank, then more headings. The
        // blank between the PLAIN line and the FOLLOWING heading must survive.
        val md = "# Title\nalpha\n\n## beta\n## gamma"
        assertEquals(md, roundTripThroughAztec(md))
    }

    @Test
    fun typingPlainLinesWithEnterDoesNotStoreBlankLines() {
        // Reproduces the on-device bug: typing "Aaa<Enter>Bbb<Enter>ccc" — modelled by incremental
        // inserts, which (unlike a single bulk insert) make Aztec attach paragraph structure so
        // toHtml() emits "\n\n" per break. The flushed markdown must store single line breaks.
        var editor: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = "", markdownKey = "", htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        val ed = rule.activity.findViewById<AztecText>(R.id.aztec)
        rule.runOnUiThread {
            ed.requestFocus()
            ed.text.insert(ed.selectionStart, "Aaa")
            ed.text.insert(ed.selectionStart, "\n")
            ed.text.insert(ed.selectionStart, "Bbb")
            ed.text.insert(ed.selectionStart, "\n")
            ed.text.insert(ed.selectionStart, "ccc")
        }
        rule.waitForIdle()
        var md = ""
        rule.runOnUiThread { md = converter.htmlToMarkdown(ed.toHtml()) }
        assertEquals("Aaa\nBbb\nccc", md)
    }

    @Test
    fun headingThenLineRendersWithoutPhantomBlankLine() {
        // The reported bug: loading "# A\nbody" rendered a stray empty line under the heading
        // because markdownToHtml put a "<br>" right after "</h1>". The loaded editable must read
        // "A\nbody" (adjacent), NOT "A\n\nbody".
        var editable = ""
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = converter.markdownToHtml("# A\nbody"), markdownKey = "# A\nbody",
                    htmlToMarkdown = converter::htmlToMarkdown, onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        val ed = rule.activity.findViewById<AztecText>(R.id.aztec)
        rule.runOnUiThread { editable = ed.editableText.toString().trimEnd('​', '\n') }
        assertEquals("A\nbody", editable)
    }

    @Test
    fun consecutiveHeadingsRenderWithoutPhantomBlankLine() {
        var editable = ""
        val md = "# A\n# B\nbody"
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = converter.markdownToHtml(md), markdownKey = md,
                    htmlToMarkdown = converter::htmlToMarkdown, onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        val ed = rule.activity.findViewById<AztecText>(R.id.aztec)
        rule.runOnUiThread { editable = ed.editableText.toString().trimEnd('​', '\n') }
        assertEquals("A\nB\nbody", editable)
    }

    @Test
    fun consecutiveH1HeadingsSurvive() {
        assertEquals("# A\n# B", roundTripThroughAztec("# A\n# B"))
    }

    @Test
    fun consecutiveH2HeadingsSurvive() {
        assertEquals("## A\n## B", roundTripThroughAztec("## A\n## B"))
    }

    @Test
    fun multiHeadingNoteShapeSurvivesWithoutGainingBlankLines() {
        // The reported "blank lines after reopen" note: a mix of H1, H2 and plain lines, NO blank
        // lines between them. The round-trip must NOT insert blank lines — the visible gap on
        // reopen is heading vertical padding (rendering), not stored newlines.
        val md = "# vdavfdvdvsdv\n## Adsvfavafv\n## Bdbvfxbfb\n# Ndghnhgnvdv\n# vdfvdvdfvdsvfv"
        assertEquals(md, roundTripThroughAztec(md))
    }
}
