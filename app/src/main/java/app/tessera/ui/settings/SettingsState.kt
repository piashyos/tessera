package app.tessera.ui.settings

import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle
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
    val dynamicColor: Boolean = true,
    val themePalette: ThemePalette = ThemePalette.Default,
    val themeStyle: ThemeStyle = ThemeStyle.Default,
    val amoled: Boolean = false,
    val grainEnabled: Boolean = true,
    val grainIntensity: Float = 0.5f,
    val grainSize: Int = 1,
    val gradientEnabled: Boolean = true,
    val backgroundMotionEnabled: Boolean = true,
    val languageTag: String = "",
    val syncStatus: SyncIndicator = SyncIndicator.Ok(),
    /** Number of notes currently in Trash; drives the "Empty trash" row's enabled state. */
    val trashCount: Int = 0,
    val isEmptyingTrash: Boolean = false,
    val isExporting: Boolean = false,
    /** One-shot result of a data action; the UI localises and shows it as a toast, then clears. */
    val message: SettingsMessage? = null,
)

/** A one-shot, localisable outcome of a Settings data action (export / empty trash). */
sealed interface SettingsMessage {
    data object TrashEmptied : SettingsMessage
    data class ExportSuccess(val count: Int) : SettingsMessage
    data object ExportFailed : SettingsMessage
}
