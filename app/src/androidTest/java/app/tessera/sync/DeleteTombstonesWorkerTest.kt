package app.tessera.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.mapper.SharedNoteMapper
import app.tessera.data.remote.RemoteFolder
import app.tessera.data.remote.RemoteFoldersDataSource
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.repository.SyncRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteTombstonesWorkerTest {
    private lateinit var database: NotesDatabase
    private lateinit var context: Context
    private val retentionMs = 30L * 24 * 60 * 60 * 1000
    private val now = 40L * 24 * 60 * 60 * 1000
    private val threshold = now - retentionMs

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun purgesLocalAndRemoteTombstonesOlderThanRetention() = runBlocking {
        val dao = database.noteDao()
        dao.upsert(tombstone(id = "old", deletedAt = threshold - 1))
        dao.upsert(tombstone(id = "recent", deletedAt = threshold + 1))

        val remote = RecordingRemote()
        val syncRepository = SyncRepositoryImpl(
            app.tessera.data.local.RoomTransactor(database),
            dao,
            database.tagDao(),
            database.folderDao(),
            database.sharedMemberDao(),
            remote,
            NoOpRemoteFolders(),
            NoOpRemoteShared(),
            NoteMapper(),
            SharedNoteMapper(),
            FixedClock(now),
        )
        val worker = buildWorker(syncRepository)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertNull(dao.getById("old"))
        assertNotNull(dao.getById("recent"))
        assertEquals(threshold, remote.purgedThreshold)
    }

    private fun buildWorker(syncRepository: SyncRepositoryImpl): DeleteTombstonesWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = DeleteTombstonesWorker(appContext, workerParameters, syncRepository)
        }
        return TestListenableWorkerBuilder<DeleteTombstonesWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(DeleteTombstonesWorker.KEY_UID to "owner-1"))
            .build()
    }

    private fun tombstone(
        id: String,
        deletedAt: Long,
    ) = NoteEntity(
        id = id,
        title = "title",
        content = "content",
        isPinned = false,
        createdAt = 1L,
        updatedAt = deletedAt,
        deletedAt = deletedAt,
        syncedAt = 1L,
        ownerId = "owner-1",
    )

    private class FixedClock(
        private val value: Long,
    ) : Clock {
        override fun nowMs(): Long = value
    }

    private class RecordingRemote : RemoteNotesDataSource {
        var purgedThreshold: Long? = null

        override suspend fun fetchAll(uid: String): List<RemoteNote> = emptyList()

        override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) = Unit

        override fun observeNotes(uid: String): Flow<List<RemoteNote>> = emptyFlow()

        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
            purgedThreshold = threshold
            return 0
        }

        override suspend fun deleteByIds(uid: String, ids: List<String>) = Unit
    }

    private class NoOpRemoteFolders : RemoteFoldersDataSource {
        override suspend fun fetchAll(uid: String): List<RemoteFolder> = emptyList()
        override suspend fun upsertAll(uid: String, folders: List<RemoteFolder>) = Unit
        override fun observeFolders(uid: String): Flow<List<RemoteFolder>> = emptyFlow()
        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int = 0
    }

    private class NoOpRemoteShared : RemoteSharedNotesDataSource {
        override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) = Unit
        override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) = Unit
        override suspend fun addMember(noteId: String, uid: String, member: app.tessera.data.remote.RemoteShareMember) = Unit
        override suspend fun updateRole(noteId: String, uid: String, role: String) = Unit
        override suspend fun removeMember(noteId: String, uid: String) = Unit
        override suspend fun leaveNote(noteId: String, myUid: String) = Unit
        override suspend fun pushSharedNote(note: RemoteSharedNote) = Unit
        override suspend fun fetchAll(uid: String): List<RemoteSharedNote> = emptyList()
        override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> = emptyFlow()
    }
}
