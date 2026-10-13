package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.db.ReferralEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.Referral
import ir.ilam.inspection.data.model.ReferralOutcome
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.acceptReferral
import ir.ilam.inspection.sync.fetchReferral
import ir.ilam.inspection.sync.fetchReferrals
import ir.ilam.inspection.sync.sendReferralResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File

/**
 * Field referrals for this expert: cached so they open with no signal, acted
 * on offline, and reconciled with the server whenever it can be reached.
 *
 * Two ways to answer one:
 * - crypto: a full «گزارش همکار» case with the referral's own RZ- code, so the
 *   devices, barcodes, amperage and official report all come from the visit
 *   the expert already knows. The server closes the referral itself when that
 *   case arrives visited.
 * - everything else: a light result form, kept on the phone until sent.
 */
class ReferralRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val vault: KeyStoreVault,
    private val cacheDir: File
) {
    private val dao = database.referralDao()

    sealed class Refresh {
        data object Done : Refresh()
        data object Offline : Refresh()
        data class Refused(val message: String) : Refresh()
    }

    sealed class Lookup {
        data class Found(val id: String) : Lookup()
        data object NotOurs : Lookup()
        data object NotAReferral : Lookup()
        data object Offline : Lookup()
    }

    fun observeAll(): Flow<List<Referral>> = dao.observeAll().map { rows -> rows.mapNotNull(::toModel) }

    fun observe(id: String): Flow<Referral?> = dao.observe(id).map { it?.let(::toModel) }

    fun openCount(): Flow<Int> = dao.openCount(Referral.STATUS_REFERRED)

    /** Results waiting on this phone go first; then the server's list replaces the cache. */
    suspend fun refresh(): Refresh {
        val (api, token) = client() ?: return Refresh.Offline
        sendPending(api, token)
        return when (val result = api.fetchReferrals(token)) {
            is ApiResult.Ok -> {
                val now = System.currentTimeMillis()
                dao.replace(result.value.map { entity(it, now) })
                Refresh.Done
            }
            is ApiResult.Refused -> Refresh.Refused(result.message.ifBlank { result.code })
            ApiResult.Unreachable -> Refresh.Offline
        }
    }

    /** A scanned QR: the cached referral if this phone has it, otherwise asked of the server. */
    suspend fun lookup(scanned: String?): Lookup {
        val id = Referral.idFromQr(scanned) ?: return Lookup.NotAReferral
        if (dao.byId(id) != null) return Lookup.Found(id)
        val (api, token) = client() ?: return Lookup.Offline
        return when (val result = api.fetchReferral(token, id)) {
            is ApiResult.Ok -> {
                dao.upsert(entity(result.value, System.currentTimeMillis()))
                Lookup.Found(id)
            }
            is ApiResult.Refused -> Lookup.NotOurs
            ApiResult.Unreachable -> Lookup.Offline
        }
    }

    /**
     * The «گزارش همکار» case for a crypto referral, with the referral's code.
     * Made on the phone at once, signal or not; the server hears about it when
     * it can, and links the two by the code even if it never hears of the accept.
     * Returns the case id — the existing one if a case was already made.
     */
    suspend fun createCase(id: String): String? {
        val row = dao.byId(id) ?: return null
        row.reportId?.let { existing -> if (database.reportDao().byId(existing) != null) return existing }
        val referral = toModel(row) ?: return null
        // A case with this code already on the phone (pulled, or made before a
        // reinstall cleared the cache) is that referral's case: linked, not duplicated.
        database.reportDao().idByTrackingCode(referral.trackingCode)?.let { existing ->
            dao.setReportId(id, existing)
            return existing
        }
        val now = System.currentTimeMillis()
        val case = ReportEntity(
            trackingCode = referral.trackingCode,
            reportType = ReportType.COLLEAGUE.code,
            status = ReportStatus.PENDING.code,
            expertCode = settings.expertCode().ifBlank { null },
            reportDate = referral.createdAt.takeIf { it > 0 } ?: now,
            createdAt = now,
            updatedAt = now,
            address = referral.address
        )
        database.reportDao().insert(case)
        dao.setReportId(id, case.id)
        client()?.let { (api, token) -> api.acceptReferral(token, id, case.id) }
        return case.id
    }

    /** The light result: kept on the phone, then sent; a send that fails is retried on the next refresh. */
    suspend fun submitResult(id: String, outcome: ReferralOutcome, note: String): Boolean {
        val pending = JSONObject().put("outcome", outcome.key).put("note", note.trim()).toString()
        dao.setPendingResult(id, pending)
        val (api, token) = client() ?: return false
        sendPending(api, token)
        refresh()
        return dao.byId(id)?.pendingResult == null
    }

    /** A reporter's photo, fetched once into the cache; null when it cannot be had. */
    suspend fun photo(photoId: String): File? {
        val target = File(cacheDir, "referrals/$photoId.jpg")
        if (target.exists() && target.length() > 0) return target
        val server = settings.current().syncTarget
        val token = vault.serverToken() ?: return null
        if (server.isBlank()) return null
        val ok = ServerFiles(server, token).downloadRoute("referrals/file&id=$photoId", target)
        return if (ok) target else null
    }

    private suspend fun sendPending(api: ServerApi, token: String) {
        dao.withPendingResult().forEach { row ->
            val json = runCatching { JSONObject(row.pendingResult ?: "") }.getOrNull() ?: return@forEach
            when (api.sendReferralResult(token, row.id, json.optString("outcome"), json.optString("note"))) {
                // Accepted, or refused for good (no longer open): either way it must not be resent for ever.
                is ApiResult.Ok, is ApiResult.Refused -> dao.setPendingResult(row.id, null)
                ApiResult.Unreachable -> return
            }
        }
    }

    private suspend fun client(): Pair<ServerApi, String>? {
        val api = ServerApi(settings.current().syncTarget)
        val token = vault.serverToken() ?: return null
        return if (api.configured) api to token else null
    }

    private fun entity(json: JSONObject, now: Long) = ReferralEntity(
        id = json.optString("id"),
        json = json.toString(),
        status = json.optInt("status"),
        dueAt = if (json.isNull("due_at")) null else json.optLong("due_at"),
        fetchedAt = now
    )

    private fun toModel(row: ReferralEntity): Referral? = runCatching {
        val pending = row.pendingResult?.let { runCatching { JSONObject(it).optString("outcome") }.getOrNull() }
        Referral.fromJson(JSONObject(row.json), row.reportId, pending)
    }.getOrNull()
}
