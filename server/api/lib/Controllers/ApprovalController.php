<?php
declare(strict_types=1);

/**
 * گردش تأیید.
 *
 * کارشناس پرونده را برای بررسی می‌فرستد؛ مدیر، یا مجری رمزارز همان ناحیه، تأیید
 * می‌کند یا برای اصلاح برمی‌گرداند. همه رفت‌وبرگشت‌ها می‌ماند، نه فقط آخرین
 * وضعیت، و قاعده تصمیم یکی است با پنل وب (CaseActions::decide).
 */
final class ApprovalController
{
    public const PENDING = 0;
    public const APPROVED = 1;
    public const RETURNED = 2;

    public function submit(Request $request): void
    {
        $user = Auth::requireAny($request, array_merge([Auth::ROLE_EXPERT], Auth::CASE_MANAGERS));
        $reportId = $request->str('report_id');
        $report = Db::one('SELECT id, tracking_code, temp_code, expert_code, area_code, case_kind FROM reports WHERE id = ?', [$reportId]);
        if ($report === null) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }
        if ((int) $user['role'] === Auth::ROLE_EXPERT && (string) $report['expert_code'] !== (string) $user['user_code']) {
            Response::fail(403, 'forbidden', 'این پرونده برای شما نیست.');
        }

        Db::run(
            'INSERT INTO approvals (id, report_id, submitted_by, submitted_at, decision, comment)
             VALUES (?, ?, ?, ?, ?, ?)',
            [Db::uuid(), $reportId, $user['id'], Db::now(), self::PENDING, $request->str('comment')]
        );
        Db::run('UPDATE reports SET approval_state = 1 WHERE id = ? AND approval_state <> 2', [$reportId]);
        CaseFlow::event($reportId, (string) $user['id'], 'submitted', $request->str('comment') ?: null);

        $text = 'پرونده ' . CaseLabels::code($report) . ' در انتظار تأیید است.';
        // رمزارز: مدیر و مجری ناحیه. برق غیرمجاز: مجری شهرستان و ستاد (مدیر فقط ناظر است).
        if (CaseKind::of($report) === CaseKind::CRYPTO) {
            Notifications::toRole(Auth::ROLE_MANAGER, 'approval_pending', 'پرونده برای بررسی ارسال شد', $text, $reportId);
        }
        Notifications::toCaseLeads($report, 'approval_pending', 'پرونده برای بررسی ارسال شد', $text, $reportId);

        Response::json(['submitted' => true]);
    }

    public function decide(Request $request): void
    {
        $user = Auth::requireAny($request, Auth::CASE_MANAGERS);
        $reportId = $request->str('report_id');
        $report = Db::one('SELECT * FROM reports WHERE id = ?', [$reportId]);
        if ($report === null || !(new Scope($user))->canSeeReport($report)) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد یا در محدوده شما نیست.');
        }
        $refusal = CaseActions::refusal($report, $user);
        if ($refusal !== null) {
            Response::fail(403, 'not_your_turn', $refusal);
        }
        $decision = $request->int('decision', self::RETURNED);
        $error = CaseActions::decide($reportId, (int) $decision, $request->str('comment'), $user);
        if ($error !== null) {
            $code = str_contains($error, 'در انتظار نیست') ? 409 : 400;
            Response::fail($code, $code === 409 ? 'nothing_pending' : 'bad_decision', $error);
        }
        Response::json(['decision' => $decision]);
    }

    /**
     * وضعیت تأیید: سابقه یک پرونده (برای کارشناس خودش یا مدیر و مجری ناحیه)، یا
     * صف در انتظار برای مدیر و مجری. کارشناس از seen_at می‌فهمد پرونده‌اش دیده شده.
     */
    public function index(Request $request): void
    {
        $user = Auth::requireAny($request, array_merge([Auth::ROLE_EXPERT], Auth::CASE_MANAGERS));
        $scope = new Scope($user);
        $reportId = $request->str('report_id');

        if ($reportId !== '') {
            $report = Db::one('SELECT * FROM reports WHERE id = ?', [$reportId]);
            $own = $report !== null && (int) $user['role'] === Auth::ROLE_EXPERT
                && (string) $report['expert_code'] === (string) $user['user_code'];
            if ($report === null || (!$own && !$scope->canSeeReport($report))) {
                Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
            }
            if ($scope->managesCases() && $scope->canActOn($report)) {
                CaseFlow::markSeen($reportId, $user);
            }
            Response::json(['approvals' => self::history($reportId)]);
        }

        if (!$scope->managesCases()) {
            Response::fail(403, 'forbidden', 'این فهرست برای مدیر و مجری است.');
        }
        [$where, $params] = $scope->reportWhere('r');
        Response::json([
            'pending' => Db::all(
                "SELECT a.*, r.tracking_code, r.temp_code, r.county, r.area_code, r.expert_code
                 FROM approvals a JOIN reports r ON r.id = a.report_id
                 WHERE a.decision = ? AND $where ORDER BY a.submitted_at",
                array_merge([self::PENDING], $params)
            ),
        ]);
    }

    /** همه رفت‌وبرگشت‌های تأیید یک پرونده، با نقش تصمیم‌گیر و بیننده. */
    public static function history(string $reportId): array
    {
        return Db::all(
            'SELECT a.*, u.full_name AS submitted_by_name, m.full_name AS decided_by_name, m.role AS decided_by_role,
                    s.full_name AS seen_by_name, s.role AS seen_by_role
             FROM approvals a
             LEFT JOIN users u ON u.id = a.submitted_by
             LEFT JOIN users m ON m.id = a.decided_by
             LEFT JOIN users s ON s.id = a.seen_by
             WHERE a.report_id = ? ORDER BY a.submitted_at DESC',
            [$reportId]
        );
    }
}
