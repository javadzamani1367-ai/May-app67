package ir.ilam.inspection.field

import android.app.Application
import android.content.Context
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.field.data.FieldAccount
import ir.ilam.inspection.field.data.FieldDrafts
import ir.ilam.inspection.field.data.FieldPrefs
import ir.ilam.inspection.field.data.db.FieldDatabase
import ir.ilam.inspection.field.sync.FieldSync
import ir.ilam.inspection.field.sync.FieldSyncWorker
import ir.ilam.inspection.field.sync.HistoryCache
import ir.ilam.inspection.util.AppFonts
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.MediaProcessor
import ir.ilam.inspection.util.MapConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** The field app's few dependencies, made once and by hand — no framework for this size. */
class FieldContainer(private val context: Context) {
    val vault by lazy { KeyStoreVault(context) }
    val prefs by lazy { FieldPrefs(context) }
    val files by lazy { FileStore(context) }
    val database by lazy { FieldDatabase.get(context) }
    val account by lazy { FieldAccount(vault, prefs) }
    /** The area's inspection history, per account like the database (pictures in the account's own folder). */
    val history by lazy {
        HistoryCache(database.history(), prefs, account, File(context.filesDir, vault.databaseName("history").removeSuffix(".db")))
    }
    val sync by lazy { FieldSync(database.dao(), prefs, account, files, history) }
    val drafts by lazy { FieldDrafts(database.dao(), files) }
    val mediaProcessor by lazy { MediaProcessor(AppFonts.typeface(context)) }
    /** For a write that must finish even when the screen that asked for it is gone. */
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class FieldApp : Application() {

    lateinit var container: FieldContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = FieldContainer(this)
        MapConfig.ensure(this)
        // Scheduling is idempotent and cheap; if WorkManager is not ready the
        // app must still open — sending is a convenience, getting in is not.
        runCatching { FieldSyncWorker.schedulePeriodic(this) }
    }
}

val Context.field: FieldContainer
    get() = (applicationContext as FieldApp).container
