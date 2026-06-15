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
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
    private val activityHolder: CurrentActivityHolder,
) : AuthRepository {
    private val credentialManager = CredentialManager.create(context)

    private val mutableCurrentUser = MutableStateFlow(firebaseAuth.currentUser?.uid?.let(::UserId))

    override val currentUser: StateFlow<UserId?> = mutableCurrentUser.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            mutableCurrentUser.value = auth.currentUser?.uid?.let(::UserId)
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
                val uid = authResult.user?.uid
                    ?: return Result.failure(IllegalStateException("Firebase user missing after sign-in"))
                Result.success(UserId(uid))
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
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
