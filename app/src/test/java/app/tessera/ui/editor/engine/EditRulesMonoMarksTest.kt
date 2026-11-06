package app.tessera.ui.editor.engine

import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A mono line has no marks (spec §4, rule 4), whichever command made the line mono: `assertSane` compares the marks with a fresh build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditRulesMonoMarksTest {
    private val style = EngineTestStyle.create()
    private val rules = EditRules(style)

    @Test
    fun theFenceMarkerOverMarkedText() {
        val f = EditFixture("**ab**‸", "", style)
        rules.replaceMarker(f.e, 0, 0, ParagraphKind.Mono)
        EditFixture.assertSane("marker", f.e, style)
        assertEquals(EditFixture.doc("```\nab\n```"), DocBinder.read(f.e))
    }

    @Test
    fun backspaceMergesMarkedBodyIntoTheMonoLineAbove() {
        val f = EditFixture("```\nab\n```\n**cd**", "", style)
        val p = f.e.toString().indexOf("cd")
        rules.backspace(f.e, p, p)
        EditFixture.assertSane("merge", f.e, style)
        assertEquals(EditFixture.doc("```\nabcd\n```"), DocBinder.read(f.e))
    }

    @Test
    fun deletingASelectionFromAMonoLineIntoMarkedBody() {
        val f = EditFixture("```\nab\n```\nx**cd**", "", style)
        val s = f.e.toString().indexOf("b")
        rules.backspace(f.e, s, f.e.toString().indexOf("x") + 1)
        EditFixture.assertSane("delete", f.e, style)
        assertEquals(EditFixture.doc("```\nacd\n```"), DocBinder.read(f.e))
    }
}
