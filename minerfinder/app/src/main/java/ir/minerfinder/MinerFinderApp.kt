package ir.minerfinder

import android.app.Application
import ir.minerfinder.ui.map.MapSetup

class MinerFinderApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        MapSetup.init(this)
        graph = AppGraph(this)
    }
}
