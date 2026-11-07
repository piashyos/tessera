package app.tessera.ui.editor.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.tessera.R
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.ClipboardBridge
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * The native editor in the real app under the device's real keyboard (Gboard): autocorrect and a suggestion over a word in a
 * checklist under a title, glide typing, Enter from the keyboard with the capital letter it should give a new item, a paste while
 * Gboard composes a word. Whatever words Gboard chooses (logged for the report), the user's structure must not move: the kinds of
 * the paragraphs stay, the document is canonical, it is laid out as a fresh editor of the same Markdown, and it is what gets saved.
 */
@HiltAndroidTest
class NativeEditorGboardLiveTest : NativeAppTest() {

    private val check = ParagraphKind.ListItem(ListType.Check, 0)

    /** A finger at the end of the last line: focus, the caret at the end, the keyboard. */
    private fun focusEnd(e: TesseraEditText, kb: Gboard) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = l.lineCount - 1
            val x = (e.totalPaddingLeft + l.getLineRight(line) + 40f).coerceAtMost(e.width - 10f)
            val y = e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
            loc[0] + x to loc[1] + y
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        kb.awaitShown()
        app.ui { e.setSelection(e.text!!.length) }
        app.settle(400)
    }

    /** The line tops of a fresh editor of [e]'s Markdown, set up as `NoteBody` sets the live one. */
    private fun freshTops(e: TesseraEditText): Pair<List<Int>, List<Int>> = app.ui {
        val fresh = TesseraEditText(android.view.ContextThemeWrapper(e.context, R.style.Theme_Tessera), e.editorStyle).apply {
            setPadding(e.paddingLeft, e.paddingTop, e.paddingRight, e.paddingBottom)
            includeFontPadding = e.includeFontPadding
            setLineSpacing(e.lineSpacingExtra, e.lineSpacingMultiplier)
            typeface = e.typeface
            setMarkdown(e.markdown())
            measure(View.MeasureSpec.makeMeasureSpec(e.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(e.height, View.MeasureSpec.EXACTLY))
            layout(0, 0, e.width, e.height)
        }
        val l = e.layout
        (0..l.lineCount).map { l.getLineTop(it) } to (0..fresh.layout.lineCount).map { fresh.layout.getLineTop(it) }
    }

    private fun assertStructure(e: TesseraEditText, kinds: List<ParagraphKind?>, label: String) {
        val md = app.md(e)
        assertEquals("$label: the kinds of the paragraphs (md \"$md\")", kinds, app.kinds(e))
        assertEquals("$label: fixed point", md, NoteMarkdown.serialize(NoteMarkdown.parse(md)))
        val (live, fresh) = freshTops(e)
        assertEquals("$label: line tops as a fresh editor (md \"$md\")", fresh, live)
    }

    @Test
    fun autocorrectAndASuggestionInAChecklistUnderATitleKeepTheKinds() {
        app.launch()
        app.seed("qa-gb-1", "# Title\n- [ ] item", BASE)
        val e = app.open("qa-gb-1")
        val kb = Gboard(app)
        focusEnd(e, kb)
        val kinds = listOf(ParagraphKind.Title, check)

        kb.type(" teh ")
        kb.log("autocorrect: \"teh \" became ${app.md(e)}")
        assertStructure(e, kinds, "after autocorrect")

        kb.type("hel")
        val candidates = kb.suggestions()
        kb.log("suggestions for \"hel\": ${candidates.map { it.first }}")
        // this emulator's Gboard has no dictionary (no candidates in Messages either): then the step is only logged
        val pick = candidates.firstOrNull { it.first.lowercase().startsWith("hel") && it.first.length > 3 } ?: candidates.firstOrNull()
        if (pick != null) kb.tap(pick.second)
        kb.log("suggestion ${pick?.first}: ${app.md(e)}")
        assertStructure(e, kinds, "after the suggestion")
        assertTrue("the item keeps its text: ${app.md(e)}", app.md(e).startsWith("# Title\n- [ ] item "))

        val md = app.md(e)
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-1")!!.content)
    }

    /**
     * Gboard on this emulator has a Russian dictionary (the English layout has none): with the app in Russian the keyboard switches
     * to it, and autocorrect and the suggestion strip are live — over a word in a checklist item under a title, and in the title itself.
     */
    @Test
    fun russianAutocorrectAndASuggestionInAChecklistAndInATitleKeepTheKinds() {
        runBlocking { prefs.setLanguageTag("ru") }
        try { russianCase() } finally { runBlocking { prefs.setLanguageTag("") } }
    }

    private fun russianCase() {
        app.launch()
        app.settle(3_000)
        app.close() // the first start applies the language (the activity is recreated): start again on a settled one
        app.launch()
        app.seed("qa-gb-ru", "# Заголовок\n- [ ] пункт", BASE)
        val e = app.open("qa-gb-ru")
        val kb = Gboard(app)
        focusEnd(e, kb)
        val kinds = listOf(ParagraphKind.Title, check)

        kb.type(" превет ")
        kb.log("ru autocorrect in an item: \"превет \" -> ${app.md(e)}")
        assertStructure(e, kinds, "after autocorrect in the item")

        kb.type("выс")
        val candidates = kb.suggestions()
        kb.log("ru suggestions for \"выс\": ${candidates.map { it.first }}")
        candidates.firstOrNull { it.first.length > 3 }?.let { kb.tap(it.second) }
        kb.log("ru suggestion: ${app.md(e)}")
        assertStructure(e, kinds, "after the suggestion")

        // the title: the caret at its end, a misspelled word, autocorrect over it
        app.ui { e.setSelection(e.text.toString().indexOf('\n')) }
        app.settle(500)
        kb.type(" превет ")
        kb.log("ru autocorrect in the title: ${app.md(e)}")
        assertStructure(e, kinds, "after autocorrect in the title")
        assertTrue("the title is one line, the item is still an item: ${app.md(e)}", app.md(e).lines().let { it.size == 2 && it[0].startsWith("# ") && it[1].startsWith("- [ ] пункт") })

        val md = app.md(e)
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-ru")!!.content)
    }

    @Test
    fun glideTypingInATitle() {
        app.launch()
        app.seed("qa-gb-2", "# Title\nbody", BASE)
        val e = app.open("qa-gb-2")
        val kb = Gboard(app)
        focusEndOfTitle(e, kb)
        kb.press("Space")
        val before = app.md(e)
        kb.glide("the")
        compose.waitUntil(5_000) { app.md(e) != before } // Gboard commits the glided word a moment after the finger is up
        app.settle(500)
        kb.log("glide \"the\" in a title: ${app.md(e)}")
        assertStructure(e, listOf(ParagraphKind.Title, ParagraphKind.Body), "after glide typing")
        val md = app.md(e)
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-2")!!.content)
    }

    private fun focusEndOfTitle(e: TesseraEditText, kb: Gboard) {
        focusEnd(e, kb)
        app.ui { e.setSelection(e.text.toString().indexOf("\n")) }
        app.settle(400)
    }

    @Test
    fun enterFromTheKeyboardMakesItemsLeavesTheListAndTheNewItemStartsWithACapital() {
        app.launch()
        app.seed("qa-gb-3", "Gboard\n- [ ] first", BASE)
        val e = app.open("qa-gb-3")
        val kb = Gboard(app)
        focusEnd(e, kb)
        kb.tap(kb.enterKey())
        assertEquals("Enter at the end of an item: a new unchecked item", "Gboard\n- [ ] first\n- [ ] ", app.md(e))
        kb.type("h")
        kb.log("first letter of a new item: ${app.md(e)}")
        assertEquals("the keyboard capitalizes the start of the new item (spec §6.1, caps mode after the sentinel)", "Gboard\n- [ ] first\n- [ ] H", app.md(e))
        kb.tap(kb.enterKey())
        kb.tap(kb.enterKey())
        assertEquals("Enter on the empty item leaves the list", "Gboard\n- [ ] first\n- [ ] H\n", app.md(e))
        assertStructure(e, listOf(ParagraphKind.Body, check, check, ParagraphKind.Body), "after the Enters")
        val md = app.md(e)
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-3")!!.content)
    }

    @Test
    fun aPasteWhileGboardComposesAWordLosesNothing() {
        app.launch()
        app.seed("qa-gb-4", "Paste\nstart", BASE)
        val e = app.open("qa-gb-4")
        val kb = Gboard(app)
        focusEnd(e, kb)
        kb.type(" wor")
        ClipboardBridge.forget()
        app.ui {
            app.activity().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, "XYZ"))
            e.onTextContextMenuItem(android.R.id.paste)
        }
        app.settle(400)
        kb.type("d")
        val md = app.md(e)
        kb.log("paste while composing \"wor\", then \"d\": $md")
        assertTrue("the composed word, the paste and the next letter are all there, in order: \"$md\"", Regex("^Paste\\nstart wor ?XYZ ?d$").matches(md))
        assertStructure(e, listOf(ParagraphKind.Body, ParagraphKind.Body), "after the paste")
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-4")!!.content)
    }

    @Test
    fun rotationWhileGboardComposesAWordKeepsTheWordAndTypingGoesOn() {
        app.launch()
        app.seed("qa-gb-5", "Rotate\n- [ ] item", BASE)
        val e = app.open("qa-gb-5")
        val kb = Gboard(app)
        focusEnd(e, kb)
        kb.type(" wor")
        try {
            app.ui { app.activity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15_000) { runCatching { app.ui { app.activity().resources.configuration.orientation } }.getOrNull() == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            val e2 = app.awaitEditor()
            assertEquals("the composed word survives the rotation", "Rotate\n- [ ] item wor", app.md(e2))
            focusEnd(e2, kb)
            kb.type("d")
            kb.log("after the rotation, \"d\": ${app.md(e2)}")
            assertStructure(e2, listOf(ParagraphKind.Body, check), "typing after the rotation")
            assertTrue("the next letter joins the text: ${app.md(e2)}", app.md(e2).matches(Regex("Rotate\\n- \\[ ] item wor ?d")))
        } finally {
            app.ui { app.activity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(15_000) { runCatching { app.ui { app.activity().resources.configuration.orientation } }.getOrNull() == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
        val md = app.md(app.awaitEditor())
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-5")!!.content)
    }

    @Test
    fun goingAwayWhileGboardComposesAWordAndComingBackDoublesNothing() {
        app.launch()
        app.seed("qa-gb-6", "Away\n- [ ] item", BASE)
        val e = app.open("qa-gb-6")
        val kb = Gboard(app)
        focusEnd(e, kb)
        kb.type(" wor")
        app.scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED) // Home, another app on top
        app.settle(500)
        assertEquals("saved while away", "Away\n- [ ] item wor", app.entity("qa-gb-6")!!.content)
        app.scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        val back = app.awaitEditor()
        assertEquals("shown once when back", "Away\n- [ ] item wor", app.md(back))
        focusEnd(back, kb)
        kb.type("d")
        val md = app.md(back)
        kb.log("away mid-word, back, \"d\": $md")
        assertTrue("the next letter joins the text once: \"$md\"", Regex("Away\\n- \\[ ] item wor ?d").matches(md))
        assertStructure(back, listOf(ParagraphKind.Body, check), "after the trip")
        app.back()
        assertEquals("saved", md, app.entity("qa-gb-6")!!.content)
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
