package app.tessera.domain.usecase

import app.tessera.domain.repository.TagRepository
import javax.inject.Inject

class ObserveTagAppearancesUseCase @Inject constructor(private val repo: TagRepository) {
    operator fun invoke() = repo.observeAppearances()
}

class SetTagAppearanceUseCase @Inject constructor(private val repo: TagRepository) {
    suspend operator fun invoke(name: String, color: String?, icon: String?) =
        repo.setAppearance(name, color, icon)
}
