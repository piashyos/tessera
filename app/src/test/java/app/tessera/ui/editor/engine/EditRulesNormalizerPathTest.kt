package app.tessera.ui.editor.engine

import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The deletion of a selection also reaches the engine through the IME, past [EditRules]: the normalizer alone must give the same kind (spec §6.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditRulesNormalizerPathTest {
    private val style = EngineTestStyle.create()
    private val normalizer = Normalizer(style)

    private fun result(md: String, start: Int, end: Int): List<ParagraphKind> {
        val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))
        val origin = normalizer.originKind(e, start)
        e.delete(start, end)
        normalizer.normalize(e, start, start, origin)
        EditFixture.assertSane("ime delete", e, style)
        return DocBinder.read(e).blocks.map { (it as app.tessera.core.text.notedoc.NoteBlock.Text).kind }
    }

    @Test
    fun aSelectionFromTheStartOfABodyIntoAHeadingLeavesBody() {
        assertEquals(listOf(ParagraphKind.Body), result("ab\n## cd", 0, 4))
    }

    @Test
    fun aSelectionFromTheMiddleOfAHeadingIntoBodyLeavesTheHeading() {
        assertEquals(listOf(ParagraphKind.Heading), result("## ab\ncd", 1, 4))
    }
}
