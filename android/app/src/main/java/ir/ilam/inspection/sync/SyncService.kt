package ir.ilam.inspection.sync

import android.content.Context
import android.provider.Settings
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.data.repo.SettingsRepository
import ir.ilam.inspection.util.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

/** Starts and stops the server, hands out the pairing code, builds packages. */
class SyncService(
    private val context: Context,
    private val database: AppDatabase,
    private val reports: ReportRepository,
    private val files: FileStore,
    private val settings: SettingsRepository
) {

    private val packageExporter = PackageExporter(database, reports, files)

    private var server: SyncServer? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    val pendingCount: Flow<Int> = database.reportDao().pendingSyncCount()

    suspend fun start(): String {
        stop()
        val code = "%06d".format(Random.nextInt(0, 1_000_000))
        val instance = SyncServer(
            database = database,
            reports = reports,
            files = files,
            deviceId = deviceId(),
            expertCode = settings.expertCode(),
            pairingCode = code
        )
        instance.start(SOCKET_TIMEOUT, true)
        server = instance
        _pairingCode.value = code
        _running.value = true
        return code
    }

    fun stop() {
        server?.stop()
        server = null
        _running.value = false
        _pairingCode.value = ""
    }

    /** `http://<phone ip>:8765`, or null when not attached to a network. */
    fun address(): String? = localAddress()?.let { "http://$it:$SYNC_PORT" }

    suspend fun exportPackage(vault: KeyStoreVault): File? = packageExporter.export(
        fileName = "package-" + System.currentTimeMillis(),
        password = vault.packagePassword(),
        deviceId = deviceId(),
        expertCode = settings.expertCode()
    )

    suspend fun acknowledgeExported(ids: List<String>) = packageExporter.markSynced(ids)

    suspend fun pendingIds(): List<String> = packageExporter.pendingIds()

    /** Stable per-install identifier; no account, no server, no registration. */
    fun deviceId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown"

    /** Works for Wi-Fi and for the loopback route `adb forward` sets up. */
    private fun localAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }

    private companion object {
        const val SOCKET_TIMEOUT = 15_000
    }
}
