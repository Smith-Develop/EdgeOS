package io.vidrio.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import io.vidrio.launcher.debloat.Debloater
import io.vidrio.launcher.ui.VidrioApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var model: AppsModel
    private val homeEvents = mutableIntStateOf(0)
    private val resumeEvents = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        model = AppsModel(applicationContext, scope).also { it.start() }
        val debloater = Debloater(applicationContext, prefs)
        setContent {
            VidrioApp(model, prefs, debloater, homeEvents.intValue, resumeEvents.intValue)
        }
    }

    /** Pulsar Inicio con el launcher ya delante. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeEvents.intValue++
    }

    override fun onResume() {
        super.onResume()
        resumeEvents.intValue++
    }

    override fun onDestroy() {
        model.stop()
        scope.cancel()
        super.onDestroy()
    }
}
