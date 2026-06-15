package app.tessera.domain.repository

import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserId?>

    /** Richer profile (name, email, avatar) of the signed-in user, or null when signed out. */
    val currentProfile: StateFlow<UserProfile?>

    suspend fun signIn(): Result<UserId>

    suspend fun signOut()
}
