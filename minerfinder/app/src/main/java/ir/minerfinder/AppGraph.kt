package ir.minerfinder

import android.content.Context
import ir.minerfinder.audio.AudioHub
import ir.minerfinder.audio.AudioInputs
import ir.minerfinder.data.AppDatabase
import ir.minerfinder.location.HeadingSensor
import ir.minerfinder.location.LocationTracker
import ir.minerfinder.scan.ScanController
import ir.minerfinder.session.SessionRecorder
import ir.minerfinder.signature.SignatureLibrary
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
    val database = AppDatabase.open(appContext)
    val locations = LocationTracker(appContext)
    val heading = HeadingSensor(appContext, locations)
    val catalog = SourceCatalog(this)
    val scan = ScanController(this)
    val recorder = SessionRecorder(this)
    val signatures = SignatureLibrary(this)

    /** Session the map shows; null means the one being recorded, else the latest. */
    val mapSession = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
}

val Context.graph: AppGraph get() = (applicationContext as MinerFinderApp).graph
