package app.tessera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.tessera.ui.navigation.NotesNavGraph

// The theme is applied by MainActivity via TesseraTheme(themeMode = ...). Re-wrapping
// here would override the user's selected mode with the system default, so we don't.
@Composable
fun TesseraApp() {
    // Transparent so the app-wide background (and its film grain) painted by GrainOverlay shows
    // through — screens paint no opaque full-screen background of their own. contentColor is still
    // set explicitly so text/icons inherit the on-background color.
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        NotesNavGraph()
    }
}
