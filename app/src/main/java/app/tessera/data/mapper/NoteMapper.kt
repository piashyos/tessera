package app.tessera.data.mapper

import app.tessera.data.local.entity.NoteEntity
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

    fun normalizeTags(tags: List<Tag>): List<Tag> =
        tags
            .asSequence()
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map(::Tag)
            .toList()
}
