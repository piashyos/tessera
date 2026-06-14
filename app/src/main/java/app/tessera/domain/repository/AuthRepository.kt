package app.tessera.domain.repository

import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<UserId?>

    suspend fun signIn(): Result<UserId>

    suspend fun signOut()
}
