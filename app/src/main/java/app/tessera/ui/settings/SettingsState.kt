package app.tessera.ui.settings

import app.tessera.core.preferences.ThemeMode
import app.tessera.domain.model.UserId
import app.tessera.sync.SyncIndicator

/**
 * An unresolved account switch: the user signed in as [newUid] but local notes are still
 * owned by [existingUid]. The Settings dialog asks how to reconcile the two.
 */
data class PendingAccountSwitch(
    val existingUid: UserId,
    val newUid: UserId,
)

data class SettingsState(
    val isSignedIn: Boolean = false,
    val userId: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isBusy: Boolean = false,
    val pendingSwitch: PendingAccountSwitch? = null,
    val errorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val languageTag: String = "",
    val syncStatus: SyncIndicator = SyncIndicator.Ok(),
)
