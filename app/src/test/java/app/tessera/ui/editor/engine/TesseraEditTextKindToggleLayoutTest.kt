package app.tessera.ui.editor.engine

import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** LAYOUT-H on the JVM: after a change of the kind of a paragraph through the panel the live layout has the line tops of a fresh one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TesseraEditTextKindToggleLayoutTest {
    private val note = "ShoppingTitle line\nFf\nSecond line\nThird line\nFourth line"

    private fun tops(h: HostFixture): List<Int> { val l = h.view.layout!!; return (0..l.lineCount).map { l.getLineTop(it) } }

    private fun assertFresh(label: String, h: HostFixture) {
        h.layout()
        val fresh = HostFixture(h.md()).also { it.layout() }
        assertEquals("$label: line tops", tops(fresh), tops(h))
    }

    @Test fun kindChangesLeaveTheLayoutAsAFreshOne() {
        val h = HostFixture(note)
        h.layout()
        h.caret(3)
        h.view.toggleKind(ParagraphKind.Title); assertFresh("body to H1", h)
        h.view.toggleKind(ParagraphKind.Title); assertFresh("H1 off", h)
        h.view.toggleKind(ParagraphKind.Title); h.view.toggleKind(ParagraphKind.Heading); assertFresh("H1 to H2", h)
        h.view.toggleKind(ParagraphKind.Heading); assertFresh("H2 off", h)
        for (k in listOf(ParagraphKind.ListItem(ListType.Bullet, 0), ParagraphKind.Quote, ParagraphKind.Mono, ParagraphKind.Subheading)) {
            h.caret(h.at("Ff", 1)); h.view.toggleKind(k); assertFresh("Ff to $k", h)
            h.view.toggleKind(k); assertFresh("Ff off $k", h)
        }
    }
}
