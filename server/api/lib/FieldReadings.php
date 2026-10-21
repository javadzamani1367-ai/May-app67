<?php
declare(strict_types=1);

/**
 * خوانش‌های بازرسی شبکه، هر کدام یک سطر: آمپر سه فاز کلید کل و هر فیدر تابلو، هر تیر
 * تیربه‌تیر، و دماهای هر تصویر ترموویژن. سوابق و مقایسه (FieldHistory) از همین جدول
 * ساخته می‌شود، با ناحیه و پلاک یکسان‌شده — پلاک در هر ناحیه یکتاست.
 *
 * سطرهای هر مورد هر بار از نو ساخته می‌شوند (rebuild): payload منبع است و جدول فقط
 * نمایه آن. مورد ردشده یا ادغام‌شده سطرهایش را نگه می‌دارد ولی در سوابق نمی‌آید
 * (FieldHistory::VALID)، تا برگرداندن تصمیم چیزی را گم نکند.
 */
final class FieldReadings
{
    public const ASSET_POLE = 0;
    public const ASSET_PANEL = 1;

    public const PART_MAIN = 'main';
    public const PART_POLE = 'pole';
    public const PART_THERMAL = 'thermal';
    public const FEEDER_PARTS = ['A', 'B', 'C', 'D'];

    public const TABLES = [
        "CREATE TABLE IF NOT EXISTS field_readings (
           id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
           item_id      CHAR(36)     NOT NULL,
           kind         TINYINT      NOT NULL,
           area_code    VARCHAR(8)   NULL,
           asset        TINYINT      NOT NULL,
           plate        VARCHAR(64)  NOT NULL,
           plate_key    VARCHAR(64)  NOT NULL,
           part         VARCHAR(8)   NOT NULL,
           file_id      CHAR(36)     NULL,
           r            DOUBLE       NULL,
           s            DOUBLE       NULL,
           t            DOUBLE       NULL,
           n            DOUBLE       NULL,
           temp_max     DOUBLE       NULL,
           temp_box_max DOUBLE       NULL,
           temp_box_avg DOUBLE       NULL,
           measured_at  BIGINT       NOT NULL,
           user_id      CHAR(36)     NOT NULL,
           anomaly      TINYINT      NOT NULL DEFAULT 0,
           anomaly_note VARCHAR(255) NULL,
           created_at   BIGINT       NOT NULL,
           KEY idx_field_readings_key (area_code, asset, plate_key, part, measured_at),
           KEY idx_field_readings_item (item_id),
           KEY idx_field_readings_created (created_at)
         ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    /** زمان نخستین افزایش نامتعارف مورد؛ نشان فهرست و هشدار یک‌باره از همین است. */
    public const COLUMNS = [['field_items', 'anomaly_at', 'BIGINT NULL']];
    public const INDEXES = [['field_items', 'idx_field_items_anomaly', 'anomaly_at']];

    /** پلاک برای مقایسه: رقم لاتین، حرف بزرگ، بی فاصله و خط تیره. «۱۲-۳۴۵» و «12345» یکی‌اند. */
    public static function key(?string $plate): string
    {
        $latin = strtoupper(Jalali::latin(trim((string) $plate)));
        return substr((string) preg_replace('/[^A-Z0-9]/', '', $latin), 0, 64);
    }

    /**
     * خوانش‌های یک مورد، از payload و فایل‌هایش؛ بی شناسه و بی نشان نامتعارف.
     *
     * @param array<string, mixed> $item سطر field_items
     * @param list<array<string, mixed>> $files سطرهای field_files همان مورد
     * @return list<array<string, mixed>>
     */
    public static function extract(array $item, array $files = []): array
    {
        $kind = (int) $item['kind'];
        if (!in_array($kind, Field::INSPECTION_KINDS, true)) {
            return [];
        }
        $payload = json_decode((string) ($item['payload'] ?? ''), true);
        $payload = is_array($payload) ? $payload : [];
        $at = (int) ($item['position_at'] ?? 0) ?: (int) $item['created_at'];
        $plate = trim((string) ($item['plate'] ?? ''));
        $out = [];
        $add = static function (int $asset, string $plate, string $part, int $at, array $values) use (&$out): void {
            if (self::key($plate) !== '') {
                $out[] = ['asset' => $asset, 'plate' => mb_substr($plate, 0, 64), 'plate_key' => self::key($plate),
                    'part' => $part, 'measured_at' => $at] + $values + ['file_id' => null];
            }
        };
        if ($kind === Field::KIND_FEEDER) {
            $main = self::phases($payload['main'] ?? null);
            if ($main !== null) {
                $add(self::ASSET_PANEL, $plate, self::PART_MAIN, $at, $main);
            }
            foreach (self::FEEDER_PARTS as $letter) {
                $feeder = (array) ($payload['feeders'][$letter] ?? []);
                $phases = ($feeder['state'] ?? '') === 'measured' ? self::phases($feeder['phases'] ?? null) : null;
                if ($phases !== null) {
                    $add(self::ASSET_PANEL, $plate, $letter, $at, $phases);
                }
            }
        } elseif ($kind === Field::KIND_LINE) {
            // ابتدای فیدر در تابلو، همان فیدر A تا D تابلو است؛ «سایر» جایی در تابلو ندارد.
            $start = self::phases($payload['start'] ?? null);
            if ($start !== null && in_array($payload['feeder'] ?? '', self::FEEDER_PARTS, true)) {
                $add(self::ASSET_PANEL, $plate, (string) $payload['feeder'], $at, $start);
            }
            foreach ((array) ($payload['poles'] ?? []) as $pole) {
                $pole = (array) $pole;
                $phases = ($pole['state'] ?? 'measured') === 'measured' ? self::phases($pole['phases'] ?? null) : null;
                if ($phases !== null) {
                    $phases['n'] = self::number($pole['neutral'] ?? null);
                    $add(self::ASSET_POLE, (string) ($pole['plate'] ?? ''), self::PART_POLE, (int) ($pole['at'] ?? 0) ?: $at, $phases);
                }
            }
        } else {
            // ترموویژن: هر تصویر اصلی که به تیر یا تابلویی داده شده، با دماهایش اگر وارد شده.
            foreach ($files as $file) {
                if ((int) $file['role'] !== Field::ROLE_THERMAL || $file['asset_type'] === null) {
                    continue;
                }
                $temps = (array) ($payload['files'][$file['id']]['temperatures'] ?? []);
                $add((int) $file['asset_type'] === Field::ASSET_PANEL ? self::ASSET_PANEL : self::ASSET_POLE,
                    (string) ($file['plate'] ?? ''), self::PART_THERMAL, (int) ($file['captured_at'] ?? 0) ?: $at, [
                        'temp_max' => self::number($temps['max'] ?? null),
                        'temp_box_max' => self::number($temps['r1_max'] ?? null),
                        'temp_box_avg' => self::number($temps['r1_avg'] ?? null),
                        'file_id' => (string) $file['id'],
                    ]);
            }
        }
        return $out;
    }

    /** سطرهای یک مورد را از نو می‌سازد و افزایش نامتعارف را می‌سنجد؛ [$notify] برای مورد تازه. */
    public static function rebuild(string $itemId, bool $notify = true): void
    {
        $item = Db::one('SELECT * FROM field_items WHERE id = ?', [$itemId]);
        if ($item === null) {
            return;
        }
        $files = Db::all('SELECT id, role, asset_type, plate, captured_at FROM field_files WHERE item_id = ?', [$itemId]);
        Db::run('DELETE FROM field_readings WHERE item_id = ?', [$itemId]);
        $now = Db::now();
        foreach (self::extract($item, $files) as $r) {
            Db::run(
                'INSERT INTO field_readings (item_id, kind, area_code, asset, plate, plate_key, part, file_id, r, s, t, n,
                                             temp_max, temp_box_max, temp_box_avg, measured_at, user_id, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
                [$itemId, (int) $item['kind'], $item['area_code'], $r['asset'], $r['plate'], $r['plate_key'], $r['part'],
                 $r['file_id'], $r['r'] ?? null, $r['s'] ?? null, $r['t'] ?? null, $r['n'] ?? null, $r['temp_max'] ?? null,
                 $r['temp_box_max'] ?? null, $r['temp_box_avg'] ?? null, $r['measured_at'], $item['user_id'], $now]
            );
        }
        FieldHistory::mark($item, $notify);
    }

    /** گام ۱۰: همه بازرسی‌های گذشته، بی هشدار — سوابق از همان روز اول پر است. */
    public static function backfill(): void
    {
        $ids = Db::all('SELECT id FROM field_items WHERE kind IN (?, ?, ?) ORDER BY created_at', Field::INSPECTION_KINDS);
        foreach ($ids as $row) {
            self::rebuild((string) $row['id'], false);
        }
    }

    /** @return array{r: ?float, s: ?float, t: ?float}|null تهی وقتی هیچ فازی عدد ندارد */
    private static function phases(mixed $json): ?array
    {
        $json = is_array($json) ? $json : [];
        $values = ['r' => self::number($json['r'] ?? null), 's' => self::number($json['s'] ?? null), 't' => self::number($json['t'] ?? null)];
        return array_filter($values, static fn ($v) => $v !== null) === [] ? null : $values;
    }

    private static function number(mixed $value): ?float
    {
        return is_numeric($value) ? (float) $value : null;
    }
}
