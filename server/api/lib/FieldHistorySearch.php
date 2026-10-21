<?php
declare(strict_types=1);

require_once __DIR__ . '/FieldHistory.php';

/**
 * جستجوی سوابق برای مدیر و مجری‌ها، و بسته سوابق برای گوشی کاربر میدانی. دامنه دید
 * مدیر و مجری‌ها همان Scope::fieldWhere است؛ کاربر میدانی ناحیه‌های کاربری خودش را دارد.
 */
final class FieldHistorySearch
{
    /** کلیدهای هر صفحه بسته گوشی؛ هر کلید آخرین [PHONE_ROWS] خوانشش را دارد. */
    public const PAGE_KEYS = 300;
    public const PHONE_ROWS = 20;
    /** تصاویر ترموویژن که گوشی از هر پلاک نگه می‌دارد. */
    public const PHONE_IMAGES = 3;

    /**
     * هر کلید (تابلو یا تیر و بخش) با شمار خوانش‌ها، آخرین زمان، شمار نامتعارف‌ها و آخرین خوانش.
     *
     * @param array{area?: ?string, asset?: ?int, plate?: string, kind?: ?int, from?: ?int, to?: ?int, anomaly?: bool} $f
     * @return list<array<string, mixed>>
     */
    public static function search(Scope $scope, array $f, int $limit = 200): array
    {
        [$where, $params] = $scope->fieldWhere('i');
        $sql = 'SELECT h.area_code, h.asset, h.plate_key, MAX(h.plate) AS plate, h.part, COUNT(*) AS n,
                       MAX(h.measured_at) AS last_at, MIN(h.measured_at) AS first_at, SUM(h.anomaly) AS anomalies
                FROM field_readings h JOIN field_items i ON i.id = h.item_id
                WHERE ' . $where . ' AND ' . FieldHistory::VALID;
        if (($f['area'] ?? null) !== null) {
            $sql .= ' AND h.area_code = ?';
            $params[] = $f['area'];
        }
        if (($f['asset'] ?? null) !== null) {
            $sql .= ' AND h.asset = ?';
            $params[] = (int) $f['asset'];
        }
        if (FieldReadings::key($f['plate'] ?? '') !== '') {
            $sql .= ' AND h.plate_key LIKE ?';
            $params[] = '%' . FieldReadings::key($f['plate']) . '%';
        }
        if (($f['kind'] ?? null) !== null) {
            $sql .= ' AND h.kind = ?';
            $params[] = (int) $f['kind'];
        }
        foreach (['from' => '>=', 'to' => '<='] as $key => $op) {
            if (($f[$key] ?? null) !== null) {
                $sql .= " AND h.measured_at $op ?";
                $params[] = (int) $f[$key];
            }
        }
        $sql .= ' GROUP BY h.area_code, h.asset, h.plate_key, h.part'
            . (!empty($f['anomaly']) ? ' HAVING SUM(h.anomaly) > 0' : '')
            . ' ORDER BY last_at DESC LIMIT ' . max(1, min(500, $limit));
        return array_map(static function (array $k): array {
            $last = FieldHistory::series($k['area_code'], (int) $k['asset'], (string) $k['plate_key'], (string) $k['part'], null, 1)[0] ?? null;
            return ['area' => $k['area_code'], 'area_label' => $k['area_code'] === null ? '—' : Areas::label((string) $k['area_code']),
                'asset' => (int) $k['asset'], 'plate' => (string) $k['plate'], 'plate_key' => (string) $k['plate_key'],
                'part' => (string) $k['part'], 'label' => FieldHistory::label((int) $k['asset'], (string) $k['plate'], (string) $k['part']),
                'count' => (int) $k['n'], 'first_at' => (int) $k['first_at'], 'last_at' => (int) $k['last_at'],
                'anomalies' => (int) $k['anomalies'], 'last' => $last];
        }, Db::all($sql, $params));
    }

    /** آیا این کاربر این کلید را می‌بیند: همان دامنه موردهای میدانی‌اش. */
    public static function canSeeKey(Scope $scope, ?string $area, int $asset, string $plateKey, string $part): bool
    {
        [$where, $params] = $scope->fieldWhere('i');
        return Db::one(
            'SELECT 1 AS ok FROM field_readings h JOIN field_items i ON i.id = h.item_id
             WHERE (h.area_code <=> ?) AND h.asset = ? AND h.plate_key = ? AND h.part = ? AND ' . $where . ' LIMIT 1',
            array_merge([$area, $asset, $plateKey, $part], $params)
        ) !== null;
    }

    /** ناحیه‌های کاربر میدانی؛ کاربری بی‌ناحیه (پیش از ناحیه‌ها) همه استان را می‌بیند. */
    public static function phoneAreas(array $user): array
    {
        return Areas::parse($user['areas'] ?? '');
    }

    /**
     * سوابق یک پلاک برای گوشی: بی نام ثبت‌کننده. [$asset] تهی یعنی هر دو.
     *
     * @return list<array<string, mixed>>
     */
    public static function forPhone(array $user, ?int $asset, string $plate, ?string $part): array
    {
        $key = FieldReadings::key($plate);
        if ($key === '') {
            return [];
        }
        $areas = self::phoneAreas($user);
        $rows = Db::all(
            'SELECT h.* FROM field_readings h JOIN field_items i ON i.id = h.item_id WHERE h.plate_key = ? AND ' . FieldHistory::VALID
                . ($areas === [] ? '' : ' AND h.area_code IN (' . implode(',', array_fill(0, count($areas), '?')) . ')')
                . ($asset === null ? '' : ' AND h.asset = ' . (int) $asset)
                . ($part === null ? '' : ' AND h.part = ?')
                . ' ORDER BY h.measured_at DESC, h.id DESC LIMIT 200',
            array_merge([$key], $areas, $part === null ? [] : [$part])
        );
        return array_map(static fn (array $r) => FieldHistory::present($r, false), $rows);
    }

    /**
     * بسته سوابق ناحیه کاربر برای کار بی‌اینترنت: کلیدهایی که از [$since] تغییر کرده‌اند
     * (خوانش تازه، یا موردی که رد یا ادغام شد)، هر کدام با آخرین خوانش‌هایش. کلیدی که
     * دیگر خوانش معتبری ندارد با فهرست خالی می‌آید تا گوشی پاکش کند. صفحه‌به‌صفحه با [$after].
     *
     * @return array{keys: list<array<string, mixed>>, next: ?string, server_time: int}
     */
    public static function bundle(array $user, int $since, string $after): array
    {
        $now = Db::now();
        $areas = self::phoneAreas($user);
        $k = "CONCAT_WS('|', IFNULL(h.area_code, ''), h.asset, h.plate_key, h.part)";
        $keys = Db::all(
            "SELECT DISTINCT h.area_code, h.asset, h.plate_key, h.part, $k AS k FROM field_readings h
             JOIN field_items i ON i.id = h.item_id
             WHERE (h.created_at > ? OR i.updated_at > ?) AND $k > ?"
                . ($areas === [] ? '' : ' AND h.area_code IN (' . implode(',', array_fill(0, count($areas), '?')) . ')')
                . ' ORDER BY k LIMIT ' . (self::PAGE_KEYS + 1),
            array_merge([$since, $since, $after], $areas)
        );
        $more = count($keys) > self::PAGE_KEYS;
        $keys = array_slice($keys, 0, self::PAGE_KEYS);
        $out = [];
        foreach ($keys as $key) {
            $rows = FieldHistory::series($key['area_code'], (int) $key['asset'], (string) $key['plate_key'], (string) $key['part'],
                null, self::PHONE_ROWS);
            $out[] = ['area' => $key['area_code'], 'asset' => (int) $key['asset'], 'plate_key' => (string) $key['plate_key'],
                'part' => (string) $key['part'], 'plate' => (string) ($rows[0]['plate'] ?? $key['plate_key']), 'rows' => $rows];
        }
        return ['keys' => $out, 'next' => $more ? (string) end($keys)['k'] : null, 'server_time' => $now];
    }

    /** تصویر ترموویژن یک سابقه، اگر در ناحیه کاربر و معتبر است. */
    public static function phoneImage(array $user, string $fileId): ?string
    {
        if (!Field::isUuid($fileId)) {
            return null;
        }
        $areas = self::phoneAreas($user);
        $row = Db::one(
            'SELECT f.path FROM field_files f JOIN field_readings h ON h.file_id = f.id JOIN field_items i ON i.id = h.item_id
             WHERE f.id = ? AND f.complete = 1 AND f.role = ? AND f.mime LIKE \'image/%\' AND ' . FieldHistory::VALID
                . ($areas === [] ? '' : ' AND h.area_code IN (' . implode(',', array_fill(0, count($areas), '?')) . ')') . ' LIMIT 1',
            array_merge([$fileId, Field::ROLE_THERMAL], $areas)
        );
        $path = $row === null || $row['path'] === null ? null : Storage::resolve((string) $row['path']);
        return $path !== null && is_file($path) ? $path : null;
    }
}
