package app.tessera.data.mapper

import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.remote.RemoteNote
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import javax.inject.Inject

class NoteMapper @Inject constructor() {
    fun toDomain(
        entity: NoteEntity,
        tagNames: List<String>,
    ): Note =
        Note(
            id = NoteId(entity.id),
            title = entity.title,
            content = entity.content,
            tags = normalizeTags(tagNames.map(::Tag)),
            isPinned = entity.isPinned,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            syncedAt = entity.syncedAt,
            ownerId = entity.ownerId?.let(::UserId),
        )

    fun toEntity(note: Note): NoteEntity =
        NoteEntity(
            id = note.id.value,
            title = note.title,
            content = note.content,
            isPinned = note.isPinned,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            deletedAt = note.deletedAt,
            syncedAt = note.syncedAt,
            ownerId = note.ownerId?.value,
        )

    fun toRemote(
        entity: NoteEntity,
        tagNames: List<String>,
    ): RemoteNote =
        RemoteNote(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            tags = normalizeTagNames(tagNames),
            isPinned = entity.isPinned,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
        )

    fun toEntity(
        remote: RemoteNote,
        ownerId: String,
        syncedAt: Long?,
    ): NoteEntity =
        NoteEntity(
            id = remote.id,
            title = remote.title,
            content = remote.content,
            isPinned = remote.isPinned,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
            deletedAt = remote.deletedAt,
            syncedAt = syncedAt,
            ownerId = ownerId,
        )

    fun normalizeTags(tags: List<Tag>): List<Tag> =
        normalizeTagNames(tags.map(Tag::name)).map(::Tag)

    private fun normalizeTagNames(names: List<String>): List<String> =
        names
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
}
