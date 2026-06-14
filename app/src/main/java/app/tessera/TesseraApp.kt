package app.tessera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.navigation.NotesNavGraph

@Composable
fun TesseraApp() {
    TesseraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NotesNavGraph()
        }
    }
}
