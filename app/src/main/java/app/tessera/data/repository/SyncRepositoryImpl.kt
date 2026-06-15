package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.SyncRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val remote: RemoteNotesDataSource,
    private val mapper: NoteMapper,
    private val clock: Clock,
) : SyncRepository {
    override suspend fun syncNow(uid: UserId): Result<Unit> =
        runCatching {
            pushPending(uid)
            mergeRemote(uid, remote.fetchAll(uid.value))
        }

    override suspend fun purgeTombstones(uid: UserId): Result<Unit> =
        runCatching {
            val threshold = clock.nowMs() - TOMBSTONE_RETENTION_MS
            noteDao.purgeOldTombstones(threshold)
            remote.deleteOlderTombstones(uid.value, threshold)
        }

    override fun observeRemote(uid: UserId): Flow<Unit> =
        remote.observeNotes(uid.value).map { remoteNotes -> mergeRemote(uid, remoteNotes) }

    private suspend fun pushPending(uid: UserId) {
        val pending =
            noteDao
                .getPendingSync()
                .filterNot { it.title.isBlank() && it.content.isBlank() }
        if (pending.isEmpty()) return

        val payload = pending.map { entity -> mapper.toRemote(entity, tagDao.tagsForNote(entity.id)) }
        remote.upsertAll(uid.value, payload)

        val now = clock.nowMs()
        pending.forEach { noteDao.markSynced(it.id, now) }
    }

    internal suspend fun mergeRemote(
        uid: UserId,
        remoteNotes: List<RemoteNote>,
    ) {
        val now = clock.nowMs()
        for (remoteNote in remoteNotes) {
            val local = noteDao.getById(remoteNote.id)
            if (local != null && local.updatedAt >= remoteNote.updatedAt) continue

            noteDao.upsert(mapper.toEntity(remoteNote, ownerId = uid.value, syncedAt = now))
            replaceTags(remoteNote.id, remoteNote.tags)
        }
    }

    private suspend fun replaceTags(
        noteId: String,
        tagNames: List<String>,
    ) {
        tagDao.clearTagsForNote(noteId)
        if (tagNames.isEmpty()) return

        val existing = tagDao.findByNames(tagNames).associateBy { it.name }
        val missing =
            tagNames
                .filterNot(existing::containsKey)
                .distinct()
                .map { name -> TagEntity(id = UUID.randomUUID().toString(), name = name) }
        if (missing.isNotEmpty()) {
            tagDao.insertTags(missing)
        }

        val byName = tagDao.findByNames(tagNames).associateBy { it.name }
        val refs =
            tagNames.mapNotNull { name ->
                byName[name]?.let { tag -> NoteTagCrossRef(noteId = noteId, tagId = tag.id) }
            }
        tagDao.linkTagsToNote(refs)
    }

    private companion object {
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
