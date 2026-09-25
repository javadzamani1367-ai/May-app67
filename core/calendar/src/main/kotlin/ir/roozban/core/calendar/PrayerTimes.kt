package ir.roozban.core.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

data class City(val id: String, val name: String, val latitude: Double, val longitude: Double)

/** Provincial capitals of Iran, for prayer times. */
object IranCities {
    val all = listOf(
        City("tehran", "تهران", 35.6892, 51.3890),
        City("mashhad", "مشهد", 36.2605, 59.6168),
        City("isfahan", "اصفهان", 32.6546, 51.6680),
        City("shiraz", "شیراز", 29.5918, 52.5837),
        City("tabriz", "تبریز", 38.0800, 46.2919),
        City("karaj", "کرج", 35.8400, 50.9391),
        City("ahvaz", "اهواز", 31.3183, 48.6706),
        City("qom", "قم", 34.6399, 50.8759),
        City("kermanshah", "کرمانشاه", 34.3142, 47.0650),
        City("urmia", "ارومیه", 37.5527, 45.0761),
        City("rasht", "رشت", 37.2808, 49.5832),
        City("zahedan", "زاهدان", 29.4963, 60.8629),
        City("kerman", "کرمان", 30.2839, 57.0834),
        City("hamedan", "همدان", 34.7992, 48.5146),
        City("yazd", "یزد", 31.8974, 54.3569),
        City("ardabil", "اردبیل", 38.2498, 48.2933),
        City("bandar_abbas", "بندرعباس", 27.1832, 56.2666),
        City("arak", "اراک", 34.0917, 49.6892),
        City("zanjan", "زنجان", 36.6736, 48.4787),
        City("sanandaj", "سنندج", 35.3219, 46.9862),
        City("qazvin", "قزوین", 36.2688, 50.0041),
        City("khorramabad", "خرم‌آباد", 33.4878, 48.3558),
        City("gorgan", "گرگان", 36.8456, 54.4393),
        City("sari", "ساری", 36.5633, 53.0601),
        City("bushehr", "بوشهر", 28.9234, 50.8203),
        City("birjand", "بیرجند", 32.8663, 59.2211),
        City("bojnurd", "بجنورد", 37.4747, 57.3290),
        City("ilam", "ایلام", 33.6374, 46.4227),
        City("shahrekord", "شهرکرد", 32.3256, 50.8644),
        City("yasuj", "یاسوج", 30.6682, 51.5880),
        City("semnan", "سمنان", 35.5729, 53.3971),
    )

    fun byId(id: String?): City? = all.firstOrNull { it.id == id }
}

data class PrayerDay(
    val imsak: LocalTime,
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val sunset: LocalTime,
    val maghrib: LocalTime,
    val midnight: LocalTime,
)

/**
 * Prayer times with the Tehran (Institute of Geophysics) method, as published in Iran:
 * Fajr 17.7°, Maghrib 4.5°, Isha 14°, midnight = middle of sunset..Fajr. Based on the
 * astronomical formulas of PrayTimes.org. Accuracy is about a minute.
 */
object PrayerTimes {
    private const val FAJR_ANGLE = 17.7
    private const val MAGHRIB_ANGLE = 4.5
    private const val SUN_ANGLE = 0.833
    private const val IMSAK_MINUTES = 10.0

    fun compute(date: LocalDate, city: City, zone: ZoneId = ZoneId.of("Asia/Tehran")): PrayerDay {
        val offsetHours = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - city.longitude / (15 * 24)
        val lat = city.latitude

        fun sun(time: Double): Pair<Double, Double> = sunPosition(jd + time / 24)
        fun midDay(time: Double): Double = fixHour(12 - sun(time).second)
        fun angleTime(angle: Double, time: Double, ccw: Boolean): Double {
            val decl = sun(time).first
            val noon = midDay(time)
            val cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            val t = darccos(cosT.coerceIn(-1.0, 1.0)) / 15
            return noon + if (ccw) -t else t
        }

        // First guesses as day portions, then one refinement, as in PrayTimes.
        val fajr = angleTime(FAJR_ANGLE, 5.0, ccw = true)
        val sunrise = angleTime(SUN_ANGLE, 6.0, ccw = true)
        val dhuhr = midDay(12.0)
        val sunset = angleTime(SUN_ANGLE, 18.0, ccw = false)
        val maghrib = angleTime(MAGHRIB_ANGLE, 18.0, ccw = false)

        val adjust = offsetHours - city.longitude / 15
        val f = fajr + adjust
        val sr = sunrise + adjust
        val dh = dhuhr + adjust
        val ss = sunset + adjust
        val mg = maghrib + adjust
        val midnight = ss + timeDiff(ss, f + 24) / 2
        return PrayerDay(
            imsak = toTime(f - IMSAK_MINUTES / 60),
            fajr = toTime(f),
            sunrise = toTime(sr),
            dhuhr = toTime(dh),
            sunset = toTime(ss),
            maghrib = toTime(mg),
            midnight = toTime(midnight),
        )
    }

    /** (declination, equation of time in hours). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15
        val eqt = q / 15 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun toTime(hours: Double): LocalTime {
        val minutes = (fixHour(hours) * 60).roundToLong() % (24 * 60)
        return LocalTime.of((minutes / 60).toInt(), (minutes % 60).toInt())
    }

    private fun timeDiff(a: Double, b: Double) = fixHour(b - a)
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }
}
