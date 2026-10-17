<?php
declare(strict_types=1);

/**
 * میز پیگیری مدیر و مجری: پرونده‌های باز محدوده، هر کدام با مرحله‌ای که در آن
 * مانده و اینکه دیر شده یا نه. مرحله ذخیره نمی‌شود؛ از ستون‌های گردش و پرونده
 * خوانده می‌شود تا هرگز با واقعیت اختلاف پیدا نکند.
 */
final class CaseBoard
{
    public const STAGES = [
        'awaiting_referral' => 'در انتظار ارجاع مدیر',
        'with_executor' => 'نزد مجری؛ هنوز به کارشناس نرسیده',
        'with_expert' => 'نزد کارشناس؛ بازدید نشده',
        'in_progress' => 'در دست کارشناس',
        'awaiting_approval' => 'در انتظار تأیید',
        'returned' => 'برگشت برای اصلاح',
        'approved' => 'تأییدشده؛ هنوز به واحدی نرفته',
    ];

    /** مرحله یک پرونده باز، یا تهی اگر کاری روی آن نمانده. */
    public static function stage(array $row): ?string
    {
        $approval = (int) ($row['approval_state'] ?? 0);
        if ($approval === 1) {
            return 'awaiting_approval';
        }
        if ($approval === 3) {
            return 'returned';
        }
        if ($approval === 2) {
            return $row['dispatched_at'] === null ? 'approved' : null;
        }
        if ((int) $row['status'] >= 1) {
            return 'in_progress';
        }
        if ($row['expert_id'] !== null) {
            return 'with_expert';
        }
        if ($row['executor_id'] !== null) {
            return 'with_executor';
        }
        return (int) ($row['origin'] ?? 0) === CaseIntake::ORIGIN_MANAGER ? 'awaiting_referral' : 'in_progress';
    }

    /** پرونده‌های باز محدوده، دیرشده‌ها و قدیمی‌ترها اول. */
    public static function open(Scope $scope, int $limit = 300): array
    {
        [$where, $params] = $scope->reportWhere('r');
        $rows = Db::all(
            "SELECT r.id, r.tracking_code, r.temp_code, r.report_type, r.status, r.approval_state, r.area_code, r.county,
                    r.address, r.created_at, r.expert_code, f.origin, f.intake_code, f.executor_id, f.expert_id, f.due_at,
                    f.refer_due_at, f.dispatched_at, f.expert_alert_at, f.manager_alert_at, f.auto_referred,
                    x.full_name AS executor_name, p.full_name AS expert_name
             FROM reports r LEFT JOIN case_flow f ON f.report_id = r.id
             LEFT JOIN users x ON x.id = f.executor_id LEFT JOIN users p ON p.id = f.expert_id
             WHERE $where AND r.status < 2 AND (r.approval_state <> 2 OR f.dispatched_at IS NULL)
             ORDER BY r.created_at LIMIT " . max(1, $limit),
            $params
        );
        $now = Db::now();
        $out = [];
        foreach ($rows as $row) {
            $stage = self::stage($row);
            if ($stage === null) {
                continue;
            }
            $late = $row['manager_alert_at'] !== null || $row['expert_alert_at'] !== null
                || ($stage === 'with_expert' && $row['due_at'] !== null && (int) $row['due_at'] < $now);
            $out[] = [
                'id' => $row['id'],
                'code' => CaseLabels::code($row) ?: (string) $row['intake_code'],
                'type' => CaseLabels::REPORT_TYPES[(int) $row['report_type']] ?? '',
                'area' => Areas::label($row['area_code']),
                'address' => $row['address'],
                'created_at' => (int) $row['created_at'],
                'stage' => $stage,
                'stage_name' => self::STAGES[$stage],
                'executor' => $row['executor_name'],
                'expert' => $row['expert_name'],
                'due_at' => $row['due_at'] === null ? null : (int) $row['due_at'],
                'refer_due_at' => $row['refer_due_at'] === null ? null : (int) $row['refer_due_at'],
                'auto_referred' => (int) ($row['auto_referred'] ?? 0) === 1,
                'late' => $late,
            ];
        }
        usort($out, static fn ($a, $b) => [$b['late'], $a['created_at']] <=> [$a['late'], $b['created_at']]);
        return $out;
    }

    /** شمار هر مرحله، برای کاشی‌های داشبورد. */
    public static function counts(array $open): array
    {
        $counts = array_fill_keys(array_keys(self::STAGES), 0);
        $late = 0;
        foreach ($open as $row) {
            $counts[$row['stage']]++;
            $late += $row['late'] ? 1 : 0;
        }
        return ['stages' => $counts, 'late' => $late];
    }
}
