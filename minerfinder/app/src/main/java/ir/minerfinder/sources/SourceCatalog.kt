package ir.minerfinder.sources

import ir.minerfinder.AppGraph
import ir.minerfinder.core.sensor.SensorSource

/**
 * Every source the app knows. Adding one (RTL-SDR over USB OTG, ESP32 over
 * BLE...) means writing a SensorSource and adding it to [all]; the engine,
 * the weights in the settings and the search screen take it from here.
 */
class SourceCatalog(graph: AppGraph) {
    val audio = AcousticSource(graph)
    val magnetometer = MagnetometerSource(graph)
    val coil = CoilSource(graph)

    val all: List<SensorSource> = listOf(audio, magnetometer, coil)

    fun byId(id: String): SensorSource? = all.firstOrNull { it.id == id }
}
