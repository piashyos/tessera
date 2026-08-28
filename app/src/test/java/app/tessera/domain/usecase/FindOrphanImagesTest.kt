package app.tessera.domain.usecase

import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FindOrphanImagesTest {
    private fun row(
        imageId: String,
        noteId: String = "note-1",
        ownerUid: String? = "owner-1",
        status: ImageStatus = ImageStatus.UPLOADED,
        orphanedAt: Long? = null,
    ) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = ownerUid,
        localPath = null,
        driveFileId = "drive-$imageId",
        status = status,
        width = 10,
        height = 10,
        orphanedAt = orphanedAt,
    )

    private fun ref(imageId: String) = "before ${TESSERA_IMAGE_SCHEME}$imageId after"

    @Test
    fun `unreferenced row with no stamp is stamped with now`() {
        val img = row("img-1", orphanedAt = null)
        val result = findOrphans(listOf(img), notesContent = mapOf("note-1" to "no images here"), now = 500L)

        assertEquals(listOf("img-1"), result.toStamp.map { it.imageId })
        assertEquals(500L, result.toStamp.single().orphanedAt)
        assertTrue(result.toClear.isEmpty())
    }

    @Test
    fun `re-referenced orphan is cleared back to null`() {
        val img = row("img-1", orphanedAt = 100L)
        val result = findOrphans(listOf(img), notesContent = mapOf("note-1" to ref("img-1")), now = 500L)

        assertEquals(listOf("img-1"), result.toClear.map { it.imageId })
        assertEquals(null, result.toClear.single().orphanedAt)
        assertTrue(result.toStamp.isEmpty())
    }

    @Test
    fun `referenced row with no stamp is left alone`() {
        val img = row("img-1", orphanedAt = null)
        val result = findOrphans(listOf(img), notesContent = mapOf("note-1" to ref("img-1")), now = 500L)

        assertTrue(result.toStamp.isEmpty())
        assertTrue(result.toClear.isEmpty())
    }

    @Test
    fun `already-stamped unreferenced row is not re-stamped`() {
        val img = row("img-1", orphanedAt = 100L)
        val result = findOrphans(listOf(img), notesContent = mapOf("note-1" to "nothing"), now = 500L)

        assertTrue(result.toStamp.isEmpty())
        assertTrue(result.toClear.isEmpty())
    }

    @Test
    fun `ACCESS_LOST rows are never stamped nor cleared`() {
        val unreferenced = row("img-1", status = ImageStatus.ACCESS_LOST, orphanedAt = null)
        val staleStamp = row("img-2", status = ImageStatus.ACCESS_LOST, orphanedAt = 100L)
        val result = findOrphans(
            listOf(unreferenced, staleStamp),
            notesContent = mapOf("note-1" to ref("img-2")), // img-2 referenced, img-1 not
            now = 500L,
        )

        assertTrue(result.toStamp.isEmpty())
        assertTrue(result.toClear.isEmpty())
    }

    @Test
    fun `an app-trash note still keeps its image referenced`() {
        // The note is soft-deleted (its content is still supplied to the sweep) and references img-1.
        val img = row("img-1", orphanedAt = 100L)
        val result = findOrphans(
            listOf(img),
            notesContent = mapOf("trashed-note" to ref("img-1")),
            now = 500L,
        )

        // Because a live-but-trashed note references it, the orphan stamp is cleared, not kept.
        assertEquals(listOf("img-1"), result.toClear.map { it.imageId })
        assertTrue(result.toStamp.isEmpty())
    }

    @Test
    fun `a reference from any note counts, not only the owning note`() {
        val img = row("img-1", noteId = "note-1", orphanedAt = null)
        // Referenced by a different note than the row's noteId — still counts as referenced.
        val result = findOrphans(
            listOf(img),
            notesContent = mapOf("note-2" to ref("img-1")),
            now = 500L,
        )

        assertTrue(result.toStamp.isEmpty())
        assertTrue(result.toClear.isEmpty())
    }
}
