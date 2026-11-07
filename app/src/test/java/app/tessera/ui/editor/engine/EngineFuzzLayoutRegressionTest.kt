package app.tessera.ui.editor.engine

import app.tessera.ui.editor.engine.EngineFuzz.A
import app.tessera.ui.editor.engine.EngineFuzz.Step
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The layout bugs that the layout fuzzer found, as minimized sequences with their seeds; the live layout is compared with a fresh one after
 * EVERY step. Native graphics: lines wrap, a re-run alone if `RenderNodeNatives` flakes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EngineFuzzLayoutRegressionTest {
    private fun st(a: A, x: Double = 0.0, y: Double = 0.0, z: Double = 0.0, i: Int = 0, s: String = "") = Step(a, x, y, z, i, s)

    private fun replay(seed: Int, start: String, steps: List<Step>) {
        val f = EngineFuzz.runSteps(start, steps, layoutEvery = 1, label = "seed $seed")
        assertNull("seed $seed: $f", f)
    }

    /**
     * A paragraph that a deletion collapsed to nothing was still a list item or a quote while the layout measured the paragraphs around it:
     * the item after a Body line measured as the second of a series (10 px of gap lost), and a collapsed span's margin broke a word onto a second
     * line. The normalizer now takes such spans off and makes the layout measure the paragraph again; the layout does not read them as kinds.
     */
    @Test
    fun seed100020_aCollapsedBlockSpanDoesNotMeasureTheNeighbourAsTheNextOfASeries() = replay(
        100020, "",
        listOf(
            st(A.TYPE, 0.073797, 0.515462, 0.711396, 997, "\u202B"), st(A.TYPE, 0.463712, 0.378215, 0.559115, 182, "\u0628"),
            st(A.TYPE, 0.022843, 0.019621, 0.355959, 323, "Z"), st(A.TYPE, 0.538081, 0.044548, 0.987333, 848, "b"),
            st(A.TYPE, 0.077183, 0.079305, 0.580253, 900, "Z"), st(A.TYPE, 0.574312, 0.514237, 0.887963, 579, "o\u0308\u0301"),
            st(A.MARKER, 0.325208, 0.343190, 0.890135, 394, "1) "), st(A.MARKER, 0.597159, 0.139469, 0.175832, 614, "``` "),
            st(A.COPY_PASTE, 0.728330, 0.292143, 0.020587, 474), st(A.ENTER_COMMIT, 0.187932, 0.257188, 0.642152, 676),
            st(A.ENTER_IC, 0.789128, 0.403024, 0.601618, 697), st(A.BACK_IC, 0.816828, 0.356024, 0.458763, 851),
            st(A.ENTER_IC, 0.927142, 0.309622, 0.472100, 97), st(A.COPY_PASTE, 0.144796, 0.598715, 0.086145, 375),
            st(A.BACK_IC, 0.865649, 0.765398, 0.399403, 603), st(A.DELETE_SEL, 0.765819, 0.092983, 0.353881, 658),
        ),
    )

    /** A placeholder moved next to a full-width line: the layout is told about every object that moves (EngineEditable). */
    @Test
    fun seed100162_anObjectThatMovedBreaksNoLineAfterIt() = replay(
        100162, "---\n> ",
        listOf(
            st(A.TYPE, 0.072493, 0.915150, 0.079154, 73, "\u202C"), st(A.TYPE, 0.340718, 0.851152, 0.420993, 563, "."),
            st(A.KIND, 0.645792, 0.418349, 0.292581, 656, "sel"), st(A.ENTER_COMMIT, 0.346004, 0.001914, 0.963490, 334),
            st(A.DELETE_SEL, 0.649346, 0.194428, 0.732331, 467),
        ),
    )

    @Test
    fun seed100155_aWordIsNotBrokenAfterACutOfSeveralParagraphs() = replay(
        100155, "above\n- [ ] check\n> \nbelow",
        listOf(
            st(A.HR, 0.179847, 0.064903, 0.317009, 334), st(A.ENTER_COMMIT, 0.431009, 0.415546, 0.927788, 502),
            st(A.TYPE, 0.468131, 0.822067, 0.904821, 866, "o\u0308\u0301"), st(A.COPY_PASTE, 0.676204, 0.953508, 0.431077, 416),
            st(A.INDENT, 0.027340, 0.536072, 0.827334, 174), st(A.COPY_PASTE, 0.854870, 0.058270, 0.206074, 647),
            st(A.ENTER_KEY, 0.049249, 0.566457, 0.972152, 767), st(A.UNDO_CMD, 0.055842, 0.154396, 0.326752, 863, "sel"),
            st(A.CUT_PASTE, 0.742829, 0.019301, 0.930833, 741),
        ),
    )

    /** The sessions of the layout fuzzer that failed on the way, whole (not minimized). */
    @Test
    fun theSeedsThatFailedStillPassWhole() {
        for (seed in listOf(100007, 100020, 100041, 100052, 100155, 100162, 100163, 110005)) {
            val f = EngineFuzz.runSteps(EngineFuzz.startOfLayout(seed), EngineFuzz.sequence(seed, 120), layoutEvery = 10, label = "seed $seed")
            assertNull("seed $seed: $f", f)
        }
    }
}
