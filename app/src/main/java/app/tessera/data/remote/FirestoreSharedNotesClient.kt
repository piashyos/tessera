package app.tessera.data.remote

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed [RemoteSharedNotesDataSource]. Raw CRUD + membership mutations + a snapshot
 * listener only; all routing / last-write-wins / role logic lives in the sync repository.
 */
@Singleton
class FirestoreSharedNotesClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteSharedNotesDataSource {
    private val sharedNotes get() = firestore.collection(SHARED_NOTES)

    private fun privateDoc(ownerUid: String, noteId: String) =
        firestore.collection(USERS).document(ownerUid).collection(NOTES).document(noteId)

    override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) {
        firestore.batch().apply {
            set(sharedNotes.document(note.id), note)
            delete(privateDoc(ownerUid, note.id))
        }.commit().await()
    }

    override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) {
        firestore.batch().apply {
            set(privateDoc(ownerUid, privateNote.id), privateNote)
            delete(sharedNotes.document(privateNote.id))
        }.commit().await()
    }

    override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) {
        sharedNotes.document(noteId)
            .update(
                mapOf(
                    "$FIELD_MEMBERS.$uid" to member,
                    FIELD_MEMBER_IDS to FieldValue.arrayUnion(uid),
                ),
            )
            .await()
    }

    override suspend fun updateRole(noteId: String, uid: String, role: String) {
        sharedNotes.document(noteId)
            .update("$FIELD_MEMBERS.$uid.$FIELD_ROLE", role)
            .await()
    }

    override suspend fun removeMember(noteId: String, uid: String) {
        sharedNotes.document(noteId)
            .update(
                mapOf(
                    "$FIELD_MEMBERS.$uid" to FieldValue.delete(),
                    FIELD_MEMBER_IDS to FieldValue.arrayRemove(uid),
                ),
            )
            .await()
    }

    override suspend fun leaveNote(noteId: String, myUid: String) = removeMember(noteId, myUid)

    override suspend fun pushSharedNote(note: RemoteSharedNote) {
        // Content fields only — membership is mutated through the dedicated operations and must
        // never be overwritten by an editing device's stale local mirror.
        sharedNotes.document(note.id)
            .update(
                mapOf(
                    FIELD_TITLE to note.title,
                    FIELD_CONTENT to note.content,
                    FIELD_TAGS to note.tags,
                    FIELD_IS_PINNED to note.pinned,
                    FIELD_IS_ARCHIVED to note.archived,
                    FIELD_UPDATED_AT to note.updatedAt,
                    FIELD_DELETED_AT to note.deletedAt,
                ),
            )
            .await()
    }

    override suspend fun fetchAll(uid: String): List<RemoteSharedNote> =
        sharedNotes
            .whereArrayContains(FIELD_MEMBER_IDS, uid)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject<RemoteSharedNote>() }

    override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> =
        callbackFlow {
            val registration =
                sharedNotes
                    .whereArrayContains(FIELD_MEMBER_IDS, uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        val notes = snapshot?.documents?.mapNotNull { it.toObject<RemoteSharedNote>() }.orEmpty()
                        trySend(notes)
                    }
            awaitClose { registration.remove() }
        }

    override suspend fun updateImageManifest(noteId: String, imageId: String, ref: RemoteImageRef?) {
        // FieldPath.of(...) treats "images"/imageId as literal segments, so an imageId containing a
        // dot can't be misread as a nested path. A null ref removes just this one map key.
        val value: Any = ref ?: FieldValue.delete()
        sharedNotes.document(noteId)
            .update(FieldPath.of(FIELD_IMAGES, imageId), value)
            .await()
    }

    private companion object {
        const val SHARED_NOTES = "sharedNotes"
        const val FIELD_IMAGES = "images"
        const val USERS = "users"
        const val NOTES = "notes"
        const val FIELD_MEMBERS = "members"
        const val FIELD_MEMBER_IDS = "memberIds"
        const val FIELD_ROLE = "role"
        const val FIELD_TITLE = "title"
        const val FIELD_CONTENT = "content"
        const val FIELD_TAGS = "tags"
        // Wire names deliberately match the reflective (getter-derived) keys used by
        // shareNote/private notes — "pinned"/"archived", NOT "isPinned" (the JavaBean is-prefix
        // pitfall that silently broke archive sync between devices).
        const val FIELD_IS_PINNED = "pinned"
        const val FIELD_IS_ARCHIVED = "archived"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DELETED_AT = "deletedAt"
    }
}
