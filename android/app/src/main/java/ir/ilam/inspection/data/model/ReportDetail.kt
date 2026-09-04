package ir.ilam.inspection.data.model

import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.AttendeeEntity
import ir.ilam.inspection.data.db.DeviceEntity
import ir.ilam.inspection.data.db.DispatchEntity
import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity

/** A whole case with its children — what the exporters and the sync API need. */
data class ReportDetail(
    val report: ReportEntity,
    val devices: List<DeviceEntity> = emptyList(),
    val attendees: List<AttendeeEntity> = emptyList(),
    val media: List<MediaEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val dispatches: List<DispatchEntity> = emptyList()
) {
    val photos: List<MediaEntity> get() = media.filter { it.type == MediaType.IMAGE.code }
    val videos: List<MediaEntity> get() = media.filter { it.type == MediaType.VIDEO.code }
    val totalPower: Double get() = devices.sumOf { it.powerWatt ?: 0.0 }
    val deviceCount: Int get() = devices.size
}
