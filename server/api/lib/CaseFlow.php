<?php
declare(strict_types=1);

/**
 * گردش هر پرونده میان مدیر، مجری ناحیه و کارشناس: سپردن به مجری، ارجاع به
 * کارشناس، پیگیری، و خط زمانی. هر کار یک سطر case_events است.
 *
 * ارجاع به کارشناس `updated_at` پرونده را جلو می‌برد، برخلاف قاعده پنل: پرونده‌ای
 * که هنوز بازدید نشده روی هیچ گوشی ویرایش آفلاینی ندارد که رد شود، و گوشی کارشناس
 * فقط با همین جلو رفتن پرونده تازه را در همگام‌سازی بعدی می‌گیرد.
 */
final class CaseFlow
{
    public const EVENT_NAMES = [
        'intake' => 'ثبت گزارش', 'registered' => 'ثبت کارشناس', 'to_executor' => 'سپردن به مجری ناحیه',
        'auto_executor' => 'ارجاع خودکار به مجری ناحیه', 'no_executor' => 'ناحیه مجری ندارد',
        'to_expert' => 'ارجاع به کارشناس', 'followup' => 'پیگیری', 'visited' => 'بازدید ثبت شد',
        'submitted' => 'ارسال برای تأیید', 'seen' => 'دیده شد', 'approved' => 'تأیید', 'returned' => 'برگشت برای اصلاح',
        'dispatched' => 'ارسال به واحد', 'expert_alert' => 'هشدار: کارشناس اقدام نکرده',
        'manager_alert' => 'هشدار به مدیر', 'unit_stage' => 'اقدام واحد',
        'field_intake' => 'گزارش میدانی رسید', 'hq_alert' => 'هشدار به ستاد: مجری شهرستان ارجاع نداد',
        'auto_expert' => 'ارجاع خودکار به کارشناس', 'decide_alert' => 'هشدار به ستاد: تأیید یا ارسال معطل مانده',
        'hq_late' => 'هشدار به ستاد',
    ];

    public static function row(string $reportId): ?array
    {
        return Db::one('SELECT * FROM case_flow WHERE report_id = ?', [$reportId]);
    }

    /** ستون‌های گردش یک پرونده را می‌نویسد؛ ردیفی که نیست ساخته می‌شود. */
    public static function touch(string $reportId, array $columns): void
    {
        $columns['updated_at'] = Db::now();
        $names = array_keys($columns);
        Db::run(
            'INSERT INTO case_flow (report_id, ' . Db::cols($names) . ') VALUES (?' . str_repeat(', ?', count($names)) . ')
             ON DUPLICATE KEY UPDATE ' . implode(', ', array_map(static fn ($c) => Db::col($c) . ' = VALUES(' . Db::col($c) . ')', $names)),
            array_merge([$reportId], array_values($columns))
        );
    }

    public static function event(string $reportId, ?string $userId, string $action, ?string $note = null): void
    {
        Db::run('INSERT INTO case_events (report_id, at, user_id, action, note) VALUES (?, ?, ?, ?, ?)',
            [$reportId, Db::now(), $userId, $action, $note]);
    }

    /** پرونده‌ای که این کاربر می‌گرداند، یا خطای فارسی. */
    private static function owned(string $reportId, Scope $scope): array|string
    {
        $report = Db::one('SELECT * FROM reports WHERE id = ?', [$reportId]);
        if ($report === null || !$scope->canSeeReport($report)) {
            return 'پرونده پیدا نشد یا در محدوده شما نیست.';
        }
        if (!$scope->canActOn($report)) {
            return 'پرونده‌های برق غیرمجاز را مجریان غیرمجاز می‌گردانند؛ مدیر ناظر است.';
        }
        return $report;
    }

    /** بالادست حوزه (مدیر یا ستاد) پرونده را به گرداننده شهرستان (یا یکی از گردانندگان همان ناحیه) می‌سپارد. */
    public static function toExecutor(string $reportId, ?string $executorId, string $note, Scope $scope): ?string
    {
        $report = self::owned($reportId, $scope);
        if (is_string($report)) {
            return $report;
        }
        $kind = CaseKind::of($report);
        if ($scope->role !== CaseKind::topRole($kind)) {
            return $kind === CaseKind::ILLEGAL ? 'سپردن به مجری شهرستان کار ستاد است.' : 'سپردن به مجری کار مدیر است.';
        }
        $executor = $executorId === null || $executorId === '' ? Scope::executorOf($report['area_code'], $kind)
            : (array_values(array_filter($scope->executors($report['area_code']), static fn ($e) => $e['id'] === $executorId))[0] ?? null);
        if ($executor === null) {
            return 'برای ناحیه این پرونده مجری فعالی تعریف نشده است.';
        }
        $now = Db::now();
        self::touch($reportId, ['executor_id' => $executor['id'], 'executor_at' => $now, 'refer_due_at' => null,
            'expert_alert_at' => null, 'manager_alert_at' => null]);
        self::event($reportId, (string) $scope->user['id'], 'to_executor', trim($note) === '' ? $executor['name'] : $executor['name'] . ' — ' . trim($note));
        Notifications::toUser((string) $executor['id'], 'case_referred', 'پرونده برای پیگیری به شما سپرده شد',
            CaseLabels::code($report) . (trim($note) === '' ? '' : ' — ' . trim($note)), $reportId);
        return null;
    }

    /**
     * مدیر یا مجری پرونده را به کارشناس همان ناحیه ارجاع می‌دهد. فقط پیش از بازدید:
     * پرونده بازدیدشده روی گوشی کارشناس خودش است و جابه‌جا کردنش کار او را گم می‌کرد.
     */
    public static function toExpert(string $reportId, string $expertId, ?int $dueDays, string $note, Scope $scope): ?string
    {
        $report = self::owned($reportId, $scope);
        if (is_string($report)) {
            return $report;
        }
        if ((int) $report['status'] !== 0) {
            return 'این پرونده بازدید شده است و دیگر به کارشناس دیگری ارجاع نمی‌شود.';
        }
        $expert = array_values(array_filter($scope->experts($report['area_code']), static fn ($e) => $e['id'] === $expertId))[0] ?? null;
        if ($expert === null) {
            return 'کارشناس انتخاب‌شده در ناحیه این پرونده نیست.';
        }
        if ($dueDays !== null && ($dueDays < 1 || $dueDays > 60)) {
            return 'مهلت بازدید را بین ۱ تا ۶۰ روز کاری بنویسید.';
        }
        $flow = self::row($reportId) ?? [];
        $now = Db::now();
        $days = $dueDays ?? (int) FieldSettings::all()['expert_days'];
        $columns = ['expert_id' => $expert['id'], 'assigned_by' => $scope->user['id'], 'assigned_at' => $now,
            'due_at' => CrewCalendar::fromSettings()->addWorkDays($now, $days), 'refer_due_at' => null,
            'county_due_at' => null, 'hq_due_at' => null, 'expert_alert_at' => null, 'manager_alert_at' => null];
        $kind = CaseKind::of($report);
        if ($scope->isAreaExecutor()) {
            $columns['executor_action_at'] = $now;
            if (($flow['executor_id'] ?? null) === null) {
                $columns += ['executor_id' => $scope->user['id'], 'executor_at' => $now];
            }
        } elseif (($flow['executor_id'] ?? null) === null) {
            // بالادست مستقیم به کارشناس داد: پیگیری از این لحظه با گرداننده همان ناحیه است.
            $executor = Scope::executorOf($report['area_code'], $kind);
            if ($executor !== null) {
                $columns += ['executor_id' => $executor['id'], 'executor_at' => $now];
                Notifications::toUser((string) $executor['id'], 'case_referred', 'پرونده ناحیه شما به کارشناس ارجاع شد',
                    CaseLabels::code($report) . ' — ' . $expert['name'], $reportId);
            }
        }
        self::touch($reportId, $columns);
        Db::run('UPDATE reports SET expert_code = ?, updated_at = ? WHERE id = ?', [$expert['user_code'], $now, $reportId]);
        self::event($reportId, (string) $scope->user['id'], 'to_expert',
            $expert['name'] . (trim($note) === '' ? '' : ' — ' . trim($note)));
        Notifications::toUser((string) $expert['id'], 'case_assigned', 'پرونده تازه برای بازدید',
            CaseLabels::code($report) . (trim($note) === '' ? '' : ' — ' . trim($note)), $reportId);
        return null;
    }

    /** یادداشت پیگیری مدیر یا مجری؛ برای مجری «اقدام» حساب می‌شود و هشدار به مدیر را عقب می‌اندازد. */
    public static function followUp(string $reportId, string $note, Scope $scope): ?string
    {
        $report = self::owned($reportId, $scope);
        if (is_string($report)) {
            return $report;
        }
        $note = trim($note);
        if ($note === '' || mb_strlen($note) > 2000) {
            return 'متن پیگیری را بنویسید (حداکثر ۲۰۰۰ نویسه).';
        }
        if ($scope->isAreaExecutor()) {
            self::touch($reportId, ['executor_action_at' => Db::now()]);
        }
        self::event($reportId, (string) $scope->user['id'], 'followup', $note);
        $flow = self::row($reportId);
        if (($flow['expert_id'] ?? null) !== null && (int) $report['status'] === 0) {
            Notifications::toUser((string) $flow['expert_id'], 'case_followup', 'پیگیری پرونده',
                CaseLabels::code($report) . ' — ' . $note, $reportId);
        }
        return null;
    }

    /** پرونده‌ای که از گوشی رسید: ردیف گردش، و ثبت بازدید وقتی اولین بار «بازدید شده» آمد. */
    public static function onPushed(array $report, array $user, bool $isNew): void
    {
        $id = (string) $report['id'];
        $flow = self::row($id);
        if ($flow === null) {
            self::touch($id, ['origin' => CaseIntake::ORIGIN_EXPERT]);
            if ($isNew && (int) $user['role'] === Auth::ROLE_EXPERT) {
                self::event($id, (string) $user['id'], 'registered');
            }
            $flow = self::row($id);
        }
        if ((int) ($report['status'] ?? 0) >= 1 && ($flow['visited_at'] ?? null) === null) {
            self::touch($id, ['visited_at' => Db::now()]);
            self::event($id, (string) $user['id'], 'visited');
            Notifications::toCaseLeads($report, 'case_visited', 'بازدید پرونده ثبت شد', CaseLabels::code($report), $id);
        }
    }

    /** ارسال به واحد (از هر راهی): اولین ارسال در گردش، و برای مجری «اقدام». */
    public static function onDispatched(string $reportId, array $user, int $unit): void
    {
        $flow = self::row($reportId);
        $columns = ($flow['dispatched_at'] ?? null) === null ? ['dispatched_at' => Db::now()] : [];
        if (in_array((int) $user['role'], [Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL_AREA], true)) {
            $columns['executor_action_at'] = Db::now();
        }
        if ($columns !== [] || $flow === null) {
            self::touch($reportId, $columns);
        }
        self::event($reportId, (string) $user['id'], 'dispatched', UnitStages::NAMES[$unit] ?? null);
    }

    /** تصمیم تأیید یا برگشت، از اپ یا پنل، به دست مدیر یا مجری. */
    public static function onDecided(string $reportId, array $user, bool $approved, string $comment): void
    {
        $columns = $approved ? ['approved_at' => Db::now()] : [];
        if (in_array((int) $user['role'], [Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL_AREA], true)) {
            $columns['executor_action_at'] = Db::now();
        }
        self::touch($reportId, $columns);
        self::event($reportId, (string) $user['id'], $approved ? 'approved' : 'returned', trim($comment) === '' ? null : $comment);
    }

    /**
     * مدیر یا مجری پرونده‌ای را که برای تأیید آمده باز کرد: کارشناس در اپش می‌بیند که
     * نتیجه‌اش دیده شده. فقط بار اول ثبت می‌شود.
     */
    public static function markSeen(string $reportId, array $user): void
    {
        $pending = Db::one('SELECT id FROM approvals WHERE report_id = ? AND decision = 0 AND seen_at IS NULL
                            ORDER BY submitted_at DESC LIMIT 1', [$reportId]);
        if ($pending === null) {
            return;
        }
        Db::run('UPDATE approvals SET seen_at = ?, seen_by = ? WHERE id = ?', [Db::now(), $user['id'], $pending['id']]);
        self::event($reportId, (string) $user['id'], 'seen');
    }

    /** خط زمانی پرونده، قدیمی‌ترین اول، با نام انجام‌دهنده. */
    public static function timeline(string $reportId): array
    {
        return array_map(static fn ($e) => [
            'at' => (int) $e['at'],
            'who' => $e['who'],
            'what' => self::EVENT_NAMES[$e['action']] ?? (string) $e['action'],
            'action' => (string) $e['action'],
            'note' => $e['note'],
        ], Db::all(
            'SELECT e.*, u.full_name AS who FROM case_events e LEFT JOIN users u ON u.id = e.user_id
             WHERE e.report_id = ? ORDER BY e.at, e.id',
            [$reportId]
        ));
    }

    /** خلاصه گردش برای صفحه پرونده و اپ: چه کسی دارد، تا کی، و چه چیزی دیر شده. */
    public static function summary(string $reportId): array
    {
        $flow = Db::one(
            'SELECT f.*, x.full_name AS executor_name, p.full_name AS expert_name, b.full_name AS intake_by_name
             FROM case_flow f LEFT JOIN users x ON x.id = f.executor_id LEFT JOIN users p ON p.id = f.expert_id
             LEFT JOIN users b ON b.id = f.intake_by WHERE f.report_id = ?',
            [$reportId]
        ) ?? [];
        $num = static fn ($k) => isset($flow[$k]) && $flow[$k] !== null ? (int) $flow[$k] : null;
        $report = Db::one('SELECT tracking_code, temp_code, area_code, status, approval_state, address, report_type, case_kind
                           FROM reports WHERE id = ?', [$reportId]) ?? [];
        return [
            'code' => CaseLabels::code($report) ?: ($flow['intake_code'] ?? ''),
            'area_code' => $report['area_code'] ?? null,
            'area' => Areas::label($report['area_code'] ?? null),
            'status' => (int) ($report['status'] ?? 0),
            'approval_state' => (int) ($report['approval_state'] ?? 0),
            'address' => $report['address'] ?? null,
            'type' => CaseLabels::REPORT_TYPES[(int) ($report['report_type'] ?? 0)] ?? '',
            'case_kind' => CaseKind::of($report),
            'kind_name' => CaseKind::label($report),
            'county_due_at' => $num('county_due_at'),
            'hq_alert_at' => $num('hq_alert_at'),
            'hq_due_at' => $num('hq_due_at'),
            'origin' => (int) ($flow['origin'] ?? 0),
            'origin_name' => CaseIntake::ORIGIN_NAMES[(int) ($flow['origin'] ?? 0)] ?? '',
            'intake_code' => $flow['intake_code'] ?? null,
            'intake_by' => $flow['intake_by_name'] ?? null,
            'intake_note' => $flow['intake_note'] ?? null,
            'refer_due_at' => $num('refer_due_at'),
            'executor' => $flow['executor_name'] ?? null,
            'executor_id' => $flow['executor_id'] ?? null,
            'auto_referred' => (int) ($flow['auto_referred'] ?? 0) === 1,
            'expert' => $flow['expert_name'] ?? null,
            'expert_id' => $flow['expert_id'] ?? null,
            'assigned_at' => $num('assigned_at'),
            'due_at' => $num('due_at'),
            'visited_at' => $num('visited_at'),
            'approved_at' => $num('approved_at'),
            'dispatched_at' => $num('dispatched_at'),
            'expert_late' => $num('visited_at') === null && $num('due_at') !== null && $num('due_at') < Db::now(),
            'timeline' => self::timeline($reportId),
        ];
    }
}
