package app.tessera.ui.editor.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.icu.text.BreakIterator
import android.util.Log
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.ClipboardBridge
import app.tessera.ui.editor.engine.DocBinder
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The text corpus of Chunk 4c, on the device, in the real app: emoji of every kind, RTL and mixed scripts, combining marks,
 * Markdown characters inside text and lines that look like markers, odd whitespace and invisible characters, a paragraph of
 * more than 10 000 characters. For each: typed as a keyboard commits it (one user-perceived character at a time; marker-like
 * lines pasted, so no auto-replace), copied and pasted inside the note, saved and reopened, then deleted to the start with
 * Backspace — every press takes exactly one user-perceived character (or one line break) off the end, nothing else.
 */
@HiltAndroidTest
class NativeEditorCorpusTest : NativeAppTest() {

    private class Sample(val name: String, val text: String, val pasted: Boolean = false) {
        /** What the paragraph holds: a typed U+FFFC is not an object, the engine drops it (spec §6.2 rule 5). */
        val expected = text.replace("￼", "")
    }

    private val longParagraph = buildString {
        var i = 0
        while (length < 10_500) append("Слово$i word${i % 7} 👍🏽 שלום é **x** ").also { i++ }
    }

    private val samples = listOf(
        Sample("simple emoji", "😀🎉✨❤️"),
        Sample("skin tones", "👍🏻👍🏼👍🏽👍🏾👍🏿"),
        Sample("ZWJ sequences", "👨‍👩‍👧‍👦 👩‍💻 🏳️‍🌈 🧑🏽‍🚀"),
        Sample("flags", "🇷🇺🇺🇸🇯🇵🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
        Sample("keycaps", "1️⃣#️⃣*️⃣🔟"),
        Sample("Hebrew", "שלום עולם"),
        Sample("Arabic", "مرحبا بالعالم"),
        Sample("mixed RTL / LTR", "Hello שלום 123 مرحبا world"),
        Sample("combining marks", "é ä ñ Tiếng Việt Z̶͐a͑"),
        Sample("Devanagari", "क्षत्रिय नमस्ते"),
        Sample("Cyrillic and Latin", "Привет, world! Ёжик ёлка"),
        Sample("Markdown characters inside text", "a * _ \\ # - + > [ ] | ` ~ < & ! **b** ~~c~~ <u>d</u> `e` 2 * 3"),
        Sample("whitespace", "a b\tc  d  "),
        Sample("invisible characters", "a​b﻿c￼d"),
        Sample("numbered-looking line", "1. not a list", pasted = true),
        Sample("task-looking line", "- [ ] not a task", pasted = true),
        Sample("divider-looking line", "---", pasted = true),
        Sample("fence-looking line", "```", pasted = true),
        Sample("comment-looking line", "<!-- not a comment -->", pasted = true),
        Sample("title-looking line", "# not a title", pasted = true),
        Sample("quote-looking line", "> not a quote", pasted = true),
        Sample("escaped-looking line", "\\- already escaped", pasted = true),
        Sample("leading spaces", "    four spaces", pasted = true),
        Sample("paragraph of 10 000+ characters", longParagraph),
    )

    private fun graphemes(s: String): List<String> {
        val it = BreakIterator.getCharacterInstance().apply { setText(s) }
        val out = ArrayList<String>()
        var a = it.first()
        var b = it.next()
        while (b != BreakIterator.DONE) { out += s.substring(a, b); a = b; b = it.next() }
        return out
    }

    private fun lastParagraph(e: TesseraEditText): NoteBlock.Text = app.ui { DocBinder.read(e.text!!).blocks.last() as NoteBlock.Text }

    private fun paste(e: TesseraEditText, text: String) {
        ClipboardBridge.forget()
        app.ui {
            app.activity().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
            e.onTextContextMenuItem(android.R.id.paste)
        }
    }

    private fun type(e: TesseraEditText, s: Sample) {
        if (s.pasted) return paste(e, s.text)
        val g = graphemes(s.text)
        // a long text in batches of whole characters (as a keyboard commits words), never half a surrogate pair or cluster
        val units = if (s.text.length > 1_000) g.chunked(60).map { it.joinToString("") } else g
        units.forEach { u -> app.ime(e) { commitText(u, 1) } }
    }

    @Test
    fun everyTextIsTypedCopiedPastedSavedReopenedAndDeleted() {
        app.launch()
        val broken = ArrayList<String>()
        samples.forEachIndexed { i, s ->
            runCatching { exercise(i, s) }.onFailure { broken += "${s.name}: ${it.message?.take(400)}" }
        }
        assertEquals("texts that broke (${broken.size} of ${samples.size})", emptyList<String>(), broken)
    }

    private fun exercise(i: Int, s: Sample) {
        val id = "qa-corpus-$i"
        val head = "Corpus $i"
        app.seed(id, "$head\n", System.currentTimeMillis()) // above the notes edited before it: its card is on screen
        val e = app.open(id)
        app.ui { e.setSelection(e.text!!.length) }

        // typed
        type(e, s)
        val typed = lastParagraph(e)
        assertEquals("typed: the text", s.expected, typed.content.text)
        assertEquals("typed: still Body", ParagraphKind.Body, typed.kind)

        // copied and pasted inside the note, as a new paragraph
        app.ui {
            val t = e.text.toString()
            e.setSelection(t.length - s.expected.length, t.length)
            e.onTextContextMenuItem(android.R.id.copy)
            e.setSelection(e.text!!.length)
        }
        app.ime(e) { commitText("\n", 1) }
        app.ui { e.onTextContextMenuItem(android.R.id.paste) }
        assertEquals("pasted copy", s.expected, lastParagraph(e).content.text)
        val md = app.md(e)
        assertEquals("fixed point", md, NoteMarkdown.serialize(NoteMarkdown.parse(md)))

        // saved and reopened
        app.back()
        assertEquals("saved", md, app.entity(id)!!.content)
        val again = app.open(id)
        assertEquals("reopened: Markdown", md, app.md(again))
        assertEquals("reopened: text", "$head\n${s.expected}\n${s.expected}", app.text(again))

        // deleted to the start, one press at a time (Gboard's deleteSurroundingText(1, 0))
        app.ui { again.setSelection(again.text!!.length) }
        var presses = 0
        val times = ArrayList<Double>()
        val budget = if (s.expected.length > 1_000) 400 else Int.MAX_VALUE
        while (app.text(again) != head && presses < budget) {
            val before = app.text(again)
            val t0 = System.nanoTime()
            app.ime(again) { deleteSurroundingText(1, 0) }
            times += (System.nanoTime() - t0) / 1e6
            val after = app.text(again)
            presses++
            val removed = if (before.startsWith(after)) before.substring(after.length) else null
            val want = graphemes(before).last()
            if (removed != want) {
                error("press $presses took ${removed?.let(::codes) ?: "a change in the middle: ${codes(after.takeLast(20))}"} instead of ${codes(want)}")
            }
        }
        if (s.expected.length > 1_000) {
            Log.i("NativeEditorCorpus", "Backspace in a ${s.expected.length}-char paragraph: median %.1f ms, max %.1f ms over $presses presses"
                .format(times.sorted()[times.size / 2], times.max()))
            app.ui { again.setSelection(head.length, again.text!!.length) }
            app.ime(again) { commitText("", 1) }
        }
        assertEquals("deleted to the start", head, app.text(again))
        app.back()
        assertEquals("saved after the deletion", head, app.entity(id)!!.content)
    }

    // ------------------------------------------------------------------ every text in every kind of paragraph

    private class Para(val kind: ParagraphKind, val text: String)

    private fun doc(e: TesseraEditText): List<Para> = app.ui { DocBinder.read(e.text!!).blocks.map { b -> (b as NoteBlock.Text).let { Para(it.kind, it.content.text) } } }

    /** A Backspace at the end of a document is one of: a character off the last paragraph; its kind one step towards Body (an empty
     *  paragraph); the empty last Body paragraph joined to the one above (spec §6.4). Nothing else may change. */
    private fun oneStep(before: List<Para>, after: List<Para>): String? {
        val b = before.last()
        val prefix = before.dropLast(1)
        fun same(x: List<Para>, y: List<Para>) = x.size == y.size && x.zip(y).all { (p, q) -> p.kind == q.kind && p.text == q.text }
        if (b.text.isNotEmpty()) {
            val a = after.last()
            val want = b.text.substring(0, b.text.length - graphemes(b.text).last().length)
            return if (same(prefix, after.dropLast(1)) && a.kind == b.kind && a.text == want) null
            else "took more than \"${graphemes(b.text).last()}\": ${after.last().kind} \"${after.last().text.takeLast(12)}\""
        }
        if (b.kind != ParagraphKind.Body) {
            val a = after.last()
            val down = b.kind is ParagraphKind.ListItem && (b.kind as ParagraphKind.ListItem).level > 0
            val ok = same(prefix, after.dropLast(1)) && a.text.isEmpty() &&
                (if (down) a.kind == (b.kind as ParagraphKind.ListItem).copy(level = (b.kind as ParagraphKind.ListItem).level - 1) else a.kind == ParagraphKind.Body)
            return if (ok) null else "an empty ${b.kind}: became ${after.lastOrNull()?.kind} \"${after.lastOrNull()?.text}\" (${after.size} paragraphs)"
        }
        return if (same(prefix, after)) null else "an empty Body: not joined to the paragraph above (${after.size} paragraphs)"
    }

    @Test
    fun everyTextInEveryKindOfParagraphIsTypedSavedReopenedAndDeleted() {
        app.launch()
        val texts = samples.filter { !it.pasted && it.text.length < 1_000 && it.name in KIND_SAMPLES }
        val kinds = listOf(
            "Title" to "# ",
            "checklist item" to "- [ ] ",
            "numbered item, level 2" to "        1. ",
            "quote" to "> ",
            "mono" to "```\n",
        )
        val broken = ArrayList<String>()
        var n = 0
        for ((kindName, marker) in kinds) for (s in texts) {
            val label = "${s.name} in a $kindName"
            runCatching { inKind(n++, marker, s) }.onFailure { broken += "$label: ${it.message?.take(300)}" }
        }
        assertEquals("texts that broke in a kind (${broken.size} of ${kinds.size * texts.size})", emptyList<String>(), broken)
    }

    private fun inKind(i: Int, marker: String, s: Sample) {
        val id = "qa-corpus-kind-$i"
        val head = "Kinds $i"
        val seed = if (marker.startsWith("```")) "$head\n```\n\n```" else "$head\n$marker"
        app.seed(id, seed, System.currentTimeMillis())
        val e = app.open(id)
        app.ui { e.setSelection(e.text!!.length) }
        val kind = doc(e).last().kind
        check(kind != ParagraphKind.Body) { "the seed has no ${marker.trim()} paragraph at the end: $kind" }

        graphemes(s.text).forEach { g -> app.ime(e) { commitText(g, 1) } }
        assertEquals("typed: the text", s.expected, doc(e).last().text)
        assertEquals("typed: the kind", kind, doc(e).last().kind)
        val md = app.md(e)
        assertEquals("fixed point", md, NoteMarkdown.serialize(NoteMarkdown.parse(md)))

        app.back()
        assertEquals("saved", md, app.entity(id)!!.content)
        val again = app.open(id)
        assertEquals("reopened", md, app.md(again))

        app.ui { again.setSelection(again.text!!.length) }
        var presses = 0
        while (doc(again).let { it.size > 1 || it[0].text != head || it[0].kind != ParagraphKind.Body } && presses < 400) {
            val before = doc(again)
            app.ime(again) { deleteSurroundingText(1, 0) }
            presses++
            oneStep(before, doc(again))?.let { error("press $presses: $it") }
        }
        assertEquals("deleted to the start", head, app.md(again))
        app.back()
    }

    private fun codes(s: String) = s.codePoints().toArray().joinToString(" ", "[", "]") { "U+%04X".format(it) }

    private companion object {
        val KIND_SAMPLES = setOf("ZWJ sequences", "flags", "mixed RTL / LTR", "combining marks", "Markdown characters inside text", "whitespace")
    }
}
