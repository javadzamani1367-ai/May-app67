<?php
declare(strict_types=1);

/**
 * چگونگی ثبت یک موقعیت روی نقشه: کِی، و کاربر در آن لحظه کجا بود. برای اینکه
 * مدیر ببیند تابلو یا تیر ایستاده کنار خودش ثبت شده یا از دو خیابان آن‌طرف‌تر.
 * همان فاصله‌سنجی اپ (LocationRefiner.distanceMeters): برای چند صد متر دقیق است.
 */
final class PositionAudit
{
    private const EARTH_RADIUS = 6_371_000.0;

    public static function distance(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $meanLat = deg2rad(($lat1 + $lat2) / 2);
        $dx = deg2rad($lon2 - $lon1) * cos($meanLat);
        $dy = deg2rad($lat2 - $lat1);
        return sqrt($dx * $dx + $dy * $dy) * self::EARTH_RADIUS;
    }

    /**
     * فاصله کاربر از نقطه به متر، یا تهی اگر موقعیت دقیق کاربر ثبت نشده.
     */
    public static function observerDistance(mixed $lat, mixed $lon, mixed $observerLat, mixed $observerLon): ?float
    {
        if (!is_numeric($lat) || !is_numeric($lon) || !is_numeric($observerLat) || !is_numeric($observerLon)) {
            return null;
        }
        return self::distance((float) $lat, (float) $lon, (float) $observerLat, (float) $observerLon);
    }

    /**
     * یک سطر خوانا: «۱۴۰۵/۰۷/۲۳ - ۱۰:۲۲ · کاربر ۴ متر از نقطه (دقت ±۶ متر)».
     * بی زمان ثبت (داده پیش از این نسخه)، تهی.
     */
    public static function text(mixed $at, mixed $lat, mixed $lon, mixed $observerLat, mixed $observerLon,
                                mixed $observerAccuracy, string $who = 'کاربر'): ?string
    {
        if (!is_numeric($at) || (int) $at <= 0) {
            return null;
        }
        $when = Jalali::format((int) $at);
        $distance = self::observerDistance($lat, $lon, $observerLat, $observerLon);
        if ($distance === null) {
            return $when . ' · موقعیت دقیق ' . $who . ' در آن لحظه در دست نبود';
        }
        $accuracy = is_numeric($observerAccuracy) ? ' (دقت ±' . Jalali::digits((string) (int) round((float) $observerAccuracy)) . ' متر)' : '';
        return $when . ' · ' . $who . ' ' . Jalali::digits((string) (int) round($distance)) . ' متر از نقطه' . $accuracy;
    }
}
