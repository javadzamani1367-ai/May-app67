package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.UnitPerformance
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.unitStats

/** Where the performance figures came from, which the report has to state. */
enum class PerformanceSource { SERVER, THIS_PHONE }

data class PerformanceReport(
    val rows: List<UnitPerformance>,
    val source: PerformanceSource,
    val from: Long,
    val to: Long
)

/**
 * The manager's view of how the units are doing.
 *
 * The server is asked first, because only it sees every expert's dispatches.
 * When it cannot be reached the figures are computed from this phone's own
 * rows instead — a partial answer that says it is partial, which is more use
 * to a manager than an empty screen.
 */
class PerformanceRepository(
    private val db: AppDatabase,
    private val vault: KeyStoreVault,
    private val settings: SettingsRepository
) {

    suspend fun load(from: Long, to: Long): PerformanceReport {
        fromServer(from, to)?.let { return it }

        val now = System.currentTimeMillis()
        val rows = db.dispatchDao().performance(from, to, now).map { row ->
            UnitPerformance(
                unit = DispatchUnit.of(row.unit),
                sent = row.sent,
                seen = row.seen,
                answered = row.answered,
                overdue = row.overdue,
                onTime = row.onTime,
                averageAnswerHours = row.avgMillis?.let { it / 3_600_000 }
            )
        }
        return PerformanceReport(
            rows = UnitPerformance.table(rows),
            source = PerformanceSource.THIS_PHONE,
            from = from,
            to = to
        )
    }

    private suspend fun fromServer(from: Long, to: Long): PerformanceReport? {
        val api = ServerApi(settings.current().syncTarget)
        val token = vault.serverToken()
        if (!api.configured || token == null) return null
        return when (val result = api.unitStats(token, from, to)) {
            is ApiResult.Ok -> PerformanceReport(
                rows = UnitPerformance.table(result.value),
                source = PerformanceSource.SERVER,
                from = from,
                to = to
            )
            is ApiResult.Refused, ApiResult.Unreachable -> null
        }
    }
}
