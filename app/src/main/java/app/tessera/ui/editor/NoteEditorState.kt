package app.tessera.ui.editor

data class NoteEditorState(
    val noteId: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = true,
)
