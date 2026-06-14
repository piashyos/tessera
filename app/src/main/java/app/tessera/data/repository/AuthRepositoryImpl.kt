package app.tessera.data.repository

import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthRepositoryImpl @Inject constructor() : AuthRepository {
    private val mutableCurrentUser = MutableStateFlow<UserId?>(null)

    override val currentUser: StateFlow<UserId?> = mutableCurrentUser.asStateFlow()

    override suspend fun signIn(): Result<UserId> =
        Result.failure(UnsupportedOperationException("Auth is implemented in Chunk 7"))

    override suspend fun signOut() {
        mutableCurrentUser.value = null
    }
}
