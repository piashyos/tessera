package app.tessera.ui.editor.engine

import android.text.Spanned
import app.tessera.ui.editor.engine.spans.BlockSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object EngineAssertions {
    /** Invariant 1 of spec §6.2: every paragraph has at most one block span, and it starts and ends exactly on the paragraph (with its line break). */
    fun assertInvariant1(e: Spanned, label: String = "") {
        val s = e.toString()
        var ps = 0
        while (true) {
            val pe = paragraphEnd(s, ps)
            val end = minOf(pe + 1, s.length)
            val own = e.getSpans(ps, end, BlockSpan::class.java).filter { e.getSpanStart(it) >= ps || e.getSpanEnd(it) > ps }
            assertTrue("$label paragraph at $ps: ${own.size} block spans", own.size <= 1)
            own.forEach {
                assertEquals("$label start of the span at $ps", ps, e.getSpanStart(it))
                assertEquals("$label end of the span at $ps", end, e.getSpanEnd(it))
            }
            if (pe >= s.length) break
            ps = pe + 1
        }
        // and no block span outside a paragraph of its own
        e.getSpans(0, e.length, BlockSpan::class.java).forEach { b ->
            assertTrue("$label span ${e.getSpanStart(b)}..${e.getSpanEnd(b)} not on a paragraph", e.getSpanStart(b) == 0 || s[e.getSpanStart(b) - 1] == '\n')
        }
    }
}
