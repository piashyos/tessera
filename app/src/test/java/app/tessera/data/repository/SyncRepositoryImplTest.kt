package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncRepositoryImplTest {
    private val uid = UserId("owner-1")

    private fun entity(
        id: String,
        title: String = "title",
        content: String = "content",
        updatedAt: Long = 1L,
        deletedAt: Long? = null,
        syncedAt: Long? = null,
        ownerId: String? = "owner-1",
    ) = NoteEntity(
        id = id,
        title = title,
        content = content,
        isPinned = false,
        createdAt = 1L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncedAt = syncedAt,
        ownerId = ownerId,
    )

    private fun create(
        noteDao: NoteDao = SyncFakeNoteDao(),
        tagDao: TagDao = SyncFakeTagDao(),
        folderDao: SyncFakeFolderDao = SyncFakeFolderDao(),
        remote: FakeRemoteNotesDataSource = FakeRemoteNotesDataSource(),
        remoteFolders: SyncFakeRemoteFoldersDataSource = SyncFakeRemoteFoldersDataSource(),
        clock: Clock = SyncTestClock(1000L),
    ) = SyncRepositoryImpl(
        noteDao,
        tagDao,
        folderDao,
        remote,
        remoteFolders,
        NoteMapper(),
        clock,
    )

    @Test
    fun `pushPending uploads unsynced non-blank notes and marks them synced`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val clock = SyncTestClock(500L)
            val repo = create(noteDao = noteDao, remote = remote, clock = clock)
            noteDao.upsert(entity("a", syncedAt = null))
            noteDao.upsert(entity("b", syncedAt = 1L))
            noteDao.upsert(entity("blank", title = "  ", content = "", syncedAt = null))

            repo.syncNow(uid)

            assertEquals(setOf("a"), remote.uploaded.keys)
            assertEquals(500L, noteDao.getById("a")?.syncedAt)
            assertNull(noteDao.getById("blank")?.syncedAt)
        }

    @Test
    fun `pull writes a brand-new remote note into Room with synced flag`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val clock = SyncTestClock(700L)
            val repo = create(noteDao = noteDao, remote = remote, clock = clock)
            remote.store(RemoteNote(id = "r1", title = "Cloud", content = "Body", updatedAt = 10L))

            repo.syncNow(uid)

            val stored = noteDao.getById("r1")
            assertNotNull(stored)
            assertEquals("Cloud", stored?.title)
            assertEquals("owner-1", stored?.ownerId)
            assertEquals(700L, stored?.syncedAt)
        }

    @Test
    fun `merge keeps local when local is newer and overwrites when remote is newer`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val repo = create(noteDao = noteDao, remote = remote)
            noteDao.upsert(entity("newer", title = "LocalNew", updatedAt = 100L, syncedAt = 5L))
            noteDao.upsert(entity("older", title = "LocalOld", updatedAt = 1L, syncedAt = 5L))
            remote.store(RemoteNote(id = "newer", title = "RemoteOld", content = "c", updatedAt = 50L))
            remote.store(RemoteNote(id = "older", title = "RemoteNew", content = "c", updatedAt = 99L))

            repo.syncNow(uid)

            assertEquals("LocalNew", noteDao.getById("newer")?.title)
            assertEquals("RemoteNew", noteDao.getById("older")?.title)
        }

    @Test
    fun `remote tombstone soft-deletes the local note`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val repo = create(noteDao = noteDao, remote = remote)
            noteDao.upsert(entity("t", title = "Live", updatedAt = 10L, syncedAt = 5L))
            remote.store(
                RemoteNote(id = "t", title = "Live", content = "content", updatedAt = 20L, deletedAt = 20L),
            )

            repo.syncNow(uid)

            assertEquals(20L, noteDao.getById("t")?.deletedAt)
            assertTrue(noteDao.observeActive().first().none { it.id == "t" })
        }

    @Test
    fun `observeRemote ignores a stale snapshot that would clobber a fresh local edit`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val repo = create(noteDao = noteDao, remote = remote)
            noteDao.upsert(entity("x", title = "FreshLocal", updatedAt = 100L, syncedAt = null))

            remote.emit(listOf(RemoteNote(id = "x", title = "StaleRemote", content = "c", updatedAt = 40L)))
            repo.observeRemote(uid).first()

            assertEquals("FreshLocal", noteDao.getById("x")?.title)
        }

    @Test
    fun `pulled tags are persisted to the tag tables`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val tagDao = SyncFakeTagDao()
            val remote = FakeRemoteNotesDataSource()
            val repo = create(noteDao = noteDao, tagDao = tagDao, remote = remote)
            remote.store(
                RemoteNote(id = "tagged", title = "T", content = "c", tags = listOf("alpha", "beta"), updatedAt = 10L),
            )

            repo.syncNow(uid)

            assertEquals(listOf("alpha", "beta"), tagDao.tagsForNote("tagged").sorted())
        }

    @Test
    fun `pull writes a remote folder into Room and reattaches assigned notes`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val folderDao = SyncFakeFolderDao()
            val remote = FakeRemoteNotesDataSource()
            val remoteFolders = SyncFakeRemoteFoldersDataSource()
            val clock = SyncTestClock(700L)
            val repo = create(
                noteDao = noteDao,
                folderDao = folderDao,
                remote = remote,
                remoteFolders = remoteFolders,
                clock = clock,
            )
            remoteFolders.store(
                app.tessera.data.remote.RemoteFolder(
                    id = "folder-1",
                    name = "Work",
                    createdAt = 5L,
                    updatedAt = 10L,
                ),
            )
            remote.store(
                RemoteNote(
                    id = "note-1",
                    title = "Hello",
                    content = "Body",
                    updatedAt = 20L,
                    folderId = "folder-1",
                ),
            )

            repo.syncNow(uid)

            val pulledFolder = folderDao.getById("folder-1")
            assertNotNull(pulledFolder)
            assertEquals("Work", pulledFolder?.name)
            assertEquals(700L, pulledFolder?.syncedAt)

            val pulledNote = noteDao.getById("note-1")
            assertNotNull(pulledNote)
            assertEquals("Hello", pulledNote?.title)
            assertEquals("folder-1", folderDao.folderIdForNote("note-1"))
        }

    @Test
    fun `purgeTombstones purges local and remote older than the retention window`() =
        runTest {
            val noteDao = SyncFakeNoteDao()
            val remote = FakeRemoteNotesDataSource()
            val now = 40L * 24 * 60 * 60 * 1000
            val clock = SyncTestClock(now)
            val repo = create(noteDao = noteDao, remote = remote, clock = clock)
            val retention = 30L * 24 * 60 * 60 * 1000
            val threshold = now - retention
            noteDao.upsert(entity("old", updatedAt = 1L, deletedAt = threshold - 1))
            noteDao.upsert(entity("recent", updatedAt = 1L, deletedAt = threshold + 1))

            repo.purgeTombstones(uid)

            assertNull(noteDao.getById("old"))
            assertNotNull(noteDao.getById("recent"))
            assertEquals(threshold, remote.purgedThreshold)
        }
}

private class SyncTestClock(
    var now: Long,
) : Clock {
    override fun nowMs(): Long = now
}

private class FakeRemoteNotesDataSource : RemoteNotesDataSource {
    private val notes = linkedMapOf<String, RemoteNote>()
    private val flow = MutableStateFlow(emptyList<RemoteNote>())
    val uploaded = linkedMapOf<String, RemoteNote>()
    var purgedThreshold: Long? = null

    fun store(note: RemoteNote) {
        notes[note.id] = note
    }

    fun emit(remoteNotes: List<RemoteNote>) {
        flow.value = remoteNotes
    }

    override suspend fun fetchAll(uid: String): List<RemoteNote> = notes.values.toList()

    override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) {
        notes.forEach { note ->
            this.notes[note.id] = note
            uploaded[note.id] = note
        }
    }

    override fun observeNotes(uid: String): Flow<List<RemoteNote>> = flow

    override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
        purgedThreshold = threshold
        val stale = notes.values.filter { it.deletedAt != null && it.deletedAt < threshold }.map { it.id }
        stale.forEach { notes.remove(it) }
        return stale.size
    }
}

private class SyncFakeNoteDao : NoteDao {
    private val notes = linkedMapOf<String, NoteEntity>()
    private val activeFlow = MutableStateFlow(emptyList<NoteEntity>())

    override fun observeActive(): Flow<List<NoteEntity>> = activeFlow

    override fun observeArchived(): Flow<List<NoteEntity>> = MutableStateFlow(emptyList())

    override fun observeById(id: String): Flow<NoteEntity?> = MutableStateFlow(notes[id])

    override suspend fun getById(id: String): NoteEntity? = notes[id]

    override fun search(query: String): Flow<List<NoteEntity>> = activeFlow

    override suspend fun upsert(note: NoteEntity) {
        notes[note.id] = note
        emitActive()
    }

    override suspend fun upsertAll(notes: List<NoteEntity>) {
        notes.forEach { this.notes[it.id] = it }
        emitActive()
    }

    override suspend fun softDelete(id: String, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(deletedAt = ts, updatedAt = ts, syncedAt = null) }
        emitActive()
    }

    override suspend fun restore(id: String, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(deletedAt = null, updatedAt = ts, syncedAt = null) }
        emitActive()
    }

    override suspend fun setPinned(id: String, pinned: Boolean, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(isPinned = pinned, updatedAt = ts, syncedAt = null) }
        emitActive()
    }

    override suspend fun setArchived(id: String, archived: Boolean, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(isArchived = archived, updatedAt = ts, syncedAt = null) }
        emitActive()
    }

    override suspend fun purgeOldTombstones(threshold: Long): Int {
        val stale = notes.values.filter { it.deletedAt != null && it.deletedAt < threshold }.map { it.id }
        stale.forEach { notes.remove(it) }
        emitActive()
        return stale.size
    }

    override suspend fun hardDelete(id: String) {
        notes.remove(id)
        emitActive()
    }

    override suspend fun getPendingSync(): List<NoteEntity> = notes.values.filter { it.syncedAt == null }

    override suspend fun markSynced(id: String, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(syncedAt = ts) }
    }

    override suspend fun markPendingSync(id: String, ts: Long) {
        notes[id]?.let { notes[id] = it.copy(syncedAt = null, updatedAt = ts) }
        emitActive()
    }

    override suspend fun getCurrentOwnerId(): String? = notes.values.firstOrNull { it.ownerId != null }?.ownerId

    override suspend fun adoptLocalNotes(uid: String) {
        notes.entries.forEach { e ->
            if (e.value.ownerId == null) e.setValue(e.value.copy(ownerId = uid, syncedAt = null))
        }
        emitActive()
    }

    override suspend fun reassignAllNotes(uid: String) {
        notes.entries.forEach { e -> e.setValue(e.value.copy(ownerId = uid, syncedAt = null)) }
        emitActive()
    }

    override suspend fun deleteByOwner(uid: String) {
        val ids = notes.values.filter { it.ownerId == uid }.map { it.id }
        ids.forEach { notes.remove(it) }
        emitActive()
    }

    private fun emitActive() {
        activeFlow.value =
            notes.values
                .filter { it.deletedAt == null }
                .sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
    }
}

private class SyncFakeTagDao : TagDao {
    private val tagsByName = linkedMapOf<String, TagEntity>()
    private val tagIdsByNote = mutableMapOf<String, MutableSet<String>>()

    override fun observeAll(): Flow<List<TagEntity>> = MutableStateFlow(tagsByName.values.toList())

    override suspend fun insertTags(tags: List<TagEntity>) {
        tags.forEach { tagsByName.putIfAbsent(it.name, it) }
    }

    override suspend fun findByNames(names: List<String>): List<TagEntity> = names.mapNotNull(tagsByName::get)

    override suspend fun linkTagsToNote(refs: List<NoteTagCrossRef>) {
        refs.forEach { ref -> tagIdsByNote.getOrPut(ref.noteId) { linkedSetOf() }.add(ref.tagId) }
    }

    override suspend fun clearTagsForNote(noteId: String) {
        tagIdsByNote.remove(noteId)
    }

    override suspend fun tagsForNote(noteId: String): List<String> =
        tagIdsByNote[noteId].orEmpty().mapNotNull { id -> tagsByName.values.firstOrNull { it.id == id }?.name }

    override fun observeTagsForNote(noteId: String): Flow<List<String>> = MutableStateFlow(emptyList())

    override fun observeAllCrossRefs(): Flow<List<NoteTagCrossRef>> = MutableStateFlow(emptyList())
}

private class SyncFakeFolderDao : app.tessera.data.local.dao.FolderDao {
    private val folders = linkedMapOf<String, app.tessera.data.local.entity.FolderEntity>()
    private val assignments = mutableMapOf<String, String>()

    override fun observeAll(): Flow<List<app.tessera.data.local.entity.FolderEntity>> =
        MutableStateFlow(folders.values.filter { it.deletedAt == null })

    override suspend fun getById(id: String) = folders[id]

    override suspend fun upsert(folder: app.tessera.data.local.entity.FolderEntity) {
        folders[folder.id] = folder
    }

    override suspend fun softDelete(id: String, ts: Long) {
        folders[id]?.let { folders[id] = it.copy(deletedAt = ts, updatedAt = ts, syncedAt = null) }
    }

    override suspend fun hardDelete(id: String) {
        folders.remove(id)
    }

    override suspend fun rename(id: String, name: String, ts: Long) {
        folders[id]?.let { folders[id] = it.copy(name = name, updatedAt = ts, syncedAt = null) }
    }

    override suspend fun updateParent(id: String, parentId: String?, ts: Long) {
        folders[id]?.let { folders[id] = it.copy(parentId = parentId, updatedAt = ts, syncedAt = null) }
    }

    override suspend fun getPendingSync() = folders.values.filter { it.syncedAt == null }
    override suspend fun markSynced(id: String, ts: Long) {
        folders[id]?.let { folders[id] = it.copy(syncedAt = ts) }
    }

    override suspend fun purgeOldTombstones(threshold: Long): Int {
        val gone = folders.values.filter { it.deletedAt != null && it.deletedAt < threshold }
        gone.forEach { folders.remove(it.id) }
        return gone.size
    }

    override suspend fun assignNote(ref: app.tessera.data.local.entity.NoteFolderCrossRef) {
        assignments[ref.noteId] = ref.folderId
    }

    override suspend fun clearAssignment(noteId: String) {
        assignments.remove(noteId)
    }

    override fun observeFolderForNote(noteId: String): Flow<String?> =
        MutableStateFlow(assignments[noteId])

    override suspend fun folderIdForNote(noteId: String): String? = assignments[noteId]

    override fun observeNotesInFolder(folderId: String): Flow<List<NoteEntity>> =
        MutableStateFlow(emptyList())
}

private class SyncFakeRemoteFoldersDataSource : app.tessera.data.remote.RemoteFoldersDataSource {
    private val folders = linkedMapOf<String, app.tessera.data.remote.RemoteFolder>()
    val uploaded = linkedMapOf<String, app.tessera.data.remote.RemoteFolder>()

    fun store(folder: app.tessera.data.remote.RemoteFolder) {
        folders[folder.id] = folder
    }

    override suspend fun fetchAll(uid: String) = folders.values.toList()
    override suspend fun upsertAll(uid: String, folders: List<app.tessera.data.remote.RemoteFolder>) {
        folders.forEach { uploaded[it.id] = it; this.folders[it.id] = it }
    }
    override fun observeFolders(uid: String) =
        MutableStateFlow(folders.values.toList())

    override suspend fun deleteOlderTombstones(uid: String, threshold: Long) = 0
}
