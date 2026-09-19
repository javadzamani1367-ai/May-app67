package ir.ilam.inspection

import android.app.Application
import android.content.Context
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.sync.ServerSyncWorker
import ir.ilam.inspection.util.MapConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manual dependency container. The app is small and offline; a DI framework
 * would only add method count and build time.
 */
class InspectionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapConfig.ensure(this)

        // Reading the setting touches the encrypted database, so it cannot
        // happen on the main thread during startup. Registering the job is
        // cheap and idempotent — an already scheduled one keeps its place.
        CoroutineScope(Dispatchers.IO).launch {
            if (container.settingsRepository.autoSync()) {
                ServerSyncWorker.schedule(this@InspectionApp)
            } else {
                ServerSyncWorker.cancel(this@InspectionApp)
            }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as InspectionApp).container
