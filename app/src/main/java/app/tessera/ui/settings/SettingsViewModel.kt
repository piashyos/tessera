package app.tessera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.core.export.MarkdownExport
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle
import app.tessera.core.preferences.UserPreferences
import app.tessera.domain.model.AccountSwitchDecision
import app.tessera.domain.model.Note
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncScheduler
import app.tessera.domain.usecase.HandleAccountSwitchUseCase
import app.tessera.domain.usecase.PermanentlyDeleteNoteUseCase
import app.tessera.domain.usecase.SignInUseCase
import app.tessera.domain.usecase.SignOutUseCase
import app.tessera.sync.SyncStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val handleAccountSwitchUseCase: HandleAccountSwitchUseCase,
    private val userPreferences: UserPreferences,
    private val syncScheduler: SyncScheduler,
    private val notesRepository: NotesRepository,
    private val permanentlyDeleteNoteUseCase: PermanentlyDeleteNoteUseCase,
    syncStatusProvider: SyncStatusProvider,
) : ViewModel() {
    private val uiState = MutableStateFlow(SettingsState())

    // The theme-related prefs are independent; bundle them so the top-level combine stays
    // within its 5-arg arity.
    private data class ThemePrefs(
        val mode: ThemeMode,
        val dynamic: Boolean,
        val palette: ThemePalette,
        val style: ThemeStyle,
        val amoled: Boolean,
        val grainEnabled: Boolean,
        val grainIntensity: Float,
    )

    // The 5-arg color prefs + the 2 grain prefs = 7 flows; nest a 5-way combine inside a 3-way one
    // to stay within combine's typed arity while keeping it fully type-safe.
    private val themePrefs =
        combine(
            combine(
                userPreferences.themeMode,
                userPreferences.dynamicColor,
                userPreferences.themePalette,
                userPreferences.themeStyle,
                userPreferences.amoled,
            ) { mode, dynamic, palette, style, amoled ->
                ThemePrefs(mode, dynamic, palette, style, amoled, grainEnabled = true, grainIntensity = 0.5f)
            },
            userPreferences.grainEnabled,
            userPreferences.grainIntensity,
        ) { base, grainEnabled, grainIntensity ->
            base.copy(grainEnabled = grainEnabled, grainIntensity = grainIntensity)
        }

    // languageTag + trash size + native-editor flag — grouped for the same arity reason as themePrefs.
    private val langAndTrash =
        combine(
            userPreferences.languageTag,
            notesRepository.observeDeletedNotes(),
            userPreferences.useNativeEditor,
        ) { lang, deleted, nativeEditor ->
            Triple(lang, deleted.size, nativeEditor)
        }

    val state: StateFlow<SettingsState> =
        combine(
            authRepository.currentProfile,
            uiState,
            themePrefs,
            langAndTrash,
            syncStatusProvider.syncStatus,
        ) { profile, ui, theme, langTrash, sync ->
            ui.copy(
                isSignedIn = profile != null,
                userId = profile?.id?.value,
                displayName = profile?.displayName,
                email = profile?.email,
                themeMode = theme.mode,
                dynamicColor = theme.dynamic,
                themePalette = theme.palette,
                themeStyle = theme.style,
                amoled = theme.amoled,
                grainEnabled = theme.grainEnabled,
                grainIntensity = theme.grainIntensity,
                languageTag = langTrash.first,
                trashCount = langTrash.second,
                useNativeEditor = langTrash.third,
                syncStatus = sync,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsState(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(enabled) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { userPreferences.setThemePalette(palette) }
    }

    fun setThemeStyle(style: ThemeStyle) {
        viewModelScope.launch { userPreferences.setThemeStyle(style) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAmoled(enabled) }
    }

    fun setGrainEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setGrainEnabled(enabled) }
    }

    fun setGrainIntensity(intensity: Float) {
        viewModelScope.launch { userPreferences.setGrainIntensity(intensity) }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch { userPreferences.setLanguageTag(tag) }
    }

    fun setUseNativeEditor(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setUseNativeEditor(enabled) }
    }

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

    /** Irreversibly removes every note currently in Trash. */
    fun onEmptyTrash() {
        if (uiState.value.isEmptyingTrash) return
        uiState.value = uiState.value.copy(isEmptyingTrash = true)
        viewModelScope.launch {
            val ids = notesRepository.observeDeletedNotes().first().map(Note::id)
            permanentlyDeleteNoteUseCase(ids)
            uiState.value = uiState.value.copy(
                isEmptyingTrash = false,
                message = SettingsMessage.TrashEmptied,
            )
        }
    }

    /**
     * Writes all active + archived notes as a Markdown `.zip` to [output] (a destination the UI
     * opened from the user's SAF pick). Always closes [output]; emits a typed [SettingsMessage]
     * the UI localises.
     */
    fun onExport(output: OutputStream) {
        if (uiState.value.isExporting) return
        uiState.value = uiState.value.copy(isExporting = true)
        viewModelScope.launch {
            val notes = notesRepository.observeNotes().first() +
                notesRepository.observeArchivedNotes().first()
            val message = runCatching {
                withContext(Dispatchers.IO) { MarkdownExport.writeZip(notes, output) }
                SettingsMessage.ExportSuccess(notes.size)
            }.getOrElse { SettingsMessage.ExportFailed }
            uiState.value = uiState.value.copy(isExporting = false, message = message)
        }
    }

    fun onMessageShown() {
        uiState.value = uiState.value.copy(message = null)
    }

    /** Re-enqueue a one-time sync, e.g. after a sync error shown in the sync status row. */
    fun onRetrySync() {
        syncScheduler.requestSync()
    }
}
