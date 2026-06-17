package app.tessera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.UserPreferencesRepository
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.core.util.CurrentActivityHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var currentActivityHolder: CurrentActivityHolder

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by userPreferencesRepository.themeMode.collectAsState(initial = ThemeMode.AUTO)
            val languageTag by userPreferencesRepository.languageTag.collectAsState(initial = "")

            LaunchedEffect(languageTag) {
                val locales = if (languageTag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }

            TesseraTheme(themeMode = themeMode) {
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
