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

    // Editor screen
    const val EditorRoot = "editor_root"
    const val EditorTitleField = "editor_title_field"
    const val EditorBodyField = "editor_body_field"
    const val EditorBack = "editor_back"
    const val EditorPin = "editor_pin"
    const val EditorDelete = "editor_delete"

    fun editorChecklistItem(index: Int): String = "editor_checklist_$index"

    fun noteCard(noteId: String): String = "note_card_$noteId"

    fun revealPinAction(noteId: String): String = "pin_action_$noteId"

    fun revealDeleteAction(noteId: String): String = "delete_action_$noteId"
}
