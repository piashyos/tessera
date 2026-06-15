package app.tessera.domain.usecase

import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Result<UserId> = authRepository.signIn()
}
