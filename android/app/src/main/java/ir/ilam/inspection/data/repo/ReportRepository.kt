package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.TrackingCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The case lifecycle: intake, field completion, status changes, search. */
class ReportRepository(private val db: AppDatabase, private val settings: SettingsRepository) {

    private val reports = db.reportDao()

    fun observePending(): Flow<List<ReportEntity>> =
        reports.observeByStatusOldestFirst(ReportStatus.PENDING.code)

    fun observeVisited(): Flow<List<ReportEntity>> =
        reports.observeByStatusNewestFirst(ReportStatus.VISITED.code)

    fun observeArchived(): Flow<List<ReportEntity>> =
        reports.observeByStatusNewestFirst(ReportStatus.ARCHIVED.code)

    fun search(status: ReportStatus?, query: String): Flow<List<ReportEntity>> =
        reports.search(status?.code, query.trim())

    fun observeReport(id: String): Flow<ReportEntity?> = reports.observeById(id)

    fun observeDetail(id: String): Flow<ReportDetail?> = combine(
        reports.observeById(id),
        db.deviceDao().observeFor(id),
        db.attendeeDao().observeFor(id),
        db.mediaDao().observeFor(id),
        db.attachmentDao().observeFor(id)
    ) { report, devices, attendees, media, attachments ->
        report?.let { ReportDetail(it, devices, attendees, media, attachments) }
    }

    fun observeDispatches(id: String) = db.dispatchDao().observeFor(id)

    suspend fun detail(id: String): ReportDetail? {
        val report = reports.byId(id) ?: return null
        return ReportDetail(
            report = report,
            devices = db.deviceDao().listFor(id),
            attendees = db.attendeeDao().listFor(id),
            media = db.mediaDao().listFor(id),
            attachments = db.attachmentDao().listFor(id),
            dispatches = db.dispatchDao().listFor(id)
        )
    }

    /**
     * Quick intake. A code is generated straight away; when the subscription
     * number is still unknown the case gets a temporary code and keeps it in
     * `temp_code` for the rest of its life so the paper trail survives.
     */
    suspend fun createIntake(
        type: ReportType,
        countyIndex: Int?,
        countyName: String?,
        fallbackAreaCode: String,
        district: String?,
        address: String?,
        subscription: String?,
        reportDate: Long,
        manualTrackingCode: String? = null
    ): Result<String> {
        val areaCode = settings.areaCodeFor(countyIndex, fallbackAreaCode)
        var trackingCode: String? = null
        var tempCode: String? = null

        if (type.generatesCode) {
            val generated = TrackingCode.generate(type, areaCode, reportDate, subscription)
            if (generated != null) {
                trackingCode = generated
            } else {
                tempCode = TrackingCode.temporary(type, areaCode, reportDate, nextTempSequence())
            }
        } else {
            val manual = manualTrackingCode?.trim().orEmpty()
            if (manual.isEmpty()) return Result.failure(IllegalArgumentException(MISSING_MANUAL_CODE))
            trackingCode = manual
        }

        trackingCode?.let {
            if (reports.countByTrackingCode(it) > 0) {
                return Result.failure(IllegalStateException(DUPLICATE_CODE))
            }
        }

        val now = System.currentTimeMillis()
        val entity = ReportEntity(
            trackingCode = trackingCode,
            tempCode = tempCode,
            reportType = type.code,
            status = ReportStatus.PENDING.code,
            expertCode = settings.expertCode().ifBlank { null },
            reportDate = reportDate,
            createdAt = now,
            updatedAt = now,
            county = countyName,
            district = district?.trim()?.ifBlank { null },
            address = address?.trim()?.ifBlank { null },
            subscriptionNumber = subscription?.trim()?.ifBlank { null }
        )
        reports.insert(entity)
        return Result.success(entity.id)
    }

    /** Applies an edit and always refreshes `updated_at` so sync notices it. */
    suspend fun edit(id: String, mutate: (ReportEntity) -> ReportEntity): ReportEntity? {
        val current = reports.byId(id) ?: return null
        val updated = mutate(current).copy(updatedAt = System.currentTimeMillis())
        reports.update(updated)
        return updated
    }

    /**
     * Once the subscription number is known the final code replaces the
     * temporary one; the temporary code stays on the row as history.
     */
    suspend fun assignFinalCode(id: String): ReportEntity? {
        val current = reports.byId(id) ?: return null
        if (current.trackingCode != null) return current
        val type = ReportType.of(current.reportType)
        val areaCode = current.tempCode?.split(TrackingCode.SEPARATOR)?.getOrNull(1)
            ?: settings.defaultAreaCode()
        val generated = TrackingCode.generate(type, areaCode, current.reportDate, current.subscriptionNumber)
            ?: return current
        if (reports.countByTrackingCode(generated) > 0) return current
        val updated = current.copy(trackingCode = generated, updatedAt = System.currentTimeMillis())
        reports.update(updated)
        return updated
    }

    /** Returns the missing-item string resources; empty list means it moved. */
    suspend fun markVisited(id: String): List<Int> {
        val detail = detail(id) ?: return emptyList()
        val missing = Completion.missing(detail)
        if (missing.isNotEmpty()) return missing
        val now = System.currentTimeMillis()
        reports.update(
            detail.report.copy(
                status = ReportStatus.VISITED.code,
                visitDate = detail.report.visitDate ?: now,
                updatedAt = now
            )
        )
        return emptyList()
    }

    suspend fun archive(id: String) {
        edit(id) { it.copy(status = ReportStatus.ARCHIVED.code) }
    }

    suspend fun reopen(id: String) {
        edit(id) { it.copy(status = ReportStatus.PENDING.code) }
    }

    suspend fun delete(id: String) = reports.delete(id)

    suspend fun filter(
        status: ReportStatus?,
        county: String?,
        type: ReportType?,
        expert: String?,
        fromDate: Long?,
        toDate: Long?
    ): List<ReportEntity> = reports.filter(
        status?.code, county?.ifBlank { null }, type?.code, expert?.ifBlank { null }, fromDate, toDate
    )

    fun countAll(): Flow<Int> = reports.countAll()
    fun countPending(): Flow<Int> = reports.countByStatus(ReportStatus.PENDING.code)
    fun countVisited(): Flow<Int> = reports.countByStatus(ReportStatus.VISITED.code)
    fun countArchived(): Flow<Int> = reports.countByStatus(ReportStatus.ARCHIVED.code)
    fun countByType(): Flow<Map<Int, Int>> = reports.countByType().map { rows ->
        rows.associate { it.bucket to it.total }
    }
    fun countByCounty(): Flow<Map<String, Int>> = reports.countByCounty().map { rows ->
        rows.filter { it.bucket.isNotBlank() }.associate { it.bucket to it.total }
    }
    fun totalPower(): Flow<Double> = reports.totalDiscoveredPower()

    /** How long a pending case has been waiting, in whole days. */
    fun daysWaiting(report: ReportEntity): Int =
        PersianDate.daysBetween(report.reportDate, System.currentTimeMillis()).coerceAtLeast(0)

    private suspend fun nextTempSequence(): Int {
        val now = System.currentTimeMillis()
        return reports.countTempCodesInRange(PersianDate.startOfDay(now), PersianDate.endOfDay(now)) + 1
    }

    companion object {
        const val MISSING_MANUAL_CODE = "manual_tracking_code_required"
        const val DUPLICATE_CODE = "duplicate_tracking_code"
    }
}
