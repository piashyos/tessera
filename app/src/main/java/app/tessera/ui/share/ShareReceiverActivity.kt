package app.tessera.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tessera.R
import app.tessera.core.preferences.UserPreferences
import app.tessera.core.ui.theme.TesseraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android share-sheet target: receives ACTION_SEND text and shows only a bottom-sheet
 * modal (the activity window is translucent — the app itself never opens). Saving
 * creates a new note by default or appends to a picked existing note.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private val viewModel: ShareReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val sharedText = incomingText()
        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }
        viewModel.setSharedText(sharedText)

        setContent {
            // Same seeding strategy as MainActivity: start from the cached values so the
            // sheet doesn't flash the default theme while DataStore emits.
            val themeMode by produceState(userPreferences.cachedThemeMode, userPreferences) {
                userPreferences.themeMode.collect { value = it }
            }
            val dynamicColor by produceState(userPreferences.cachedDynamicColor, userPreferences) {
                userPreferences.dynamicColor.collect { value = it }
            }
            val themePalette by produceState(userPreferences.cachedThemePalette, userPreferences) {
                userPreferences.themePalette.collect { value = it }
            }
            val themeStyle by produceState(userPreferences.cachedThemeStyle, userPreferences) {
                userPreferences.themeStyle.collect { value = it }
            }
            val amoled by produceState(userPreferences.cachedAmoled, userPreferences) {
                userPreferences.amoled.collect { value = it }
            }

            TesseraTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                palette = themePalette,
                style = themeStyle,
                amoled = amoled,
            ) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val pickerNotes by viewModel.pickerNotes.collectAsStateWithLifecycle()
                val pickerQuery by viewModel.query.collectAsStateWithLifecycle()

                LaunchedEffect(state.saved) {
                    if (state.saved) {
                        Toast.makeText(
                            this@ShareReceiverActivity,
                            getString(R.string.share_saved),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }

                ShareSheet(
                    state = state,
                    pickerNotes = pickerNotes,
                    pickerQuery = pickerQuery,
                    onTextChange = viewModel::onTextChange,
                    onQueryChange = viewModel::onQueryChange,
                    onSelectNewNote = viewModel::selectNewNote,
                    onSelectNote = viewModel::selectExistingNote,
                    onAddTag = viewModel::addTag,
                    onRemoveTag = viewModel::removeTag,
                    onSave = viewModel::save,
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun incomingText(): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return when {
            text.isNullOrBlank() -> subject
            subject.isNullOrBlank() -> text
            // Some apps (browsers) put the page title in EXTRA_SUBJECT — keep it as a first line.
            text.contains(subject) -> text
            else -> "$subject\n$text"
        }
    }
}
