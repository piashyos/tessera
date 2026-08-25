package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.text.Spannable
import android.text.style.AbsoluteSizeSpan
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.text.SerifHeadingTypefaceSpan
import app.tessera.core.text.applySerifHeadingSpans
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.spans.AztecHeadingSpan

/**
 * Guards the heading-styling behaviour:
 *  1. Toggling a heading via the toolbar must re-apply the serif overlay (the toggle changes
 *     spans without firing the TextWatcher that schedules reapply).
 *  2. The overlay is typeface-only — never an absolute-size span (which would stack on Aztec's
 *     multiplicative heading scale and blow the line up to ~45sp).
 *  3. Toggling a heading off must strip the overlay; toggling must not add stray blank lines.
 *  4. Both H1 and H2 get the serif overlay.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class HeadingToggleArtifactsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    private fun setup(markdown: String, recorded: MutableList<String>): Pair<AztecText, () -> RichBodyActions> {
        var actions: RichBodyActions? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = converter.markdownToHtml(markdown),
                    markdownKey = markdown,
                    htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = { recorded += it },
                    onActionsReady = { actions = it },
                )
            }
        }
        rule.waitForIdle()
        val editor = rule.activity.findViewById<AztecText>(R.id.aztec)
        return editor to { actions!! }
    }

    @Test
    fun togglingH1ViaToolbarAppliesSerifTypefaceButNoSizeSpan() {
        val (editor, actions) = setup("hello", mutableListOf())
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0, editor.length())
            actions().toggleHeading1()
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val sp = editor.text as Spannable
            val headings = sp.getSpans(0, sp.length, AztecHeadingSpan::class.java)
            assertTrue("expected an H1 heading span", headings.isNotEmpty())
            val face = sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java)
            assertEquals("serif typeface overlay must be applied after toolbar H1 toggle", 1, face.size)
            assertEquals(
                "overlay must be typeface-only — an absolute-size span would stack on Aztec's scale",
                0,
                sp.getSpans(0, sp.length, AbsoluteSizeSpan::class.java).size,
            )
        }
    }

    @Test
    fun togglingH2ViaToolbarAlsoAppliesSerifTypeface() {
        val (editor, actions) = setup("hello", mutableListOf())
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0, editor.length())
            actions().toggleHeading2()
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val sp = editor.text as Spannable
            val face = sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java)
            assertEquals("H2 must also get the serif typeface overlay", 1, face.size)
        }
    }

    @Test
    fun togglingH1OffRemovesOverlay() {
        val (editor, actions) = setup("hello", mutableListOf())
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0, editor.length())
            actions().toggleHeading1() // on
            actions().toggleHeading1() // off -> paragraph
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val sp = editor.text as Spannable
            val face = sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java)
            assertEquals("no lingering serif overlay once the heading is removed", 0, face.size)
        }
    }

    @Test
    fun loadedH1ThenRemovedViaToolbarLeavesNoStaleOverlay() {
        // Loading "# Title\nbody" applies the serif overlay on load. Removing the heading via the
        // toolbar (no text change -> no TextWatcher) must strip the overlay.
        val (editor, actions) = setup("# Title\nbody", mutableListOf())
        rule.runOnUiThread {
            // Sanity: overlay present after load.
            val sp = editor.text as Spannable
            assertEquals(1, sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java).size)
            editor.requestFocus()
            editor.setSelection(0, 0) // caret on the heading line
            actions().toggleHeading1() // off -> paragraph
        }
        rule.waitForIdle()

        rule.runOnUiThread {
            val sp = editor.text as Spannable
            assertEquals(
                "removing the heading via toolbar must strip the serif overlay",
                0,
                sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java).size,
            )
        }
    }

    @Test
    fun togglingH1DoesNotAddBlankLines() {
        val recorded = mutableListOf<String>()
        val (editor, actions) = setup("hello\nworld", recorded)
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0, 0) // caret on first line
            actions().toggleHeading1()
        }
        rule.waitForIdle()

        val md = recorded.lastOrNull()
        assertEquals("# hello\nworld", md)
    }

    @Test
    fun togglingH1AtEndOfLastLineConvertsThatLine() {
        // Caret at the genuine end of the typed text (just after the last visible char, which is
        // the furthest a user's caret can sit — Aztec's zero-width end-of-buffer marker is past
        // it and non-editable). Toggling a heading must convert the last line, not append a stray
        // empty heading. Guards the "extra line breaks / empty heading" artifacts.
        val recorded = mutableListOf<String>()
        val (editor, actions) = setup("intro\nlast", recorded)
        rule.runOnUiThread {
            editor.requestFocus()
            val end = "intro\nlast".length // 10 — end of visible content, before Aztec's marker
            editor.setSelection(end, end)
            actions().toggleHeading1()
        }
        rule.waitForIdle()
        assertEquals("intro\n# last", recorded.lastOrNull())
    }

    @Test
    fun togglingH1MidLastLineStillConvertsThatLine() {
        val recorded = mutableListOf<String>()
        val (editor, actions) = setup("intro\nlast", recorded)
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(8, 8) // inside "last"
            actions().toggleHeading1()
        }
        rule.waitForIdle()

        assertEquals("intro\n# last", recorded.lastOrNull())
    }

    @Test
    fun reapplyIsANoOpWhenHeadingRangesUnchanged() {
        // The serif overlay reflows the layout every time it remove-then-re-adds its spans. To
        // avoid churning the layout on each keystroke/caret event (which can surface a stale frame
        // on the fast release pipeline), a reapply whose heading ranges are unchanged must be a
        // genuine no-op: the SAME span instance survives, so no setSpan/removeSpan fires.
        val (editor, _) = setup("# Title\nbody", mutableListOf())
        rule.runOnUiThread {
            val sp = editor.text as Spannable
            val before = sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java)
            assertEquals(1, before.size)

            applySerifHeadingSpans(sp, editor.context)

            val after = sp.getSpans(0, sp.length, SerifHeadingTypefaceSpan::class.java)
            assertEquals(1, after.size)
            assertTrue(
                "ranges unchanged -> the existing serif span instance must be preserved (no reflow)",
                before[0] === after[0],
            )
        }
    }

    @Test
    fun insertingABlankBetweenHeadingAndChecklistStoresABlankLine() {
        // The reported case: heading + checkbox list, insert a blank line between them. Aztec
        // absorbs that blank into the list as an EMPTY leading item; the converter turns a non-last
        // empty item back into a real blank line, which round-trips as an NBSP paragraph.
        val (editor, actions) = setup("", mutableListOf())
        var md = ""
        rule.runOnUiThread {
            editor.requestFocus()
            editor.text.insert(0, "H"); editor.setSelection(0); actions().toggleHeading1()
            editor.setSelection(editor.editableText.length)
            editor.text.insert(editor.selectionStart, "\n"); editor.text.insert(editor.selectionStart, "task")
            val t = editor.editableText.toString().indexOf("task"); editor.setSelection(t); actions().toggleTaskList()
            val t2 = editor.editableText.toString().indexOf("task"); editor.setSelection(t2); editor.text.insert(t2, "\n")
            md = converter.htmlToMarkdown(editor.toHtml())
        }
        assertEquals("# H\n\n- [ ] task", md)
    }

    @Test
    fun togglingAPlainLineToHeadingAddsNoBlankLines() {
        // The reported bug: toggling an existing plain line (followed by another line) to a heading
        // injected blank lines after it — Aztec emits `<h1>body\n</h1>\n\nXxx` for editable
        // `body\nXxx`, which naively became `# body\n\n\nXxx`. Must stay adjacent.
        val recorded = mutableListOf<String>()
        val (editor, actions) = setup("body\nXxx", recorded)
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0) // caret on "body"
            actions().toggleHeading1()
        }
        rule.waitForIdle()
        assertEquals("# body\nXxx", recorded.lastOrNull())
    }

    @Test
    fun headingFollowedByTextStaysAdjacentInMarkdown() {
        // The visible gap under a heading is Aztec's headingVerticalPadding (rendering only) — it
        // must NOT correspond to an actual blank line in the stored markdown. Guards against a
        // regression where a heading + body would serialize with a stray empty line between them.
        val rec = mutableListOf<String>()
        val (editor, actions) = setup("Title", rec)
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection("Title".length)
            actions().toggleHeading1()
            // Simulate Enter at the end of the heading, then typing body text.
            editor.text.insert(editor.selectionStart, "\n")
            editor.text.insert(editor.selectionStart, "body")
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            assertEquals("# Title\nbody", converter.htmlToMarkdown(editor.toHtml()))
        }
    }

    @Test
    fun insertingABlankAtEndOfAListItemStoresABlankLineNotAnEmptyItem() {
        // Reported bug: inserting a break at the END of a list item (between this list and the next)
        // — Aztec absorbs it as a TRAILING empty <li>. The converter must turn it into a real blank
        // line (since content follows the list), not keep it as an empty "- " marker. Mirrors the
        // END_OF_BULLET probe: editable "a\n\nb", toHtml "<ul><li>a</li><li></li></ul><ol><li>b</li></ol>".
        val (editor, _) = setup("- a\n1. b", mutableListOf())
        var md = ""
        rule.runOnUiThread {
            editor.requestFocus()
            val pos = editor.editableText.toString().indexOf("a") + 1
            editor.setSelection(pos)
            editor.text.insert(pos, "\n")
            md = converter.htmlToMarkdown(editor.toHtml())
        }
        assertEquals("- a\n\n1. b", md)
    }
}
