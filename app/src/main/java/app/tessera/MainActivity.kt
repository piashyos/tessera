package app.tessera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.os.LocaleListCompat
import app.tessera.core.preferences.ThemeMode
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
            val themeMode by produceState<ThemeMode?>(initialValue = null, userPreferences) {
                userPreferences.themeMode.collect { value = it }
            }

            LaunchedEffect(languageTag) {
                val tag = languageTag ?: return@LaunchedEffect
                val target = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                // Skip if already applied — avoids triggering a system Activity restart
                // for a no-op call.
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == target.toLanguageTags()) {
                    return@LaunchedEffect
                }
                AppCompatDelegate.setApplicationLocales(target)
            }

            TesseraTheme(themeMode = themeMode ?: ThemeMode.AUTO) {
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
