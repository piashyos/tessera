package app.tessera.data.repository.sharede2e

import android.content.Context
import androidx.room.Room
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.RoomTransactor
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.mapper.SharedNoteMapper
import app.tessera.data.repository.SharedNotesGatewayImpl
import app.tessera.data.repository.SyncRepositoryImpl
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One simulated device: its own Robolectric in-memory Room DB plus the real
 * [SharedNotesGatewayImpl] (user actions) and [SyncRepositoryImpl] (background sync), wired to the
 * shared [backend]. Registers the user in the backend directory so other devices can invite them
 * by email. Two devices sharing one backend let tests drive true two-user flows.
 */
class SharedNotesTestDevice(
    context: Context,
    backend: InMemorySharedBackend,
    val uid: String,
    displayName: String,
    email: String,
    private val clock: Clock = MonotonicClock(),
) {
    private val db: NotesDatabase =
        Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    val noteDao = db.noteDao()
    val tagDao = db.tagDao()
    val sharedMemberDao = db.sharedMemberDao()

    private val auth: AuthRepository =
        FixedAuth(uid, UserProfile(UserId(uid), displayName, email, null))

    val gateway = SharedNotesGatewayImpl(
        noteDao = noteDao,
        tagDao = tagDao,
        sharedMemberDao = sharedMemberDao,
        remoteShared = backend.sharedFor(uid),
        userDirectory = backend.directory(),
        authRepository = auth,
        noteMapper = NoteMapper(),
        sharedMapper = SharedNoteMapper(),
        clock = clock,
    )

    val sync = SyncRepositoryImpl(
        RoomTransactor(db),
        noteDao,
        tagDao,
        db.folderDao(),
        sharedMemberDao,
        db.noteImageDao(),
        backend.privateFor(uid),
        EmptyRemoteFolders(),
        backend.sharedFor(uid),
        NoteMapper(),
        SharedNoteMapper(),
        app.tessera.data.mapper.NoteImageMapper(),
        clock,
    )

    /** Pull + merge + push, as the WorkManager job would. */
    suspend fun syncNow() = sync.syncNow(UserId(uid))

    fun close() = db.close()

    init {
        backend.register(uid, displayName, email, null)
    }
}

/**
 * Strictly increasing clock. Critical for last-write-wins: an edit made after a sync must carry a
 * larger `updatedAt` than the synced value, otherwise the merge would discard it.
 */
private class MonotonicClock : Clock {
    private var t = 1_000L
    override fun nowMs(): Long = ++t
}

private class FixedAuth(uid: String, profile: UserProfile) : AuthRepository {
    override val currentUser: StateFlow<UserId?> = MutableStateFlow(UserId(uid))
    override val currentProfile: StateFlow<UserProfile?> = MutableStateFlow(profile)
    override suspend fun signIn(): Result<UserId> = Result.success(currentUser.value!!)
    override suspend fun signOut() {}
}
