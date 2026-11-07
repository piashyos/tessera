package app.tessera.ui.editor.engine

import android.content.ClipData
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.text.notedoc.normalize
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.random.Random

/**
 * Fuzzer of editing sessions (Chunk 4c, spec §6). A session starts from a document of [EngineTextCorpus] and makes a random sequence of
 * [Step]s on a real [TesseraEditText] (Robolectric); after every step [Session.check] verifies the invariants of §6.2, the fixed point of §5.3
 * and that the live document is what a fresh build of it would be. A failing sequence is shortened by [minimize] and printed with its seed.
 *
 * A step holds positions as fractions of the text length, so a sequence stays meaningful when a step is cut out of it.
 */
internal object EngineFuzz {
    enum class A {
        TYPE, COMPOSE, COMPOSE_REGION, COMPOSE_FINISH, ENTER_COMMIT, ENTER_KEY, ENTER_IC, BACK_DELETE, BACK_KEY, BACK_IC,
        SELECT, CARET, DELETE_SEL, KIND, MARK, INDENT, OUTDENT, HR, CHECK, MARKER, UNDO, REDO,
        COPY_PASTE, CUT_PASTE, PASTE_EXT, PASTE_STYLED, SET_MD, ECHO, STALE, THEME, READ_ONLY, PASS, FLUSH, DISPOSE, UNDO_RUN, UNDO_CMD,
    }

    data class Step(val a: A, val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0, val i: Int = 0, val s: String = "") {
        override fun toString() = "$a(x=%.6f y=%.6f z=%.6f i=$i s=${s.map { if (it.code in 32..126) it.toString() else "\\u%04X".format(it.code) }.joinToString("")})".format(x, y, z)
    }

    private val weights = listOf(
        A.TYPE to 24, A.COMPOSE to 6, A.COMPOSE_REGION to 3, A.COMPOSE_FINISH to 2,
        A.ENTER_COMMIT to 3, A.ENTER_KEY to 3, A.ENTER_IC to 3, A.BACK_DELETE to 4, A.BACK_KEY to 4, A.BACK_IC to 4,
        A.SELECT to 5, A.CARET to 6, A.DELETE_SEL to 3, A.KIND to 6, A.MARK to 4, A.INDENT to 2, A.OUTDENT to 2, A.HR to 1, A.CHECK to 2,
        A.MARKER to 3, A.UNDO to 3, A.REDO to 2, A.COPY_PASTE to 2, A.CUT_PASTE to 1, A.PASTE_EXT to 2, A.PASTE_STYLED to 1,
        A.SET_MD to 1, A.ECHO to 1, A.STALE to 1, A.THEME to 1, A.READ_ONLY to 1, A.PASS to 4, A.FLUSH to 1, A.DISPOSE to 0,
        A.UNDO_RUN to 1, A.UNDO_CMD to 1,
    )
    private val total = weights.sumOf { it.second }
    private val words = listOf("при", "привет", "hello", "wor", "мир", "ok", "x", "é", "😀", "שלום", "مر")
    private val pauses = listOf(50, 200, 350, 600, 1500)

    fun gen(rnd: Random): Step {
        var r = rnd.nextInt(total)
        var a = weights.first().first
        for ((k, w) in weights) { if (r < w) { a = k; break }; r -= w }
        val g = EngineTextCorpus
        return Step(
            a, rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextInt(1000),
            when (a) {
                A.TYPE, A.UNDO_RUN -> List(if (a == A.UNDO_RUN) rnd.nextInt(1, 4) else 1) { (if (a == A.TYPE) g.graphemes else g.keptGraphemes).let { l -> l[rnd.nextInt(l.size)] } }.joinToString("\u0000")
                A.COMPOSE, A.COMPOSE_REGION -> words[rnd.nextInt(words.size)]
                A.MARKER -> g.markers[rnd.nextInt(g.markers.size)]
                A.PASTE_EXT, A.PASTE_STYLED -> g.texts[rnd.nextInt(g.texts.size)].take(300)
                A.KIND, A.MARK, A.UNDO_CMD -> if (rnd.nextInt(3) == 0) "sel" else ""
                else -> ""
            },
        )
    }

    fun startOfLayout(seed: Int): String = if (seed % 4 == 0) "" else EngineTextCorpus.markdowns[Random(seed * 17 + 3).nextInt(EngineTextCorpus.markdowns.size)]

    fun sequence(seed: Int, steps: Int): List<Step> = Random(seed).let { rnd -> List(steps) { gen(rnd) } }

    fun startOf(seed: Int): String {
        val rnd = Random(seed * 31 + 7)
        return when {
            seed % 25 == 24 -> EngineTextCorpus.bigMarkdowns[rnd.nextInt(EngineTextCorpus.bigMarkdowns.size)]
            seed % 4 == 0 -> ""
            else -> EngineTextCorpus.markdowns[rnd.nextInt(EngineTextCorpus.markdowns.size)]
        }
    }

    private val kindsOfPanel = listOf(
        ParagraphKind.Title, ParagraphKind.Heading, ParagraphKind.Subheading, ParagraphKind.Quote, ParagraphKind.Mono,
        ParagraphKind.ListItem(ListType.Bullet, 0), ParagraphKind.ListItem(ListType.Dash, 0),
        ParagraphKind.ListItem(ListType.Number, 0), ParagraphKind.ListItem(ListType.Check, 0),
    )
    private val marksOfPanel = listOf(Mark.Bold, Mark.Italic, Mark.Underline, Mark.Strike)

    /** What went wrong at which step. */
    class Failure(val index: Int, val error: Throwable) {
        override fun toString() = "step #$index: ${error::class.simpleName}: ${error.message?.take(600)}"
    }

    class Session(val h: HostFixture, val startMd: String, private val layoutEvery: Int = 0) : AutoCloseable {
        constructor(startMd: String, style: EditorStyle = EngineTestStyle.create(), layoutEvery: Int = 0) : this(HostFixture(startMd, style = style), startMd, layoutEvery)

        private val incoming = arrayListOf(startMd)
        private var externalSinceEmit = false
        private var disposed = false
        private val big = startMd.length > 20_000

        private fun ic(): InputConnection? = h.view.onCreateInputConnection(EditorInfo())
        private fun len() = h.text.length
        private fun pos(x: Double) = (x * (len() + 1)).toInt().coerceIn(0, len())
        private fun md() = h.md()
        private fun key(code: Int) = h.key(code)
        private fun icKey(c: InputConnection, code: Int) {
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            c.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        private fun select(x: Double, y: Double) { val a = pos(x); val b = pos(y); h.select(minOf(a, b), maxOf(a, b)) }
        private fun canonical(m: String) = NoteMarkdown.serialize(NoteMarkdown.parse(m))
        private fun chars(): Int = DocBinder.read(h.text).blocks.filterIsInstance<NoteBlock.Text>().sumOf { it.content.text.length }
        private fun composing() = BaseInputConnection.getComposingSpanStart(h.text) >= 0

        fun run(step: Step) {
            val before = md()
            val emits = h.emitted.size
            val editable = h.view.editable
            when (step.a) {
                A.TYPE -> {
                    val c = ic() ?: return
                    h.caret(pos(step.x))
                    val before0 = chars()
                    val plain = !composing() && h.view.selectionStart == h.view.selectionEnd
                    for (g in step.s.split("\u0000")) c.commitText(g, 1)
                    if (plain && editable && step.s.split("\u0000").all { it in EngineTextCorpus.keptGraphemes }) assertEquals("typing ${step.s} adds its text", before0 + step.s.replace("\u0000", "").length, chars())
                }
                A.COMPOSE -> { val c = ic() ?: return; h.caret(pos(step.x)); c.setComposingText(step.s, 1) }
                A.COMPOSE_REGION -> {
                    val c = ic() ?: return
                    val a = pos(step.x)
                    val b = minOf(a + 1 + step.i % 6, paragraphEnd(h.text, a))
                    if (b > a) { c.setComposingRegion(a, b); c.setComposingText(step.s, 1) }
                }
                A.COMPOSE_FINISH -> ic()?.finishComposingText()
                A.ENTER_COMMIT -> { val c = ic() ?: return; h.caret(pos(step.x)); c.commitText("\n", 1) }
                A.ENTER_KEY -> { h.caret(pos(step.x)); key(KeyEvent.KEYCODE_ENTER) }
                A.ENTER_IC -> { val c = ic() ?: return; h.caret(pos(step.x)); icKey(c, KeyEvent.KEYCODE_ENTER) }
                A.BACK_DELETE -> { val c = ic() ?: return; h.caret(pos(step.x)); c.deleteSurroundingText(1, 0) }
                A.BACK_KEY -> { h.caret(pos(step.x)); key(KeyEvent.KEYCODE_DEL) }
                A.BACK_IC -> { val c = ic() ?: return; h.caret(pos(step.x)); icKey(c, KeyEvent.KEYCODE_DEL) }
                A.SELECT -> select(step.x, step.y)
                A.CARET -> h.caret(pos(step.x))
                A.DELETE_SEL -> { val c = ic() ?: return; select(step.x, step.y); if (step.i % 2 == 0) icKey(c, KeyEvent.KEYCODE_DEL) else c.commitText(step.s.ifEmpty { "q" }, 1) }
                A.KIND -> { if (step.s == "sel") select(step.x, step.y) else h.caret(pos(step.x)); h.view.toggleKind(kindsOfPanel[step.i % kindsOfPanel.size]) }
                A.MARK -> { if (step.s == "sel") select(step.x, step.y) else h.caret(pos(step.x)); h.view.toggleMark(marksOfPanel[step.i % marksOfPanel.size]) }
                A.INDENT -> { select(step.x, step.y); if (step.i % 2 == 0) h.view.indent() else h.key(KeyEvent.KEYCODE_TAB) }
                A.OUTDENT -> { select(step.x, step.y); if (step.i % 2 == 0) h.view.outdent() else h.key(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON) }
                A.HR -> { h.caret(pos(step.x)); h.view.insertHorizontalRule() }
                A.CHECK -> h.view.toggleCheckAt(paragraphStart(h.text, pos(step.x)))
                A.MARKER -> {
                    val c = ic() ?: return
                    h.caret(paragraphStart(h.text, pos(step.x)))
                    for (ch in step.s) c.commitText(ch.toString(), 1)
                }
                A.UNDO -> h.view.undo()
                A.REDO -> h.view.redo()
                A.COPY_PASTE, A.CUT_PASTE -> {
                    val a = pos(step.x)
                    val b = pos(step.y)
                    h.select(minOf(a, b), minOf(maxOf(a, b), minOf(a, b) + 300)) // a longer copy pasted again and again doubles the note
                    h.view.onTextContextMenuItem(if (step.a == A.COPY_PASTE) android.R.id.copy else android.R.id.cut)
                    h.caret(pos(step.z))
                    h.view.onTextContextMenuItem(android.R.id.paste)
                }
                A.PASTE_EXT -> { h.view.setClip(step.s); h.caret(pos(step.x)); h.view.onTextContextMenuItem(android.R.id.paste) }
                A.PASTE_STYLED -> {
                    h.view.context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newHtmlText(null, step.s, "<b>${step.s}</b>"))
                    h.caret(pos(step.x))
                    h.view.onTextContextMenuItem(android.R.id.paste)
                }
                A.SET_MD -> {
                    val m = EngineTextCorpus.markdowns[step.i % EngineTextCorpus.markdowns.size]
                    incoming += m; externalSinceEmit = true
                    h.view.setMarkdown(m)
                    assertTrue("external update applied or skipped, never half", md() == before || md() == canonical(m))
                }
                A.STALE -> {
                    val m = incoming[step.i % incoming.size]
                    externalSinceEmit = true
                    h.view.setMarkdown(m)
                    assertTrue("old value applied or skipped", md() == before || md() == canonical(m))
                }
                A.ECHO -> {
                    val e = h.emitted.lastOrNull() ?: return
                    h.view.setMarkdown(e)
                    if (!externalSinceEmit) assertEquals("the echo of our own emit changes nothing", before, md())
                }
                A.THEME -> {
                    h.view.editorStyle = h.view.editorStyle.copy(accentColor = 0xFF000000.toInt() or (step.i * 2654435), quoteBarColor = 0xFF000000.toInt() or (step.i * 40503))
                    assertEquals("a theme change is not an edit", before, md())
                }
                A.READ_ONLY -> {
                    if (!h.view.editable) return
                    h.view.editable = false
                    val m = md()
                    ic()?.let { it.commitText("zz", 1); it.deleteSurroundingText(1, 0); icKey(it, KeyEvent.KEYCODE_DEL); icKey(it, KeyEvent.KEYCODE_ENTER) }
                    h.caret(pos(step.x)); key(KeyEvent.KEYCODE_ENTER); key(KeyEvent.KEYCODE_DEL)
                    h.view.toggleKind(ParagraphKind.Title); h.view.toggleMark(Mark.Bold); h.view.indent(); h.view.undo(); h.view.insertHorizontalRule()
                    h.view.toggleCheckAt(paragraphStart(h.text, pos(step.x)))
                    h.view.onTextContextMenuItem(android.R.id.paste)
                    assertEquals("a read-only editor does not change", m, md())
                    h.view.editable = true
                    assertEquals("switching back does not change either", m, md())
                }
                A.PASS -> h.pass(pauses[step.i % pauses.size].toLong())
                A.FLUSH -> h.view.flushPendingEdit()
                A.DISPOSE -> { h.view.dispose(); disposed = true }
                A.UNDO_RUN -> {
                    val c = ic() ?: return
                    if (!editable) return
                    h.pass(600)
                    val m0 = md()
                    h.caret(pos(step.x))
                    for (g in step.s.split("\u0000")) c.commitText(g, 1)
                    val m1 = md()
                    if (m1 != m0) { h.view.undo(); assertEquals("undo of a typing run gives the snapshot back byte for byte", m0, md()); h.view.redo(); assertEquals("redo", m1, md()) }
                }
                A.UNDO_CMD -> {
                    if (!editable) return
                    h.pass(600)
                    val m0 = md()
                    if (step.s == "sel") select(step.x, step.y) else h.caret(pos(step.x))
                    if (step.i % 3 == 0) h.view.insertHorizontalRule() else h.view.toggleKind(kindsOfPanel[step.i % kindsOfPanel.size])
                    val m1 = md()
                    if (m1 != m0) { h.view.undo(); assertEquals("undo of a command gives the snapshot back byte for byte", m0, md()); h.view.redo(); assertEquals("redo", m1, md()) }
                }
            }
            if (h.emitted.size > emits) {
                assertTrue("an emit happens only at a pause, a flush or a dispose", step.a in listOf(A.PASS, A.FLUSH, A.DISPOSE, A.UNDO_RUN, A.UNDO_CMD))
                if (step.a == A.PASS || step.a == A.FLUSH || step.a == A.DISPOSE) assertEquals("what is emitted is the document", md(), h.emitted.last())
                externalSinceEmit = false
            }
            if (disposed) assertEquals("nothing is emitted after dispose", emits, h.emitted.size)
            h.layout()
        }

        /** The invariants that hold after every step. */
        fun check(label: String) {
            HostFixture.releaseNatives()
            val e = h.text
            EngineAssertions.assertInvariant1(e, label)
            for (sp in e.getSpans(0, e.length, Any::class.java)) {
                assertFalse("$label: a foreign span stays: $sp", Normalizer.isForeignFormatting(sp, e.getSpanFlags(sp)))
            }
            for (i in 0 until e.length) if (e[i] == '￼') assertTrue("$label: U+FFFC at $i is not an object", isObjectParagraph(e, paragraphStart(e, i)))
            val doc = DocBinder.read(e)
            assertEquals("$label: read(editable) == normalize(read(editable))", normalize(doc), doc)
            val md = NoteMarkdown.serialize(doc)
            assertEquals("$label: the cached markdown is the document's", md, md())
            val back = NoteMarkdown.parse(md)
            assertEquals("$label: fixed point, parse(serialize(doc)) == doc", doc, back)
            assertEquals("$label: fixed point, serialize(parse(md)) == md", md, NoteMarkdown.serialize(back))
            EditFixture.assertSane(label, e, h.view.editorStyle)
            val s = h.view.selectionStart
            val t = h.view.selectionEnd
            assertTrue("$label: selection $s..$t in 0..${e.length}", s in 0..t && t <= e.length)
        }

        /** The live layout is what a fresh one gives: line tops, numbers, the spacing the spans measured with. */
        fun checkLayout(label: String) {
            val live = h.view.layout ?: return
            val fresh = HostFixture(md(), style = h.view.editorStyle)
            val f = fresh.view.layout
            assertNotNull(f)
            // a mark that starts or ends on a white space stays on the screen and is trimmed by `read` (spec §4 rule 2): its italic or code font
            // can change a line's height by a pixel or two, so a fresh build of the saved document is not the reference for those lines
            val t = h.text
            val trimmedMarks = t.getSpans(0, t.length, MarkSpan::class.java).any { m ->
                val a = t.getSpanStart(m); val b = t.getSpanEnd(m)
                a < b && (t[a].isWhitespace() || t[b - 1].isWhitespace())
            }
            assertEquals("$label: line count", f.lineCount, live.lineCount)
            if (!trimmedMarks) for (i in 0..f.lineCount) assertEquals("$label: top of line $i", f.getLineTop(i), live.getLineTop(i))
            fun numbers(t: android.text.Spanned) = t.getSpans(0, t.length, ListItemSpan::class.java).sortedBy { t.getSpanStart(it) }.map { it.number }
            assertEquals("$label: numbers", numbers(fresh.text), numbers(h.text))
            fun baked(t: android.text.Spanned) = t.getSpans(0, t.length, BlockSpan::class.java).sortedBy { t.getSpanStart(it) }.map { it.baked }
            assertEquals("$label: the spacing the spans measured with", baked(fresh.text), baked(h.text))
        }

        fun finish(label: String) {
            check("$label/end")
            if (layoutEvery > 0) checkLayout("$label/end")
            val emits = h.emitted.size
            h.view.flushPendingEdit()
            if (!disposed && h.emitted.size > emits) assertEquals("$label: flush emits exactly markdown()", md(), h.emitted.last())
            val n = h.emitted.size
            h.view.flushPendingEdit()
            assertEquals("$label: a second flush emits nothing", n, h.emitted.size)
        }

        /** Takes the pending emit off the main looper: a session that is left would keep its whole text there and a long run would run out of memory. */
        override fun close() = h.view.dispose()

        fun everyN() = if (big) 10 else 1
        fun layoutN() = layoutEvery
    }

    /** Runs [steps] from [startMd]; the first failure, or null. */
    fun runSteps(startMd: String, steps: List<Step>, layoutEvery: Int = 0, label: String = ""): Failure? {
        var index = -1
        var session: Session? = null
        try {
            val s = Session(startMd, layoutEvery = layoutEvery)
            session = s
            s.check("$label/start")
            for ((i, step) in steps.withIndex()) {
                index = i
                s.run(step)
                if ((i + 1) % s.everyN() == 0) s.check("$label/#$i $step")
                if (layoutEvery > 0 && (i + 1) % layoutEvery == 0) s.checkLayout("$label/#$i $step")
            }
            index = steps.size
            s.finish(label)
            return null
        } catch (t: Throwable) {
            return Failure(index, t)
        } finally {
            session?.close()
        }
    }

    /** Shortens a failing sequence: cut the tail, then drop one step at a time while it still fails. */
    fun minimize(startMd: String, steps: List<Step>, failure: Failure, layoutEvery: Int = 0, budgetMs: Long = 90_000): List<Step> {
        val deadline = System.currentTimeMillis() + budgetMs
        var cur = steps.take(minOf(failure.index + 1, steps.size))
        var changed = true
        while (changed && System.currentTimeMillis() < deadline) {
            changed = false
            var i = 0
            while (i < cur.size && System.currentTimeMillis() < deadline) {
                val trial = cur.toMutableList().also { it.removeAt(i) }
                if (runSteps(startMd, trial, layoutEvery) != null) { cur = trial; changed = true } else i++
            }
        }
        return cur
    }

    fun report(seed: Int, startMd: String, steps: List<Step>, failure: Failure, layoutEvery: Int = 0): String {
        val min = minimize(startMd, steps, failure, layoutEvery)
        val again = runSteps(startMd, min, layoutEvery)
        return buildString {
            appendLine("FUZZ FAILURE seed=$seed ${failure}")
            appendLine("start (Markdown, ${startMd.length} chars): ${startMd.take(400).replace("\n", "\\n")}")
            appendLine("minimized to ${min.size} steps (of ${steps.size}), fails with: $again")
            min.forEachIndexed { i, s -> appendLine("  $i. $s") }
        }
    }

    /** Sessions for the deep mode: `ENGINE_FUZZ_DEEP=1` in the environment (a system property of the Gradle client does not reach the forked test JVM) or `-Dengine.fuzz.deep`. */
    val deep: Boolean = System.getenv("ENGINE_FUZZ_DEEP") == "1" || System.getProperty("engine.fuzz.deep") == "true"
}
