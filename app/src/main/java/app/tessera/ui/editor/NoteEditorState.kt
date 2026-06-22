package app.tessera.ui.editor

data class NoteEditorState(
    val noteId: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val availableTags: List<String> = emptyList(),
    /** Note's last-modified epoch millis, shown in the editor meta row. 0 = unknown/new. */
    val updatedAt: Long = 0L,
    val isLoading: Boolean = true,
)
