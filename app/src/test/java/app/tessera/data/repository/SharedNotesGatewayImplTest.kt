package app.tessera.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.SharedMemberEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.mapper.SharedNoteMapper
import app.tessera.data.remote.DirectoryUser
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.remote.UserDirectoryDataSource
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedNotesGatewayImplTest {
    private lateinit var database: NotesDatabase
    private lateinit var remote: FakeRemoteShared
    private lateinit var directory: FakeUserDirectory
    private lateinit var auth: FakeAuth
    private lateinit var gateway: SharedNotesGatewayImpl

    private val noteId = NoteId("note-1")

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                NotesDatabase::class.java,
            ).allowMainThreadQueries().build()
        remote = FakeRemoteShared()
        directory = FakeUserDirectory()
        auth = FakeAuth(uid = "me", profile = UserProfile(UserId("me"), "Me", "me@x.com", null))
        gateway =
            SharedNotesGatewayImpl(
                noteDao = database.noteDao(),
                tagDao = database.tagDao(),
                sharedMemberDao = database.sharedMemberDao(),
                remoteShared = remote,
                userDirectory = directory,
                authRepository = auth,
                noteMapper = NoteMapper(),
                sharedMapper = SharedNoteMapper(),
                clock = FixedClock(),
            )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `shareNote moves a private note to shared and seeds the owner member`() =
        runTest {
            database.noteDao().upsert(privateEntity(owner = "me"))

            val result = gateway.shareNote(noteId)

            assertTrue(result.isSuccess)
            val stored = database.noteDao().getById(noteId.value)!!
            assertEquals(NoteEntity.SHARE_STATE_SHARED, stored.shareState)
            assertEquals(ShareRole.OWNER.name, stored.myRole)
            val members = database.sharedMemberDao().getForNote(noteId.value)
            assertEquals(listOf("me"), members.map { it.uid })
            assertEquals(ShareRole.OWNER.name, members.single().role)
            assertEquals("me", remote.sharedDocument?.ownerId)
        }

    @Test
    fun `inviteByEmail returns UserNotFound and adds nobody when the email is unknown`() =
        runTest {
            seedShared(myRole = ShareRole.OWNER)
            directory.result = null

            val result = gateway.inviteByEmail(noteId, "ghost@x.com", ShareRole.EDITOR)

            assertEquals(InviteResult.UserNotFound, result.getOrNull())
            assertEquals(listOf("me"), database.sharedMemberDao().getForNote(noteId.value).map { it.uid })
            assertNull(remote.addedUid)
        }

    @Test
    fun `inviteByEmail returns AlreadyMember when the user is present`() =
        runTest {
            seedShared(myRole = ShareRole.OWNER)
            directory.result = DirectoryUser(UserId("me"), "Me", "me@x.com", null)

            val result = gateway.inviteByEmail(noteId, "me@x.com", ShareRole.EDITOR)

            assertEquals(InviteResult.AlreadyMember, result.getOrNull())
            assertNull(remote.addedUid)
        }

    @Test
    fun `inviteByEmail adds the resolved user locally and remotely`() =
        runTest {
            seedShared(myRole = ShareRole.OWNER)
            directory.result = DirectoryUser(UserId("bob"), "Bob", "bob@x.com", "photo")

            val result = gateway.inviteByEmail(noteId, "bob@x.com", ShareRole.EDITOR)

            assertEquals(InviteResult.Added, result.getOrNull())
            assertEquals("bob", remote.addedUid)
            val bob = database.sharedMemberDao().getForNote(noteId.value).first { it.uid == "bob" }
            assertEquals(ShareRole.EDITOR.name, bob.role)
            assertEquals("Bob", bob.displayName)
        }

    @Test
    fun `leaveSharedNote removes the note from this device`() =
        runTest {
            seedShared(myRole = ShareRole.EDITOR)

            val result = gateway.leaveSharedNote(noteId)

            assertTrue(result.isSuccess)
            assertNull(database.noteDao().getById(noteId.value))
            assertEquals("me", remote.leftUid)
        }

    @Test
    fun `unshareNote flips the note back to private and clears members`() =
        runTest {
            seedShared(myRole = ShareRole.OWNER, owner = "me")

            val result = gateway.unshareNote(noteId)

            assertTrue(result.isSuccess)
            val stored = database.noteDao().getById(noteId.value)!!
            assertEquals(NoteEntity.SHARE_STATE_PRIVATE, stored.shareState)
            assertNull(stored.myRole)
            assertTrue(database.sharedMemberDao().getForNote(noteId.value).isEmpty())
            assertTrue(remote.unshared)
        }

    private suspend fun seedShared(myRole: ShareRole, owner: String = "owner") {
        database.noteDao().upsert(
            privateEntity(owner = owner).copy(
                shareState = NoteEntity.SHARE_STATE_SHARED,
                myRole = myRole.name,
            ),
        )
        database.sharedMemberDao().upsertAll(
            listOf(
                SharedMemberEntity(noteId.value, "me", "Me", "me@x.com", null, myRole.name, 1L),
            ),
        )
    }

    private fun privateEntity(owner: String) =
        NoteEntity(
            id = noteId.value,
            title = "T",
            content = "B",
            isPinned = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncedAt = 1L,
            ownerId = owner,
        )
}

private class FixedClock : Clock {
    override fun nowMs(): Long = 42L
}

private class FakeUserDirectory : UserDirectoryDataSource {
    var result: DirectoryUser? = null

    override suspend fun lookupByEmail(email: String): DirectoryUser? = result
}

private class FakeAuth(uid: String, profile: UserProfile) : AuthRepository {
    override val currentUser: StateFlow<UserId?> = MutableStateFlow(UserId(uid))
    override val currentProfile: StateFlow<UserProfile?> = MutableStateFlow(profile)

    override suspend fun signIn(): Result<UserId> = Result.success(currentUser.value!!)

    override suspend fun signOut() {}
}

private class FakeRemoteShared : RemoteSharedNotesDataSource {
    var sharedDocument: RemoteSharedNote? = null
    var addedUid: String? = null
    var leftUid: String? = null
    var unshared = false

    override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) {
        sharedDocument = note
    }

    override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) {
        unshared = true
    }

    override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) {
        addedUid = uid
    }

    override suspend fun updateRole(noteId: String, uid: String, role: String) {}

    override suspend fun removeMember(noteId: String, uid: String) {}

    override suspend fun leaveNote(noteId: String, myUid: String) {
        leftUid = myUid
    }

    override suspend fun pushSharedNote(note: RemoteSharedNote) {}

    override suspend fun fetchAll(uid: String): List<RemoteSharedNote> = emptyList()

    override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> = emptyFlow()
}
