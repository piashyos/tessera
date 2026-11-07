package app.tessera.ui.editor.engine

import app.tessera.core.text.notedoc.Mark
import app.tessera.ui.editor.engine.EngineFuzz.A
import app.tessera.ui.editor.engine.EngineFuzz.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One test per bug that the fuzzer of Chunk 4c found: the minimized sequence with its seed (positions are fractions of the text length, so
 * the replay is exact), and where the cause is plain, a direct test of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineFuzzRegressionTest {
    private fun st(a: A, x: Double = 0.0, y: Double = 0.0, z: Double = 0.0, i: Int = 0, s: String = "") = Step(a, x, y, z, i, s)

    private fun replay(seed: Int, start: String, steps: List<Step>) {
        val f = EngineFuzz.runSteps(start, steps, label = "seed $seed")
        assertNull("seed $seed: $f", f)
    }

    /** Spec §4 rule 11: a carriage return is a space in the document, so the editor holds a space. Seed 0. */
    @Test
    fun aPastedCarriageReturnBecomesASpace() {
        val s = EngineFuzz.Session("")
        s.h.view.setClip("a\rb")
        s.h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("a b", s.h.text.toString())
        s.check("cr")
    }

    /** Rule 12: a selection that ends inside a surrogate pair does not cut the pair in two marks. Seed 2. */
    @Test
    fun aMarkOverASelectionEndingInsideASurrogatePairTakesTheWholeOrNoneOfIt() {
        for (selEnd in 1..3) {
            val s = EngineFuzz.Session("x\uD83C\uDDF7y")
            s.h.select(0, selEnd)
            s.h.view.toggleMark(Mark.Strike)
            s.check("pair, selection 0..$selEnd")
        }
        val s = EngineFuzz.Session("x\uD83C\uDDF7y")
        s.h.select(2, 4)
        s.h.view.toggleMark(Mark.Bold)
        s.check("pair, selection 2..4")
    }

    @Test
    fun seed2_aMarkEndingInsideASurrogatePair() = replay(
        2, "above\n# title\n| A | B |\n|---|---|\n| 1 | 2 |\nbelow",
        listOf(
            st(A.BACK_DELETE, 0.284440, 0.003573, 0.829055, 625), st(A.BACK_IC, 0.570545, 0.423814, 0.268245, 841),
            st(A.PASTE_STYLED, 0.109015, 0.724513, 0.800777, 833, "## y"), st(A.COPY_PASTE, 0.568774, 0.122272, 0.893120, 265),
            st(A.COMPOSE, 0.261022, 0.469093, 0.087781, 500, "при"), st(A.TYPE, 0.574967, 0.968805, 0.563842, 132, "\uD83C\uDDF7\uD83C\uDDFA"),
            st(A.DELETE_SEL, 0.888949, 0.382747, 0.308278, 872), st(A.KIND, 0.158690, 0.775943, 0.323050, 945),
            st(A.DELETE_SEL, 0.614192, 0.997884, 0.998234, 739), st(A.MARKER, 0.669610, 0.174345, 0.775496, 660, "1. "),
            st(A.ENTER_KEY, 0.628179, 0.080965, 0.718196, 392), st(A.TYPE, 0.422498, 0.734514, 0.367689, 144, "+"),
            st(A.TYPE, 0.244547, 0.993172, 0.718716, 373, "|"), st(A.MARKER, 0.712779, 0.119810, 0.823782, 250, "1) "),
            st(A.COMPOSE, 0.538112, 0.808366, 0.718848, 203, "hello"), st(A.PASTE_EXT, 0.296015, 0.410682, 0.587904, 500, "\u00A0a\u00A0b\u00A0"),
            st(A.MARK, 0.744572, 0.935001, 0.457298, 871, "sel"),
        ),
    )

    /** An empty last non-Body paragraph holds a sentinel and the block span covers it: the paragraph is not left with a span of nothing. Seed 56. */
    @Test
    fun seed56_aSentinelParagraphKeepsItsSpanWhenTheWholeTextIsDeleted() = replay(
        56, "",
        listOf(
            st(A.TYPE, 0.933056, 0.430096, 0.008934, 154, "\uD83D\uDE00"), st(A.ENTER_IC, 0.268256, 0.171957, 0.797789, 59),
            st(A.KIND, 0.153568, 0.663582, 0.665422, 144, "sel"), st(A.BACK_DELETE, 0.007286, 0.254176, 0.264219, 272),
            st(A.DELETE_SEL, 0.028386, 0.950769, 0.097609, 574),
        ),
    )

    /** Rule 4: a mono line has no marks, however it became mono (the text typed over a selection took the kind of the first paragraph). Seed 35. */
    @Test
    fun seed35_noMarkInAMonoLineAfterTheSelectionWasReplaced() = replay(
        35, EngineFuzz.startOf(35),
        listOf(
            st(A.CUT_PASTE, 0.265489, 0.970896, 0.596945, 77), st(A.HR, 0.795994, 0.842534, 0.896237, 437),
            st(A.MARK, 0.732053, 0.700214, 0.892181, 144, "sel"),
            st(A.TYPE, 0.022797, 0.791094, 0.824429, 205, "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"),
            st(A.DELETE_SEL, 0.498687, 0.742622, 0.526085, 901),
        ),
    )

    @Test
    fun aTypedMarkAcrossAMonoParagraphIsDropped() {
        val s = EngineFuzz.Session("a\n```\ncode\n```")
        s.h.caret(2)
        s.h.view.toggleMark(Mark.Bold)
        s.h.select(1, 4)
        s.h.ic().commitText("q", 1) // the text over the selection takes the kind of the first paragraph (Mono)
        s.check("mono marks")
        s.h.select(0, s.h.text.length)
        s.h.view.toggleMark(Mark.Bold)
        s.check("mono marks 2")
    }

    /** Rule 9: two tables in a row read back as one; an empty Body goes between them on the screen. Seed 3060. */
    @Test
    fun twoTablesInARowAreKeptApartByAnEmptyBody() {
        val md = "| A |\n| --- |\n| 1 |\n\n| B |\n| --- |\n| 2 |"
        val s = EngineFuzz.Session(md)
        val e = s.h.text
        val empty = e.toString().indexOf('\uFFFC') + 2 // the empty paragraph between the two objects
        s.h.select(empty - 1, empty + 1)
        s.h.ic().sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
        s.check("tables apart")
        assertEquals(2, s.h.text.count { it == '\uFFFC' })
        // and by a paste of a fragment that starts with a table next to a table
        val t = EngineFuzz.Session("| A |\n| --- |\n| 1 |")
        t.h.select(0, t.h.text.length)
        t.h.view.onTextContextMenuItem(android.R.id.copy)
        t.h.caret(t.h.text.length)
        t.h.view.onTextContextMenuItem(android.R.id.paste)
        t.check("tables pasted")
    }

    @Test
    fun seed110005_marksOverACodeAreOneMarkEachOrNone() = replay(
        110005, EngineFuzz.startOfLayout(110005),
        listOf(
            st(A.TYPE, 0.918193, 0.982763, 0.586959, 748, "\u2764\uFE0F"), st(A.COPY_PASTE, 0.603878, 0.755059, 0.111574, 468),
            st(A.ENTER_KEY, 0.794477, 0.617989, 0.139347, 306), st(A.CUT_PASTE, 0.706942, 0.472959, 0.947682, 670),
            st(A.TYPE, 0.909955, 0.913608, 0.070212, 759, "("), st(A.TYPE, 0.547820, 0.977619, 0.797421, 809, "я"),
            st(A.COPY_PASTE, 0.718694, 0.676396, 0.264476, 408), st(A.BACK_IC, 0.211290, 0.961452, 0.278760, 764),
            st(A.TYPE, 0.164601, 0.825441, 0.414016, 502, "\\"), st(A.TYPE, 0.237601, 0.918599, 0.623216, 505, "*"),
            st(A.TYPE, 0.159837, 0.292137, 0.993775, 810, "|"), st(A.BACK_KEY, 0.608315, 0.987783, 0.258666, 131),
            st(A.COPY_PASTE, 0.748663, 0.336289, 0.033767, 274), st(A.UNDO_CMD, 0.449187, 0.774668, 0.355770, 273, "sel"),
            st(A.CUT_PASTE, 0.170929, 0.544569, 0.514241, 536),
        ),
    )

    /** Rule 1 and 3 on the screen: marks of one kind that touch are one, so a mark that covers a code is judged whole. */
    @Test
    fun adjacentMarksOfOneKindAreJudgedTogetherAgainstACode() {
        val s = EngineFuzz.Session("ab `cd` ef")
        s.h.select(0, 3)
        s.h.view.toggleMark(Mark.Italic) // ab + the space before the code
        s.h.select(3, 8)
        s.h.view.toggleMark(Mark.Italic) // the code and what follows: touches the first mark
        s.check("marks over code")
        assertFalse(s.h.md().isEmpty())
    }

    /** Found by the corpus (a paragraph of 11 000 characters): Backspace looked at the whole paragraph every time and ran the heap out. */
    @Test
    fun backspaceInAVeryLongParagraphTakesAWholeClusterAndLooksOnlyAroundTheCaret() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        val s = EngineFuzz.Session("слово word ".repeat(1000) + family)
        s.use {
            val h = s.h
            h.caret(h.text.length)
            h.ic().deleteSurroundingText(1, 0)
            assertEquals("the family goes in one press", 11_000, h.text.length)
            val t0 = System.nanoTime()
            repeat(1000) { h.ic().deleteSurroundingText(1, 0) }
            val ms = (System.nanoTime() - t0) / 1e6
            println("backspace in an 11 000-character paragraph: %.2f ms per press".format(ms / 1000))
            assertEquals(10_000, h.text.length)
            s.check("long paragraph")
        }
    }
}
