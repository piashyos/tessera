package app.tessera.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed [RemoteNotesDataSource]. Contains only raw CRUD + a snapshot listener;
 * all last-write-wins / merge logic lives in the sync repository.
 */
@Singleton
class FirestoreNotesClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteNotesDataSource {
    private fun notesCollection(uid: String) =
        firestore.collection(USERS).document(uid).collection(NOTES)

    override suspend fun fetchAll(uid: String): List<RemoteNote> =
        notesCollection(uid)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject<RemoteNote>() }

    override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) {
        if (notes.isEmpty()) return
        val collection = notesCollection(uid)
        val batch = firestore.batch()
        notes.forEach { note -> batch.set(collection.document(note.id), note) }
        batch.commit().await()
    }

    override fun observeNotes(uid: String): Flow<List<RemoteNote>> =
        callbackFlow {
            val registration =
                notesCollection(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val notes = snapshot?.documents?.mapNotNull { it.toObject<RemoteNote>() }.orEmpty()
                    trySend(notes)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
        val collection = notesCollection(uid)
        val stale =
            collection
                .whereLessThan(FIELD_DELETED_AT, threshold)
                .get()
                .await()
                .documents
        if (stale.isEmpty()) return 0
        val batch = firestore.batch()
        stale.forEach { batch.delete(it.reference) }
        batch.commit().await()
        return stale.size
    }

    override suspend fun deleteByIds(uid: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val collection = notesCollection(uid)
        val batch = firestore.batch()
        ids.forEach { id -> batch.delete(collection.document(id)) }
        batch.commit().await()
    }

    private companion object {
        const val USERS = "users"
        const val NOTES = "notes"
        const val FIELD_DELETED_AT = "deletedAt"
    }
}
