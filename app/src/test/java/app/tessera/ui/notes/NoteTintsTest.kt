package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteTintsTest {
    private val blue = Folder(FolderId("blue"), "Blue", null, 1L, color = "blue")
    private val plain = Folder(FolderId("plain"), "Plain", null, 2L)
    private val subOfBlue = Folder(FolderId("sub-blue"), "Sub", blue.id, 3L)
    private val subRed = Folder(FolderId("sub-red"), "SubRed", blue.id, 4L, color = "red")
    private val subOfPlain = Folder(FolderId("sub-plain"), "SubPlain", plain.id, 5L)
    private val folders = listOf(blue, plain, subOfBlue, subRed, subOfPlain)

    private fun note(id: String) =
        Note(
            id = NoteId(id), title = "t", content = "b", tags = emptyList(), isPinned = false,
            createdAt = 1L, updatedAt = 1L, deletedAt = null, syncedAt = null, ownerId = null,
        )

    @Test
    fun `own colour wins`() {
        val tints = noteTints(listOf(note("a")), mapOf(NoteId("a") to blue.id), folders)
        assertEquals(mapOf(NoteId("a") to "blue"), tints)
    }

    @Test
    fun `a subfolder with its own colour keeps it, one without takes its parent's`() {
        val tints = noteTints(
            listOf(note("own"), note("inherit")),
            mapOf(NoteId("own") to subRed.id, NoteId("inherit") to subOfBlue.id),
            folders,
        )
        assertEquals(mapOf(NoteId("own") to "red", NoteId("inherit") to "blue"), tints)
    }

    @Test
    fun `no colour on the folder or its parent gives no tint`() {
        val tints = noteTints(
            listOf(note("a"), note("b")),
            mapOf(NoteId("a") to plain.id, NoteId("b") to subOfPlain.id),
            folders,
        )
        assertEquals(emptyMap<NoteId, String>(), tints)
    }

    @Test
    fun `a note without a folder, or in an unknown one, gives no tint`() {
        val tints = noteTints(
            listOf(note("loose"), note("ghost")),
            mapOf(NoteId("ghost") to FolderId("deleted")),
            folders,
        )
        assertEquals(emptyMap<NoteId, String>(), tints)
    }
}
