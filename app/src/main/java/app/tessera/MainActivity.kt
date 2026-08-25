package app.tessera

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import app.tessera.core.preferences.UserPreferences
import app.tessera.core.util.CurrentActivityHolder
import app.tessera.core.ui.theme.TesseraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var currentActivityHolder: CurrentActivityHolder

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Use null-initial produceState so we can distinguish "DataStore hasn't
            // emitted yet" from "user wants system default ("")". Without this, the
            // initial-value placeholder ("" / AUTO) races the real value through the
            // flow → MainActivity briefly applies (empty locale, AUTO theme) after
            // every restart, which causes AppCompatDelegate.setApplicationLocales to
            // ricochet between "ru" (persisted) and empty, restarting the activity in
            // a loop and freezing the theme on its initial AUTO state.
            val languageTag by produceState<String?>(initialValue = null, userPreferences) {
                userPreferences.languageTag.collect { value = it }
            }
            // Seed with the last known theme (not null/AUTO) so a recreation — e.g. triggered by a
            // language change — keeps the user's theme instead of flashing to the system default
            // while DataStore re-emits.
            val themeMode by produceState(
                initialValue = userPreferences.cachedThemeMode,
                userPreferences,
            ) {
                userPreferences.themeMode.collect { value = it }
            }
            val dynamicColor by produceState(
                initialValue = userPreferences.cachedDynamicColor,
                userPreferences,
            ) {
                userPreferences.dynamicColor.collect { value = it }
            }
            val themePalette by produceState(
                initialValue = userPreferences.cachedThemePalette,
                userPreferences,
            ) {
                userPreferences.themePalette.collect { value = it }
            }

            val context = LocalContext.current
            LaunchedEffect(languageTag) {
                val tag = languageTag ?: return@LaunchedEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Direct system API. AppCompatDelegate.setApplicationLocales relies on a
                    // private `mAppContext` that's only populated by AppCompatActivity.onCreate;
                    // we extend ComponentActivity, so AppCompat's static path is a silent no-op.
                    val lm = context.getSystemService(LocaleManager::class.java) ?: return@LaunchedEffect
                    val target = if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
                                 else LocaleList.forLanguageTags(tag)
                    if (lm.applicationLocales.toLanguageTags() == target.toLanguageTags()) {
                        return@LaunchedEffect
                    }
                    lm.applicationLocales = target
                } else {
                    val target = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                 else LocaleListCompat.forLanguageTags(tag)
                    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == target.toLanguageTags()) {
                        return@LaunchedEffect
                    }
                    AppCompatDelegate.setApplicationLocales(target)
                }
            }

            // Keep status/navigation bar icon tint in sync with the active theme. With
            // edge-to-edge the bars are transparent, so we only flip light/dark icons:
            // dark icons on a light theme, light icons on a dark theme.
            val resolvedDark = themeMode.resolveDark(isSystemInDarkTheme())
            val view = LocalView.current
            LaunchedEffect(resolvedDark) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !resolvedDark
                controller.isAppearanceLightNavigationBars = !resolvedDark
            }

            TesseraTheme(themeMode = themeMode, dynamicColor = dynamicColor, palette = themePalette) {
                TesseraApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentActivityHolder.activity = this
    }

    override fun onDestroy() {
        if (currentActivityHolder.activity === this) {
            currentActivityHolder.activity = null
        }
        super.onDestroy()
    }
}
