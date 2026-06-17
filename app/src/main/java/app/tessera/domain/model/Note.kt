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
) {
    fun isBlankDraft(): Boolean = content.isBlank()
}
