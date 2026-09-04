package ir.ilam.inspection.data

import android.content.Context
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.repo.CaseContentRepository
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.data.repo.SettingsRepository
import ir.ilam.inspection.export.ExcelExporter
import ir.ilam.inspection.export.HtmlReportBuilder
import ir.ilam.inspection.export.PdfExporter
import ir.ilam.inspection.export.WordExporter
import ir.ilam.inspection.sync.SyncService
import ir.ilam.inspection.util.AppFonts
import ir.ilam.inspection.util.FileStore
import ir.ilam.inspection.util.MediaProcessor

/** Single place where every long lived object is built, in dependency order. */
class AppContainer(private val context: Context) {

    val vault: KeyStoreVault by lazy { KeyStoreVault(context) }
    val database: AppDatabase by lazy { AppDatabase.get(context) }
    val fileStore: FileStore by lazy { FileStore(context) }
    val counties: CountyCatalog by lazy { CountyCatalog(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database.settingDao()) }
    val reportRepository: ReportRepository by lazy { ReportRepository(database, settingsRepository) }
    val contentRepository: CaseContentRepository by lazy {
        CaseContentRepository(database, reportRepository, fileStore)
    }

    val mediaProcessor: MediaProcessor by lazy { MediaProcessor(AppFonts.typeface(context)) }
    val htmlReportBuilder: HtmlReportBuilder by lazy { HtmlReportBuilder(context, fileStore) }
    val pdfExporter: PdfExporter by lazy { PdfExporter(context, fileStore) }
    val wordExporter: WordExporter by lazy { WordExporter(context, fileStore) }
    val excelExporter: ExcelExporter by lazy { ExcelExporter(context, fileStore) }

    val syncService: SyncService by lazy {
        SyncService(context, database, reportRepository, fileStore, settingsRepository)
    }
}
