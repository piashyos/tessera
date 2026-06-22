package app.tessera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tessera.ui.navigation.NotesNavGraph

// The theme is applied by MainActivity via TesseraTheme(themeMode = ...). Re-wrapping
// here would override the user's selected mode with the system default, so we don't.
@Composable
fun TesseraApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        NotesNavGraph()
    }
}
