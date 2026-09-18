<?php
declare(strict_types=1);

/**
 * گردش تأیید.
 *
 * کارشناس پرونده را برای بررسی می‌فرستد؛ تا وقتی مدیر تأیید نکرده، ثبت نهایی
 * انجام نمی‌شود. مدیر می‌تواند پرونده را برای اصلاح یا تکمیل برگرداند و
 * کارشناس دوباره بفرستد. همه رفت‌وبرگشت‌ها می‌ماند، نه فقط آخرین وضعیت.
 */
final class ApprovalController
{
    public const PENDING = 0;
    public const APPROVED = 1;
    public const RETURNED = 2;

    public function submit(Request $request): void
    {
        $user = Auth::require($request);
        $reportId = $request->str('report_id');
        $report = Db::one('SELECT id, tracking_code, expert_code FROM reports WHERE id = ?', [$reportId]);
        if ($report === null) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }

        Db::run(
            'INSERT INTO approvals (id, report_id, submitted_by, submitted_at, decision, comment)
             VALUES (?, ?, ?, ?, ?, ?)',
            [Db::uuid(), $reportId, $user['id'], Db::now(), self::PENDING, $request->str('comment')]
        );

        Notifications::toRole(
            Auth::ROLE_MANAGER,
            'approval_pending',
            'پرونده برای بررسی ارسال شد',
            'پرونده ' . ($report['tracking_code'] ?? $reportId) . ' در انتظار تأیید شماست.',
            $reportId
        );

        Response::json(['submitted' => true]);
    }

    public function decide(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        $reportId = $request->str('report_id');
        $decision = $request->int('decision', self::RETURNED);
        if (!in_array($decision, [self::APPROVED, self::RETURNED], true)) {
            Response::fail(400, 'bad_decision', 'تصمیم نامعتبر است.');
        }

        $pending = Db::one(
            'SELECT * FROM approvals WHERE report_id = ? AND decision = ? ORDER BY submitted_at DESC LIMIT 1',
            [$reportId, self::PENDING]
        );
        if ($pending === null) {
            Response::fail(409, 'nothing_pending', 'برای این پرونده درخواست تأییدی در انتظار نیست.');
        }

        Db::run(
            'UPDATE approvals SET decision = ?, decided_by = ?, decided_at = ?, comment = ? WHERE id = ?',
            [$decision, $manager['id'], Db::now(), $request->str('comment'), $pending['id']]
        );

        $report = Db::one('SELECT tracking_code, expert_code FROM reports WHERE id = ?', [$reportId]);
        $expert = Db::one('SELECT id FROM users WHERE user_code = ?', [$report['expert_code'] ?? '']);
        if ($expert !== null) {
            $approved = $decision === self::APPROVED;
            Notifications::toUser(
                (string) $expert['id'],
                $approved ? 'approved' : 'returned',
                $approved ? 'پرونده تأیید شد' : 'پرونده برای اصلاح برگشت خورد',
                ($report['tracking_code'] ?? $reportId) . ' — ' . $request->str('comment'),
                $reportId
            );
        }

        Response::json(['decision' => $decision]);
    }

    /** وضعیت تأیید پرونده‌ها: در انتظار برای مدیر، سابقه کامل برای یک پرونده. */
    public function index(Request $request): void
    {
        $user = Auth::require($request);
        $reportId = $request->str('report_id');

        if ($reportId !== '') {
            Response::json([
                'approvals' => Db::all(
                    'SELECT a.*, u.full_name AS submitted_by_name, m.full_name AS decided_by_name
                     FROM approvals a
                     LEFT JOIN users u ON u.id = a.submitted_by
                     LEFT JOIN users m ON m.id = a.decided_by
                     WHERE a.report_id = ? ORDER BY a.submitted_at DESC',
                    [$reportId]
                ),
            ]);
        }

        if ((int) $user['role'] !== Auth::ROLE_MANAGER) {
            Response::fail(403, 'forbidden', 'این فهرست برای مدیر است.');
        }
        Response::json([
            'pending' => Db::all(
                'SELECT a.*, r.tracking_code, r.county, r.expert_code
                 FROM approvals a JOIN reports r ON r.id = a.report_id
                 WHERE a.decision = ? ORDER BY a.submitted_at',
                [self::PENDING]
            ),
        ]);
    }
}
