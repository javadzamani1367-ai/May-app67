package ir.roozban.core.domain

import kotlinx.coroutines.flow.StateFlow

/** How model and voice downloads may use the network. */
interface DownloadSettings {
    /** True: only on Wi-Fi (or another unmetered network); false: mobile data too. */
    val wifiOnly: StateFlow<Boolean>

    fun setWifiOnly(value: Boolean)
}
