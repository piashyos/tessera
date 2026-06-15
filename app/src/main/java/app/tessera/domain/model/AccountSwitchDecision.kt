package app.tessera.domain.model

sealed interface AccountSwitchDecision {
    /** No prior account owns local notes; the new account can adopt them silently. */
    data object None : AccountSwitchDecision

    /** The signed-in account already owns the local notes; nothing to resolve. */
    data object SameAccount : AccountSwitchDecision

    /** Local notes belong to a different account; the user must choose how to resolve it. */
    data class DifferentAccount(val existingUid: UserId) : AccountSwitchDecision
}
