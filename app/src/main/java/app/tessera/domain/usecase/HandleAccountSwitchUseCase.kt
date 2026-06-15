package app.tessera.domain.usecase

import app.tessera.domain.model.AccountSwitchDecision
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * Decides how a just-signed-in account ([newUid]) relates to the account that currently
 * owns the local notes. The caller acts on the decision (adopt / no-op / show dialog).
 */
class HandleAccountSwitchUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(newUid: UserId): AccountSwitchDecision =
        when (val existing = accountRepository.currentOwnerId()) {
            null -> AccountSwitchDecision.None
            newUid -> AccountSwitchDecision.SameAccount
            else -> AccountSwitchDecision.DifferentAccount(existing)
        }
}
