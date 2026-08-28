package app.tessera.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.tessera.core.util.Clock
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveFile
import app.tessera.data.drive.DrivePermission
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.remote.RemoteImageRef
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageCleanupWorkerTest {
    private val uid = "owner-1"
    private val fixedNow = 10_000L
    private val hysteresis = 1_000L

    private lateinit var context: Context
    private lateinit var database: NotesDatabase
    private lateinit var imageDao: NoteImageDao
    private lateinit var noteDao: NoteDao

    private lateinit var drive: FakeDriveApi
    private lateinit var auth: FakeAuth
    private lateinit var notesRemote: FakeNotesRemote
    private lateinit var sharedRemote: FakeSharedRemote

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        imageDao = database.noteImageDao()
        noteDao = database.noteDao()
        drive = FakeDriveApi()
        auth = FakeAuth()
        notesRemote = FakeNotesRemote()
        sharedRemote = FakeSharedRemote()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `my orphan past the window is trashed, de-manifested, and dropped`() = runBlocking {
        noteDao.upsert(note("note-1", content = "no image reference here"))
        val local = tempFile("img-1")
        imageDao.upsert(orphan("img-1", "note-1", driveFileId = "drive-1", localPath = local.path, orphanedAt = 0L))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("drive-1"), drive.trashed)
        assertEquals(listOf(Triple(uid, "note-1", "img-1")), notesRemote.manifestRemovals)
        assertFalse(local.exists())
        assertNull(imageDao.byId("img-1"))
    }

    @Test
    fun `my orphan still within the window is left untouched`() = runBlocking {
        noteDao.upsert(note("note-1", content = "nothing"))
        imageDao.upsert(orphan("img-1", "note-1", driveFileId = "drive-1", orphanedAt = fixedNow - 500))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.trashed.isEmpty())
        assertNotNull(imageDao.byId("img-1"))
    }

    @Test
    fun `a recipient's orphan drops only the row and never calls Drive`() = runBlocking {
        // No note references it and the note is gone; the file belongs to someone else.
        imageDao.upsert(
            orphan("img-1", "note-1", ownerUid = "someone-else", driveFileId = "drive-1", orphanedAt = 0L),
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.trashed.isEmpty())
        assertTrue(notesRemote.manifestRemovals.isEmpty())
        assertNull(imageDao.byId("img-1"))
    }

    @Test
    fun `ACCESS_LOST rows are never swept`() = runBlocking {
        imageDao.upsert(
            orphan("img-1", "note-1", driveFileId = "drive-1", orphanedAt = 0L)
                .copy(status = ImageStatus.ACCESS_LOST),
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.trashed.isEmpty())
        assertNotNull(imageDao.byId("img-1"))
    }

    @Test
    fun `a 404 on trash is treated as already-gone and the row still drops`() = runBlocking {
        drive.failTrashWith = DriveApiException(404, "not found")
        noteDao.upsert(note("note-1", content = "nothing"))
        imageDao.upsert(orphan("img-1", "note-1", driveFileId = "drive-1", orphanedAt = 0L))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(Triple(uid, "note-1", "img-1")), notesRemote.manifestRemovals)
        assertNull(imageDao.byId("img-1"))
    }

    @Test
    fun `a missing token defers Drive work but still drops recipient rows`() = runBlocking {
        auth.token = null
        noteDao.upsert(note("note-1", content = "nothing"))
        imageDao.upsert(orphan("mine", "note-1", driveFileId = "drive-1", orphanedAt = 0L))
        imageDao.upsert(
            orphan("theirs", "note-2", ownerUid = "someone-else", driveFileId = "drive-2", orphanedAt = 0L),
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.trashed.isEmpty())
        // My Drive-backed orphan is deferred (kept for a later run); the recipient row is dropped.
        assertNotNull(imageDao.byId("mine"))
        assertNull(imageDao.byId("theirs"))
    }

    @Test
    fun `an auth failure invalidates the token and retries`() = runBlocking {
        drive.failTrashWith = DriveAuthException()
        noteDao.upsert(note("note-1", content = "nothing"))
        imageDao.upsert(orphan("img-1", "note-1", driveFileId = "drive-1", orphanedAt = 0L))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(auth.invalidated)
        assertNotNull(imageDao.byId("img-1")) // not deleted; retried later
    }

    @Test
    fun `an unreferenced row with no stamp is stamped this run, not reclaimed`() = runBlocking {
        noteDao.upsert(note("note-1", content = "nothing"))
        imageDao.upsert(orphan("img-1", "note-1", driveFileId = "drive-1", orphanedAt = null))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.trashed.isEmpty())
        assertEquals(fixedNow, imageDao.byId("img-1")?.orphanedAt) // stamped, awaiting the window
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun runWorker(): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = ImageCleanupWorker(
                appContext,
                workerParameters,
                drive,
                auth,
                imageDao,
                noteDao,
                notesRemote,
                sharedRemote,
                object : Clock { override fun nowMs(): Long = fixedNow },
                hysteresis,
            )
        }
        val worker = TestListenableWorkerBuilder<ImageCleanupWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ImageCleanupWorker.KEY_UID to uid))
            .build()
        return runBlocking { worker.doWork() }
    }

    private fun tempFile(name: String): File =
        File.createTempFile(name, ".webp", context.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    private fun note(id: String, content: String) = NoteEntity(
        id = id,
        title = "title",
        content = content,
        isPinned = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = 1L,
        ownerId = uid,
    )

    private fun orphan(
        imageId: String,
        noteId: String,
        ownerUid: String? = uid,
        driveFileId: String?,
        localPath: String? = null,
        orphanedAt: Long?,
    ) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = ownerUid,
        localPath = localPath,
        driveFileId = driveFileId,
        status = ImageStatus.UPLOADED,
        width = 10,
        height = 10,
        orphanedAt = orphanedAt,
    )

    // --- fakes ------------------------------------------------------------------------------------

    private class FakeDriveApi : DriveApi {
        val trashed = mutableListOf<String>()
        var failTrashWith: Exception? = null

        override suspend fun trashFile(token: String, fileId: String) {
            failTrashWith?.let { throw it }
            trashed += fileId
        }

        override suspend fun uploadFile(
            token: String,
            name: String,
            parentId: String,
            appImageId: String,
            bytes: ByteArray,
        ): DriveFile = throw UnsupportedOperationException()

        override suspend fun findFolder(token: String, name: String): String? = throw UnsupportedOperationException()
        override suspend fun createFolder(token: String, name: String): String = throw UnsupportedOperationException()
        override suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray =
            throw UnsupportedOperationException()
        override suspend fun listPermissions(token: String, fileId: String): List<DrivePermission> =
            throw UnsupportedOperationException()
        override suspend fun createPermission(token: String, fileId: String, email: String) =
            throw UnsupportedOperationException()
        override suspend fun createAnyonePermission(token: String, fileId: String) =
            throw UnsupportedOperationException()
        override suspend fun deletePermission(token: String, fileId: String, permissionId: String) =
            throw UnsupportedOperationException()
    }

    private class FakeAuth : DriveAuthManager {
        var token: String? = "token"
        var invalidated = false
        private val _consent = kotlinx.coroutines.flow.MutableSharedFlow<android.app.PendingIntent>()
        override val consentRequests: SharedFlow<android.app.PendingIntent> = _consent

        override suspend fun accessToken(): String? = token
        override fun invalidateToken() { invalidated = true }
    }

    private class FakeNotesRemote : RemoteNotesDataSource {
        val manifestRemovals = mutableListOf<Triple<String, String, String>>()

        override suspend fun updateImageManifest(uid: String, noteId: String, imageId: String, ref: RemoteImageRef?) {
            require(ref == null) { "cleanup must remove, never set, a manifest entry" }
            manifestRemovals += Triple(uid, noteId, imageId)
        }

        override suspend fun fetchAll(uid: String): List<RemoteNote> = emptyList()
        override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) = Unit
        override fun observeNotes(uid: String): Flow<List<RemoteNote>> = MutableStateFlow(emptyList())
        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int = 0
        override suspend fun deleteByIds(uid: String, ids: List<String>) = Unit
    }

    private class FakeSharedRemote : RemoteSharedNotesDataSource {
        val manifestRemovals = mutableListOf<Pair<String, String>>()

        override suspend fun updateImageManifest(noteId: String, imageId: String, ref: RemoteImageRef?) {
            require(ref == null)
            manifestRemovals += noteId to imageId
        }

        override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) = Unit
        override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) = Unit
        override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) = Unit
        override suspend fun updateRole(noteId: String, uid: String, role: String) = Unit
        override suspend fun removeMember(noteId: String, uid: String) = Unit
        override suspend fun leaveNote(noteId: String, myUid: String) = Unit
        override suspend fun pushSharedNote(note: RemoteSharedNote) = Unit
        override suspend fun fetchAll(uid: String): List<RemoteSharedNote> = emptyList()
        override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> = MutableStateFlow(emptyList())
    }
}
