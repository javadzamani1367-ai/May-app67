package ir.ilam.inspection.data

import android.content.Context
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.repo.CaseContentRepository
import ir.ilam.inspection.data.repo.ImportRepository
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.data.repo.AccountRepository
import ir.ilam.inspection.data.repo.PerformanceRepository
import ir.ilam.inspection.data.repo.SettingsRepository
import ir.ilam.inspection.data.repo.SnippetRepository
import ir.ilam.inspection.data.repo.UserRepository
import ir.ilam.inspection.export.ExcelExporter
import ir.ilam.inspection.export.HtmlReportBuilder
import ir.ilam.inspection.export.PdfExporter
import ir.ilam.inspection.export.WordExporter
import ir.ilam.inspection.sync.ServerCaseSync
import ir.ilam.inspection.sync.SyncService
import ir.ilam.inspection.util.AppFonts
import ir.ilam.inspection.sync.ApprovalSync
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.MediaImporter
import ir.ilam.inspection.util.MediaProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Single place where every long lived object is built, in dependency order. */
class AppContainer(private val context: Context) {

    /**
     * Work that must outlive the screen that started it, such as sending a
     * case to the server after a dispatch. A view model's scope ends when the
     * expert leaves the screen, which is exactly when they tend to leave it.
     */
    val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val vault: KeyStoreVault by lazy { KeyStoreVault(context) }
    val database: AppDatabase by lazy { AppDatabase.get(context) }
    val fileStore: FileStore by lazy { FileStore(context) }
    val counties: CountyCatalog by lazy { CountyCatalog(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database.settingDao()) }
    val snippetRepository: SnippetRepository by lazy { SnippetRepository(database.snippetDao()) }
    val userRepository: UserRepository by lazy { UserRepository(database.userDao()) }
    val accountRepository: AccountRepository by lazy { AccountRepository(vault, settingsRepository) }
    val approvalSync: ApprovalSync by lazy { ApprovalSync(vault, settingsRepository, serverCaseSync) }
    val performanceRepository: PerformanceRepository by lazy {
        PerformanceRepository(database, vault, settingsRepository)
    }
    val reportRepository: ReportRepository by lazy { ReportRepository(database, settingsRepository, fileStore) }
    val contentRepository: CaseContentRepository by lazy {
        CaseContentRepository(database, reportRepository, fileStore)
    }

    val mediaProcessor: MediaProcessor by lazy { MediaProcessor(AppFonts.typeface(context)) }
    val mediaImporter: MediaImporter by lazy { MediaImporter(context, fileStore, mediaProcessor) }
    val htmlReportBuilder: HtmlReportBuilder by lazy { HtmlReportBuilder(context, fileStore) }
    val pdfExporter: PdfExporter by lazy { PdfExporter(context, fileStore) }
    val wordExporter: WordExporter by lazy { WordExporter(context, fileStore) }
    val excelExporter: ExcelExporter by lazy { ExcelExporter(context, fileStore) }

    val syncService: SyncService by lazy {
        SyncService(context, database, reportRepository, fileStore, settingsRepository)
    }

    val importRepository: ImportRepository by lazy { ImportRepository(database, fileStore) }

    /**
     * Cases to and from the central server. Takes the device code from
     * [syncService] rather than reading it itself, so there is one answer to
     * "which installation is this" across the whole app.
     */
    val serverCaseSync: ServerCaseSync by lazy {
        ServerCaseSync(
            database = database,
            reports = reportRepository,
            imports = importRepository,
            settings = settingsRepository,
            vault = vault,
            deviceCode = { syncService.deviceId() }
        )
    }
}
