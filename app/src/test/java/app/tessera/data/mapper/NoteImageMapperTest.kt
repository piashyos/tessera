package app.tessera.data.mapper

import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.remote.RemoteImageRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteImageMapperTest {
    private val mapper = NoteImageMapper()

    private fun uploadedRow(
        imageId: String = "img-1",
        noteId: String = "note-1",
    ) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = "owner-1",
        localPath = "/local/$imageId.webp",
        driveFileId = "drive-1",
        resourceKey = "rk-1",
        status = ImageStatus.UPLOADED,
        width = 640,
        height = 480,
    )

    @Test
    fun `toRemoteRef then toPulledEntity round-trips the manifest fields`() {
        val ref = mapper.toRemoteRef(uploadedRow())

        assertEquals("drive-1", ref.driveFileId)
        assertEquals("rk-1", ref.resourceKey)
        assertEquals("owner-1", ref.ownerUid)
        assertEquals(640, ref.width)
        assertEquals(480, ref.height)

        val pulled = mapper.toPulledEntity("note-1", "img-1", ref)
        assertEquals("img-1", pulled.imageId)
        assertEquals("note-1", pulled.noteId)
        assertEquals("drive-1", pulled.driveFileId)
        assertEquals("rk-1", pulled.resourceKey)
        assertEquals("owner-1", pulled.ownerUid)
        assertEquals(640, pulled.width)
        assertEquals(480, pulled.height)
    }

    @Test
    fun `toPulledEntity produces an UPLOADED row with no local file`() {
        val pulled = mapper.toPulledEntity(
            "note-1",
            "img-1",
            RemoteImageRef(driveFileId = "drive-1", ownerUid = "someone-else", width = 10, height = 20),
        )

        assertEquals(ImageStatus.UPLOADED, pulled.status)
        assertNull(pulled.localPath)
        // The row carries the real owner, so backfillOwner (null-owner only) can't mis-claim it.
        assertEquals("someone-else", pulled.ownerUid)
    }

    @Test
    fun `rowsToUpsert returns only imageIds absent from the local set`() {
        val manifest = mapOf(
            "existing" to RemoteImageRef(driveFileId = "d-existing", ownerUid = "o"),
            "fresh" to RemoteImageRef(driveFileId = "d-fresh", ownerUid = "o"),
        )

        val result = mapper.rowsToUpsert("note-1", manifest, existingImageIds = setOf("existing"))

        assertEquals(listOf("fresh"), result.map { it.imageId })
        assertEquals(ImageStatus.UPLOADED, result.single().status)
    }

    @Test
    fun `rowsToUpsert is empty when every manifest image already has a local row`() {
        val manifest = mapOf("a" to RemoteImageRef(driveFileId = "da", ownerUid = "o"))

        val result = mapper.rowsToUpsert("note-1", manifest, existingImageIds = setOf("a"))

        assertTrue(result.isEmpty())
    }
}
