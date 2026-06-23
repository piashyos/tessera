package app.tessera.data.remote

import app.tessera.domain.model.UserId
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Firestore-backed [UserDirectoryDataSource] reading the `usersByEmail/{emailKey}` index. */
@Singleton
class FirestoreUserDirectory @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserDirectoryDataSource {
    override suspend fun lookupByEmail(email: String): UserId? {
        val snapshot =
            firestore.collection(USERS_BY_EMAIL)
                .document(emailKey(email))
                .get()
                .await()
        if (!snapshot.exists()) return null
        return snapshot.getString(FIELD_UID)?.takeIf { it.isNotBlank() }?.let(::UserId)
    }

    private companion object {
        const val USERS_BY_EMAIL = "usersByEmail"
        const val FIELD_UID = "uid"
    }
}
