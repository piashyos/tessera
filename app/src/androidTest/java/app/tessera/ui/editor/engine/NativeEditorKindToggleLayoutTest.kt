package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * LAYOUT-H: a change of the kind of a paragraph through the panel (a heading turned off, a body made a heading, H1 to H2, list, quote, mono) changes
 * the height of that paragraph and the margins of its neighbours. What the window shows afterwards must be what a fresh editor of the same text shows:
 * the line tops of the layout, the pixels of the hardware pipeline, and the pixels of a software `View.draw`. The live editor stands next to the fresh one
 * in one window and is never detached.
 */
class NativeEditorKindToggleLayoutTest {
    private val note = "ShoppingTitle line\nFf\nSecond line\nThird line\nFourth line"

    private class Board(val e: DeviceEditor, val w: Int, val h: Int, val focus: Boolean) { var fresh: TesseraEditText? = null }

    /** The settings `NoteBody` gives its editor: extra leading, no font padding, the padding above and below. */
    private fun asInTheApp(v: TesseraEditText) {
        val d = v.resources.displayMetrics.density
        v.setPadding(0, (8 * d).toInt(), 0, (24 * d).toInt())
        v.includeFontPadding = false
        v.setLineSpacing(4 * d, 1f)
    }

    private fun board(md: String, focus: Boolean): Board {
        val e = DeviceEditor(md, focus)
        val (w, h) = e.ui {
            asInTheApp(e.view)
            val w = e.container.width / 2
            val h = e.container.height / 2
            e.view.layoutParams = FrameLayout.LayoutParams(w, h)
            w to h
        }
        e.idle()
        return Board(e, w, h, focus)
    }

    private fun soft(v: TesseraEditText): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    private fun assertLooksFresh(b: Board, label: String) {
        val e = b.e
        val live = e.capture(e.view)
        val caret = e.ui { e.view.caretRect() }
        val scrollY = e.ui { e.view.scrollY }
        val fresh = e.ui {
            b.fresh?.let { e.container.removeView(it) }
            e.newEditor(e.view.markdown()).also {
                asInTheApp(it)
                it.setMarkdown(e.view.markdown())
                e.container.addView(it, FrameLayout.LayoutParams(b.w, b.h).apply { leftMargin = b.w })
                b.fresh = it
            }
        }
        e.idle()
        if (e.ui { e.view.scrollY } > 0) e.ui { fresh.scrollTo(0, e.view.scrollY) } // a live editor that took the caret mid-note has scrolled to it: the fresh one is shown at the same place
        val freshShot = e.capture(fresh)
        val (liveSoft, freshSoft) = e.ui { soft(e.view) to soft(fresh) }
        val (liveTops, freshTops) = e.ui {
            val l = e.view.layout
            val f = fresh.layout
            (0..l.lineCount).map { l.getLineTop(it) } to (0..f.lineCount).map { f.getLineTop(it) }
        }
        assertEquals("$label: text", e.ui { fresh.text.toString() }, e.text())
        assertEquals("$label: line tops of the layout", freshTops, liveTops)
        // the caret of a focused live editor is not in the fresh one: its column is blanked in both
        if (caret != null) for (bmp in listOf(live, freshShot, liveSoft, freshSoft)) {
            for (x in (caret.left.toInt() - 3).coerceAtLeast(0)..(caret.right.toInt() + 3).coerceAtMost(bmp.width - 1)) {
                for (y in (caret.top.toInt() - scrollY - 2).coerceAtLeast(0)..(caret.bottom.toInt() - scrollY + 2).coerceAtMost(bmp.height - 1)) bmp.setPixel(x, y, 0)
            }
        }
        pixelDiff(live, freshShot)?.let { hw ->
            val name = "${javaClass.simpleName}-${label.replace(Regex("[^A-Za-z0-9]+"), "-")}"
            savePng(e.activity, "$name-live", live)
            savePng(e.activity, "$name-fresh", freshShot)
            val lines = e.ui {
                val l = e.view.layout
                val rows = (0 until live.height).filter { y -> (0 until live.width).any { live.getPixel(it, y) != freshShot.getPixel(it, y) } }
                rows.map { l.getLineForVertical(it - e.view.totalPaddingTop + e.view.scrollY) }.distinct()
            }
            fail("$label: on screen (hardware), live vs fresh: $hw, lines $lines, tops $liveTops\nmd: ${e.md()}")
        }
        assertNull("$label: View.draw (software), live vs fresh", pixelDiff(liveSoft, freshSoft))
    }

    private fun toggle(e: DeviceEditor, kind: ParagraphKind) { e.ui { e.view.toggleKind(kind) }; e.idle() }

    private fun run(focus: Boolean, note: String = this.note) {
        val b = board(note, focus)
        b.e.use { e ->
            e.caret(e.at("ShoppingTitle", 3))
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "body to H1 (focus $focus)")
            toggle(e, ParagraphKind.Title)
            assertEquals("the heading is off", note, e.md())
            assertLooksFresh(b, "H1 off (focus $focus)")
            toggle(e, ParagraphKind.Title)
            toggle(e, ParagraphKind.Heading)
            assertLooksFresh(b, "H1 to H2 (focus $focus)")
            toggle(e, ParagraphKind.Heading)
            assertLooksFresh(b, "H2 off (focus $focus)")
            for (k in listOf(ParagraphKind.ListItem(ListType.Bullet, 0), ParagraphKind.Quote, ParagraphKind.Mono, ParagraphKind.Subheading)) {
                e.caret(e.at("Ff", 1))
                toggle(e, k)
                assertLooksFresh(b, "Ff to $k on (focus $focus)")
                toggle(e, k)
                assertLooksFresh(b, "Ff to $k off (focus $focus)")
            }
        }
    }

    @Test fun kindChangesLeaveTheWindowAsAFreshEditorShowsIt() = run(focus = false)

    @Test fun kindChangesWithTheFocusInTheEditor() = run(focus = true)

    /** The note of the report: a heading, a short line, and a screenful of paragraphs under them (more blocks of lines than the short note). */
    @Test fun kindChangesInALongerNote() = run(
        focus = false,
        note = "ShoppingTitle line\nFf\n" + (1..14).joinToString("\n") { "Paragraph $it of the note with some words to fill a line, and a few more to wrap" },
    )

    /** The report itself: the heading comes from the saved note (not made in this session), the caret is in it, the panel turns it off, then on again. */
    @Test fun aSavedHeadingTurnedOffAndOnAgain() = savedHeading(focus = false)

    @Test fun aSavedHeadingTurnedOffAndOnAgainWithTheFocus() = savedHeading(focus = true)

    private fun savedHeading(focus: Boolean) {
        val b = board("# ShoppingTitle line\nFf\nSecond line\nThird line\nFourth line", focus = focus)
        b.e.use { e ->
            assertLooksFresh(b, "start")
            e.caret(e.at("ShoppingTitle", 3))
            toggle(e, ParagraphKind.Title)
            assertEquals("ShoppingTitle line\nFf\nSecond line\nThird line\nFourth line", e.md())
            assertLooksFresh(b, "saved H1 off")
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "H1 on again")
            toggle(e, ParagraphKind.Heading)
            assertLooksFresh(b, "H1 to H2")
        }
    }

    @Test fun aSavedHeadingInALongerNoteTurnedOff() {
        val tail = (1..14).joinToString("\n") { "Paragraph $it of the note with some words to fill a line, and a few more to wrap" }
        val b = board("# ShoppingTitle line\nFf\n$tail", focus = false)
        b.e.use { e ->
            e.caret(e.at("ShoppingTitle", 3))
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "saved H1 off, long note")
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "H1 on, long note")
        }
    }

    /** The video of the report: the note is typed in this session (so the layout is made of incremental reflows), then the heading is turned off. */
    @Test fun aHeadingTypedInTheSessionTurnedOff() = typedHeading(focus = false)

    @Test fun aHeadingTypedInTheSessionTurnedOffWithTheFocus() = typedHeading(focus = true)

    private fun typedHeading(focus: Boolean) {
        val b = board("", focus = focus)
        // plain words: the spell checker would underline others in the live editor only
        b.e.use { e ->
            e.caret(0)
            toggle(e, ParagraphKind.Title)
            for ((i, line) in listOf("Groceries today", "bread", "second line", "buy these", "milk", "eggs").withIndex()) {
                if (i > 0) e.ime { commitText("\n", 1) }
                for (ch in line) e.ime { commitText(ch.toString(), 1) } // a key at a time, as a keyboard types: many small reflows, many blocks of lines
            }
            assertLooksFresh(b, "typed")
            e.caret(e.at("Groceries", 3))
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "typed H1 off")
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "typed H1 on again")
        }
    }

    // ---- commands that add or remove lines: `repaintBelow` is not called for them (the layout shifts the blocks itself); the window must still match.

    private val longNote = "# Title line\nFf\n" + (1..40).joinToString("\n") { "Paragraph $it of the note with some words to fill a line, and a few more to wrap onto a second line of the screen" }

    @Test fun enterInTheMiddleOfALongNote() {
        val b = board(longNote, focus = true)
        b.e.use { e ->
            e.capture(e.view) // the blocks of the live editor are recorded before the commands
            for (at in listOf("Paragraph 6 ", "Paragraph 12 ", "Paragraph 30 ", "Paragraph 38 ")) {
                e.caret(e.at(at, 4))
                e.ui { e.view.performEnter() }
                e.idle()
                assertLooksFresh(b, "Enter in \"$at\"")
            }
        }
    }

    @Test fun backspaceMergeInTheMiddleOfALongNote() {
        val b = board(longNote, focus = true)
        b.e.use { e ->
            e.capture(e.view)
            for (at in listOf("Paragraph 6 ", "Paragraph 13 ", "Paragraph 20 ")) {
                e.caret(e.at(at))
                e.ui { e.view.performBackspace() }
                e.idle()
                assertLooksFresh(b, "Backspace-merge at \"$at\"")
            }
        }
    }

    @Test fun aMultiLinePasteInTheMiddleOfALongNote() {
        val b = board(longNote, focus = true)
        b.e.use { e ->
            e.capture(e.view)
            e.caret(e.at("Paragraph 9 ", 4))
            e.ime { commitText("alpha beta\ngamma delta\nepsilon zeta eta theta", 1) }
            assertLooksFresh(b, "paste of three lines")
            e.caret(e.at("Paragraph 25 ", 4))
            e.ime { commitText("one\ntwo", 1) }
            assertLooksFresh(b, "paste of two lines")
        }
    }

    /** A heading that wraps (more lines than the body paragraph had) and one that does not: the first changes the number of lines too. */
    @Test fun aKindChangeThatWrapsTheParagraph() {
        val b = board(longNote, focus = false)
        b.e.use { e ->
            e.capture(e.view)
            e.caret(e.at("Paragraph 5 ", 3))
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "wrapping H1 on")
            toggle(e, ParagraphKind.Title)
            assertLooksFresh(b, "wrapping H1 off")
        }
    }
}
