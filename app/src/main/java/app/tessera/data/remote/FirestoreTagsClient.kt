package app.tessera.data.remote

import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreTagsClient @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteTagsDataSource {
    private fun tagsCollection(uid: String) =
        firestore.collection(USERS).document(uid).collection(TAGS)

    override suspend fun fetchAll(uid: String): List<RemoteTag> =
        tagsCollection(uid)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject<RemoteTag>() }

    override suspend fun upsertAll(uid: String, tags: List<RemoteTag>) {
        if (tags.isEmpty()) return
        val collection = tagsCollection(uid)
        val batch = firestore.batch()
        tags.forEach { tag -> batch.set(collection.document(tagDocId(tag.name)), tag) }
        batch.commit().await()
    }

    override fun observeTags(uid: String): Flow<List<RemoteTag>> =
        callbackFlow {
            val registration =
                tagsCollection(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.mapNotNull { it.toObject<RemoteTag>() }.orEmpty())
                }
            awaitClose { registration.remove() }
        }

    private companion object {
        const val USERS = "users"
        const val TAGS = "tags"
    }
}

/** A tag name may contain "/", which a Firestore document id can't — so the id is the encoded name. */
internal fun tagDocId(name: String): String =
    Base64.encodeToString(name.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
