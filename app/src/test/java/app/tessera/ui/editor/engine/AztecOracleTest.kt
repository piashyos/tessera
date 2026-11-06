package app.tessera.ui.editor.engine

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.text.notedoc.NoteDocFixtures
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.RichNoteBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText
import java.io.File

/**
 * Oracle (spec §8): what the current Aztec path shows for a stored Markdown against what the engine shows —
 * visible text and kind of every line. Lives until Aztec is removed (stage 2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AztecOracleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()
    private val style = EngineTestStyle.create()
    private var editor: AztecText? = null

    private val corpus = NoteDocFixtures.legacyCorpus

    private fun aztec(): (String) -> List<OracleLine> {
        if (editor == null) {
            rule.setContent {
                TesseraTheme(darkTheme = false, dynamicColor = false) {
                    RichNoteBody(html = "", markdownKey = "", htmlToMarkdown = converter::htmlToMarkdown, onMarkdownChange = {}, onActionsReady = {})
                }
            }
            rule.waitForIdle()
            editor = rule.activity.findViewById(R.id.aztec)
        }
        return { md ->
            var lines = emptyList<OracleLine>()
            rule.runOnUiThread {
                editor!!.fromHtml(converter.markdownToHtml(md))
                lines = AztecOracle.aztecLines(editor!!.text)
            }
            lines
        }
    }

    private fun compare(markdowns: List<String>) = AztecOracle.compare(markdowns, style, aztec())

    @Test
    fun corpusDivergencesAreAllAllowed() {
        val result = compare(corpus)
        println("oracle: skipped (a table): ${corpus.filter(AztecOracle::hasTable).map { it.replace("\n", "\\n") }}")
        val unexplained = result.filter { it.rule == null }
        println("oracle: ${corpus.size} documents, ${result.size} differ, ${unexplained.size} unexplained")
        result.forEach { println(it) }
        assertTrue("unexplained divergences:\n" + unexplained.joinToString("\n"), unexplained.isEmpty())
    }


    // ---- allowed divergences (spec §8): one case each, with the example that Aztec shows differently ----

    private fun assertExplainedBy(rule: String, vararg markdowns: String) {
        val result = compare(markdowns.toList())
        markdowns.forEach { md ->
            val d = result.firstOrNull { it.markdown == md }
            assertTrue("no divergence for ${md.replace("\n", "\\n")} - the example does not diverge", d != null)
            assertEquals(d.toString(), rule, d!!.rule)
        }
    }

    /** `* `, `+ `, `N) `, `###`..`######`, `[X]` and an indent before a marker: Aztec showed them as text. */
    @Test
    fun allowedLiteralBlockMarkers() = assertExplainedBy(
        "literal block marker",
        "* star item", "+ plus item", "2) paren item", "### three", "###### six", "* [ ] star box", "- [X] big x", "    - indented", "    1. indented number", "    - [x] indented box",
    )

    /** `***` / `___` as a divider. */
    @Test
    fun allowedLiteralRules() = assertExplainedBy("literal rule", "***", "___")

    /** `2 * 3 * 4`: Aztec made italics out of it, the delimiter-run parser keeps the text. */
    @Test
    fun allowedEmphasisFlanking() = assertExplainedBy("emphasis flanking", "2 * 3 * 4")

    /** A typed `<b>`, `<i>`, `<s>`: Aztec kept the tag as text, the dialect reads a mark. */
    @Test
    fun allowedLiteralTags() = assertExplainedBy("literal tags b i s", "a <b>x</b> b", "<i>y</i>", "<s>z</s>")

    /** `\*` outside code. */
    @Test
    fun allowedBackslashBeforePunctuation() = assertExplainedBy("backslash before punctuation", "a \\* b", "\\# not a title")

    /** Code keeps its backslash on both sides: no divergence at all. */
    @Test
    fun backslashInCodeDoesNotDiverge() {
        assertTrue(compare(listOf("`\\d+\\.` here")).isEmpty())
    }

    /** What is not in the list is not explained. */
    @Test
    fun anOtherDifferenceIsNotExplained() {
        val wrongKind = { _: String -> listOf(OracleLine("x", OracleKind.Quote)) }
        val d = AztecOracle.compare(listOf("x"), style, wrongKind).single()
        assertEquals(null, d.rule)
        val wrongText = { _: String -> listOf(OracleLine("y", OracleKind.Body)) }
        assertEquals(null, AztecOracle.compare(listOf("x"), style, wrongText).single().rule)
        // a parser that left real emphasis literal must not hide behind the flanking rule
        val flanking = AztecOracle.allowed.first { it.name == "emphasis flanking" }
        fun body(t: String) = OracleLine(t, OracleKind.Body)
        assertTrue(flanking.explains(body("2 3 4"), body("2 * 3 * 4")))
        assertTrue(!flanking.explains(body("bold"), body("**bold**")))
        assertTrue(!flanking.explains(body("x"), body("~~x~~")))
        assertTrue(!flanking.explains(body("abc"), body("a*b*c")))
        val extraLine = { _: String -> listOf(OracleLine("x", OracleKind.Body), OracleLine("tail", OracleKind.Body)) }
        assertEquals(null, AztecOracle.compare(listOf("x"), style, extraLine).single().rule)
    }

    /** Fixed by the legacy-emphasis pass of Chunk 2 (spec §5.3): these must match Aztec, they are not allowed divergences. */
    @Test
    fun legacyEmphasisMatchesAztec() {
        val strict = listOf("**bold **text", "~~s ~~x", "**a****b**", "~~a~~~~b~~")
        val result = compare(strict)
        assertTrue("legacy emphasis diverges:\n" + result.joinToString("\n"), result.isEmpty())
    }

    /** `** bold**`: only the leading space differs (Aztec collapses it on load). */
    @Test
    fun allowedLeadingSpaces() {
        assertExplainedBy("leading spaces", "** bold**", "   indented text")
        // not only the leading spaces: not explained
        val d = AztecOracle.compare(listOf("x"), style) { listOf(OracleLine("y", OracleKind.Body)) }.single()
        assertEquals(null, d.rule)
        val otherKind = AztecOracle.compare(listOf(" x"), style) { listOf(OracleLine("x", OracleKind.Quote)) }.single()
        assertEquals(null, otherKind.rule)
    }

    /** A fence of 4+ backticks inside legacy code (orchestrator's list). */
    @Test
    fun allowedLongFence() = assertExplainedBy(AztecOracle.LONG_FENCE_RULE, "```\na\n````\n*b*\n```\n# T")

    /** The 4+ backtick line outside code does not mask another divergence; only the tail after it is explained. */
    @Test
    fun longFenceDoesNotMaskOtherDivergences() {
        val md = "zzz\n````\nq"
        // the first differing line is not the Mono line of 4+ backticks
        val fake = { _: String -> listOf(OracleLine("other", OracleKind.Body), OracleLine("````", OracleKind.Mono), OracleLine("q", OracleKind.Mono)) }
        assertEquals(null, AztecOracle.compare(listOf(md), style, fake).single().rule)
        // a Mono line of 4+ backticks that is not the first differing line does not explain a difference before it
        val fake2 = { _: String -> listOf(OracleLine("zzz", OracleKind.Quote), OracleLine("````", OracleKind.Mono)) }
        assertEquals(null, AztecOracle.compare(listOf(md), style, fake2).single().rule)
    }

    /** The kind the dialect reads follows from the literal marker. */
    @Test
    fun literalMarkerMustGiveTheKindOfItsMarker() {
        val marker = AztecOracle.allowed.first { it.name == "literal block marker" }
        fun body(t: String) = OracleLine(t, OracleKind.Body)
        assertTrue(marker.explains(body("* x"), OracleLine("x", OracleKind.Bullet)))
        assertTrue(marker.explains(body("2) x"), OracleLine("x", OracleKind.Number)))
        assertTrue(marker.explains(body("### x"), OracleLine("x", OracleKind.Subheading)))
        assertTrue(marker.explains(body("* [ ] x"), OracleLine("x", OracleKind.CheckOff)))
        assertTrue(marker.explains(body("+ [x] x"), OracleLine("x", OracleKind.CheckOn)))
        // same text, other kind: not explained
        assertTrue(!marker.explains(body("* x"), OracleLine("x", OracleKind.Quote)))
        assertTrue(!marker.explains(body("2) x"), OracleLine("x", OracleKind.Bullet)))
        assertTrue(!marker.explains(body("### x"), OracleLine("x", OracleKind.Heading)))
        assertTrue(!marker.explains(body("* [ ] x"), OracleLine("x", OracleKind.CheckOn)))
        assertTrue(!marker.explains(body("* x"), OracleLine("x", OracleKind.Body)))
        // an ordinary Body line is not a marker
        assertTrue(!marker.explains(body("x"), OracleLine("x", OracleKind.Bullet)))
    }

    /** Adjacent italics `*a**b*` (orchestrator's list): today it matches Aztec; if it ever diverges, it must be a printed, explained one. */
    @Test
    fun adjacentItalics() {
        val result = compare(listOf("*a**b*"))
        println("adjacent italics: ${if (result.isEmpty()) "matches Aztec" else result.single().toString()}")
        assertTrue(result.toString(), result.all { it.rule != null })
    }

    /** What Aztec adds past the end of a plain note, as found by the corpus run. */
    @Test
    fun allowedEndOfBufferLines() = assertExplainedBy(AztecOracle.END_OF_BUFFER, "a\nb")

    /** Dump of real notes: every .md file in `app/build/oracle-dump/`, one note per file (QA puts it there, Chunk 6). */
    @Test
    fun realNotesDump() {
        val dir = File("build/oracle-dump")
        assumeTrue("no dump at ${dir.absolutePath}", dir.isDirectory)
        val files = dir.listFiles { f -> f.extension == "md" }.orEmpty().sortedBy { it.name }
        assumeTrue("empty dump", files.isNotEmpty())
        val result = AztecOracle.compare(files.map { it.readText() }, style, aztec())
        val names = files.map { it.name }
        println("oracle dump: ${files.size} notes, skipped (a table): ${files.filter { AztecOracle.hasTable(it.readText()) }.map { it.name }}, ${result.size} differ")
        val unexplained = result.filter { it.rule == null }
        unexplained.forEach { println("UNEXPLAINED ${names[files.indexOfFirst { f -> f.readText() == it.markdown }]}: $it") }
        assertEquals("unexplained divergences in the dump", emptyList<Divergence>(), unexplained)
    }
}
