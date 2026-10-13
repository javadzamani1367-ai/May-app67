<?php
declare(strict_types=1);

/**
 * کارهای مدیر روی پرونده‌های بازدید از پنل وب — همان سه کار اپ مدیر: تأیید یا
 * برگشت، ارسال به واحدها، و گزارش عملکرد واحدها.
 *
 * پنل وب `updated_at` پرونده را جلو نمی‌برد. آن ستون مال گوشی کارشناس است و
 * سرور ارسال کهنه‌تر را رد می‌کند؛ اگر وب آن را جلو می‌برد، ویرایشی که کارشناس
 * بی‌اینترنت انجام داده و هنوز نفرستاده، به سرور نمی‌رسید.
 */
final class CaseActions
{
    public const PENDING = 0;
    public const APPROVED = 1;
    public const RETURNED = 2;

    /** درخواست‌های در انتظار، قدیمی‌ترین اول، با نام کارشناس فرستنده. */
    public static function pendingApprovals(): array
    {
        return Db::all(
            'SELECT a.id, a.report_id, a.submitted_at, a.comment, r.tracking_code, r.temp_code, r.report_type,
                    r.county, r.area_code, r.address, r.expert_code, u.full_name AS submitted_by_name
             FROM approvals a JOIN reports r ON r.id = a.report_id LEFT JOIN users u ON u.id = a.submitted_by
             WHERE a.decision = ? ORDER BY a.submitted_at',
            [self::PENDING]
        );
    }

    /** آخرین تصمیم‌ها، برای سابقه زیر صف. */
    public static function recentDecisions(int $limit = 30): array
    {
        return Db::all(
            'SELECT a.report_id, a.decision, a.decided_at, a.comment, r.tracking_code, r.temp_code,
                    m.full_name AS decided_by_name
             FROM approvals a JOIN reports r ON r.id = a.report_id LEFT JOIN users m ON m.id = a.decided_by
             WHERE a.decision <> ? ORDER BY a.decided_at DESC LIMIT ' . max(1, $limit),
            [self::PENDING]
        );
    }

    /** همه رفت‌وبرگشت‌های تأیید یک پرونده، تازه‌ترین اول. */
    public static function history(string $reportId): array
    {
        return Db::all(
            'SELECT a.*, u.full_name AS submitted_by_name, m.full_name AS decided_by_name
             FROM approvals a LEFT JOIN users u ON u.id = a.submitted_by LEFT JOIN users m ON m.id = a.decided_by
             WHERE a.report_id = ? ORDER BY a.submitted_at DESC',
            [$reportId]
        );
    }

    /**
     * تصمیم مدیر روی درخواست در انتظار. برگشت بی‌توضیح پذیرفته نیست: کارشناس
     * باید بداند چه چیزی را اصلاح کند. خطا به فارسی، یا تهی اگر ثبت شد.
     */
    public static function decide(string $reportId, int $decision, string $comment, array $manager): ?string
    {
        if (!in_array($decision, [self::APPROVED, self::RETURNED], true)) {
            return 'تصمیم نامعتبر است.';
        }
        $comment = trim($comment);
        if ($decision === self::RETURNED && $comment === '') {
            return 'برای برگشت، بنویسید چه چیزی باید اصلاح شود.';
        }
        if (mb_strlen($comment) > 2000) {
            return 'توضیح بیش از حد بلند است.';
        }
        $pending = Db::one('SELECT id FROM approvals WHERE report_id = ? AND decision = ? ORDER BY submitted_at DESC LIMIT 1',
            [$reportId, self::PENDING]);
        if ($pending === null) {
            return 'برای این پرونده درخواست تأییدی در انتظار نیست.';
        }
        $now = Db::now();
        Db::run('UPDATE approvals SET decision = ?, decided_by = ?, decided_at = ?, comment = ? WHERE id = ?',
            [$decision, $manager['id'], $now, $comment, $pending['id']]);
        // وضعیت تأیید خود پرونده (۲ تأیید / ۳ برگشت)، تا آرشیو و فرم رسمی همان را نشان دهند.
        Db::run('UPDATE reports SET approval_state = ?, approval_comment = ?, approval_at = ? WHERE id = ?',
            [$decision === self::APPROVED ? 2 : 3, $comment === '' ? null : $comment, $now, $reportId]);

        $report = Db::one('SELECT tracking_code, temp_code, expert_code FROM reports WHERE id = ?', [$reportId]);
        $expert = Db::one('SELECT id FROM users WHERE user_code = ?', [$report['expert_code'] ?? '']);
        if ($expert !== null) {
            $approved = $decision === self::APPROVED;
            Notifications::toUser((string) $expert['id'], $approved ? 'approved' : 'returned',
                $approved ? 'پرونده تأیید شد' : 'پرونده برای اصلاح برگشت خورد',
                CaseLabels::code($report ?? []) . ($comment === '' ? '' : ' — ' . $comment), $reportId);
        }
        return null;
    }

    /**
     * ارسال پرونده به یک واحد از پنل وب: راه «سامانه»، با مدارک انتخاب‌شده (شناسه
     * تصویر و پیوست، همان که پرتال واحد می‌خواند) و مهلت به روز.
     *
     * @param list<string> $items
     */
    public static function dispatch(string $reportId, int $unit, array $items, string $note, ?int $deadlineDays,
                                    int $format, array $manager): ?string
    {
        if (!isset(UNIT_NAMES[$unit])) {
            return 'واحد مقصد را انتخاب کنید.';
        }
        if ($deadlineDays !== null && ($deadlineDays < 1 || $deadlineDays > 365)) {
            return 'مهلت را بین ۱ تا ۳۶۵ روز بنویسید.';
        }
        if (mb_strlen(trim($note)) > 2000) {
            return 'توضیح بیش از حد بلند است.';
        }
        $report = Db::one('SELECT id, tracking_code, temp_code FROM reports WHERE id = ?', [$reportId]);
        if ($report === null) {
            return 'پرونده پیدا نشد.';
        }
        // فقط مدارک همین پرونده؛ شناسه‌ای که مال پرونده دیگری است دور ریخته می‌شود.
        $own = array_column(Db::all('SELECT id FROM media WHERE report_id = ? UNION ALL SELECT id FROM attachments WHERE report_id = ?',
            [$reportId, $reportId]), 'id');
        $items = array_values(array_intersect(array_map('strval', $items), $own));
        $now = Db::now();
        Db::run(
            'INSERT INTO dispatches (id, report_id, unit, included_items, note, output_format, dispatched_at, sent_by,
                                     channel, deadline_at, status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0)',
            [Db::uuid(), $reportId, $unit, json_encode($items), trim($note), $format === 1 ? 1 : 0, $now, $manager['id'],
             $deadlineDays === null ? null : $now + $deadlineDays * 86_400_000]
        );
        Notifications::toUnit($unit, 'dispatch', 'مدارک جدید دریافت شد',
            'پرونده ' . CaseLabels::code($report) . ' برای واحد شما ارسال شد.', $reportId);
        return null;
    }

    /**
     * عملکرد هر واحد در یک بازه (زمان ارسال): رسیده، دیده‌شده، پاسخ‌داده، مهلت گذشته،
     * پاسخ به‌موقع و میانگین زمان پاسخ به ساعت. همان که اپ مدیر از /stats/units می‌گیرد.
     */
    public static function unitStats(int $from, int $to): array
    {
        $rows = Db::all(
            'SELECT unit, COUNT(*) AS sent, SUM(status >= 1) AS seen, SUM(status = 2) AS answered,
                    SUM(deadline_at IS NOT NULL AND status <> 2 AND deadline_at < ?) AS overdue,
                    SUM(deadline_at IS NOT NULL AND status = 2 AND answered_at <= deadline_at) AS on_time,
                    AVG(CASE WHEN answered_at IS NOT NULL THEN answered_at - dispatched_at END) AS avg_answer_millis
             FROM dispatches WHERE dispatched_at BETWEEN ? AND ? GROUP BY unit ORDER BY unit',
            [Db::now(), $from, $to]
        );
        foreach ($rows as &$row) {
            $row['answer_rate'] = round(((int) $row['answered'] / max(1, (int) $row['sent'])) * 100, 1);
            $row['avg_answer_hours'] = $row['avg_answer_millis'] === null ? null
                : round(((float) $row['avg_answer_millis']) / 3_600_000, 1);
            unset($row['avg_answer_millis']);
        }
        return $rows;
    }

    /** ارسال‌هایی که مهلتشان گذشته و هنوز پاسخ ندارند، قدیمی‌ترین مهلت اول. */
    public static function overdueDispatches(int $limit = 50): array
    {
        return Db::all(
            'SELECT d.id, d.report_id, d.unit, d.dispatched_at, d.deadline_at, d.status, r.tracking_code, r.temp_code
             FROM dispatches d JOIN reports r ON r.id = d.report_id
             WHERE d.deadline_at IS NOT NULL AND d.status <> 2 AND d.deadline_at < ?
             ORDER BY d.deadline_at LIMIT ' . max(1, $limit),
            [Db::now()]
        );
    }
}
