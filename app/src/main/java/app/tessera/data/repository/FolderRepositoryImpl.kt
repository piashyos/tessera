package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import app.tessera.data.mapper.NoteMapper
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.repository.SyncScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val noteMapper: NoteMapper,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { entities -> entities.map(::toDomain) }

    override fun observeFolderTree(): Flow<List<FolderTreeNode>> =
        observeFolders().map { flat ->
            val roots = flat.filter { it.parentId == null }
            val byParent = flat.filter { it.parentId != null }.groupBy { it.parentId!! }
            roots.map { root -> FolderTreeNode(root, byParent[root.id].orEmpty()) }
        }

    override fun observeFolderForNote(noteId: NoteId): Flow<FolderId?> =
        folderDao.observeFolderForNote(noteId.value).map { it?.let(::FolderId) }

    override fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>> =
        folderDao.observeNotesInFolder(folderId.value).map { entities ->
            entities.map { entity ->
                noteMapper.toDomain(entity, tagDao.tagsForNote(entity.id))
            }
        }

    override suspend fun createFolder(name: String): Folder = createFolderInternal(name, parentId = null)

    override suspend fun createSubfolder(parentId: FolderId, name: String): Folder =
        createFolderInternal(name, parentId = parentId.value)

    private suspend fun createFolderInternal(name: String, parentId: String?): Folder {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "folder name must not be blank" }
        val now = clock.nowMs()
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            parentId = parentId,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncedAt = null,
        )
        folderDao.upsert(folder)
        syncScheduler.requestSync()
        return toDomain(folder)
    }

    override suspend fun renameFolder(id: FolderId, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "folder name must not be blank" }
        folderDao.rename(id.value, trimmed, clock.nowMs())
        syncScheduler.requestSync()
    }

    override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) {
        folderDao.updateParent(id.value, newParentId?.value, clock.nowMs())
        syncScheduler.requestSync()
    }

    override suspend fun deleteFolder(id: FolderId) {
        val now = clock.nowMs()
        val notesInFolder = folderDao.observeNotesInFolder(id.value).first()
        notesInFolder.forEach { entity -> noteDao.softDelete(entity.id, now) }
        folderDao.softDelete(id.value, now)
        syncScheduler.requestSync()
    }

    override suspend fun assignNote(noteId: NoteId, folderId: FolderId?) {
        folderDao.clearAssignment(noteId.value)
        if (folderId != null) {
            folderDao.assignNote(NoteFolderCrossRef(noteId = noteId.value, folderId = folderId.value))
        }
        val now = clock.nowMs()
        noteDao.markPendingSync(noteId.value, now)
        syncScheduler.requestSync()
    }

    private fun toDomain(e: FolderEntity): Folder =
        Folder(
            id = FolderId(e.id),
            name = e.name,
            parentId = e.parentId?.let(::FolderId),
            createdAt = e.createdAt,
        )
}
