package app.tessera.domain.model

data class Note(
    val id: NoteId,
    val title: String,
    val content: String,
    val tags: List<Tag>,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncedAt: Long?,
    val ownerId: UserId?,
    val isArchived: Boolean = false,
    val shareState: ShareState = ShareState.Private,
) {
    fun isBlankDraft(): Boolean = content.isBlank()
}

/** The current user's role on this note, or null when the note is private. */
val Note.myRole: ShareRole?
    get() = (shareState as? ShareState.Shared)?.myRole

/** Whether this note is shared (lives in `sharedNotes`) rather than private to its owner. */
val Note.isShared: Boolean
    get() = shareState is ShareState.Shared
