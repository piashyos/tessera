package app.tessera.data.repository.sharede2e

import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two-user E2E scenarios driving the REAL [app.tessera.data.repository.SharedNotesGatewayImpl] and
 * [app.tessera.data.repository.SyncRepositoryImpl] across two devices (alice, bob) over one shared
 * in-memory backend. Each test walks one step of the share lifecycle: invite -> appears in Shared
 * -> edit (document-level sync) -> role change -> leave/remove/unshare eviction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoUserSharedNotesE2ETest {
    private val backend = InMemorySharedBackend()
    private val devices = mutableListOf<SharedNotesTestDevice>()

    private fun device(uid: String): SharedNotesTestDevice =
        SharedNotesTestDevice(
            context = ApplicationProvider.getApplicationContext(),
            backend = backend,
            uid = uid,
            displayName = uid.replaceFirstChar { it.uppercase() },
            email = "$uid@x.io",
        ).also { devices += it }

    @After fun tearDown() = devices.forEach { it.close() }

    /** Upsert a private, already-synced note owned by this device's user. */
    private suspend fun SharedNotesTestDevice.seedPrivate(id: String, title: String = "T", body: String = "B") {
        noteDao.upsert(NoteEntity(id, title, body, false, 1L, 1L, null, 1L, ownerId = uid))
    }

    @Test
    fun `invite then sync surfaces the shared note for the invitee with EDITOR role`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")

        alice.gateway.shareNote(NoteId("n1"))
        val invite = alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        assertEquals(InviteResult.Added, invite.getOrNull())

        bob.syncNow()

        val bobNote = bob.noteDao.getById("n1")
        assertNotNull(bobNote)
        assertEquals(NoteEntity.SHARE_STATE_SHARED, bobNote?.shareState)
        assertEquals(ShareRole.EDITOR.name, bobNote?.myRole)
        assertTrue(
            bob.sharedMemberDao.getForNote("n1").map { it.uid }.containsAll(listOf("alice", "bob")),
        )
    }

    @Test
    fun `editor edit propagates to owner on next sync (last-write-wins)`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "original")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow() // Bob pulls the shared note

        // Bob edits content locally and marks it pending (as a note editor would).
        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "edited-by-bob", updatedAt = 10_000L, syncedAt = null))
        bob.syncNow() // push Bob's edit to the shared doc

        alice.syncNow() // owner pulls

        assertEquals("edited-by-bob", alice.noteDao.getById("n1")?.content)
    }

    @Test
    fun `downgrade to viewer rolls back the invitee's unsynced edit`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "owner-body")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        // Alice downgrades Bob to VIEWER.
        alice.gateway.changeRole(NoteId("n1"), UserId("bob"), ShareRole.VIEWER)

        // Bob made a local unsynced edit before the next sync.
        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "sneaky-edit", updatedAt = 20_000L, syncedAt = null))

        bob.syncNow() // merge sees VIEWER role -> rolls back local edit, skips content push

        val bobNote = bob.noteDao.getById("n1")!!
        assertEquals(ShareRole.VIEWER.name, bobNote.myRole)
        assertEquals("owner-body", bobNote.content) // edit rolled back to server content
    }

    @Test
    fun `a viewer's local edit never reaches the shared document`() = runTest {
        // Bob is invited directly as VIEWER, so this exercises the LOCAL `canEditContent`
        // fast-path in pushPendingShared: the role guard short-circuits before the backend
        // is ever hit. Server-side rejection of a stale-role push is covered separately by
        // InMemorySharedBackendTest (pushSharedNote permission check) and by the
        // `downgrade to viewer rolls back the invitee's unsynced edit` scenario above.
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "owner-body")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.VIEWER)
        bob.syncNow()

        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "viewer-edit", updatedAt = 30_000L, syncedAt = null))
        bob.syncNow()
        alice.syncNow()

        assertEquals("owner-body", alice.noteDao.getById("n1")?.content)
    }

    @Test
    fun `removing a member evicts the note from their device on sync`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()
        assertNotNull(bob.noteDao.getById("n1"))

        alice.gateway.removeMember(NoteId("n1"), UserId("bob"))
        bob.syncNow()

        assertNull(bob.noteDao.getById("n1")) // non-owner removed from member set -> evicted
        assertNotNull(alice.noteDao.getById("n1")) // owner keeps it
    }

    @Test
    fun `leaving a shared note removes it locally and drops membership remotely`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        bob.gateway.leaveSharedNote(NoteId("n1"))

        assertNull(bob.noteDao.getById("n1"))
        assertTrue(
            backend.sharedFor("alice").fetchAll("alice").single().memberIds.none { it == "bob" },
        )
    }

    @Test
    fun `owner unshare returns the note to private and clears membership`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        alice.gateway.unshareNote(NoteId("n1"))
        bob.syncNow()

        val aliceNote = alice.noteDao.getById("n1")!!
        assertEquals(NoteEntity.SHARE_STATE_PRIVATE, aliceNote.shareState)
        assertNull(aliceNote.myRole)
        assertNull(bob.noteDao.getById("n1")) // gone from the (former) member's device
    }

    @Test
    fun `delete for all removes the note from every member`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()
        assertNotNull(bob.noteDao.getById("n1"))

        // "Удалить для всех": the editor screen's delete action routes through
        // DeleteNoteUseCase -> NotesRepositoryImpl.deleteNote -> NoteDao.softDelete for a
        // SHARED note (NotesRepositoryImpl isn't wired into this device harness, so we drive
        // the same softDelete directly). The note stays shareState=SHARED with deletedAt set,
        // so the next sync pushes the tombstone into the shared doc as a content edit.
        alice.noteDao.softDelete("n1", ts = 50_000L)
        alice.syncNow() // owner pushes the deletedAt tombstone to the shared document
        bob.syncNow() // every member picks the tombstone up via mergeRemoteShared

        // Designed behavior: delete-for-all is a soft-delete tombstone, not a hard removal.
        // The note is gone from each member's active list (deletedAt != null) on BOTH devices.
        val aliceNote = alice.noteDao.getById("n1")
        assertNotNull(aliceNote)
        assertNotNull(aliceNote?.deletedAt)
        val bobNote = bob.noteDao.getById("n1")
        assertNotNull(bobNote)
        assertNotNull(bobNote?.deletedAt)
    }
}
