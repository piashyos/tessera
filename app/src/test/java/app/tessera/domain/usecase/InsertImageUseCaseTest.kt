package app.tessera.domain.usecase

import android.net.Uri
import app.tessera.data.local.entity.ImageStatus
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NoteImagesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed (not plain JUnit5, unlike sibling use-case tests) because the use case
 * signature takes a real android.net.Uri, which the mockable-android-jar stub can't supply.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsertImageUseCaseTest {
    private val noteId = NoteId("note-1")
    private val source: Uri = Uri.parse("content://media/external/images/1")

    @Test
    fun `returns the new imageId`() =
        runTest {
            val repository = FakeNoteImagesRepository(currentUid = "uid-1")
            val useCase = InsertImageUseCase(repository)

            val result = useCase(noteId, source)

            assertTrue(result.isSuccess)
            assertFalse(result.getOrThrow().isBlank())
        }

    @Test
    fun `inserts a LOCAL_ONLY row owned by the signed-in user`() =
        runTest {
            val repository = FakeNoteImagesRepository(currentUid = "uid-1")
            val useCase = InsertImageUseCase(repository)

            val imageId = useCase(noteId, source).getOrThrow()

            val inserted = repository.lastInserted
            assertEquals(imageId, inserted?.imageId)
            assertEquals(noteId, inserted?.noteId)
            assertEquals(ImageStatus.LOCAL_ONLY, inserted?.status)
            assertEquals("uid-1", inserted?.ownerUid)
        }

    @Test
    fun `inserts an unowned row when signed out`() =
        runTest {
            val repository = FakeNoteImagesRepository(currentUid = null)
            val useCase = InsertImageUseCase(repository)

            useCase(noteId, source).getOrThrow()

            assertNull(repository.lastInserted?.ownerUid)
        }

    @Test
    fun `requests an upload exactly once`() =
        runTest {
            val repository = FakeNoteImagesRepository(currentUid = "uid-1")
            val useCase = InsertImageUseCase(repository)

            useCase(noteId, source)

            assertEquals(1, repository.scheduleUploadCount)
        }
}

/**
 * Mirrors the contract [app.tessera.data.repository.NoteImagesRepositoryImpl] must satisfy:
 * insert a LOCAL_ONLY row owned by the current uid, then request exactly one upload.
 */
private class FakeNoteImagesRepository(
    private val currentUid: String?,
) : NoteImagesRepository {
    data class Inserted(val imageId: String, val noteId: NoteId, val ownerUid: String?, val status: ImageStatus)

    var lastInserted: Inserted? = null
        private set
    var scheduleUploadCount: Int = 0
        private set

    override suspend fun insertLocalImage(noteId: NoteId, source: Uri): Result<String> {
        val imageId = "image-${lastInserted?.let { 2 } ?: 1}"
        lastInserted = Inserted(imageId, noteId, currentUid, ImageStatus.LOCAL_ONLY)
        scheduleUploadCount++
        return Result.success(imageId)
    }
}
