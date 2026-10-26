<?php
declare(strict_types=1);

/**
 * دو گام ورود فایل: پیش‌نمایش (چه برگه‌ها و ستون‌هایی پیدا شد، هر سطر با کدام سطر جور
 * شد یا تازه است، کدام خانه‌ها ناسازگار می‌شوند) و اجرا. فایل میان دو گام با یک نشانه
 * تصادفی در پوشه ذخیره می‌ماند و فقط همان کاربر اجرایش می‌کند؛ پس از اجرا پاک می‌شود.
 *
 * سطری که کلیدهایش با سطر برگه دیگری از همین فایل یکی است (فرم توانیر برگه «کلی» و
 * برگه سال را با هم دارد) «تکراری» است و وارد نمی‌شود؛ سطر تکراری در همان برگه وارد
 * می‌شود، چون یک مرکز می‌تواند دو سطر داشته باشد.
 */
final class CenterImportRun
{
    public static function stash(string $uploaded, array $user, int $year): string
    {
        $dir = self::dir();
        foreach (glob($dir . '/*') ?: [] as $old) {
            if (filemtime($old) < time() - 86400) {
                @unlink($old);
            }
        }
        $token = bin2hex(random_bytes(16));
        if (!@copy($uploaded, "$dir/$token.xlsx")) {
            throw new RuntimeException('cannot store');
        }
        file_put_contents("$dir/$token.json", json_encode(['user' => $user['id'], 'year' => $year]));
        return $token;
    }

    /** @return array{0:string, 1:int}|null مسیر فایل و سال پیش‌فرض، اگر نشانه مال همین کاربر است */
    public static function open(string $token, array $user): ?array
    {
        if (preg_match('/^[a-f0-9]{32}$/', $token) !== 1) {
            return null;
        }
        $meta = CenterValues::decode((string) @file_get_contents(self::dir() . "/$token.json"));
        $path = self::dir() . "/$token.xlsx";
        return ($meta['user'] ?? null) === $user['id'] && is_file($path) ? [$path, (int) $meta['year']] : null;
    }

    public static function discard(string $token): void
    {
        @unlink(self::dir() . "/$token.xlsx");
        @unlink(self::dir() . "/$token.json");
    }

    /**
     * برنامه ورود: هر سطر فایل با وضعیتش. @param list<string>|null $sheets برگه‌های انتخابی
     * @return array{sheets:list<array>, rows:list<array>, supplement:list<array>, summary:array<string,int>}
     */
    public static function plan(string $path, int $year, ?array $sheets): array
    {
        CenterRows::sync();
        $parsed = CenterImport::parse($path, $year);
        $years = [];
        foreach ($parsed as $sheet) {
            foreach ($sheet['rows'] as $row) {
                $years[$row['year']] = true;
            }
        }
        $index = self::index(array_keys($years));
        $locked = CenterLocks::years();
        $plan = [];
        $supplement = [];
        $seen = [];
        $taken = [];
        $info = [];
        foreach ($parsed as $sheet) {
            $selected = $sheet['kind'] !== 'none' && ($sheets === null || in_array($sheet['name'], $sheets, true));
            $info[] = ['name' => $sheet['name'], 'kind' => $sheet['kind'], 'rows' => count($sheet['rows']), 'selected' => $selected,
                'columns' => array_values($sheet['columns']), 'unknown' => $sheet['unknown'],
                'missing' => array_values(array_map([CenterColumns::class, 'label'],
                    array_diff(CenterColumns::templateKeys(), array_keys($sheet['columns']), ['row'])))];
            if (!$selected) {
                continue;
            }
            if ($sheet['kind'] === 'supplement') {
                array_push($supplement, ...$sheet['rows']);
                continue;
            }
            foreach ($sheet['rows'] as $row) {
                $keys = CenterMatch::keys($row['values']);
                $repeat = null;
                foreach ($keys as $key) {
                    if (isset($seen[$row['year'] . $key]) && $seen[$row['year'] . $key] !== $sheet['name']) {
                        $repeat = $seen[$row['year'] . $key];
                    }
                }
                foreach ($keys as $key) {
                    $seen[$row['year'] . $key] ??= $sheet['name'];
                }
                $target = null;
                foreach ($keys as $key) {
                    foreach ($index[$row['year'] . ':' . $key] ?? [] as $candidate) {
                        if (!isset($taken[$candidate['id']])) {
                            $target = $candidate;
                            break 2;
                        }
                    }
                }
                $status = match (true) {
                    $repeat !== null => 'repeat',
                    isset($locked[$row['year']]) => 'locked',
                    $target !== null => 'match',
                    default => 'new',
                };
                if ($status === 'match') {
                    $taken[$target['id']] = true;
                }
                $plan[] = $row + ['sheet' => $sheet['name'], 'status' => $status, 'repeat_of' => $repeat,
                    'target' => $status === 'match' ? $target['id'] : null,
                    'conflicts' => $status === 'match' ? self::conflicts($row['values'], $target['system']) : []];
            }
        }
        $summary = ['match' => 0, 'new' => 0, 'repeat' => 0, 'locked' => 0, 'conflicts' => 0, 'supplement' => count($supplement)];
        foreach ($plan as $row) {
            $summary[$row['status']]++;
            $summary['conflicts'] += count($row['conflicts']);
        }
        return ['sheets' => $info, 'rows' => $plan, 'supplement' => $supplement, 'summary' => $summary];
    }

    /** اجرای برنامه. @return array<string, int> شمار هر نتیجه */
    public static function apply(array $plan, array $user): array
    {
        $now = Db::now();
        $done = ['updated' => 0, 'created' => 0, 'supplement' => 0];
        foreach ($plan['rows'] as $row) {
            $json = CenterValues::encode($row['values']);
            if ($row['status'] === 'match') {
                $before = Db::one('SELECT import_json FROM center_rows WHERE id = ?', [$row['target']]);
                Db::run('UPDATE center_rows SET import_json = ?, updated_at = ? WHERE id = ?', [$json, $now, $row['target']]);
                foreach (self::diff(CenterValues::decode($before['import_json'] ?? null), $row['values']) as [$key, $old, $new]) {
                    CenterEdit::log($row['target'], $key, $old, $new, 'import', $user);
                }
                $done['updated']++;
            } elseif ($row['status'] === 'new') {
                $id = Db::uuid();
                Db::run(
                    'INSERT INTO center_rows (id, report_id, year, area_code, row_date, source, import_json, created_by, created_at, updated_at)
                     VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)',
                    [$id, $row['year'], $row['area'], $row['date'], CenterSchema::SOURCE_IMPORT, $json, $user['id'], $now, $now]
                );
                CenterEdit::log($id, '*', null, $row['sheet'] . ' / ' . $row['line'], 'import', $user);
                $done['created']++;
            }
        }
        foreach ($plan['supplement'] as $row) {
            if (!CenterLocks::locked($row['year']) && CenterSupplement::store($row['year'], $row['data'])) {
                $done['supplement']++;
            }
        }
        // پرونده‌ای که هنوز سطر نگرفته، حالا شاید با سطر تازه فایل جور شود.
        CenterRows::sync();
        return $done;
    }

    /** خانه‌هایی که فایل و سامانه هر دو دارند و یکی نیستند. @return list<array{key:string, label:string, file:mixed, system:mixed}> */
    private static function conflicts(array $file, array $system): array
    {
        $out = [];
        foreach ($file as $key => $value) {
            $column = CenterColumns::get($key);
            $sys = $system[$key] ?? null;
            if ($column !== null && $sys !== null && !CenterValues::same($column['type'], $sys, $value)) {
                $out[] = ['key' => $key, 'label' => CenterColumns::label($key),
                    'file' => CenterValues::display($column['type'], $value), 'system' => CenterValues::display($column['type'], $sys)];
            }
        }
        return $out;
    }

    /** @return array<string, list<array{id:string, system:array}>> «سال:کلید» → سطرهای موجود */
    private static function index(array $years): array
    {
        if ($years === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($years), '?'));
        $rows = Db::all("SELECT * FROM center_rows WHERE deleted_at IS NULL AND year IN ($in) ORDER BY created_at", $years);
        $index = [];
        foreach (CenterRows::build($rows, false, true) as $built) {
            foreach (CenterMatch::keys($built['values']) as $key) {
                $index[$built['year'] . ':' . $key][] = ['id' => $built['id'], 'system' => $built['system']];
            }
        }
        return $index;
    }

    /** @return list<array{0:string, 1:mixed, 2:mixed}> */
    private static function diff(array $before, array $after): array
    {
        $out = [];
        foreach (array_unique(array_merge(array_keys($before), array_keys($after))) as $key) {
            if (($before[$key] ?? null) != ($after[$key] ?? null)) {
                $out[] = [$key, $before[$key] ?? null, $after[$key] ?? null];
            }
        }
        return $out;
    }

    private static function dir(): string
    {
        $dir = Storage::root() . '/center-import';
        if (!is_dir($dir)) {
            @mkdir($dir, 0700, true);
        }
        return $dir;
    }
}
