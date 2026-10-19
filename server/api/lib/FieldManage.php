<?php
declare(strict_types=1);

/**
 * موردهای میدانی برای اپ مدیر: همان فهرست، فیلتر و چرخه وضعیت پنل وب
 * (FieldAdmin)، به شکل JSON. مدیر همه موردها را می‌بیند، نام گزارش‌دهنده
 * را هم؛ تغییر وضعیت فقط از FieldAdmin::transition می‌گذرد تا قاعده‌ها یکی بمانند.
 */
final class FieldManage
{
    public const PAGE_SIZE = 50;

    public const EVENT_NAMES = ['registered' => 'ثبت از گوشی', 'status' => 'تغییر وضعیت', 'merged' => 'ادغام',
        'unmerged' => 'جدا شدن از ادغام', 'accepted' => 'پذیرش کارشناس', 'result' => 'نتیجه ارجاع',
        'handed' => 'سپردن به مجری', 'followup' => 'پیگیری', 'expert_alert' => 'هشدار: کارشناس اقدام نکرده',
        'manager_alert' => 'هشدار به مدیر'];

    /** وضعیت‌هایی که بی‌دلیل ثبت نمی‌شوند؛ اپ همان‌جا توضیح را اجباری نشان می‌دهد. */
    private const NEED_NOTE = [Field::STATUS_REJECTED, Field::STATUS_REVISIT];

    /**
     * یک صفحه از فهرست، با شمار هر وضعیت برای تب‌ها (بی فیلتر وضعیت).
     *
     * @param array<string, mixed> $get
     */
    public static function list(array $get, int $page, ?Scope $scope = null): array
    {
        $filter = self::scoped(FieldAdmin::filters($get), $scope);
        $page = max(0, $page);
        $rows = Db::all(FieldAdmin::listSql($filter['where']) . ' LIMIT ' . (self::PAGE_SIZE + 1)
            . ' OFFSET ' . ($page * self::PAGE_SIZE), $filter['params']);
        $more = count($rows) > self::PAGE_SIZE;
        $rows = array_slice($rows, 0, self::PAGE_SIZE);

        $countGet = $get;
        unset($countGet['status']);
        $all = self::scoped(FieldAdmin::filters($countGet), $scope);
        $counts = [];
        foreach (Db::all('SELECT i.status AS k, COUNT(*) AS n FROM field_items i WHERE ' . $all['where']
            . ' GROUP BY i.status', $all['params']) as $row) {
            $counts[(string) (int) $row['k']] = (int) $row['n'];
        }
        $now = Db::now();
        return [
            'items' => array_map(static fn (array $row): array => self::summary($row, $now), $rows),
            'more' => $more,
            'counts' => (object) $counts,
            'overdue' => (int) (Db::one('SELECT COUNT(*) AS n FROM field_items i WHERE i.merged_into IS NULL AND '
                . FieldAdmin::OVERDUE_SQL . ' AND ' . $all['scope'], array_merge([$now], $all['scope_params']))['n'] ?? 0),
        ];
    }

    /** فیلتر فهرست، تنگ‌شده به محدوده کاربر (مجری: ناحیه‌اش بی رمزارز؛ غیرمجاز: فقط غیرمجاز). */
    public static function scoped(array $filter, ?Scope $scope): array
    {
        [$where, $params] = $scope?->fieldWhere('i') ?? ['1=1', []];
        $filter['where'] .= ' AND ' . $where;
        $filter['params'] = array_merge($filter['params'], $params);
        $filter['scope'] = $where;
        $filter['scope_params'] = $params;
        return $filter;
    }

    /** یک سطر فهرست. */
    public static function summary(array $row, ?int $now = null): array
    {
        return [
            'id' => $row['id'],
            'kind' => (int) $row['kind'],
            'kind_name' => FieldAdmin::KIND_NAMES[(int) $row['kind']] ?? '',
            'tracking_code' => (string) $row['tracking_code'],
            'area' => Areas::label($row['area_code'] ?? null),
            'handler' => $row['handler'] ?? null,
            'status' => (int) $row['status'],
            'status_name' => FieldAdmin::STATUS_NAMES[(int) $row['status']] ?? '',
            'priority' => $row['priority'] === null ? null : (int) $row['priority'],
            'created_at' => (int) $row['created_at'],
            'due_at' => $row['due_at'] === null ? null : (int) $row['due_at'],
            'overdue' => FieldAdmin::isOverdue($row, $now),
            'address' => $row['address'],
            'plate' => $row['plate'],
            'reporter' => (string) ($row['reporter'] ?? ''),
            'expert' => $row['expert'] ?? null,
        ];
    }

    /** یک مورد کامل: جزئیات، عکس‌ها، تاریخچه، و آنچه مدیر می‌تواند با آن بکند. */
    public static function item(string $id, ?Scope $scope = null): ?array
    {
        $row = Field::isUuid($id) ? Db::one(
            'SELECT i.*, u.full_name AS reporter, u.user_code AS reporter_code, a.full_name AS expert, h.full_name AS handler
             FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to
             LEFT JOIN users h ON h.id = i.handler_id WHERE i.id = ?',
            [$id]
        ) : null;
        if ($row === null || ($scope !== null && !$scope->canSeeField($row))) {
            return null;
        }
        $scope ??= new Scope(['role' => Auth::ROLE_MANAGER, 'areas' => null, 'id' => '']);
        $payload = json_decode((string) $row['payload'], true);
        $payload = is_array($payload) ? $payload : [];
        $result = json_decode((string) ($row['result'] ?? ''), true);
        $photos = Db::all(
            'SELECT id, role, captured_at, note FROM field_files WHERE item_id = ? AND complete = 1 AND role IN (?, ?, ?)
             ORDER BY captured_at, role LIMIT 60',
            [$id, Field::ROLE_PHOTO_STAMPED, Field::ROLE_PHOTO, Field::ROLE_THERMAL_EXIF]
        );
        $events = Db::all(
            'SELECT e.action, e.from_status, e.to_status, e.note, e.at, u.full_name AS who
             FROM field_events e LEFT JOIN users u ON u.id = e.user_id WHERE e.item_id = ? ORDER BY e.at, e.id',
            [$id]
        );
        $files = (int) (Db::one('SELECT COUNT(*) AS n FROM field_files WHERE item_id = ?', [$id])['n'] ?? 0);
        // گزارش غیرمجازی که پرونده شده، از صفحه پرونده گردانده می‌شود؛ اینجا فقط دیده می‌شود.
        $inCase = (int) $row['kind'] === Field::KIND_ILLEGAL && ($row['report_id'] ?? null) !== null;
        $next = $row['merged_into'] === null && !$inCase ? (FieldAdmin::NEXT[(int) $row['status']] ?? []) : [];
        $details = FieldPresenter::rows((int) $row['kind'], $payload);
        if ((int) $row['kind'] === Field::KIND_LINE) {
            foreach (FieldLine::poleTable($payload) as $pole) {
                $details[] = ['تیر ' . $pole[0] . ($pole[1] === '' ? '' : ' — ' . $pole[1]),
                    $pole[2] . ' · ' . $pole[3] . ' · انشعاب ' . $pole[4] . ($pole[6] === '' ? '' : "\n" . $pole[6])];
            }
            foreach (FieldSubscribers::table($payload) as $s) {
                $details[] = ['تیر ' . $s[0] . ' — مشترک نامتعارف ' . $s[1] . ($s[2] === '' ? '' : ' — ' . $s[2]),
                    implode("\n", array_filter([$s[3] . ' · ' . $s[4], $s[5] === '' ? '' : 'کد پستی: ' . $s[5], $s[6], $s[8], $s[9]]))];
            }
        }

        return self::summary($row) + [
            'reporter_code' => (string) ($row['reporter_code'] ?? ''),
            'latitude' => $row['latitude'] === null ? null : (float) $row['latitude'],
            'longitude' => $row['longitude'] === null ? null : (float) $row['longitude'],
            'accuracy' => $row['accuracy'] === null ? null : (float) $row['accuracy'],
            'description' => $row['description'],
            'position_note' => PositionAudit::text($row['position_at'] ?? null, $row['latitude'], $row['longitude'],
                $row['observer_lat'] ?? null, $row['observer_lon'] ?? null, $row['observer_accuracy'] ?? null),
            'merged_into' => $row['merged_into'],
            'report_id' => $row['report_id'],
            'details' => array_map(static fn ($r) => ['label' => $r[0], 'value' => $r[1]], $details),
            'result' => is_array($result) ? $result : null,
            'result_name' => is_array($result) ? (FieldReferrals::OUTCOMES[$result['outcome'] ?? ''] ?? null) : null,
            'photos' => array_map(static fn ($p) => [
                'id' => $p['id'],
                'captured_at' => $p['captured_at'] === null ? null : (int) $p['captured_at'],
                'note' => (string) ($p['note'] ?? ''),
            ], $photos),
            'file_count' => $files,
            'events' => array_map(static fn ($e) => [
                'at' => (int) $e['at'],
                'who' => $e['who'],
                'what' => (self::EVENT_NAMES[$e['action']] ?? (string) $e['action'])
                    . ($e['to_status'] === null || $e['from_status'] === null ? '' : ': ' . (FieldAdmin::STATUS_NAMES[(int) $e['to_status']] ?? '')),
                'note' => $e['note'],
            ], $events),
            'next' => array_map(static fn (int $to) => [
                'status' => $to,
                'name' => FieldAdmin::STATUS_NAMES[$to] ?? '',
                'needs_note' => in_array($to, self::NEED_NOTE, true),
                'needs_expert' => $to === Field::STATUS_REFERRED,
            ], $next),
            'experts' => $next === [] ? [] : array_map(static fn ($e) => ['id' => $e['id'], 'name' => $e['name']],
                $scope->experts($scope->wholeProvince() ? null : ($row['area_code'] ?? null))),
            'executors' => $next === [] || !$scope->isManager() ? [] : array_map(
                static fn ($e) => ['id' => $e['id'], 'name' => $e['name'] . ' — ' . implode('، ', array_map([Areas::class, 'label'], $e['areas']))],
                $scope->executors()),
            'can_hand' => $next !== [] && $scope->isManager(),
        ];
    }

    /** مسیر عکسی از یک مورد، برای مدیر؛ تهی اگر نیست یا هنوز کامل نرسیده. */
    public static function photoPath(string $fileId): ?string
    {
        $row = Field::isUuid($fileId) ? Db::one(
            'SELECT path FROM field_files WHERE id = ? AND complete = 1 AND role IN (?, ?, ?)',
            [$fileId, Field::ROLE_PHOTO, Field::ROLE_PHOTO_STAMPED, Field::ROLE_THERMAL_EXIF]
        ) : null;
        $path = $row === null || $row['path'] === null ? null : Storage::resolve((string) $row['path']);
        return $path !== null && is_file($path) ? $path : null;
    }
}
