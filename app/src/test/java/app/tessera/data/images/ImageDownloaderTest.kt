package app.tessera.data.images

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveFile
import app.tessera.data.drive.DrivePermission
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageDownloaderTest {
    private lateinit var context: Context
    private lateinit var imagesDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imagesDir = File(context.cacheDir, "images")
        imagesDir.deleteRecursively()
    }

    private fun recipientRow(
        imageId: String = "img-1",
        driveFileId: String? = "drive-1",
        resourceKey: String? = "rk-1",
    ) = NoteImageEntity(
        imageId = imageId,
        noteId = "note-1",
        ownerUid = "owner-uid",
        localPath = null,
        driveFileId = driveFileId,
        resourceKey = resourceKey,
        status = ImageStatus.UPLOADED,
        width = 100,
        height = 80,
    )

    @Test
    fun `resolves row then downloads bytes into cacheDir images and returns the file`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val api = FakeDriveApi(downloadResult = bytes)
        val auth = FakeDriveAuthManager(token = "tok")
        val dao = FakeNoteImageDao().apply { rows["img-1"] = recipientRow() }
        val downloader = DriveImageDownloader(api, auth, context, dao)

        val file = downloader.download("img-1")

        assertTrue(file != null && file.exists())
        assertEquals(File(imagesDir, "img-1.webp").absolutePath, file!!.absolutePath)
        assertArrayEquals(bytes, file.readBytes())
        // Downloaded with the row's driveFileId + resourceKey (+ auth token).
        assertEquals(listOf("tok" to ("drive-1" to "rk-1")), api.downloadCalls)
    }

    @Test
    fun `returns null when row is missing`() = runTest {
        val api = FakeDriveApi(downloadResult = byteArrayOf(1))
        val auth = FakeDriveAuthManager(token = "tok")
        val downloader = DriveImageDownloader(api, auth, context, FakeNoteImageDao())

        assertNull(downloader.download("missing"))
        assertTrue(api.downloadCalls.isEmpty())
    }

    @Test
    fun `returns null when row has no driveFileId`() = runTest {
        val api = FakeDriveApi(downloadResult = byteArrayOf(1))
        val auth = FakeDriveAuthManager(token = "tok")
        val dao = FakeNoteImageDao().apply { rows["img-1"] = recipientRow(driveFileId = null) }
        val downloader = DriveImageDownloader(api, auth, context, dao)

        assertNull(downloader.download("img-1"))
        assertTrue(api.downloadCalls.isEmpty())
    }

    @Test
    fun `403 or 404 returns null and writes no file`() = runTest {
        val api = FakeDriveApi(downloadError = DriveApiException(403, "forbidden"))
        val auth = FakeDriveAuthManager(token = "tok")
        val dao = FakeNoteImageDao().apply { rows["img-1"] = recipientRow() }
        val downloader = DriveImageDownloader(api, auth, context, dao)

        assertNull(downloader.download("img-1"))
        assertFalse(File(imagesDir, "img-1.webp").exists())
    }

    @Test
    fun `no token returns null WITHOUT calling the API`() = runTest {
        val api = FakeDriveApi(downloadResult = byteArrayOf(1))
        val auth = FakeDriveAuthManager(token = null)
        val dao = FakeNoteImageDao().apply { rows["img-1"] = recipientRow() }
        val downloader = DriveImageDownloader(api, auth, context, dao)

        assertNull(downloader.download("img-1"))
        assertTrue(api.downloadCalls.isEmpty())
    }

    @Test
    fun `auth exception invalidates token and returns null`() = runTest {
        val api = FakeDriveApi(downloadError = DriveAuthException())
        val auth = FakeDriveAuthManager(token = "tok")
        val dao = FakeNoteImageDao().apply { rows["img-1"] = recipientRow() }
        val downloader = DriveImageDownloader(api, auth, context, dao)

        assertNull(downloader.download("img-1"))
        assertEquals(1, auth.invalidateCount)
        assertFalse(File(imagesDir, "img-1.webp").exists())
    }

    @Test
    fun `evictImageCache deletes oldest first until under the limit`() {
        imagesDir.mkdirs()
        // Four 10-byte files; limit 25 bytes → must drop the two oldest to reach 20.
        val a = writeAged("a.webp", size = 10, ageMillis = 4000)
        val b = writeAged("b.webp", size = 10, ageMillis = 3000)
        val c = writeAged("c.webp", size = 10, ageMillis = 2000)
        val d = writeAged("d.webp", size = 10, ageMillis = 1000)

        evictImageCache(imagesDir, maxBytes = 25)

        assertFalse("oldest deleted", a.exists())
        assertFalse("second-oldest deleted", b.exists())
        assertTrue("newer kept", c.exists())
        assertTrue("newest kept", d.exists())
        assertTrue(imagesDir.listFiles()!!.sumOf { it.length() } <= 25)
    }

    @Test
    fun `evictImageCache is a no-op when already under the limit`() {
        imagesDir.mkdirs()
        val a = writeAged("a.webp", size = 10, ageMillis = 2000)
        val b = writeAged("b.webp", size = 10, ageMillis = 1000)

        evictImageCache(imagesDir, maxBytes = 1000)

        assertTrue(a.exists())
        assertTrue(b.exists())
    }

    private fun writeAged(name: String, size: Int, ageMillis: Long): File {
        val f = File(imagesDir, name)
        f.writeBytes(ByteArray(size))
        f.setLastModified(System.currentTimeMillis() - ageMillis)
        return f
    }
}

private class FakeDriveAuthManager(
    private val token: String?,
) : DriveAuthManager {
    var invalidateCount = 0
        private set
    override val consentRequests: SharedFlow<android.app.PendingIntent> = MutableSharedFlow()

    override suspend fun accessToken(): String? = token

    override fun invalidateToken() {
        invalidateCount++
    }
}

private class FakeDriveApi(
    private val downloadResult: ByteArray = ByteArray(0),
    private val downloadError: Throwable? = null,
) : DriveApi {
    /** (token, (fileId, resourceKey)) tuples of each downloadFile call. */
    val downloadCalls = mutableListOf<Pair<String, Pair<String, String?>>>()

    override suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray {
        downloadCalls.add(token to (fileId to resourceKey))
        downloadError?.let { throw it }
        return downloadResult
    }

    override suspend fun uploadFile(token: String, name: String, parentId: String, appImageId: String, bytes: ByteArray): DriveFile =
        throw UnsupportedOperationException()
    override suspend fun findFolder(token: String, name: String): String? = throw UnsupportedOperationException()
    override suspend fun createFolder(token: String, name: String): String = throw UnsupportedOperationException()
    override suspend fun listPermissions(token: String, fileId: String): List<DrivePermission> = throw UnsupportedOperationException()
    override suspend fun createPermission(token: String, fileId: String, email: String) = throw UnsupportedOperationException()
    override suspend fun createAnyonePermission(token: String, fileId: String) = throw UnsupportedOperationException()
    override suspend fun deletePermission(token: String, fileId: String, permissionId: String) = throw UnsupportedOperationException()
    override suspend fun trashFile(token: String, fileId: String) = throw UnsupportedOperationException()
}

private class FakeNoteImageDao : NoteImageDao {
    val rows = mutableMapOf<String, NoteImageEntity>()

    override suspend fun byId(imageId: String): NoteImageEntity? = rows[imageId]

    override suspend fun upsert(image: NoteImageEntity) { rows[image.imageId] = image }
    override suspend fun byNote(noteId: String): List<NoteImageEntity> = rows.values.filter { it.noteId == noteId }
    override suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity> =
        rows.values.filter { it.ownerUid == uid && it.status == status }
    override suspend fun byOwner(uid: String): List<NoteImageEntity> = rows.values.filter { it.ownerUid == uid }
    override suspend fun backfillOwner(uid: String) = Unit
    override suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus) = Unit
    override suspend fun delete(imageId: String) { rows.remove(imageId) }
}
