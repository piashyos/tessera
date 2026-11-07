package app.tessera.ui.editor.engine

/**
 * Undo / redo of spec §6.7: snapshots of `(markdown, selection)`. A run of typing is one step while the pauses between its edits are
 * at most [mergeMs]; a command (panel button, Enter, paste, auto-replace) is a step of its own; at most [limit] steps are kept.
 * Pure state: time comes from the caller, the host builds the snapshots and rebuilds the `Editable` from the one it gets back.
 *
 * A step is stored as the state *before* it. [undo] and [redo] take the current state and give back the one to show.
 */
class EditHistory(private val limit: Int = 100, private val mergeMs: Long = 500) {
    /**
     * One state of the note. Its [markdown] may be built later: a snapshot taken on a key is a light copy of the document ([lazy]), and the
     * Markdown is written only when the step is used (undo, redo, a comparison) — serializing a note of a thousand paragraphs on every first key of
     * a typing run was most of the cost of that key.
     */
    class Snapshot private constructor(private var text: String?, private var source: (() -> String)?, val selStart: Int, val selEnd: Int) {
        constructor(markdown: String, selStart: Int, selEnd: Int) : this(markdown, null, selStart, selEnd)

        val markdown: String
            get() = text ?: source!!().also { text = it; source = null }

        override fun equals(other: Any?) = other is Snapshot && other.markdown == markdown && other.selStart == selStart && other.selEnd == selEnd
        override fun hashCode() = markdown.hashCode() * 31 + selStart * 7 + selEnd
        override fun toString() = "Snapshot(selStart=$selStart, selEnd=$selEnd)"

        companion object {
            /** [markdown] is called once, when the Markdown is first needed; what it reads must not change. */
            fun lazy(selStart: Int, selEnd: Int, markdown: () -> String) = Snapshot(null, markdown, selStart, selEnd)
        }
    }

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastTypingAt = NO_SERIES

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** An edit of the typing run at [now]; [before] (the state before the edit) is built only when this edit starts a new step. */
    fun typing(now: Long, before: () -> Snapshot) {
        if (lastTypingAt == NO_SERIES || now - lastTypingAt > mergeMs) push(before())
        lastTypingAt = now
    }

    /** A command: [before] is the state before it. The typing run ends. */
    fun step(before: Snapshot) {
        push(before)
        lastTypingAt = NO_SERIES
    }

    /** Ends the typing run: the next typing starts a new step. */
    fun endRun() {
        lastTypingAt = NO_SERIES
    }

    fun undo(current: Snapshot): Snapshot? {
        val target = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastTypingAt = NO_SERIES
        return target
    }

    fun redo(current: Snapshot): Snapshot? {
        val target = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastTypingAt = NO_SERIES
        return target
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastTypingAt = NO_SERIES
    }

    private fun push(s: Snapshot) {
        undoStack.addLast(s)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    private companion object {
        const val NO_SERIES = Long.MIN_VALUE
    }
}
