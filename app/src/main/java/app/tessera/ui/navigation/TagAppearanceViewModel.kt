package app.tessera.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.core.ui.tessera.TagStyle
import app.tessera.core.ui.tessera.parseEntityIcon
import app.tessera.core.ui.theme.AccentColor
import app.tessera.domain.usecase.ObserveTagAppearancesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Tag name -> chosen colour and icon for the whole app (provided as `LocalTagAppearances` at the
 * navigation root). A key written by a newer app version maps to `null`, i.e. the default look.
 */
@HiltViewModel
class TagAppearanceViewModel @Inject constructor(
    observeTagAppearances: ObserveTagAppearancesUseCase,
) : ViewModel() {
    val styles: StateFlow<Map<String, TagStyle>> =
        observeTagAppearances()
            .map { byName ->
                byName.mapValues { (_, a) -> TagStyle(AccentColor.fromKey(a.color), parseEntityIcon(a.icon)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}
