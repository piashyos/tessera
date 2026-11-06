package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live view: after a command of the panel (or Enter) the layout of the editor looks as a fresh layout of the same text — the
 * neighbours (mono series edges, list numbers) are refreshed by the commands through the host. Run on its own if `RenderNodeNatives` flakes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TesseraEditTextLayoutTest {
    private val style = EngineTestStyle.create()

    private fun render(l: Layout, width: Int): IntArray {
        val b = Bitmap.createBitmap(width, l.height + 4, Bitmap.Config.ARGB_8888)
        Canvas(b).also { it.drawColor(Color.WHITE); l.draw(it) }
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }

    private fun assertLooksFresh(label: String, h: HostFixture) {
        val live = h.view.layout
        val fresh = DocBinder.build(DocBinder.read(h.text), style)
        DocBinder.setObjectWidth(fresh, live.width)
        val ref = StaticLayout.Builder.obtain(fresh, 0, fresh.length, h.view.paint, live.width)
            .setUseLineSpacingFromFallbacks(h.view.isFallbackLineSpacing) // what the TextView's own layout does
            .build()
        assertEquals("$label: text", fresh.toString(), h.text.toString())
        assertEquals("$label: lines", ref.lineCount, live.lineCount)
        for (i in 0..ref.lineCount) assertEquals("$label: top of line $i", ref.getLineTop(i), live.getLineTop(i))
        assertTrue("$label: picture", render(live, live.width).contentEquals(render(ref, live.width)))
    }

    @Test
    fun panelCommandsLeaveTheLiveLayoutAsAFreshOne() {
        val h = HostFixture("```\na\n```\nx\n```\nc\n```\n\n1. a\n2. b\n3. c")
        h.view.setPadding(0, 0, 0, 0)
        h.layout()
        assertLooksFresh("start", h)
        h.caret(h.at("x"))
        h.view.toggleKind(ParagraphKind.Mono) // joins two mono series
        assertLooksFresh("mono joins", h)
        h.caret(h.at("b"))
        h.view.toggleKind(ParagraphKind.ListItem(ListType.Bullet, 0)) // renumbers the run
        assertLooksFresh("number -> bullet", h)
        h.view.indent()
        assertLooksFresh("indent", h)
        h.caret(h.at("c") + 1)
        h.view.performEnter()
        assertLooksFresh("enter in a list", h)
        h.view.insertHorizontalRule()
        assertLooksFresh("divider", h)
    }
}
