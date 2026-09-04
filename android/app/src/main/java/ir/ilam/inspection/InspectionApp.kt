package ir.ilam.inspection

import android.app.Application
import android.content.Context
import ir.ilam.inspection.data.AppContainer

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
    }
}

val Context.container: AppContainer
    get() = (applicationContext as InspectionApp).container
