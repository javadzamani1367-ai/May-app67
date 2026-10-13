<?php
declare(strict_types=1);

/**
 * موردهای احتمالاً تکراری و ادغامشان.
 *
 * دو گزارش (رمزارز یا برق غیرمجاز) تکراری‌اند اگر از یک نوع باشند، کمتر از
 * ۱۵۰ متر از هم و با فاصله کمتر از ۳۰ روز ثبت شده باشند: دو همکار که یک محل
 * را جدا گزارش کرده‌اند. دو بازرسی (ترموویژن یا آمپرگیری) فقط وقتی تکراری‌اند
 * که پلاکشان یکی و فاصله‌شان کمتر از ۳ روز باشد — اندازه‌گیری دوباره یک تابلو
 * در ماه بعد، کار عادی است و تکرار نیست.
 *
 * ادغام چیزی را پاک نمی‌کند: مورد تکراری بسته می‌شود و merged_into به مورد
 * اصلی اشاره می‌کند؛ فایل‌ها و تاریخچه‌اش سر جایشان می‌مانند و ادغام برگشت‌پذیر است.
 */
final class FieldDuplicates
{
    public const REPORT_METRES = 150.0;
    public const REPORT_DAYS = 30;
    public const INSPECTION_DAYS = 3;

    private const REPORT_KINDS = Field::REPORT_KINDS;
    /** کادر جستجو در درجه؛ کمی بزرگ‌تر از ۱۵۰ متر در عرض ایلام، فاصله دقیق بعد حساب می‌شود. */
    private const BOX_LAT = 0.0015;
    private const BOX_LON = 0.0019;
    private const CLOSED = [Field::STATUS_CLOSED, Field::STATUS_REJECTED];

    /** فاصله دو نقطه به متر، تخت؛ برای چند صد متر کافی است. */
    public static function metres(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $k = 111_320.0;
        $dy = ($lat1 - $lat2) * $k;
        $dx = ($lon1 - $lon2) * $k * cos(deg2rad(($lat1 + $lat2) / 2));
        return sqrt($dx * $dx + $dy * $dy);
    }

    /**
     * جفت‌های احتمالاً تکراری میان موردهای باز. هر جفت یک بار، قدیمی‌تر اول.
     *
     * @return array<int, array<string, mixed>>
     */
    public static function pairs(int $limit = 200): array
    {
        $closed = implode(',', self::CLOSED);
        $reports = implode(',', self::REPORT_KINDS);
        $rows = Db::all(
            "SELECT a.id AS a_id, a.tracking_code AS a_code, a.created_at AS a_at, a.latitude AS a_lat, a.longitude AS a_lon,
                    b.id AS b_id, b.tracking_code AS b_code, b.created_at AS b_at, b.latitude AS b_lat, b.longitude AS b_lon,
                    a.kind, a.plate, a.address
             FROM field_items a JOIN field_items b
               ON b.kind = a.kind AND b.merged_into IS NULL AND b.status NOT IN ($closed)
              AND (b.created_at > a.created_at OR (b.created_at = a.created_at AND b.id > a.id))
              AND (
                (a.kind IN ($reports) AND b.created_at - a.created_at <= ?
                 AND b.latitude BETWEEN a.latitude - ? AND a.latitude + ?
                 AND b.longitude BETWEEN a.longitude - ? AND a.longitude + ?)
                OR (a.kind NOT IN ($reports) AND b.created_at - a.created_at <= ?
                 AND a.plate IS NOT NULL AND a.plate <> '' AND b.plate = a.plate)
              )
             WHERE a.merged_into IS NULL AND a.status NOT IN ($closed)
             ORDER BY b.created_at DESC LIMIT " . ($limit * 3),
            [self::REPORT_DAYS * 86_400_000, self::BOX_LAT, self::BOX_LAT, self::BOX_LON, self::BOX_LON,
             self::INSPECTION_DAYS * 86_400_000]
        );
        $out = [];
        foreach ($rows as $row) {
            $row['metres'] = self::distance($row['a_lat'], $row['a_lon'], $row['b_lat'], $row['b_lon']);
            if (in_array((int) $row['kind'], self::REPORT_KINDS, true) && $row['metres'] > self::REPORT_METRES) {
                continue;
            }
            $out[] = $row;
            if (count($out) >= $limit) {
                break;
            }
        }
        return $out;
    }

    /**
     * موردهایی که این یکی احتمالاً تکرارشان است (یا برعکس).
     *
     * @return array<int, array<string, mixed>>
     */
    public static function candidatesFor(array $item): array
    {
        if ($item['merged_into'] !== null) {
            return [];
        }
        $kind = (int) $item['kind'];
        $closed = implode(',', self::CLOSED);
        if (in_array($kind, self::REPORT_KINDS, true)) {
            if ($item['latitude'] === null || $item['longitude'] === null) {
                return [];
            }
            $lat = (float) $item['latitude'];
            $lon = (float) $item['longitude'];
            $rows = Db::all(
                "SELECT * FROM field_items WHERE kind = ? AND id <> ? AND merged_into IS NULL AND status NOT IN ($closed)
                 AND ABS(created_at - ?) <= ? AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
                 ORDER BY created_at LIMIT 50",
                [$kind, $item['id'], (int) $item['created_at'], self::REPORT_DAYS * 86_400_000,
                 $lat - self::BOX_LAT, $lat + self::BOX_LAT, $lon - self::BOX_LON, $lon + self::BOX_LON]
            );
        } else {
            if (trim((string) $item['plate']) === '') {
                return [];
            }
            $rows = Db::all(
                "SELECT * FROM field_items WHERE kind = ? AND id <> ? AND merged_into IS NULL AND status NOT IN ($closed)
                 AND ABS(created_at - ?) <= ? AND plate = ? ORDER BY created_at LIMIT 50",
                [$kind, $item['id'], (int) $item['created_at'], self::INSPECTION_DAYS * 86_400_000, $item['plate']]
            );
        }
        $out = [];
        foreach ($rows as $row) {
            $row['metres'] = self::distance($item['latitude'], $item['longitude'], $row['latitude'], $row['longitude']);
            if (in_array($kind, self::REPORT_KINDS, true) && $row['metres'] > self::REPORT_METRES) {
                continue;
            }
            $out[] = $row;
        }
        return $out;
    }

    /** موردهایی که در این یکی ادغام شده‌اند. */
    public static function mergedInto(string $id): array
    {
        return Db::all('SELECT id, tracking_code, created_at FROM field_items WHERE merged_into = ? ORDER BY created_at', [$id]);
    }

    /** مورد $source را در $target ادغام می‌کند. خطا به فارسی، یا تهی. */
    public static function merge(array $source, string $targetId, array $manager, string $note): ?string
    {
        $target = Field::isUuid($targetId) ? Db::one('SELECT * FROM field_items WHERE id = ?', [$targetId]) : null;
        if ($target === null || $target['id'] === $source['id']) {
            return 'مورد مقصد ادغام پیدا نشد.';
        }
        if ((int) $target['kind'] !== (int) $source['kind']) {
            return 'فقط دو مورد از یک نوع ادغام می‌شوند.';
        }
        if ($source['merged_into'] !== null || $target['merged_into'] !== null) {
            return 'یکی از این دو مورد قبلاً در مورد دیگری ادغام شده است.';
        }
        $now = Db::now();
        $note = mb_substr(trim($note), 0, 2000);
        $pdo = Db::conn();
        $pdo->beginTransaction();
        try {
            // مورد مقصد هم ممکن است خودش مقصد ادغام‌های قبلی این مورد باشد؛ آن‌ها به مقصد تازه می‌روند.
            Db::run('UPDATE field_items SET merged_into = ?, updated_at = ? WHERE merged_into = ?',
                [$target['id'], $now, $source['id']]);
            Db::run('UPDATE field_items SET merged_into = ?, status = ?, updated_at = ? WHERE id = ?',
                [$target['id'], Field::STATUS_CLOSED, $now, $source['id']]);
            Db::run('UPDATE field_items SET updated_at = ? WHERE id = ?', [$now, $target['id']]);
            Field::event((string) $source['id'], (string) $manager['id'], 'merged', (int) $source['status'],
                Field::STATUS_CLOSED, trim('ادغام در ' . $target['tracking_code'] . "\n" . $note));
            Field::event((string) $target['id'], (string) $manager['id'], 'merged', null, null,
                trim((string) $source['tracking_code'] . ' در این مورد ادغام شد' . "\n" . $note));
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
        return null;
    }

    /** ادغام اشتباه را برمی‌گرداند: مورد دوباره باز و «در حال بررسی» می‌شود. */
    public static function unmerge(array $source, array $manager): ?string
    {
        if ($source['merged_into'] === null) {
            return 'این مورد ادغام نشده است.';
        }
        $target = Db::one('SELECT id, tracking_code FROM field_items WHERE id = ?', [$source['merged_into']]);
        $now = Db::now();
        Db::run('UPDATE field_items SET merged_into = NULL, status = ?, updated_at = ? WHERE id = ?',
            [Field::STATUS_REVIEWING, $now, $source['id']]);
        Field::event((string) $source['id'], (string) $manager['id'], 'unmerged', (int) $source['status'],
            Field::STATUS_REVIEWING, 'جدا شد از ' . ($target['tracking_code'] ?? '—'));
        if ($target !== null) {
            Field::event((string) $target['id'], (string) $manager['id'], 'unmerged', null, null,
                (string) $source['tracking_code'] . ' از این مورد جدا شد');
        }
        return null;
    }

    private static function distance(mixed $lat1, mixed $lon1, mixed $lat2, mixed $lon2): ?float
    {
        if ($lat1 === null || $lon1 === null || $lat2 === null || $lon2 === null) {
            return null;
        }
        return self::metres((float) $lat1, (float) $lon1, (float) $lat2, (float) $lon2);
    }
}
