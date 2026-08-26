package app.tessera.data.mapper

import app.tessera.core.text.deriveTitleFromContent
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.remote.RemoteNote
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import javax.inject.Inject

class NoteMapper @Inject constructor() {
    /**
     * Maps a local note row to the domain model. [members] is the note's mirrored member list,
     * required to build [ShareState.Shared]; it is ignored (and may be empty) for private notes.
     */
    fun toDomain(
        entity: NoteEntity,
        tagNames: List<String>,
        members: List<ShareMember> = emptyList(),
    ): Note =
        Note(
            id = NoteId(entity.id),
            // The title is ALWAYS a pure function of the content (first-line-as-title). Deriving
            // at read time instead of trusting the stored column self-heals rows whose title was
            // computed by an older derivation (e.g. table-markup titles) — locally and for notes
            // synced from devices running older builds.
            title = deriveTitleFromContent(entity.content),
            content = entity.content,
            tags = normalizeTags(tagNames.map(::Tag)),
            isPinned = entity.isPinned,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            syncedAt = entity.syncedAt,
            ownerId = entity.ownerId?.let(::UserId),
            isArchived = entity.isArchived,
            shareState = shareState(entity, members),
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
            isArchived = note.isArchived,
            shareState =
                if (note.shareState is ShareState.Shared) {
                    NoteEntity.SHARE_STATE_SHARED
                } else {
                    NoteEntity.SHARE_STATE_PRIVATE
                },
            myRole = (note.shareState as? ShareState.Shared)?.myRole?.name,
        )

    fun toRemote(
        entity: NoteEntity,
        tagNames: List<String>,
        folderId: String? = null,
    ): RemoteNote =
        RemoteNote(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            tags = normalizeTagNames(tagNames),
            pinned = entity.isPinned,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            folderId = folderId,
            archived = entity.isArchived,
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
            isPinned = remote.pinned,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
            deletedAt = remote.deletedAt,
            syncedAt = syncedAt,
            ownerId = ownerId,
            isArchived = remote.archived,
        )

    private fun shareState(
        entity: NoteEntity,
        members: List<ShareMember>,
    ): ShareState =
        if (entity.shareState == NoteEntity.SHARE_STATE_SHARED) {
            ShareState.Shared(members = members, myRole = ShareRole.fromWire(entity.myRole))
        } else {
            ShareState.Private
        }

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
