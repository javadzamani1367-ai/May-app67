package ir.ilam.inspection.sync

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.db.ServerSyncEntity
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.data.repo.ImportRepository
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.data.repo.SettingsRepository

/**
 * Cases travelling between this phone and the central server.
 *
 * Up: every case this phone has changed since it last sent it. Down: every
 * case the server has that this phone does not, which is how a manager sees
 * the work of experts whose phones they will never hold.
 *
 * Nothing here is required for field work. A case is complete on the phone
 * that recorded it, and the Wi-Fi, USB and `.cvz` routes to the Windows
 * archive still work untouched. This is the route that lets the office see a
 * case the same day instead of at the end of the week.
 *
 * Rows first, files second, on purpose: a case whose photos are still coming
 * is worth more to a manager than nothing at all, and on a village connection
 * the photos may take until tomorrow.
 */
class ServerCaseSync(
    private val database: AppDatabase,
    private val reports: ReportRepository,
    private val imports: ImportRepository,
    private val settings: SettingsRepository,
    private val vault: KeyStoreVault,
    private val deviceCode: () -> String
) {

    /** What one run did, in the numbers the settings screen shows. */
    data class Outcome(
        val pushed: Int = 0,
        val pulled: Int = 0,
        val filesUp: Int = 0,
        val filesDown: Int = 0,
        val failed: Int = 0,
        val reachedServer: Boolean = false,
        /** A call did not arrive at all. Nothing after it is worth trying. */
        val offline: Boolean = false,
        /** The server said no to something — a stale token, a lost device registration. */
        val refusal: String? = null
    )

    suspend fun run(): Outcome {
        val target = settings.current().syncTarget
        val api = ServerApi(target)
        val token = vault.serverToken()
        if (!api.configured || token == null) return Outcome()

        val files = ServerFiles(target, token)
        val push = push(api, token, files)
        // A manager also pulls; an expert has nothing to pull that is not
        // already the case on their own phone. And once a call has failed to
        // arrive, pulling would only spend another timeout finding that out.
        return if (UserRole.isManager && !push.offline) pull(api, token, files, push) else push
    }

    /**
     * Sends one case up now, whatever its sync state.
     *
     * Submitting a case for the manager's approval needs this: the server
     * records the submission against a case it holds, so a submission for a
     * case it has never seen is refused — and the expert would be told their
     * case was waiting for a decision that nobody could ever see.
     */
    suspend fun pushOne(reportId: String): Boolean {
        val target = settings.current().syncTarget
        val api = ServerApi(target)
        val token = vault.serverToken() ?: return false
        if (!api.configured) return false
        val detail = reports.detail(reportId) ?: return false

        return when (val result = api.pushReport(token, detail, deviceCode())) {
            is ApiResult.Ok -> {
                upload(ServerFiles(target, token), detail, result.value)
                markSent(reportId)
                true
            }
            is ApiResult.Refused, ApiResult.Unreachable -> false
        }
    }

    private suspend fun push(api: ServerApi, token: String, files: ServerFiles): Outcome {
        var outcome = Outcome(reachedServer = false)
        val pending = database.serverSyncDao().pending()
        val device = deviceCode()

        pending.forEach { row ->
            val detail = reports.detail(row.id) ?: return@forEach
            when (val result = api.pushReport(token, detail, device)) {
                is ApiResult.Ok -> {
                    val sent = upload(files, detail, result.value)
                    // Marked as sent even when a photo did not make it: the
                    // rows are there, and the next run asks the server again
                    // which files it is missing. Withholding the mark would
                    // resend the whole case every time instead.
                    markSent(row.id)
                    outcome = outcome.copy(
                        pushed = outcome.pushed + 1,
                        filesUp = outcome.filesUp + sent,
                        reachedServer = true
                    )
                }
                is ApiResult.Refused -> outcome = outcome.copy(
                    failed = outcome.failed + 1,
                    reachedServer = true,
                    refusal = outcome.refusal ?: result.message.ifBlank { result.code }
                )
                ApiResult.Unreachable -> return outcome.copy(offline = true)
            }
        }
        return outcome
    }

    /**
     * Records the send in the phone's own table.
     *
     * Never in the case's `synced_at`: that column is the Windows archive's
     * watermark, and it also gates deleting an archived case. Writing it here
     * would let a case be deleted although the archive had never received it.
     */
    private suspend fun markSent(reportId: String) =
        database.serverSyncDao().mark(ServerSyncEntity(reportId, System.currentTimeMillis()))

    private suspend fun upload(
        files: ServerFiles,
        detail: ReportDetail,
        missing: List<MissingFile>
    ): Int {
        if (missing.isEmpty()) return 0
        val paths = buildMap {
            detail.media.forEach { put(it.id, it.filePath) }
            detail.attachments.forEach { put(it.id, it.filePath) }
        }
        return missing.count { file ->
            val path = paths[file.id] ?: return@count false
            files.upload(file.kind, file.id, imports.target(path))
        }
    }

    private suspend fun pull(
        api: ServerApi,
        token: String,
        files: ServerFiles,
        carried: Outcome
    ): Outcome {
        var outcome = carried
        val since = settings.pulledAt()
        val manifest = when (val result = api.remoteManifest(token, since)) {
            is ApiResult.Ok -> result.value
            is ApiResult.Refused -> return outcome.copy(
                reachedServer = true,
                refusal = outcome.refusal ?: result.message.ifBlank { result.code }
            )
            ApiResult.Unreachable -> return outcome.copy(offline = true)
        }

        var highest = since
        manifest.forEach { remote ->
            val local = database.reportDao().byId(remote.id)
            if (local != null && local.updatedAt >= remote.updatedAt) {
                highest = maxOf(highest, remote.updatedAt)
                return@forEach
            }
            val detail = when (val result = api.pullReport(token, remote.id)) {
                is ApiResult.Ok -> result.value
                is ApiResult.Refused -> null
                ApiResult.Unreachable -> return outcome.copy(offline = true)
            }
            if (detail == null) {
                outcome = outcome.copy(failed = outcome.failed + 1, reachedServer = true)
                return@forEach
            }
            if (imports.save(detail)) {
                // A case that just came down must not go straight back up on the
                // next run. It is already the server's own copy.
                markSent(remote.id)
                val fetched = imports.missingFiles(detail).count { file ->
                    files.download(file.kind, file.id, imports.target(file.relativePath))
                }
                outcome = outcome.copy(
                    pulled = outcome.pulled + 1,
                    filesDown = outcome.filesDown + fetched,
                    reachedServer = true
                )
            }
            highest = maxOf(highest, remote.updatedAt)
        }

        // Only moved once the cases up to here are actually written, so a run
        // cut off half way through is repeated rather than skipped.
        if (highest > since) settings.setPulledAt(highest)
        return outcome
    }
}
