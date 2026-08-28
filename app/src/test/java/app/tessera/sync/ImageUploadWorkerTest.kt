package app.tessera.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveFile
import app.tessera.data.drive.DriveFolderCache
import app.tessera.data.drive.DrivePermission
import app.tessera.data.drive.DriveQuotaException
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.mapper.NoteImageMapper
import app.tessera.data.remote.RemoteImageRef
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageUploadWorkerTest {
    private val uid = "owner-1"

    private lateinit var context: Context
    private lateinit var database: NotesDatabase
    private lateinit var imageDao: NoteImageDao
    private lateinit var noteDao: NoteDao

    private lateinit var drive: FakeDriveApi
    private lateinit var auth: FakeDriveAuthManager
    private lateinit var folderCache: FakeDriveFolderCache
    private lateinit var notesRemote: FakeNotesRemote
    private lateinit var sharedRemote: FakeSharedRemote
    private lateinit var notifier: FakeNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        imageDao = database.noteImageDao()
        noteDao = database.noteDao()
        drive = FakeDriveApi()
        auth = FakeDriveAuthManager()
        folderCache = FakeDriveFolderCache()
        notesRemote = FakeNotesRemote()
        sharedRemote = FakeSharedRemote()
        notifier = FakeNotifier()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- tests ------------------------------------------------------------------------------------

    @Test
    fun `happy path uploads local image, marks it UPLOADED, and patches the manifest`() = runBlocking {
        noteDao.upsert(privateNote("note-1"))
        val path = tempImage("img-1")
        imageDao.upsert(localRow("img-1", "note-1", path))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = imageDao.byId("img-1")!!
        assertEquals(ImageStatus.UPLOADED, row.status)
        assertEquals("drive-img-1", row.driveFileId)
        assertEquals("rk-img-1", row.resourceKey)
        // Manifest patched on the private (notes) document with the owner's own uid.
        assertEquals(1, notesRemote.writes.size)
        val (noteId, imageId, ref) = notesRemote.writes.single()
        assertEquals("note-1", noteId)
        assertEquals("img-1", imageId)
        assertEquals("drive-img-1", ref?.driveFileId)
        assertEquals(uid, ref?.ownerUid)
        assertEquals(640, ref?.width)
        assertTrue(sharedRemote.writes.isEmpty())
    }

    @Test
    fun `shared note routes the manifest write to the shared client`() = runBlocking {
        noteDao.upsert(privateNote("note-1").copy(shareState = NoteEntity.SHARE_STATE_SHARED, myRole = "OWNER"))
        val path = tempImage("img-1")
        imageDao.upsert(localRow("img-1", "note-1", path))

        runWorker()

        assertEquals(1, sharedRemote.writes.size)
        assertTrue(notesRemote.writes.isEmpty())
    }

    @Test
    fun `folder is resolved once and then served from the cache`() = runBlocking {
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", tempImage("img-1")))
        runWorker()

        imageDao.upsert(localRow("img-2", "note-1", tempImage("img-2")))
        runWorker()

        assertEquals(1, drive.findFolderCalls)
        assertEquals(0, drive.createFolderCalls)
        assertEquals("folder-tessera", folderCache.get())
    }

    @Test
    fun `quota exceeded marks row FAILED, emits a user message, and does not retry`() = runBlocking {
        drive.failWith = DriveQuotaException()
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", tempImage("img-1")))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(ImageStatus.FAILED, imageDao.byId("img-1")?.status)
        assertEquals(listOf(ImageUploadMessage.DRIVE_QUOTA_EXCEEDED), notifier.reported)
    }

    @Test
    fun `transient auth error invalidates the token and requests a retry`() = runBlocking {
        drive.failWith = DriveAuthException()
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", tempImage("img-1")))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(auth.invalidated)
    }

    @Test
    fun `missing source file marks the row FAILED and clears localPath`() = runBlocking {
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", "/does/not/exist.webp"))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val row = imageDao.byId("img-1")!!
        assertEquals(ImageStatus.FAILED, row.status)
        assertNull(row.localPath)
        assertEquals(0, drive.uploadCalls)
    }

    @Test
    fun `transient IO error requests a retry`() = runBlocking {
        drive.failWith = IOException("network down")
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", tempImage("img-1")))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `missing consent reverts my UPLOADING rows and leaves UPLOADED rows alone`() = runBlocking {
        auth.token = null
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("stuck", "note-1", "/x").copy(status = ImageStatus.UPLOADING))
        imageDao.upsert(localRow("done", "note-1", null).copy(status = ImageStatus.UPLOADED, driveFileId = "d"))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(ImageStatus.LOCAL_ONLY, imageDao.byId("stuck")?.status)
        assertEquals(ImageStatus.UPLOADED, imageDao.byId("done")?.status)
        assertEquals(0, drive.uploadCalls)
    }

    @Test
    fun `stale UPLOADING rows from a crashed run are reset and re-uploaded on entry`() = runBlocking {
        noteDao.upsert(privateNote("note-1"))
        imageDao.upsert(localRow("img-1", "note-1", tempImage("img-1")).copy(status = ImageStatus.UPLOADING))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        // Reset to LOCAL_ONLY on entry, then picked up and uploaded in the same run.
        assertEquals(ImageStatus.UPLOADED, imageDao.byId("img-1")?.status)
        assertEquals("drive-img-1", imageDao.byId("img-1")?.driveFileId)
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun runWorker(): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = ImageUploadWorker(
                appContext,
                workerParameters,
                drive,
                auth,
                folderCache,
                imageDao,
                noteDao,
                notesRemote,
                sharedRemote,
                NoteImageMapper(),
                notifier,
                FakeImageWorkScheduler(),
            )
        }
        val worker = TestListenableWorkerBuilder<ImageUploadWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ImageUploadWorker.KEY_UID to uid))
            .build()
        return runBlocking { worker.doWork() }
    }

    private fun tempImage(name: String): String {
        val file = File.createTempFile(name, ".webp", context.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.absolutePath
    }

    private fun privateNote(id: String) = NoteEntity(
        id = id,
        title = "title",
        content = "content",
        isPinned = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = 1L,
        ownerId = uid,
    )

    private fun localRow(imageId: String, noteId: String, localPath: String?) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = uid,
        localPath = localPath,
        status = ImageStatus.LOCAL_ONLY,
        width = 640,
        height = 480,
    )

    // --- fakes ------------------------------------------------------------------------------------

    private class FakeDriveApi : DriveApi {
        var failWith: Exception? = null
        var uploadCalls = 0
        var findFolderCalls = 0
        var createFolderCalls = 0

        override suspend fun uploadFile(
            token: String,
            name: String,
            parentId: String,
            appImageId: String,
            bytes: ByteArray,
        ): DriveFile {
            uploadCalls++
            failWith?.let { throw it }
            return DriveFile(id = "drive-$appImageId", resourceKey = "rk-$appImageId")
        }

        override suspend fun findFolder(token: String, name: String): String? {
            findFolderCalls++
            return "folder-tessera"
        }

        override suspend fun createFolder(token: String, name: String): String {
            createFolderCalls++
            return "folder-tessera"
        }

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

        override suspend fun trashFile(token: String, fileId: String) =
            throw UnsupportedOperationException()
    }

    private class FakeDriveAuthManager : DriveAuthManager {
        var token: String? = "token"
        var invalidated = false
        private val _consent = MutableSharedFlow<android.app.PendingIntent>()
        override val consentRequests: SharedFlow<android.app.PendingIntent> = _consent

        override suspend fun accessToken(): String? = token

        override fun invalidateToken() {
            invalidated = true
        }
    }

    private class FakeDriveFolderCache : DriveFolderCache {
        private var value: String? = null
        override suspend fun get(): String? = value
        override suspend fun put(folderId: String) {
            value = folderId
        }
    }

    private class FakeNotesRemote : RemoteNotesDataSource {
        val writes = mutableListOf<Triple<String, String, RemoteImageRef?>>()

        override suspend fun fetchAll(uid: String): List<RemoteNote> = emptyList()
        override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) = Unit
        override fun observeNotes(uid: String): Flow<List<RemoteNote>> = emptyFlow()
        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int = 0
        override suspend fun deleteByIds(uid: String, ids: List<String>) = Unit
        override suspend fun updateImageManifest(
            uid: String,
            noteId: String,
            imageId: String,
            ref: RemoteImageRef?,
        ) {
            writes += Triple(noteId, imageId, ref)
        }
    }

    private class FakeSharedRemote : RemoteSharedNotesDataSource {
        val writes = mutableListOf<Triple<String, String, RemoteImageRef?>>()

        override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) = Unit
        override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) = Unit
        override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) = Unit
        override suspend fun updateRole(noteId: String, uid: String, role: String) = Unit
        override suspend fun removeMember(noteId: String, uid: String) = Unit
        override suspend fun leaveNote(noteId: String, myUid: String) = Unit
        override suspend fun pushSharedNote(note: RemoteSharedNote) = Unit
        override suspend fun fetchAll(uid: String): List<RemoteSharedNote> = emptyList()
        override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> = emptyFlow()
        override suspend fun updateImageManifest(noteId: String, imageId: String, ref: RemoteImageRef?) {
            writes += Triple(noteId, imageId, ref)
        }
    }

    private class FakeImageWorkScheduler : ImageWorkScheduler {
        var uploadRequests = 0
        var permissionSyncRequests = 0
        override fun requestUpload() { uploadRequests++ }
        override fun requestPermissionSync() { permissionSyncRequests++ }
        override fun requestCleanup() = Unit
    }

    private class FakeNotifier : ImageUploadNotifier {
        val reported = mutableListOf<ImageUploadMessage>()
        override val messages: SharedFlow<ImageUploadMessage> = MutableSharedFlow()
        override fun report(message: ImageUploadMessage) {
            reported += message
        }
    }
}
