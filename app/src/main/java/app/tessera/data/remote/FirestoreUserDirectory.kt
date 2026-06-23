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
    override suspend fun lookupByEmail(email: String): DirectoryUser? {
        val indexSnapshot =
            firestore.collection(USERS_BY_EMAIL)
                .document(emailKey(email))
                .get()
                .await()
        if (!indexSnapshot.exists()) return null
        val uid = indexSnapshot.getString(FIELD_UID)?.takeIf { it.isNotBlank() } ?: return null

        // Pull the public profile (`users/{uid}` is readable by any signed-in user) so the new
        // member shows their real name/avatar immediately.
        val profile = firestore.collection(USERS).document(uid).get().await()
        return DirectoryUser(
            uid = UserId(uid),
            displayName = profile.getString(FIELD_DISPLAY_NAME),
            email = profile.getString(FIELD_EMAIL) ?: emailKey(email),
            photoUrl = profile.getString(FIELD_PHOTO_URL),
        )
    }

    private companion object {
        const val USERS_BY_EMAIL = "usersByEmail"
        const val USERS = "users"
        const val FIELD_UID = "uid"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHOTO_URL = "photoUrl"
    }
}
