package app.tessera.data.mapper

import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteMapperTest {
    private val mapper = NoteMapper()

    @Test
    fun `toDomain maps entity and normalizes tags`() {
        val entity =
            NoteEntity(
                id = "note-1",
                title = "Title",
                content = "Body",
                isPinned = true,
                createdAt = 10L,
                updatedAt = 20L,
                deletedAt = null,
                syncedAt = 30L,
                ownerId = "user-1",
            )

        val note = mapper.toDomain(entity, listOf(" beta ", "alpha", "alpha"))

        assertEquals(NoteId("note-1"), note.id)
        assertEquals("Title", note.title)
        assertEquals("Body", note.content)
        assertEquals(listOf(Tag("alpha"), Tag("beta")), note.tags)
        assertEquals(true, note.isPinned)
        assertEquals(10L, note.createdAt)
        assertEquals(20L, note.updatedAt)
        assertEquals(30L, note.syncedAt)
        assertEquals(UserId("user-1"), note.ownerId)
    }

    @Test
    fun `toEntity preserves note fields`() {
        val note =
            Note(
                id = NoteId("note-2"),
                title = "Another",
                content = "Markdown",
                tags = listOf(Tag("x")),
                isPinned = false,
                createdAt = 11L,
                updatedAt = 22L,
                deletedAt = 33L,
                syncedAt = null,
                ownerId = UserId("user-2"),
            )

        val entity = mapper.toEntity(note)

        assertEquals(
            NoteEntity(
                id = "note-2",
                title = "Another",
                content = "Markdown",
                isPinned = false,
                createdAt = 11L,
                updatedAt = 22L,
                deletedAt = 33L,
                syncedAt = null,
                ownerId = "user-2",
            ),
            entity,
        )
    }

    @Test
    fun `entity to domain to entity round trip preserves note metadata`() {
        val entity =
            NoteEntity(
                id = "note-3",
                title = "Round trip",
                content = "Keeps metadata",
                isPinned = true,
                createdAt = 100L,
                updatedAt = 200L,
                deletedAt = 300L,
                syncedAt = 400L,
                ownerId = "user-3",
            )

        val roundTrip = mapper.toEntity(mapper.toDomain(entity, listOf("tag-b", "tag-a")))

        assertEquals(entity, roundTrip)
    }
}
