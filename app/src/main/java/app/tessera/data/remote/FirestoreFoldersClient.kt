package app.tessera.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreFoldersClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteFoldersDataSource {
    private fun foldersCollection(uid: String) =
        firestore.collection(USERS).document(uid).collection(FOLDERS)

    override suspend fun fetchAll(uid: String): List<RemoteFolder> =
        foldersCollection(uid)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject<RemoteFolder>() }

    override suspend fun upsertAll(uid: String, folders: List<RemoteFolder>) {
        if (folders.isEmpty()) return
        val collection = foldersCollection(uid)
        val batch = firestore.batch()
        folders.forEach { folder -> batch.set(collection.document(folder.id), folder) }
        batch.commit().await()
    }

    override fun observeFolders(uid: String): Flow<List<RemoteFolder>> =
        callbackFlow {
            val registration =
                foldersCollection(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val folders = snapshot?.documents?.mapNotNull { it.toObject<RemoteFolder>() }.orEmpty()
                    trySend(folders)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
        val collection = foldersCollection(uid)
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

    private companion object {
        const val USERS = "users"
        const val FOLDERS = "folders"
        const val FIELD_DELETED_AT = "deletedAt"
    }
}
