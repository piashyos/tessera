package app.tessera.domain.usecase

import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.sync.ImageWorkScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryImageUploadUseCaseTest {

    private fun row(status: ImageStatus, localPath: String?) = NoteImageEntity(
        imageId = "img-1",
        noteId = "note-1",
        ownerUid = "uid-1",
        localPath = localPath,
        status = status,
        width = 10,
        height = 10,
    )

    @Test
    fun `FAILED with localPath flips to LOCAL_ONLY and schedules upload`() = runTest {
        val dao = FakeDao().apply { rows["img-1"] = row(ImageStatus.FAILED, "/local/img-1.webp") }
        val scheduler = FakeScheduler()

        RetryImageUploadUseCase(dao, scheduler)("img-1")

        assertEquals(ImageStatus.LOCAL_ONLY, dao.rows["img-1"]!!.status)
        assertEquals(1, scheduler.uploadRequests)
    }

    @Test
    fun `FAILED without localPath is a no-op`() = runTest {
        val dao = FakeDao().apply { rows["img-1"] = row(ImageStatus.FAILED, null) }
        val scheduler = FakeScheduler()

        RetryImageUploadUseCase(dao, scheduler)("img-1")

        assertEquals(ImageStatus.FAILED, dao.rows["img-1"]!!.status)
        assertEquals(0, scheduler.uploadRequests)
    }

    @Test
    fun `non-FAILED row is a no-op`() = runTest {
        val dao = FakeDao().apply { rows["img-1"] = row(ImageStatus.UPLOADED, "/local/img-1.webp") }
        val scheduler = FakeScheduler()

        RetryImageUploadUseCase(dao, scheduler)("img-1")

        assertEquals(ImageStatus.UPLOADED, dao.rows["img-1"]!!.status)
        assertEquals(0, scheduler.uploadRequests)
    }

    private class FakeScheduler : ImageWorkScheduler {
        var uploadRequests = 0
            private set
        override fun requestUpload() { uploadRequests++ }
        override fun requestPermissionSync() = Unit
        override fun requestCleanup() = Unit
    }

    private class FakeDao : NoteImageDao {
        val rows = mutableMapOf<String, NoteImageEntity>()
        override suspend fun byId(imageId: String): NoteImageEntity? = rows[imageId]
        override suspend fun upsert(image: NoteImageEntity) { rows[image.imageId] = image }
        override suspend fun byNote(noteId: String): List<NoteImageEntity> = rows.values.filter { it.noteId == noteId }
        override suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity> =
            rows.values.filter { it.ownerUid == uid && it.status == status }
        override suspend fun byOwner(uid: String): List<NoteImageEntity> = rows.values.filter { it.ownerUid == uid }
        override suspend fun all(): List<NoteImageEntity> = rows.values.toList()
        override suspend fun backfillOwner(uid: String) = Unit
        override suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus) = Unit
        override suspend fun delete(imageId: String) { rows.remove(imageId) }
    }
}
