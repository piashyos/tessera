package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.domain.model.UserId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that [InMemorySharedBackend] behaves like the real Firestore backend across two
 * "devices": writes through one uid's adapter are visible through another's reads, and the
 * content/membership separation contracts hold.
 */
class InMemorySharedBackendTest {
    private lateinit var backend: InMemorySharedBackend

    @Before
    fun setUp() {
        backend = InMemorySharedBackend()
        backend.register("alice", "Alice", "alice@x.io", null)
        backend.register("bob", "Bob", "bob@x.io", null)
    }

    private fun sharedNote(id: String = "n1") =
        RemoteSharedNote(
            id = id,
            ownerId = "alice",
            title = "Title",
            content = "Body",
            createdAt = 1L,
            updatedAt = 1L,
            members =
                mapOf(
                    "alice" to
                        RemoteShareMember(
                            role = "OWNER",
                            displayName = "Alice",
                            email = "alice@x.io",
                            joinedAt = 1L,
                        ),
                ),
            memberIds = listOf("alice"),
        )

    @Test
    fun `addMember makes the note visible to the new member's fetchAll`() = runTest {
        backend.sharedFor("alice").shareNote("alice", sharedNote())

        assertTrue(backend.sharedFor("bob").fetchAll("bob").isEmpty())

        backend.sharedFor("alice").addMember(
            "n1",
            "bob",
            RemoteShareMember(
                role = "EDITOR",
                displayName = "Bob",
                email = "bob@x.io",
                joinedAt = 2L,
            ),
        )

        val bobNotes = backend.sharedFor("bob").fetchAll("bob")
        assertEquals(1, bobNotes.size)
        val note = bobNotes.single()
        assertEquals("EDITOR", note.members["bob"]?.role)
        assertTrue("bob" in note.memberIds)
    }

    @Test
    fun `pushSharedNote updates content but preserves membership`() = runTest {
        backend.sharedFor("alice").shareNote("alice", sharedNote())
        backend.sharedFor("alice").addMember(
            "n1",
            "bob",
            RemoteShareMember(
                role = "EDITOR",
                displayName = "Bob",
                email = "bob@x.io",
                joinedAt = 2L,
            ),
        )

        val edited =
            sharedNote().copy(
                title = "Edited",
                content = "New body",
                updatedAt = 99L,
            )
        backend.sharedFor("bob").pushSharedNote(edited)

        val note = backend.sharedFor("alice").fetchAll("alice").single()
        assertEquals("Edited", note.title)
        assertEquals("New body", note.content)
        assertEquals(99L, note.updatedAt)
        assertEquals(setOf("alice", "bob"), note.memberIds.toSet())
    }

    @Test
    fun `pushSharedNote is rejected for a viewer`() = runTest {
        backend.sharedFor("alice").shareNote("alice", sharedNote())
        backend.sharedFor("alice").addMember(
            "n1",
            "bob",
            RemoteShareMember(
                role = "VIEWER",
                displayName = "Bob",
                email = "bob@x.io",
                joinedAt = 2L,
            ),
        )

        val edited = sharedNote().copy(title = "Sneaky", content = "Sneaky edit", updatedAt = 99L)
        val error =
            try {
                backend.sharedFor("bob").pushSharedNote(edited)
                null
            } catch (e: IllegalStateException) {
                e
            }
        assertTrue("expected a permission-denied throw", error != null)

        // Content unchanged: the rejected push must not mutate the canonical doc.
        val note = backend.sharedFor("alice").fetchAll("alice").single()
        assertEquals("Title", note.title)
        assertEquals("Body", note.content)
    }

    @Test
    fun `pushSharedNote succeeds for an editor`() = runTest {
        backend.sharedFor("alice").shareNote("alice", sharedNote())
        backend.sharedFor("alice").addMember(
            "n1",
            "bob",
            RemoteShareMember(
                role = "EDITOR",
                displayName = "Bob",
                email = "bob@x.io",
                joinedAt = 2L,
            ),
        )

        val edited = sharedNote().copy(title = "Edited", content = "New body", updatedAt = 99L)
        backend.sharedFor("bob").pushSharedNote(edited)

        val note = backend.sharedFor("alice").fetchAll("alice").single()
        assertEquals("Edited", note.title)
        assertEquals("New body", note.content)
    }

    @Test
    fun `removeMember hides the note from the removed user`() = runTest {
        backend.sharedFor("alice").shareNote("alice", sharedNote())
        backend.sharedFor("alice").addMember(
            "n1",
            "bob",
            RemoteShareMember(
                role = "EDITOR",
                displayName = "Bob",
                email = "bob@x.io",
                joinedAt = 2L,
            ),
        )
        assertEquals(1, backend.sharedFor("bob").fetchAll("bob").size)

        backend.sharedFor("alice").removeMember("n1", "bob")

        assertTrue(backend.sharedFor("bob").fetchAll("bob").isEmpty())
    }

    @Test
    fun `shareNote removes the private doc and unshareNote restores it`() = runTest {
        val privateNote =
            RemoteNote(
                id = "n1",
                title = "Title",
                content = "Body",
                createdAt = 1L,
                updatedAt = 1L,
            )
        backend.privateFor("alice").upsertAll("alice", listOf(privateNote))
        assertEquals(1, backend.privateFor("alice").fetchAll("alice").size)

        backend.sharedFor("alice").shareNote("alice", sharedNote())
        assertTrue(backend.privateFor("alice").fetchAll("alice").isEmpty())
        assertEquals(1, backend.sharedFor("alice").fetchAll("alice").size)

        backend.sharedFor("alice").unshareNote("alice", privateNote)
        assertEquals(1, backend.privateFor("alice").fetchAll("alice").size)
        assertTrue(backend.sharedFor("alice").fetchAll("alice").isEmpty())
    }

    @Test
    fun `directory resolves a registered email to its uid`() = runTest {
        val resolved = backend.directory().lookupByEmail("BOB@x.io")
        assertEquals(UserId("bob"), resolved?.uid)

        val missing = backend.directory().lookupByEmail("nobody@x.io")
        assertNull(missing)
    }
}
