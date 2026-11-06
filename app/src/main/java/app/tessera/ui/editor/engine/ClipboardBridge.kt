package app.tessera.ui.editor.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spanned
import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.spans.BlockSpan

/**
 * Copy / cut / paste of spec §6.8. The system clipboard gets plain text only; the process remembers the pair `(plain text, Markdown
 * fragment)`. A paste whose plain text on the clipboard is that very text inserts the fragment with its formatting, anything else —
 * a clip from another app, or from this one after it changed — is pasted as plain text by the host. The memory is shared by all editors of
 * the process, so a fragment copied in one note pastes into another.
 */
object ClipboardBridge {
    private class Entry(val plain: String, val markdown: String)

    @Volatile private var memory: Entry? = null

    /** Puts the text of [start]..[end] on the clipboard. False for an empty range (nothing is copied). */
    fun copy(context: Context, text: Spanned, start: Int, end: Int): Boolean {
        if (end <= start) return false
        val doc = fragmentOf(text, start, end)
        val plain = plainOf(doc)
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, plain))
        memory = Entry(plain, NoteMarkdown.serialize(doc))
        return true
    }

    /** The fragment to paste: remembered, and still what the clipboard holds. `null` — paste the clipboard as plain text. */
    fun fragment(context: Context): NoteDoc? {
        val m = memory ?: return null
        return if (plainText(context) == m.plain) NoteMarkdown.parse(m.markdown) else null
    }

    fun plainText(context: Context): String? {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return null
        return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
    }

    internal fun forget() {
        memory = null
    }

    /**
     * The selected paragraphs as a document. The first paragraph keeps its kind only when the selection starts at its content start
     * (a partly selected line is a piece of text, not a list item); the last one is empty Body when the selection ends at its start.
     */
    internal fun fragmentOf(text: Spanned, start: Int, end: Int): NoteDoc {
        val sub = SpannableStringBuilder(text, start, end)
        val first = paragraphStart(text, start)
        if (start > contentStart(text, first)) sub.getSpans(0, sub.length, BlockSpan::class.java).filter { sub.getSpanStart(it) == 0 }.forEach { sub.removeSpan(it) }
        return DocBinder.read(sub)
    }

    private fun plainOf(doc: NoteDoc): String = doc.blocks.joinToString("\n") {
        when (it) {
            is NoteBlock.Text -> it.content.text
            NoteBlock.Divider -> "---"
            else -> ""
        }
    }
}
