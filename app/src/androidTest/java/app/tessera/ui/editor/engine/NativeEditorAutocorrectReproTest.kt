package app.tessera.ui.editor.engine

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.HeadingSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "разметка сходит с ума" repros of `b5c80c4` (`RichNoteBodyExactNoteMarkupTest`, `RichNoteBodyParagraphAutocorrectHeadingTest`)
 * on the native editor, spec §6.2 rule 2: an IME edit — autocorrect, a suggestion, a composing region over a word already on screen,
 * as Gboard does it — never changes the kind of a paragraph. The same notes, the same IME calls, the editor focused as on the screen.
 *
 * What the user would see is asserted three ways: the kinds of all paragraphs (no paragraph became a heading, the checklist is
 * still a checklist), the Markdown (no new `# ` line), and the live `Layout` against a fresh editor of the same text (no line is
 * taller than it should be — the giant-H1 frame), right after the IME batch and again after everything settled.
 */
class NativeEditorAutocorrectReproTest {

    /** Verbatim note from the video: H1 title, paragraph, 6 checklist items (4 done, 2 open). */
    private val exactMd =
        "# Tessera\n" +
            "Что нужна добавить в приложение или исправить\n" +
            "- [x] Эффект бумаги на фон\n" +
            "- [x] Исправить сумасшествие разметки\n" +
            "- [x] Переделать теги на списке\n" +
            "- [x] При открытии доп меню в из нижнего бара весь лэйаут перестраивается, " +
            "меню не открывается как дропдаун вне основного лэйаута\n" +
            "- [ ] Исправить инпут в модалке Шеринга\n" +
            "- [ ] Прозрачный отступ под навигационной полоской"

    /** Plain paragraph (no heading) followed by a checklist. */
    private val initialMd =
        "Что нужна добавить в приложение или исправить\n" +
            "- [ ] Эффект бумаги на фон\n" +
            "- [ ] Исправить сумашествие разметки"

    /** The real note: a title line, the paragraph, the checklist. */
    private val notedMd =
        "# Tessera\n" +
            "Что нужна добавить в приложение или исправить\n" +
            "- [ ] Эффект бумаги на фон\n" +
            "- [ ] Исправить сумашествие разметки"

    private val paragraph = "Что нужна добавить в приложение или исправить"
    private val word = "приложение"
    private val check = ParagraphKind.ListItem(ListType.Check, 0)
    private val checked = ParagraphKind.ListItem(ListType.Check, 0, checked = true)

    // ------------------------------------------------------------------ RichNoteBodyExactNoteMarkupTest

    @Test
    fun autocorrectFightOnParagraphKeepsParagraphBodySized() {
        DeviceEditor(exactMd, focus = true).use { e ->
            val kinds = e.kinds()
            assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body, checked, checked, checked, checked, check, check), kinds)

            val now = e.ui {
                val start = e.view.text.toString().indexOf(word)
                val end = start + word.length
                e.view.setSelection(end)
                val ic = e.view.onCreateInputConnection(EditorInfo())!!
                ic.beginBatchEdit()
                ic.setComposingRegion(start, end) // the keyboard grabs the on-screen word
                ic.setComposingText("пиложение", 1) // the typo, region still live
                ic.setComposingText(word, 1) // autocorrect recomposes the fix, still composing
                ic.endBatchEdit()
                Snapshot.of(e)
            }
            now.assertComposing(word, "immediately after the autocorrect fight")
            now.assertParagraphShorterThanTitle("immediately after the autocorrect fight")
            now.assertLooksFresh("immediately after the autocorrect fight")
            assertEquals("no paragraph changed its kind", kinds, now.kinds)

            Thread.sleep(600)
            e.idle()
            val settled = e.ui { Snapshot.of(e) }
            settled.assertParagraphShorterThanTitle("after settle")
            settled.assertLooksFresh("after settle")
            assertEquals("no paragraph changed its kind after settle", kinds, settled.kinds)
            assertOnlyTitleHeadingInMarkdown(e.md())
            e.emitted.forEach(::assertOnlyTitleHeadingInMarkdown)
            assertTrue(
                "all six checklist items must survive:\n${e.md()}",
                listOf(
                    "- [x] Эффект бумаги на фон",
                    "- [x] Исправить сумасшествие разметки",
                    "- [x] Переделать теги на списке",
                    "весь лэйаут перестраивается",
                    "- [ ] Исправить инпут в модалке Шеринга",
                    "- [ ] Прозрачный отступ под навигационной полоской",
                ).all { e.md().contains(it) },
            )
        }
    }

    // ------------------------------------------------------------------ RichNoteBodyParagraphAutocorrectHeadingTest

    @Test
    fun composingAutocorrectInParagraphDoesNotTurnBodyIntoHeading() {
        DeviceEditor(initialMd, focus = true).use { e ->
            val now = e.ui {
                overWord(e) { s, t -> setComposingRegion(s, t); setComposingText(word, 1) }
                Snapshot.of(e)
            }
            now.assertComposing(word, "immediately after composing autocorrect")
            assertEquals(listOf(ParagraphKind.Body, check, check), now.kinds)
            now.assertNoHeadingSpan("immediately after composing autocorrect")
            now.assertLooksFresh("immediately after composing autocorrect")

            val settled = settle(e)
            assertEquals(listOf(ParagraphKind.Body, check, check), settled.kinds)
            settled.assertNoHeadingSpan("after settle")
            settled.assertLooksFresh("after settle")
            assertBodyStructurePreserved(e.md())
            e.emitted.forEach(::assertBodyStructurePreserved)
        }
    }

    @Test
    fun committedAutocorrectInParagraphKeepsStructure() {
        DeviceEditor(initialMd, focus = true).use { e ->
            val now = e.ui {
                overWord(e) { s, t -> setComposingRegion(s, t); commitText(word, 1) }
                Snapshot.of(e)
            }
            assertEquals("committed: no composing region left", -1, now.composingStart)
            assertEquals(listOf(ParagraphKind.Body, check, check), now.kinds)
            now.assertNoHeadingSpan("immediately after committed autocorrect")
            now.assertLooksFresh("immediately after committed autocorrect")

            val settled = settle(e)
            assertEquals(listOf(ParagraphKind.Body, check, check), settled.kinds)
            settled.assertLooksFresh("after settle")
            assertBodyStructurePreserved(e.md())
            e.emitted.forEach(::assertBodyStructurePreserved)
        }
    }

    @Test
    fun composingAutocorrectUnderHeadingDoesNotSpreadTheHeading() {
        DeviceEditor(notedMd, focus = true).use { e ->
            val now = e.ui {
                overWord(e) { s, t -> setComposingRegion(s, t); setComposingText(word, 1) }
                Snapshot.of(e)
            }
            now.assertComposing(word, "immediately after composing autocorrect under heading")
            assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body, check, check), now.kinds)
            now.assertOnlyTitleIsHeading("immediately after composing autocorrect under heading")
            now.assertParagraphShorterThanTitle("immediately after composing autocorrect under heading")
            now.assertLooksFresh("immediately after composing autocorrect under heading")

            val settled = settle(e)
            assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body, check, check), settled.kinds)
            settled.assertOnlyTitleIsHeading("after settle")
            settled.assertLooksFresh("after settle")
            val md = e.md()
            assertOnlyTitleHeadingInMarkdown(md)
            assertTrue(
                "the checklist must survive intact under the heading, but got:\n$md",
                md.contains("- [ ] Эффект бумаги на фон") && md.contains("- [ ] Исправить сумашествие разметки"),
            )
        }
    }

    @Test
    fun backspaceAtHeadOfParagraphUnderHeadingDoesNotSpreadTheHeading() {
        // Spec §6.4 rule 3: Backspace at the start of Body joins it to the paragraph above, which keeps its kind — the paragraph
        // joining the title is right. What must never happen: the checklist below pulled into a heading.
        DeviceEditor(notedMd, focus = true).use { e ->
            e.ui {
                e.view.setSelection(e.view.text.toString().indexOf("Что нужна"))
                val ic = e.view.onCreateInputConnection(EditorInfo())!!
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            val settled = settle(e)
            assertEquals(listOf(ParagraphKind.Title, check, check), settled.kinds)
            settled.assertNoHeadingOver("Эффект бумаги на фон", "after Backspace at the paragraph head")
            settled.assertLooksFresh("after Backspace at the paragraph head")
            val md = e.md()
            assertEquals("# TesseraЧто нужна добавить в приложение или исправить\n- [ ] Эффект бумаги на фон\n- [ ] Исправить сумашествие разметки", md)
            assertOnlyTitleHeadingInMarkdown(md)
        }
    }

    @Test
    fun selectionSpanningHeadingAndListThenCommitDoesNotHeadingTheList() {
        // A range from the paragraph to the second item, a keyboard commit over it: the result takes the kind of the first
        // paragraph (§6.4), Body — no new heading below the title.
        DeviceEditor(notedMd, focus = true).use { e ->
            e.ui {
                val text = e.view.text.toString()
                val s = text.indexOf(word)
                val t = text.indexOf("Исправить сумашествие") + "Исправить сумашествие".length
                e.view.setSelection(s, t)
                val ic = e.view.onCreateInputConnection(EditorInfo())!!
                ic.beginBatchEdit()
                ic.setComposingRegion(s, t)
                ic.commitText(word, 1)
                ic.endBatchEdit()
            }
            val settled = settle(e)
            assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body), settled.kinds)
            settled.assertOnlyTitleIsHeading("after the commit over the selection")
            settled.assertLooksFresh("after the commit over the selection")
            assertEquals("# Tessera\nЧто нужна добавить в приложение разметки", e.md())
        }
    }

    // ------------------------------------------------------------------ harness

    /** The caret at the end of [word] in the paragraph, then [calls] in one batch, on the main thread. */
    private fun overWord(e: DeviceEditor, calls: InputConnection.(Int, Int) -> Unit) {
        val start = e.view.text.toString().indexOf(word)
        val end = start + word.length
        e.view.setSelection(end)
        val ic = e.view.onCreateInputConnection(EditorInfo())!!
        ic.beginBatchEdit()
        ic.calls(start, end)
        ic.endBatchEdit()
    }

    private fun settle(e: DeviceEditor): Snapshot {
        Thread.sleep(600) // past the 300 ms emit
        e.idle()
        return e.ui { Snapshot.of(e) }
    }

    /** What the screen holds at one moment, taken on the main thread; the checks run on the test thread. */
    private class Snapshot(
        val text: String,
        val kinds: List<ParagraphKind>,
        val composingStart: Int,
        val composingEnd: Int,
        val headings: List<IntRange>,
        val lineTops: List<Int>,
        val freshText: String,
        val freshLineTops: List<Int>,
        val lineHeights: List<Pair<IntRange, Int>>, // characters of a line -> its height
    ) {
        companion object {
            fun of(e: DeviceEditor): Snapshot {
                val v = e.view
                val t = v.text!!
                val l = v.layout
                val fresh = e.newEditor(v.markdown())
                fresh.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
                fresh.layout(0, 0, v.width, v.height)
                val fl = fresh.layout
                return Snapshot(
                    text = t.toString(),
                    kinds = DocBinder.read(t).blocks.map { (it as NoteBlock.Text).kind },
                    composingStart = BaseInputConnection.getComposingSpanStart(t),
                    composingEnd = BaseInputConnection.getComposingSpanEnd(t),
                    headings = t.getSpans(0, t.length, HeadingSpan::class.java).map { t.getSpanStart(it) until t.getSpanEnd(it) },
                    lineTops = (0..l.lineCount).map { l.getLineTop(it) },
                    freshText = fresh.text.toString(),
                    freshLineTops = (0..fl.lineCount).map { fl.getLineTop(it) },
                    lineHeights = (0 until l.lineCount).map { (l.getLineStart(it) until l.getLineEnd(it)) to (l.getLineBottom(it) - l.getLineTop(it)) },
                )
            }
        }

        fun assertComposing(word: String, label: String) {
            assertTrue("the keyboard's composing region must stay alive $label ($composingStart..$composingEnd)", composingStart >= 0)
            assertEquals("the composing region covers the word $label", word, text.substring(composingStart, composingEnd))
        }

        /** Same text, same line breaks, same height of every line as a fresh editor of this Markdown. */
        fun assertLooksFresh(label: String) {
            assertEquals("text $label", freshText, text)
            assertEquals("line tops vs a fresh editor $label\ntext: \"$text\"", freshLineTops, lineTops)
        }

        fun assertParagraphShorterThanTitle(label: String) {
            val titleH = lineHeights.first { 0 in it.first }.second
            val p = text.indexOf("Что нужна")
            val pEnd = text.indexOf('\n', p).let { if (it < 0) text.length else it }
            val tallest = lineHeights.filter { it.first.first in p until pEnd }.maxOf { it.second }
            assertTrue(
                "paragraph must stay body-sized $label: its tallest line is ${tallest}px vs the title line ${titleH}px\ntext: \"$text\"",
                tallest < titleH,
            )
        }

        fun assertNoHeadingSpan(label: String) = assertTrue("no heading span may exist $label, found $headings", headings.isEmpty())

        fun assertOnlyTitleIsHeading(label: String) {
            val title = 0..text.indexOf('\n')
            assertTrue("the heading must stay on the title line $label, spans: $headings", headings.size == 1 && headings[0] == title)
        }

        fun assertNoHeadingOver(needle: String, label: String) {
            val at = text.indexOf(needle)
            assertNull("no heading span may cover \"$needle\" $label, spans: $headings", headings.firstOrNull { at in it })
        }
    }

    private fun assertOnlyTitleHeadingInMarkdown(md: String) {
        val headingLines = Regex("(?m)^#{1,6}\\s.*$").findAll(md).map { it.value }.toList()
        assertTrue(
            "only the '# Tessera' title may be a heading; found ${headingLines.size}: $headingLines\nfull markdown:\n$md",
            headingLines.size == 1 && headingLines[0].startsWith("# Tessera"),
        )
    }

    private fun assertBodyStructurePreserved(md: String) {
        assertTrue("body must never be reclassified as a heading, but got:\n$md", !Regex("(?m)^#{1,6}\\s").containsMatchIn(md))
        assertTrue(
            "the checklist must survive intact, but got:\n$md",
            md.contains("- [ ] Эффект бумаги на фон") && md.contains("- [ ] Исправить сумашествие разметки"),
        )
    }
}
