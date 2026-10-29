package ir.minerfinder

import android.content.Context
import ir.minerfinder.audio.AudioHub
import ir.minerfinder.audio.AudioInputs
import ir.minerfinder.scan.ScanController
import ir.minerfinder.settings.AppSettings
import ir.minerfinder.sources.SourceCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The app's few long-lived objects, built once in [MinerFinderApp]. */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = AppSettings(appContext)
    val audioInputs = AudioInputs(appContext)
    val audioHub = AudioHub(appContext, audioInputs, scope)
    val catalog = SourceCatalog(this)
    val scan = ScanController(this)
}

val Context.graph: AppGraph get() = (applicationContext as MinerFinderApp).graph
