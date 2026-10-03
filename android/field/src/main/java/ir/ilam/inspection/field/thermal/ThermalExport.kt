package ir.ilam.inspection.field.thermal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One thermal file as it goes into an export. */
data class ExportFile(
    val id: String,
    val name: String,
    val video: Boolean,
    val takenAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double?,
    val uncertain: Boolean,
    /** Pole or panel, already in words. */
    val assetLabel: String,
    val plate: String,
    val note: String,
    val sha256: String,
    val temperatures: Temperatures
)

/**
 * KML for Google Earth (the route as a line, each file a pin) and CSV for a
 * spreadsheet. Plain text built by hand: both formats are simple enough that a
 * library would only add weight. Times are ISO 8601 in UTC, which both tools
 * understand; the Jalali date is the app's to show, not the file's.
 */
object ThermalExport {

    fun kml(title: String, track: List<TrackPoint>, files: List<ExportFile>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
        append("<name>").append(xml(title)).append("</name>\n")
        if (track.isNotEmpty()) {
            append("<Placemark><name>").append(xml(title)).append("</name><LineString><tessellate>1</tessellate><coordinates>\n")
            track.forEach { append(it.longitude).append(',').append(it.latitude).append(",0 ") }
            append("\n</coordinates></LineString></Placemark>\n")
        }
        files.filter { it.latitude != null && it.longitude != null }.forEach { f ->
            append("<Placemark><name>").append(xml(listOf(f.assetLabel, f.plate).filter { it.isNotBlank() }.joinToString(" ")))
            append("</name><TimeStamp><when>").append(iso(f.takenAt)).append("</when></TimeStamp>")
            append("<description>").append(xml(listOf(f.name, f.note).filter { it.isNotBlank() }.joinToString(" — ")))
            append("</description><Point><coordinates>").append(f.longitude).append(',').append(f.latitude)
            append(",0</coordinates></Point></Placemark>\n")
        }
        append("</Document></kml>\n")
    }

    fun csv(files: List<ExportFile>): String = buildString {
        // A byte-order mark, so Excel opens the Persian text as UTF-8.
        append('﻿')
        append("file,kind,taken_at_utc,latitude,longitude,accuracy_m,location_uncertain,asset,plate,note,")
        append("max_c,min_c,r1_max_c,r1_min_c,r1_avg_c,sha256\n")
        files.forEach { f ->
            listOf(
                f.name, if (f.video) "video" else "photo", iso(f.takenAt),
                f.latitude?.toString().orEmpty(), f.longitude?.toString().orEmpty(), f.accuracy?.toString().orEmpty(),
                if (f.uncertain) "1" else "0", f.assetLabel, f.plate, f.note,
                f.temperatures.max, f.temperatures.min, f.temperatures.boxMax, f.temperatures.boxMin, f.temperatures.boxAvg,
                f.sha256
            ).joinTo(this, ",") { cell(it) }
            append('\n')
        }
    }

    fun iso(millis: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun cell(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
