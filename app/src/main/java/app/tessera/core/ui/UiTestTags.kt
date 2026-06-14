package app.tessera.core.ui

object UiTestTags {
    const val ShowcaseRoot = "showcase_root"
    const val TopCapsule = "top_capsule"
    const val ShowcaseCard = "showcase_card"
    const val BottomCapsule = "bottom_capsule"
    const val CreateNoteFab = "create_note_fab"
    const val NotesListRoot = "notes_list_root"
    const val NotesGrid = "notes_grid"
    const val SearchField = "search_field"
    const val EditorPlaceholderRoot = "editor_placeholder_root"

    fun noteCard(noteId: String): String = "note_card_$noteId"

    fun revealPinAction(noteId: String): String = "pin_action_$noteId"

    fun revealDeleteAction(noteId: String): String = "delete_action_$noteId"
}
