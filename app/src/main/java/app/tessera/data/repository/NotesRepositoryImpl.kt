package app.tessera.data.repository

import app.tessera.core.text.deriveTitleFromContent
import app.tessera.core.util.Clock
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val noteMapper: NoteMapper,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
    private val sharedNotes: SharedNotesGateway,
) : NotesRepository {
    override fun observeNotes(): Flow<List<Note>> =
        combine(noteDao.observeActive(), tagDao.observeAllCrossRefs()) { notes, _ -> notes }
            .map { notes -> notes.map { note -> toDomain(note) }.filterNot(Note::isBlankDraft) }

    override fun observeNote(id: NoteId): Flow<Note?> =
        noteDao.observeById(id.value).map { note ->
            note?.let { toDomain(it) }
        }

    override fun searchNotes(query: String): Flow<List<Note>> {
        val ftsQuery = query.toFtsMatchQuery()
        if (ftsQuery.isEmpty()) {
            return observeNotes()
        }

        return noteDao.search(ftsQuery).map { notes ->
            notes.map { note -> toDomain(note) }
        }
    }

    override suspend fun getNote(id: NoteId): Note? =
        noteDao.getById(id.value)?.let { toDomain(it) }

    override suspend fun createNote(ownerId: UserId?): Note {
        val now = clock.nowMs()
        val note =
            Note(
                id = NoteId(UUID.randomUUID().toString()),
                title = "",
                content = "",
                tags = emptyList(),
                isPinned = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                syncedAt = null,
                ownerId = ownerId,
            )
        noteDao.upsert(noteMapper.toEntity(note))
        syncScheduler.requestSync()
        return note
    }

    override suspend fun updateNote(note: Note): Note {
        if (note.isBlankDraft()) {
            tagDao.clearTagsForNote(note.id.value)
            noteDao.hardDelete(note.id.value)
            syncScheduler.requestSync()
            return note
        }

        val persisted =
            note.copy(
                title = deriveTitleFromContent(note.content),
                tags = noteMapper.normalizeTags(note.tags),
                updatedAt = clock.nowMs(),
                syncedAt = null,
            )

        replaceTags(persisted)
        noteDao.upsert(noteMapper.toEntity(persisted))
        syncScheduler.requestSync()
        return persisted
    }

    override suspend fun deleteNote(id: NoteId) {
        noteDao.softDelete(id.value, clock.nowMs())
        syncScheduler.requestSync()
    }

    override suspend fun permanentlyDeleteNote(id: NoteId) {
        tagDao.clearTagsForNote(id.value)
        noteDao.hardDelete(id.value)
        syncScheduler.requestSync()
    }

    override suspend fun restoreNote(id: NoteId) {
        noteDao.restore(id.value, clock.nowMs())
        syncScheduler.requestSync()
    }

    override suspend fun togglePin(id: NoteId) {
        val current = getNote(id) ?: return
        noteDao.setPinned(id.value, !current.isPinned, clock.nowMs())
        syncScheduler.requestSync()
    }

    override fun observeArchivedNotes(): Flow<List<Note>> =
        combine(noteDao.observeArchived(), tagDao.observeAllCrossRefs()) { notes, _ -> notes }
            .map { notes -> notes.map { note -> toDomain(note) }.filterNot(Note::isBlankDraft) }

    override fun observeDeletedNotes(): Flow<List<Note>> =
        combine(noteDao.observeDeleted(), tagDao.observeAllCrossRefs()) { notes, _ -> notes }
            .map { notes -> notes.map { note -> toDomain(note) }.filterNot(Note::isBlankDraft) }

    override suspend fun setArchived(id: NoteId, archived: Boolean) {
        noteDao.setArchived(id.value, archived, clock.nowMs())
        syncScheduler.requestSync()
    }

    override suspend fun shareNote(id: NoteId): Result<Unit> = sharedNotes.shareNote(id)

    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: ShareRole,
    ): Result<InviteResult> = sharedNotes.inviteByEmail(id, email, role)

    override suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit> =
        sharedNotes.changeRole(id, uid, role)

    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> =
        sharedNotes.removeMember(id, uid)

    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> =
        sharedNotes.leaveSharedNote(id)

    override suspend fun unshareNote(id: NoteId): Result<Unit> = sharedNotes.unshareNote(id)

    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> =
        sharedNotes.observeMembers(id)

    private suspend fun toDomain(entity: NoteEntity): Note {
        val members =
            if (entity.shareState == NoteEntity.SHARE_STATE_SHARED) {
                sharedNotes.membersForNote(NoteId(entity.id))
            } else {
                emptyList()
            }
        return noteMapper.toDomain(entity, tagDao.tagsForNote(entity.id), members)
    }

    private suspend fun replaceTags(note: Note) {
        val tagNames = note.tags.map { it.name }
        tagDao.clearTagsForNote(note.id.value)

        if (tagNames.isEmpty()) {
            return
        }

        val existingTags = tagDao.findByNames(tagNames).associateBy { it.name }
        val missingTags =
            tagNames
                .filterNot(existingTags::containsKey)
                .map { name -> TagEntity(id = UUID.randomUUID().toString(), name = name) }

        if (missingTags.isNotEmpty()) {
            tagDao.insertTags(missingTags)
        }

        val allTagsByName = tagDao.findByNames(tagNames).associateBy { it.name }
        val refs =
            tagNames.mapNotNull { name ->
                allTagsByName[name]?.let { tag ->
                    NoteTagCrossRef(noteId = note.id.value, tagId = tag.id)
                }
            }
        tagDao.linkTagsToNote(refs)
    }
}

private val FtsOperatorChars = Regex("[\"*:^()\\-]")

internal fun String.toFtsMatchQuery(): String =
    trim()
        .split(Regex("\\s+"))
        .map { token -> token.replace(FtsOperatorChars, "") }
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> "$token*" }
