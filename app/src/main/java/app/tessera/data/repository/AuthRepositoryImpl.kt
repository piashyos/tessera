package app.tessera.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.tessera.R
import app.tessera.core.util.CurrentActivityHolder
import app.tessera.data.remote.emailKey
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import app.tessera.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val activityHolder: CurrentActivityHolder,
) : AuthRepository {
    private val credentialManager = CredentialManager.create(context)

    private val mutableCurrentUser = MutableStateFlow(firebaseAuth.currentUser?.uid?.let(::UserId))
    private val mutableCurrentProfile = MutableStateFlow(firebaseAuth.currentUser?.toProfile())

    override val currentUser: StateFlow<UserId?> = mutableCurrentUser.asStateFlow()

    override val currentProfile: StateFlow<UserProfile?> = mutableCurrentProfile.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            mutableCurrentUser.value = auth.currentUser?.uid?.let(::UserId)
            mutableCurrentProfile.value = auth.currentUser?.toProfile()
        }
    }

    override suspend fun signIn(): Result<UserId> {
        val activity = activityHolder.activity
            ?: return Result.failure(IllegalStateException("No active screen to start sign-in"))

        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(
                context.getString(R.string.default_web_client_id),
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val firebaseUser = authResult.user
                    ?: return Result.failure(IllegalStateException("Firebase user missing after sign-in"))
                val profile = firebaseUser.toProfile()
                mutableCurrentProfile.value = profile
                saveProfile(profile)
                Result.success(UserId(firebaseUser.uid))
            } else {
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        mutableCurrentUser.value = null
        mutableCurrentProfile.value = null
    }

    /**
     * Persists the user's public profile to `users/{uid}` and upserts the `usersByEmail/{emailKey}`
     * index that the invite flow looks up. Both writes go in one idempotent batch and failures
     * never block sign-in.
     */
    private suspend fun saveProfile(profile: UserProfile) {
        runCatching {
            val now = System.currentTimeMillis()
            val batch = firestore.batch()
            batch.set(
                firestore.collection(USERS).document(profile.id.value),
                mapOf(
                    FIELD_DISPLAY_NAME to profile.displayName,
                    FIELD_EMAIL to profile.email,
                    FIELD_PHOTO_URL to profile.photoUrl,
                    FIELD_UPDATED_AT to now,
                ),
            )
            profile.email?.takeIf { it.isNotBlank() }?.let { email ->
                batch.set(
                    firestore.collection(USERS_BY_EMAIL).document(emailKey(email)),
                    mapOf(
                        FIELD_UID to profile.id.value,
                        FIELD_EMAIL to email,
                    ),
                )
            }
            batch.commit().await()
        }.onFailure { Log.w(TAG, "Failed to save user profile", it) }
    }

    private fun FirebaseUser.toProfile(): UserProfile =
        UserProfile(
            id = UserId(uid),
            displayName = displayName,
            email = email,
            photoUrl = photoUrl?.toString(),
        )

    private companion object {
        const val TAG = "AuthRepository"
        const val USERS = "users"
        const val USERS_BY_EMAIL = "usersByEmail"
        const val FIELD_UID = "uid"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHOTO_URL = "photoUrl"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
