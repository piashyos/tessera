package app.tessera.ui.editor.components

import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.core.text.MarkdownHtmlConverter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.spans.AztecHeadingSpan

/**
 * The "разметка сходит с ума" bug reproduced with the EXACT note from the video
 * (SVID_20260829_131602): a serif H1 title, a description paragraph, and the full six-item
 * checklist including the long wrapping item — mounted the way production does (fast path,
 * scrollable = true). We recreate the on-screen action: the user fights the keyboard's autocorrect
 * on the word "приложение" in the paragraph (delete → mistyped "пиложение" → autocorrect recomposes
 * "приложение"), all while an IME composing region is live.
 *
 * The giant H1 on screen is a RENDER symptom, so we assert on the actual [android.text.Layout] line
 * geometry (not only spans / markdown): every line of the description paragraph must stay
 * body-sized — shorter than the real H1 title line. If the paragraph blows up to heading metrics,
 * a paragraph line becomes as tall as (or taller than) the title line and this fails. We measure
 * IMMEDIATELY after the edit — before the 50ms serif-reapply debounce and the 300ms flush get a
 * chance to heal it — because that is the stuck window the video shows.
 */
class RichNoteBodyExactNoteMarkupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Volatile private var latestMarkdown: String? = null

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

    private val paragraph = "Что нужна добавить в приложение или исправить"

    @Test
    fun autocorrectFightOnParagraphKeepsParagraphBodySized() {
        val editor = mount(exactMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val text = editor.text.toString()
            val start = text.indexOf("приложение")
            val end = start + "приложение".length
            editor.setSelection(end)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingRegion(start, end)      // keyboard grabs the on-screen word
            ic.setComposingText("пиложение", 1)    // user's typo (dropped 'р') — region still live
            ic.setComposingText("приложение", 1)   // autocorrect recomposes the fix — still composing
            ic.endBatchEdit()
        }

        // Measure the stuck frame directly — no waitForIdle heal.
        assertParagraphStaysBodySized(editor, "immediately after the autocorrect fight (mid-composition)")
        assertHeadingConfinedToTitle(editor, "immediately after the autocorrect fight")

        // And after everything settles / re-serializes.
        Thread.sleep(600)
        composeRule.waitForIdle()
        assertParagraphStaysBodySized(editor, "after the serif-reapply + serialize settle")
        assertHeadingConfinedToTitle(editor, "after settle")

        val md = latestMarkdown
        if (md != null) {
            val headingLines = Regex("(?m)^#{1,6}\\s.*$").findAll(md).map { it.value }.toList()
            assertTrue(
                "only '# Tessera' may be a heading; found $headingLines\n$md",
                headingLines.size == 1 && headingLines[0].startsWith("# Tessera"),
            )
            assertTrue(
                "all six checklist items must survive:\n$md",
                listOf(
                    "Эффект бумаги на фон",
                    "Исправить сумасшествие разметки",
                    "Переделать теги на списке",
                    "весь лэйаут перестраивается",
                    "Исправить инпут в модалке Шеринга",
                    "Прозрачный отступ под навигационной полоской",
                ).all { md.contains(it) },
            )
        }
    }

    // --- assertions ----------------------------------------------------------------------------

    /**
     * Every rendered line of the description paragraph must be strictly shorter than the H1 title
     * line. A paragraph that has been swallowed into heading metrics renders at ≈1.7× and would be
     * as tall as — or taller than — the title.
     */
    private fun assertParagraphStaysBodySized(editor: AztecText, whenLabel: String) {
        composeRule.runOnUiThread {
            val layout = editor.layout ?: error("editor has no layout $whenLabel")
            val text = editor.text.toString()
            val titleLine = layout.getLineForOffset(text.indexOf("Tessera"))
            val titleH = layout.getLineBottom(titleLine) - layout.getLineTop(titleLine)

            val pStart = text.indexOf(paragraph)
            val pEnd = pStart + paragraph.length
            val firstLine = layout.getLineForOffset(pStart)
            val lastLine = layout.getLineForOffset(pEnd)

            var tallest = 0
            for (line in firstLine..lastLine) {
                tallest = maxOf(tallest, layout.getLineBottom(line) - layout.getLineTop(line))
            }
            assertTrue(
                "paragraph must stay body-sized $whenLabel, but its tallest line is ${tallest}px vs " +
                    "the H1 title line ${titleH}px (ratio ${"%.2f".format(tallest.toFloat() / titleH)}). " +
                    "A ratio ≈1.7 means the paragraph rendered as an oversized heading.\n" +
                    "editor text: \"$text\"",
                tallest < titleH,
            )
        }
    }

    /** Only the title carries a heading span; nothing below it may. */
    private fun assertHeadingConfinedToTitle(editor: AztecText, whenLabel: String) {
        composeRule.runOnUiThread {
            val spannable = editor.text as Spannable
            val afterTitle = spannable.toString().indexOf(paragraph).coerceAtLeast(1)
            val leaked = spannable.getSpans(afterTitle, spannable.length, AztecHeadingSpan::class.java)
            assertTrue(
                "no heading span may reach below the title $whenLabel, but found " +
                    leaked.joinToString {
                        "[${spannable.getSpanStart(it)},${spannable.getSpanEnd(it)}]=${it.heading}"
                    },
                leaked.isEmpty(),
            )
        }
    }

    // --- harness -------------------------------------------------------------------------------

    private fun mount(md: String): AztecText {
        val html = converter.markdownToHtml(md)
        composeRule.setContent {
            MaterialTheme {
                RichNoteBody(
                    html = html,
                    markdownKey = md,
                    htmlToMarkdown = { converter.htmlToMarkdown(it) },
                    onMarkdownChange = { latestMarkdown = it },
                    onActionsReady = {},
                    // Match production: EditorBody's fast path mounts the single editor scrollable.
                    scrollable = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        return findAztec(composeRule.activity.window.decorView)
            ?: error("AztecText not found in the composed view tree")
    }

    private fun findAztec(v: View): AztecText? {
        if (v is AztecText) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findAztec(v.getChildAt(i))?.let { return it }
        }
        return null
    }
}
