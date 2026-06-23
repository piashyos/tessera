package app.tessera.data.repository

import android.util.Log
import app.tessera.core.util.Clock
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.remote.RemoteFolder
import app.tessera.data.remote.RemoteFoldersDataSource
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.SyncRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val folderDao: FolderDao,
    private val remote: RemoteNotesDataSource,
    private val remoteFolders: RemoteFoldersDataSource,
    private val mapper: NoteMapper,
    private val clock: Clock,
) : SyncRepository {
    override suspend fun syncNow(uid: UserId): Result<Unit> =
        runCatching {
            Log.d(TAG, "syncNow start uid=${uid.value}")
            pushPendingFolders(uid)
            pushPending(uid)
            val remoteFolderList = remoteFolders.fetchAll(uid.value)
            Log.d(TAG, "Pulled ${remoteFolderList.size} folder(s) from Firestore")
            mergeRemoteFolders(uid, remoteFolderList)
            val remoteNoteList = remote.fetchAll(uid.value)
            Log.d(TAG, "Pulled ${remoteNoteList.size} note(s) from Firestore")
            mergeRemote(uid, remoteNoteList)
            Log.d(TAG, "syncNow finished uid=${uid.value}")
        }

    override suspend fun purgeTombstones(uid: UserId): Result<Unit> =
        runCatching {
            val threshold = clock.nowMs() - TOMBSTONE_RETENTION_MS
            noteDao.purgeOldTombstones(threshold)
            folderDao.purgeOldTombstones(threshold)
            remote.deleteOlderTombstones(uid.value, threshold)
            remoteFolders.deleteOlderTombstones(uid.value, threshold)
        }

    override suspend fun purgeLocalTombstones(): Result<Unit> =
        runCatching {
            val threshold = clock.nowMs() - TOMBSTONE_RETENTION_MS
            noteDao.purgeOldTombstones(threshold)
            folderDao.purgeOldTombstones(threshold)
        }

    override suspend fun permanentlyDelete(uid: UserId, ids: List<NoteId>): Result<Unit> =
        runCatching {
            if (ids.isEmpty()) return@runCatching
            val raw = ids.map { it.value }
            // Remove the cloud docs first so the live snapshot / next pull can't resurrect them.
            remote.deleteByIds(uid.value, raw)
            raw.forEach { id ->
                tagDao.clearTagsForNote(id)
                noteDao.hardDelete(id)
            }
        }

    override fun observeRemote(uid: UserId): Flow<Unit> {
        // Independent listeners — if the folders permission is missing/misconfigured in
        // Firestore rules, the folder snapshot stream can fail without blocking the
        // notes stream (and vice versa). Each side gets its own `catch` that logs the
        // cause and swallows the exception so the other side keeps flowing.
        val notesFlow = remote.observeNotes(uid.value)
            .onEach { remoteNotes ->
                Log.d(TAG, "Remote notes snapshot: ${remoteNotes.size} doc(s)")
                mergeRemote(uid, remoteNotes)
            }
            .catch { error -> Log.w(TAG, "Notes snapshot listener stopped", error) }
            .map { }
        val foldersFlow = remoteFolders.observeFolders(uid.value)
            .onEach { remoteFolderList ->
                Log.d(TAG, "Remote folders snapshot: ${remoteFolderList.size} doc(s)")
                mergeRemoteFolders(uid, remoteFolderList)
            }
            .catch { error -> Log.w(TAG, "Folders snapshot listener stopped", error) }
            .map { }
        return merge(foldersFlow, notesFlow)
    }

    private suspend fun pushPending(uid: UserId) {
        val pending =
            noteDao
                .getPendingSync()
                .filterNot { it.title.isBlank() && it.content.isBlank() }
        if (pending.isEmpty()) return

        val payload = pending.map { entity ->
            mapper.toRemote(
                entity,
                tagDao.tagsForNote(entity.id),
                folderId = folderDao.folderIdForNote(entity.id),
            )
        }
        remote.upsertAll(uid.value, payload)

        val now = clock.nowMs()
        pending.forEach { noteDao.markSynced(it.id, now) }
    }

    private suspend fun pushPendingFolders(uid: UserId) {
        val pending = folderDao.getPendingSync()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending folders to push")
            return
        }
        Log.d(TAG, "Pushing ${pending.size} folder(s) to Firestore")
        val payload = pending.map { entity ->
            RemoteFolder(
                id = entity.id,
                name = entity.name,
                parentId = entity.parentId,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt,
            )
        }
        remoteFolders.upsertAll(uid.value, payload)
        val now = clock.nowMs()
        pending.forEach { folderDao.markSynced(it.id, now) }
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
            replaceFolderAssignment(remoteNote.id, remoteNote.folderId)
        }
    }

    private suspend fun mergeRemoteFolders(
        @Suppress("UNUSED_PARAMETER") uid: UserId,
        remoteFolderList: List<RemoteFolder>,
    ) {
        val now = clock.nowMs()
        for (remoteFolder in remoteFolderList) {
            val local = folderDao.getById(remoteFolder.id)
            if (local != null && local.updatedAt >= remoteFolder.updatedAt) continue
            folderDao.upsert(
                FolderEntity(
                    id = remoteFolder.id,
                    name = remoteFolder.name,
                    parentId = remoteFolder.parentId,
                    createdAt = remoteFolder.createdAt,
                    updatedAt = remoteFolder.updatedAt,
                    deletedAt = remoteFolder.deletedAt,
                    syncedAt = now,
                ),
            )
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

    private suspend fun replaceFolderAssignment(noteId: String, folderId: String?) {
        folderDao.clearAssignment(noteId)
        if (folderId != null) {
            folderDao.assignNote(NoteFolderCrossRef(noteId = noteId, folderId = folderId))
        }
    }

    private companion object {
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val TAG = "SyncRepository"
    }
}
