package app.tessera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tessera.core.util.CurrentActivityHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var currentActivityHolder: CurrentActivityHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TesseraApp() }
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
