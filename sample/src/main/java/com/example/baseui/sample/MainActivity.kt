package com.example.baseui.sample

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.example.baseui.compose.BaseZoomHost
import com.example.baseui.compose.CutoutToastHost
import com.example.baseui.compose.CutoutToastState
import com.example.baseui.compose.rememberCutoutToastState

/** The app-wide cutout toast, for the Compose demos. */
val LocalToast = staticCompositionLocalOf<CutoutToastState> { error("No toast host") }

/** AppCompatActivity (a FragmentActivity) so the XML demos can use fragments. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()   // the cutout toast draws over the status bar
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                val toast = rememberCutoutToastState()
                CompositionLocalProvider(LocalToast provides toast) {
                    Box(Modifier.fillMaxSize()) {
                        BaseZoomHost { SampleApp() }   // zoomed screens draw over the whole app
                        CutoutToastHost(toast)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleApp() {
    var feature by rememberSaveable { mutableStateOf<Feature?>(null) }
    BackHandler(enabled = feature != null) { feature = null }
    when (val current = feature) {
        null -> CatalogScreen(onOpen = { feature = it })
        else -> FeatureScreen(current, onBack = { feature = null })
    }
}

@Composable
fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}
