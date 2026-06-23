package app.tessera.data.mapper

import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.remote.RemoteNote
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.ShareState
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `toDomain maps a private entity to Private share state`() {
        val entity =
            NoteEntity(
                id = "p1",
                title = "T",
                content = "B",
                isPinned = false,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
                syncedAt = null,
                ownerId = "u1",
            )

        val note = mapper.toDomain(entity, emptyList())

        assertEquals(ShareState.Private, note.shareState)
    }

    @Test
    fun `toDomain builds Shared share state with members and current role`() {
        val entity =
            NoteEntity(
                id = "s1",
                title = "T",
                content = "B",
                isPinned = false,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
                syncedAt = null,
                ownerId = "owner",
                shareState = NoteEntity.SHARE_STATE_SHARED,
                myRole = "EDITOR",
            )
        val members =
            listOf(
                ShareMember(UserId("owner"), "Owner", "o@x.com", null, ShareRole.OWNER, 1L),
                ShareMember(UserId("me"), "Me", "m@x.com", null, ShareRole.EDITOR, 2L),
            )

        val note = mapper.toDomain(entity, emptyList(), members)

        val shared = note.shareState as ShareState.Shared
        assertEquals(ShareRole.EDITOR, shared.myRole)
        assertEquals(members, shared.members)
    }

    @Test
    fun `toEntity round-trips Shared share state into shareState and myRole columns`() {
        val entity =
            NoteEntity(
                id = "s2",
                title = "T",
                content = "B",
                isPinned = false,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
                syncedAt = null,
                ownerId = "owner",
                shareState = NoteEntity.SHARE_STATE_SHARED,
                myRole = "MANAGER",
            )

        val rebuilt = mapper.toEntity(mapper.toDomain(entity, emptyList(), emptyList()))

        assertEquals(NoteEntity.SHARE_STATE_SHARED, rebuilt.shareState)
        assertEquals("MANAGER", rebuilt.myRole)
        assertTrue(rebuilt.copy(shareState = entity.shareState, myRole = entity.myRole) == entity)
    }

    @Test
    fun `toRemote copies note fields and normalizes tag names without exposing local-only state`() {
        val entity =
            NoteEntity(
                id = "note-4",
                title = "Remote",
                content = "Body",
                isPinned = true,
                createdAt = 1L,
                updatedAt = 2L,
                deletedAt = 3L,
                syncedAt = 99L,
                ownerId = "user-4",
            )

        val remote = mapper.toRemote(entity, listOf(" beta ", "alpha", "alpha", "  "))

        assertEquals("note-4", remote.id)
        assertEquals("Remote", remote.title)
        assertEquals("Body", remote.content)
        assertEquals(listOf("alpha", "beta"), remote.tags)
        assertEquals(true, remote.isPinned)
        assertEquals(1L, remote.createdAt)
        assertEquals(2L, remote.updatedAt)
        assertEquals(3L, remote.deletedAt)
    }

    @Test
    fun `toEntity from remote applies owner and synced flag and preserves remote fields`() {
        val remote =
            RemoteNote(
                id = "note-5",
                title = "Pulled",
                content = "Cloud body",
                tags = listOf("a", "b"),
                isPinned = false,
                createdAt = 5L,
                updatedAt = 6L,
                deletedAt = 7L,
            )

        val entity = mapper.toEntity(remote, ownerId = "user-5", syncedAt = 50L)

        assertEquals(
            NoteEntity(
                id = "note-5",
                title = "Pulled",
                content = "Cloud body",
                isPinned = false,
                createdAt = 5L,
                updatedAt = 6L,
                deletedAt = 7L,
                syncedAt = 50L,
                ownerId = "user-5",
            ),
            entity,
        )
    }

    @Test
    fun `entity to remote to entity round trip preserves syncable fields`() {
        val entity =
            NoteEntity(
                id = "note-6",
                title = "Round",
                content = "Trip",
                isPinned = true,
                createdAt = 100L,
                updatedAt = 200L,
                deletedAt = 300L,
                syncedAt = 400L,
                ownerId = "user-6",
                isArchived = true,
            )

        val remote = mapper.toRemote(entity, emptyList())
        val rebuilt = mapper.toEntity(remote, ownerId = "user-6", syncedAt = null)

        assertEquals(entity.copy(syncedAt = null), rebuilt)
        assertNull(rebuilt.syncedAt)
        assertEquals(true, rebuilt.isArchived)
    }
}
