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
import app.tessera.data.drive.DrivePermission
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.dao.SharedMemberDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.local.entity.SharedMemberEntity
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
class ImagePermissionWorkerTest {
    private val uid = "owner-1"
    private val myEmail = "owner@example.com"
    private val fixedNow = 777_000L

    private lateinit var context: Context
    private lateinit var database: NotesDatabase
    private lateinit var imageDao: NoteImageDao
    private lateinit var noteDao: NoteDao
    private lateinit var memberDao: SharedMemberDao

    private lateinit var drive: FakeDriveApi
    private lateinit var auth: FakeDriveAuthManager
    private lateinit var authRepo: FakeAuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        imageDao = database.noteImageDao()
        noteDao = database.noteDao()
        memberDao = database.sharedMemberDao()
        drive = FakeDriveApi()
        auth = FakeDriveAuthManager()
        authRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- tests ------------------------------------------------------------------------------------

    @Test
    fun `grants a member with no existing permission`() = runBlocking {
        noteDao.upsert(note("note-1"))
        memberDao.upsertAll(listOf(member("note-1", "u2", "alice@example.com")))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(owner())

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("drive-1" to "alice@example.com"), drive.granted)
        assertTrue(drive.revoked.isEmpty())
    }

    @Test
    fun `revokes a reader who is no longer a member`() = runBlocking {
        noteDao.upsert(note("note-1"))
        memberDao.upsertAll(listOf(member("note-1", "u2", "alice@example.com")))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(
            owner(),
            reader("perm-alice", "alice@example.com"),
            reader("perm-bob", "bob@example.com"),
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(drive.granted.isEmpty())
        assertEquals(listOf("drive-1" to "perm-bob"), drive.revoked)
    }

    @Test
    fun `private note revokes all user reader grants`() = runBlocking {
        noteDao.upsert(note("note-1"))
        // No members => private note.
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(
            owner(),
            reader("perm-alice", "alice@example.com"),
            reader("perm-bob", "bob@example.com"),
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(setOf("drive-1" to "perm-alice", "drive-1" to "perm-bob"), drive.revoked.toSet())
    }

    @Test
    fun `never revokes the owner or my own reader permission`() = runBlocking {
        noteDao.upsert(note("note-1"))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(owner(), reader("perm-me", myEmail))

        runWorker()

        assertTrue(drive.revoked.isEmpty())
        assertTrue(drive.granted.isEmpty())
    }

    @Test
    fun `stamps permissionsSyncedAt on success`() = runBlocking {
        noteDao.upsert(note("note-1"))
        memberDao.upsertAll(listOf(member("note-1", "u2", "alice@example.com")))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(owner())

        runWorker()

        assertEquals(fixedNow, imageDao.byId("img-1")?.permissionsSyncedAt)
    }

    @Test
    fun `missing consent returns success without touching Drive or rows`() = runBlocking {
        auth.token = null
        noteDao.upsert(note("note-1"))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))
        drive.permissions["drive-1"] = mutableListOf(owner())

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, drive.listCalls)
        assertNull(imageDao.byId("img-1")?.permissionsSyncedAt)
    }

    @Test
    fun `auth failure invalidates the token and retries`() = runBlocking {
        drive.failListWith = DriveAuthException()
        noteDao.upsert(note("note-1"))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(auth.invalidated)
        assertNull(imageDao.byId("img-1")?.permissionsSyncedAt)
    }

    @Test
    fun `transient Drive error retries and does not stamp`() = runBlocking {
        drive.failListWith = DriveApiException(500, "boom")
        noteDao.upsert(note("note-1"))
        imageDao.upsert(uploadedRow("img-1", "note-1", "drive-1"))

        val result = runWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertNull(imageDao.byId("img-1")?.permissionsSyncedAt)
    }

    @Test
    fun `only uploaded rows with a drive id are reconciled`() = runBlocking {
        noteDao.upsert(note("note-1"))
        imageDao.upsert(uploadedRow("img-1", "note-1", driveFileId = null))
        imageDao.upsert(
            uploadedRow("img-2", "note-1", "drive-2").copy(status = ImageStatus.LOCAL_ONLY),
        )
        drive.permissions["drive-2"] = mutableListOf(owner())

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, drive.listCalls)
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun runWorker(): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = ImagePermissionWorker(
                appContext,
                workerParameters,
                drive,
                auth,
                imageDao,
                memberDao,
                authRepo,
                object : Clock { override fun nowMs(): Long = fixedNow },
            )
        }
        val worker = TestListenableWorkerBuilder<ImagePermissionWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ImagePermissionWorker.KEY_UID to uid))
            .build()
        return runBlocking { worker.doWork() }
    }

    private fun note(id: String) = NoteEntity(
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

    private fun member(noteId: String, uid: String, email: String) = SharedMemberEntity(
        noteId = noteId,
        uid = uid,
        displayName = email.substringBefore('@'),
        email = email,
        photoUrl = null,
        role = "EDITOR",
        joinedAt = 1L,
    )

    private fun uploadedRow(imageId: String, noteId: String, driveFileId: String?) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = uid,
        localPath = null,
        driveFileId = driveFileId,
        status = ImageStatus.UPLOADED,
        width = 640,
        height = 480,
    )

    private fun owner() = DrivePermission(id = "perm-owner", emailAddress = myEmail, type = "user", role = "owner")

    private fun reader(id: String, email: String) =
        DrivePermission(id = id, emailAddress = email, type = "user", role = "reader")

    // --- fakes ------------------------------------------------------------------------------------

    private class FakeDriveApi : DriveApi {
        val permissions = mutableMapOf<String, MutableList<DrivePermission>>()
        val granted = mutableListOf<Pair<String, String>>()
        val revoked = mutableListOf<Pair<String, String>>()
        val anyoneLinked = mutableListOf<String>()
        var listCalls = 0
        var failListWith: Exception? = null

        override suspend fun listPermissions(token: String, fileId: String): List<DrivePermission> {
            listCalls++
            failListWith?.let { throw it }
            return permissions[fileId].orEmpty().toList()
        }

        override suspend fun createPermission(token: String, fileId: String, email: String) {
            granted += fileId to email
        }

        override suspend fun createAnyonePermission(token: String, fileId: String) {
            anyoneLinked += fileId
        }

        override suspend fun deletePermission(token: String, fileId: String, permissionId: String) {
            revoked += fileId to permissionId
        }

        override suspend fun uploadFile(
            token: String,
            name: String,
            parentId: String,
            appImageId: String,
            bytes: ByteArray,
        ): DriveFile = throw UnsupportedOperationException()

        override suspend fun findFolder(token: String, name: String): String? =
            throw UnsupportedOperationException()

        override suspend fun createFolder(token: String, name: String): String =
            throw UnsupportedOperationException()

        override suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray =
            throw UnsupportedOperationException()

        override suspend fun trashFile(token: String, fileId: String) =
            throw UnsupportedOperationException()
    }

    private class FakeDriveAuthManager : DriveAuthManager {
        var token: String? = "token"
        var invalidated = false
        private val _consent = kotlinx.coroutines.flow.MutableSharedFlow<android.app.PendingIntent>()
        override val consentRequests: SharedFlow<android.app.PendingIntent> = _consent

        override suspend fun accessToken(): String? = token

        override fun invalidateToken() {
            invalidated = true
        }
    }

    private inner class FakeAuthRepository : AuthRepository {
        override val currentUser: StateFlow<UserId?> = MutableStateFlow(UserId(uid))
        override val currentProfile: StateFlow<UserProfile?> =
            MutableStateFlow(UserProfile(id = UserId(uid), displayName = "Owner", email = myEmail, photoUrl = null))

        override suspend fun signIn(): Result<UserId> = Result.success(UserId(uid))
        override suspend fun signOut() = Unit
    }
}
