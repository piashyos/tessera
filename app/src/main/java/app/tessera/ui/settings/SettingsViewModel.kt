package app.tessera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.AccountSwitchDecision
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.usecase.HandleAccountSwitchUseCase
import app.tessera.domain.usecase.SignInUseCase
import app.tessera.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val handleAccountSwitchUseCase: HandleAccountSwitchUseCase,
) : ViewModel() {
    private val uiState = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> =
        combine(authRepository.currentUser, uiState) { user, ui ->
            ui.copy(
                isSignedIn = user != null,
                userId = user?.value,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsState(),
        )

    fun onSignIn() {
        if (uiState.value.isBusy) return
        uiState.value = uiState.value.copy(isBusy = true, errorMessage = null)
        viewModelScope.launch {
            signInUseCase()
                .onSuccess { newUid -> resolveAccountSwitch(newUid) }
                .onFailure { error ->
                    uiState.value = uiState.value.copy(
                        isBusy = false,
                        errorMessage = error.message ?: "Sign-in failed",
                    )
                }
        }
    }

    private suspend fun resolveAccountSwitch(newUid: UserId) {
        when (val decision = handleAccountSwitchUseCase(newUid)) {
            AccountSwitchDecision.None -> {
                accountRepository.adoptLocalNotes(newUid)
                uiState.value = uiState.value.copy(isBusy = false)
            }

            AccountSwitchDecision.SameAccount ->
                uiState.value = uiState.value.copy(isBusy = false)

            is AccountSwitchDecision.DifferentAccount ->
                uiState.value = uiState.value.copy(
                    isBusy = false,
                    pendingSwitch = PendingAccountSwitch(
                        existingUid = decision.existingUid,
                        newUid = newUid,
                    ),
                )
        }
    }

    fun onConfirmMove() {
        val pending = uiState.value.pendingSwitch ?: return
        uiState.value = uiState.value.copy(pendingSwitch = null)
        viewModelScope.launch {
            accountRepository.moveAllNotesTo(pending.newUid)
        }
    }

    fun onDeleteLocal() {
        val pending = uiState.value.pendingSwitch ?: return
        uiState.value = uiState.value.copy(pendingSwitch = null)
        viewModelScope.launch {
            accountRepository.deleteNotesOwnedBy(pending.existingUid)
            accountRepository.adoptLocalNotes(pending.newUid)
        }
    }

    fun onDismissDialog() {
        uiState.value = uiState.value.copy(pendingSwitch = null)
    }

    fun onSignOut() {
        viewModelScope.launch {
            signOutUseCase()
        }
    }

    fun onDismissError() {
        uiState.value = uiState.value.copy(errorMessage = null)
    }
}
