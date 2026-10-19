<?php
declare(strict_types=1);

/**
 * گردش پرونده در API: ثبت گزارش، سپردن به مجری، ارجاع به کارشناس، پیگیری، میز
 * پیگیری؛ و برای کارشناس، پرونده‌هایی که به او ارجاع شده و وضعیت تأیید آن‌هایی که
 * فرستاده. قاعده‌ها در CaseIntake و CaseFlow، مشترک با پنل وب.
 */
final class CaseFlowController
{
    private const STAFF = Auth::CASE_MANAGERS;

    public function intake(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $data = [];
        foreach (['report_type', 'area_code', 'district', 'address', 'postal_code', 'subscription_number', 'owner_name',
                  'owner_phone', 'subscriber_name', 'report_date', 'tracking_code', 'note'] as $key) {
            $data[$key] = $request->str($key);
        }
        $id = CaseIntake::create($data, $scope);
        $error = self::route($request, $id, $scope);
        Response::json(['id' => $id, 'flow' => CaseFlow::summary($id), 'warning' => $error]);
    }

    public function toExecutor(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $id = $request->str('report_id');
        self::done(CaseFlow::toExecutor($id, $request->str('executor_id') ?: null, $request->str('note'), $scope), $id);
    }

    public function toExpert(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $id = $request->str('report_id');
        self::done(CaseFlow::toExpert($id, $request->str('expert_id'), $request->int('due_days'), $request->str('note'), $scope), $id);
    }

    public function followUp(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $id = $request->str('report_id');
        self::done(CaseFlow::followUp($id, $request->str('note'), $scope), $id);
    }

    /** گردش و خط زمانی یک پرونده. */
    public function flow(Request $request): void
    {
        $user = Auth::requireAny($request, self::STAFF);
        $id = $request->str('report_id');
        $report = Db::one('SELECT * FROM reports WHERE id = ?', [$id]);
        if ($report === null || !(new Scope($user))->canSeeReport($report)) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }
        $scope = new Scope($user);
        if ($scope->canActOn($report)) {
            CaseFlow::markSeen($id, $user);
        }
        Response::json(['flow' => CaseFlow::summary($id) + ['can_act' => $scope->canActOn($report),
            'refusal' => CaseActions::refusal($report, $user)]]);
    }

    /** میز پیگیری: پرونده‌های باز محدوده و شمار هر مرحله. */
    public function board(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $open = CaseBoard::open($scope);
        Response::json(['cases' => $open, 'counts' => CaseBoard::counts($open), 'server_time' => Db::now()]);
    }

    /** آنچه فهرست‌های انتخاب لازم دارند: ناحیه‌ها، کارشناسان و مجریان محدوده، و انواع گزارش. */
    public function people(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, self::STAFF));
        $area = $request->str('area') ?: null;
        $areas = $scope->wholeProvince() ? array_keys(Areas::NAMES) : $scope->areas;
        Response::json([
            'role' => $scope->role,
            'areas' => array_map(static fn ($c) => ['code' => (string) $c, 'name' => Areas::label((string) $c)], $areas),
            'experts' => $scope->experts($area),
            'case_kind' => $scope->kind() ?? CaseKind::CRYPTO,
            'executors' => $scope->isAreaExecutor() ? [] : $scope->executors($area),
            'report_types' => array_map(static fn ($k, $v) => ['code' => $k, 'name' => $v],
                array_keys(CaseLabels::REPORT_TYPES), CaseLabels::REPORT_TYPES),
        ]);
    }

    /** برای گوشی کارشناس: پرونده‌های بازدیدنشده‌ای که به او ارجاع شده، با ارجاع‌دهنده و مهلت. */
    public function assigned(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $rows = Db::all(
            'SELECT r.id, r.tracking_code, r.temp_code, r.case_kind, f.intake_code, f.intake_note, f.assigned_at, f.due_at, f.origin,
                    b.full_name AS by_name, b.role AS by_role
             FROM case_flow f JOIN reports r ON r.id = f.report_id LEFT JOIN users b ON b.id = f.assigned_by
             WHERE f.expert_id = ? AND r.status = 0 ORDER BY f.due_at',
            [$user['id']]
        );
        Response::json(['assigned' => array_map(static fn ($r) => [
            'report_id' => $r['id'], 'code' => CaseLabels::code($r) ?: (string) $r['intake_code'],
            'by' => $r['by_name'], 'by_role' => $r['by_role'] === null ? null : (int) $r['by_role'],
            'at' => $r['assigned_at'] === null ? null : (int) $r['assigned_at'],
            'due_at' => $r['due_at'] === null ? null : (int) $r['due_at'], 'note' => $r['intake_note'],
            'case_kind' => (int) $r['case_kind'],
        ], $rows), 'server_time' => Db::now()]);
    }

    /**
     * برای گوشی کارشناس: آخرین وضعیت تأیید هر پرونده‌ای که فرستاده — دیده شد یا نه،
     * به دست مدیر یا مجری، و تصمیم. نتیجه اقدام واحدها عمداً اینجا نیست.
     */
    public function mine(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $rows = Db::all(
            'SELECT a.report_id, a.submitted_at, a.seen_at, a.decision, a.decided_at, a.comment, s.role AS seen_role,
                    s.full_name AS seen_name, m.role AS decided_role, m.full_name AS decided_name
             FROM approvals a JOIN reports r ON r.id = a.report_id
             LEFT JOIN users s ON s.id = a.seen_by LEFT JOIN users m ON m.id = a.decided_by
             WHERE r.expert_code = ? AND a.submitted_at = (SELECT MAX(a2.submitted_at) FROM approvals a2 WHERE a2.report_id = a.report_id)
             ORDER BY a.submitted_at DESC LIMIT 500',
            [$user['user_code']]
        );
        Response::json(['statuses' => array_map(static fn ($r) => [
            'report_id' => $r['report_id'], 'submitted_at' => (int) $r['submitted_at'],
            'seen_at' => $r['seen_at'] === null ? null : (int) $r['seen_at'],
            'seen_by' => $r['seen_name'], 'seen_role' => $r['seen_role'] === null ? null : (int) $r['seen_role'],
            'decision' => (int) $r['decision'], 'decided_at' => $r['decided_at'] === null ? null : (int) $r['decided_at'],
            'decided_by' => $r['decided_name'], 'decided_role' => $r['decided_role'] === null ? null : (int) $r['decided_role'],
            'comment' => $r['comment'],
        ], $rows), 'server_time' => Db::now()]);
    }

    /** ارجاع همراه ثبت؛ خطایش ثبت را باطل نمی‌کند، فقط به‌عنوان هشدار برمی‌گردد. */
    private static function route(Request $request, string $id, Scope $scope): ?string
    {
        $expert = $request->str('expert_id');
        if ($expert !== '') {
            return CaseFlow::toExpert($id, $expert, $request->int('due_days'), '', $scope);
        }
        if (($scope->isManager() || $scope->isIllegalHq()) && $request->str('to_executor') === '1') {
            return CaseFlow::toExecutor($id, $request->str('executor_id') ?: null, '', $scope);
        }
        return null;
    }

    private static function done(?string $error, string $id): void
    {
        if ($error !== null) {
            Response::fail(409, 'refused', $error);
        }
        Response::json(['flow' => CaseFlow::summary($id)]);
    }
}
