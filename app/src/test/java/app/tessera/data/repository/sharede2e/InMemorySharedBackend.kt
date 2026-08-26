package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.DirectoryUser
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.remote.UserDirectoryDataSource
import app.tessera.data.remote.emailKey
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.canDeleteForAll
import app.tessera.domain.model.UserId
import app.tessera.domain.model.canEditContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for a fake "cloud" shared between two test devices. It holds the canonical
 * `sharedNotes` collection, per-owner private note subtrees, and a `usersByEmail` directory, then
 * hands out per-uid adapters ([sharedFor]/[privateFor]) that all read and write the SAME state — so
 * one device's mutation is visible to another's read, exactly as Firestore would behave.
 *
 * Behaviour mirrors [app.tessera.data.remote.FirestoreSharedNotesClient]: membership operations
 * mutate `members`/`memberIds` honestly (unlike the no-op fakes used by SyncRepositoryImplTest),
 * and [RemoteSharedNotesDataSource.pushSharedNote] touches only content fields. Like the real
 * Firestore security rules, a content push is rejected (throws) when the calling uid is not a
 * member of the note or holds a role that may not edit content (VIEWER).
 */
class InMemorySharedBackend {
    /** Canonical `sharedNotes/{id}` documents, keyed by note id. */
    private val sharedNotes = linkedMapOf<String, RemoteSharedNote>()

    /** Per-owner private subtree: ownerUid -> (noteId -> RemoteNote). */
    private val privateNotes = linkedMapOf<String, LinkedHashMap<String, RemoteNote>>()

    /** `usersByEmail/{emailKey}` index. */
    private val usersByEmail = linkedMapOf<String, DirectoryUser>()

    /** Re-emits the full shared collection on every change, for observe* adapters. */
    private val sharedFlow = MutableStateFlow<List<RemoteSharedNote>>(emptyList())

    private fun emitShared() {
        sharedFlow.value = sharedNotes.values.toList()
    }

    private fun privateBucket(uid: String): LinkedHashMap<String, RemoteNote> =
        privateNotes.getOrPut(uid) { linkedMapOf() }

    /** Registers a user in the directory under the normalized email key. */
    fun register(uid: String, displayName: String?, email: String, photoUrl: String?) {
        usersByEmail[emailKey(email)] =
            DirectoryUser(
                uid = UserId(uid),
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
            )
    }

    /** Email→user lookup using [emailKey] normalization, matching the real directory. */
    fun directory(): UserDirectoryDataSource =
        object : UserDirectoryDataSource {
            override suspend fun lookupByEmail(email: String): DirectoryUser? =
                usersByEmail[emailKey(email)]
        }

    /** A [RemoteSharedNotesDataSource] view over the canonical shared state for [uid]. */
    fun sharedFor(uid: String): RemoteSharedNotesDataSource = SharedAdapter(uid)

    /** A [RemoteNotesDataSource] view over [uid]'s private subtree. */
    fun privateFor(uid: String): RemoteNotesDataSource = PrivateAdapter(uid)

    private inner class SharedAdapter(private val uid: String) :
        RemoteSharedNotesDataSource {
        override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) {
            sharedNotes[note.id] = note
            privateBucket(ownerUid).remove(note.id)
            emitShared()
        }

        override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) {
            sharedNotes.remove(privateNote.id)
            privateBucket(ownerUid)[privateNote.id] = privateNote
            emitShared()
        }

        override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) {
            val note = sharedNotes[noteId] ?: return
            sharedNotes[noteId] =
                note.copy(
                    members = note.members + (uid to member),
                    memberIds = (note.memberIds + uid).distinct(),
                )
            emitShared()
        }

        override suspend fun updateRole(noteId: String, uid: String, role: String) {
            val note = sharedNotes[noteId] ?: return
            val existing = note.members[uid] ?: return
            sharedNotes[noteId] =
                note.copy(members = note.members + (uid to existing.copy(role = role)))
            emitShared()
        }

        override suspend fun removeMember(noteId: String, uid: String) {
            val note = sharedNotes[noteId] ?: return
            sharedNotes[noteId] =
                note.copy(
                    members = note.members - uid,
                    memberIds = note.memberIds - uid,
                )
            emitShared()
        }

        override suspend fun leaveNote(noteId: String, myUid: String) = removeMember(noteId, myUid)

        override suspend fun pushSharedNote(note: RemoteSharedNote) {
            // Content fields only; membership/ownerId preserved (mirrors the real client).
            val existing = sharedNotes[note.id] ?: return
            // Faithful to Firestore security rules: a content write is only allowed for a member
            // whose role may edit content (OWNER/MANAGER/EDITOR). A VIEWER or non-member is rejected.
            val callerRole = ShareRole.fromWire(existing.members[uid]?.role)
            check(existing.members.containsKey(uid) && callerRole.canEditContent) {
                "permission denied: $uid may not edit ${note.id}"
            }
            // A tombstone (deletedAt) change is a delete-for-all — owner/manager only (mirrors rules).
            val touchesDeletedAt = note.deletedAt != existing.deletedAt
            check(!touchesDeletedAt || callerRole.canDeleteForAll) {
                "permission denied: $uid may not delete ${note.id} for all"
            }
            sharedNotes[note.id] =
                existing.copy(
                    title = note.title,
                    content = note.content,
                    tags = note.tags,
                    pinned = note.pinned,
                    archived = note.archived,
                    updatedAt = note.updatedAt,
                    deletedAt = note.deletedAt,
                )
            emitShared()
        }

        override suspend fun fetchAll(uid: String): List<RemoteSharedNote> =
            sharedNotes.values.filter { uid in it.memberIds }

        override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> =
            sharedFlow.map { notes -> notes.filter { uid in it.memberIds } }
    }

    private inner class PrivateAdapter(@Suppress("unused") private val owner: String) :
        RemoteNotesDataSource {
        override suspend fun fetchAll(uid: String): List<RemoteNote> =
            privateBucket(uid).values.toList()

        override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) {
            val bucket = privateBucket(uid)
            notes.forEach { bucket[it.id] = it }
        }

        // Not reactive: private-subtree observation is unused by the E2E scenarios (they drive sync via syncNow/fetchAll).
        override fun observeNotes(uid: String): Flow<List<RemoteNote>> =
            MutableStateFlow(privateBucket(uid).values.toList())

        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
            val bucket = privateBucket(uid)
            val toRemove =
                bucket.values.filter { it.deletedAt != null && it.deletedAt < threshold }.map { it.id }
            toRemove.forEach { bucket.remove(it) }
            return toRemove.size
        }

        override suspend fun deleteByIds(uid: String, ids: List<String>) {
            val bucket = privateBucket(uid)
            ids.forEach { bucket.remove(it) }
        }
    }
}
