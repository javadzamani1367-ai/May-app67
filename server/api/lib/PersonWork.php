<?php
declare(strict_types=1);

/**
 * کار یک شخص در یک بازه: اول محل خدمت اصلی‌اش، بعد هر شهرستان دیگر؛ در هر شهرستان
 * هر نقش جدا، با شمارش‌های همان نقش و امتیاز ۱۰۰ همان حوزه. هر عدد از کاربری همان
 * نقش در همان ناحیه می‌آید، پس کار دو شهرستان هرگز با هم جمع نمی‌شود.
 */
final class PersonWork
{
    /** @return list<array{area: string, name: string, home: bool, roles: list<array<string, mixed>>}> */
    public static function of(string $personId, int $from, int $to): array
    {
        $person = Persons::find($personId);
        if ($person === null) {
            return [];
        }
        $boards = [];
        $manager = new Scope(['role' => Auth::ROLE_MANAGER, 'areas' => '']);
        $score = static function (int $kind, string $table, string $userId) use (&$boards, $manager, $from, $to): ?int {
            $boards[$kind] ??= (new ScoreBoard($from, $to, $manager, Db::now(), $kind))->all();
            foreach ($boards[$kind][$table] as $row) {
                if ($row['id'] === $userId) {
                    return $row['score'] ?? null;
                }
            }
            return null;
        };
        $out = [];
        foreach (Persons::accounts($personId) as $account) {
            $key = $account['area'] ?? '-';
            $figures = self::figures($account, $from, $to);
            $scores = [];
            foreach (self::scoreTables($account) as [$kind, $table]) {
                $scores[] = ['kind' => CaseKind::NAMES[$kind], 'score' => $score($kind, $table, (string) $account['id'])];
            }
            $out[$key] ??= ['area' => $key, 'name' => $account['area_label'], 'home' => $key === ($person['home_area'] ?? null),
                'roles' => []];
            $out[$key]['roles'][] = ['user_code' => $account['user_code'], 'kind' => $account['kind'],
                'kind_name' => $account['kind_name'], 'active' => (int) $account['active'] === 1, 'figures' => $figures,
                'scores' => $scores];
        }
        $out = array_values($out);
        usort($out, static fn ($a, $b) => [$b['home'], $a['area']] <=> [$a['home'], $b['area']]);
        return $out;
    }

    /** @return list<array{0:int,1:string}> حوزه و جدول امتیاز هر نقش */
    private static function scoreTables(array $account): array
    {
        return match ((int) $account['role']) {
            Auth::ROLE_EXPERT => [[CaseKind::CRYPTO, 'experts'], [CaseKind::ILLEGAL, 'experts']],
            Auth::ROLE_EXECUTOR => [[CaseKind::CRYPTO, 'executors']],
            Auth::ROLE_ILLEGAL_AREA => [[CaseKind::ILLEGAL, 'executors']],
            Auth::ROLE_UNIT => [[CaseKind::CRYPTO, 'units'], [CaseKind::ILLEGAL, 'units']],
            default => [],
        };
    }

    /** @return list<array{label: string, value: int}> */
    private static function figures(array $account, int $from, int $to): array
    {
        $id = (string) $account['id'];
        $code = (string) $account['user_code'];
        $count = static fn (string $sql, array $params): int => (int) (Db::one($sql, $params)['n'] ?? 0);
        $p = [$from, $to];
        return match ((int) $account['role']) {
            Auth::ROLE_EXPERT => [
                ['label' => 'ارجاع‌شده', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow WHERE expert_id = ? AND assigned_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'بازدید رمزارز', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow f JOIN reports r ON r.id = f.report_id WHERE r.expert_code = ? AND r.case_kind = 0 AND f.visited_at BETWEEN ? AND ?', [$code, ...$p])],
                ['label' => 'بازرسی برق غیرمجاز', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow f JOIN reports r ON r.id = f.report_id WHERE r.expert_code = ? AND r.case_kind = 1 AND f.visited_at BETWEEN ? AND ?', [$code, ...$p])],
                ['label' => 'تأیید شد', 'value' => $count('SELECT COUNT(*) AS n FROM approvals a JOIN reports r ON r.id = a.report_id WHERE r.expert_code = ? AND a.decision = 1 AND a.decided_at BETWEEN ? AND ?', [$code, ...$p])],
                ['label' => 'برگشت خورد', 'value' => $count('SELECT COUNT(*) AS n FROM approvals a JOIN reports r ON r.id = a.report_id WHERE r.expert_code = ? AND a.decision = 2 AND a.decided_at BETWEEN ? AND ?', [$code, ...$p])],
                ['label' => 'دیرکرد', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow WHERE expert_id = ? AND expert_alert_at BETWEEN ? AND ?', [$id, ...$p])],
            ],
            Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL_AREA, Auth::ROLE_ILLEGAL, Auth::ROLE_MANAGER => [
                ['label' => 'سپرده به او', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow WHERE executor_id = ? AND executor_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'ارجاع به کارشناس', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow WHERE assigned_by = ? AND assigned_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'تصمیم تأیید', 'value' => $count('SELECT COUNT(*) AS n FROM approvals WHERE decided_by = ? AND decided_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'ارسال به واحد', 'value' => $count('SELECT COUNT(*) AS n FROM dispatches WHERE sent_by = ? AND dispatched_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'هشدار بی‌اقدامی', 'value' => $count('SELECT COUNT(*) AS n FROM case_flow WHERE executor_id = ? AND manager_alert_at BETWEEN ? AND ?', [$id, ...$p])],
            ],
            Auth::ROLE_UNIT => self::unitFigures($account, $p),
            Auth::ROLE_FIELD => [
                ['label' => 'مورد ارسال‌شده', 'value' => $count('SELECT COUNT(*) AS n FROM field_items WHERE user_id = ? AND received_at BETWEEN ? AND ?', [$id, ...$p])],
                ['label' => 'گزارش برق غیرمجاز', 'value' => $count('SELECT COUNT(*) AS n FROM field_items WHERE user_id = ? AND kind = ? AND received_at BETWEEN ? AND ?', [$id, Field::KIND_ILLEGAL, ...$p])],
            ],
            default => [],
        };
    }

    /** @param array{0:int,1:int} $p */
    private static function unitFigures(array $account, array $p): array
    {
        [$where, $params] = Scope::unitWhere($account);
        $row = Db::one(
            "SELECT COUNT(*) AS sent, SUM(d.status >= 1) AS seen, SUM(d.status = 2) AS answered,
                    SUM(d.deadline_at IS NOT NULL AND d.status <> 2 AND d.deadline_at < ?) AS overdue
             FROM dispatches d WHERE $where AND d.dispatched_at BETWEEN ? AND ?",
            array_merge([Db::now()], $params, $p)
        ) ?? [];
        return [
            ['label' => 'ارسال رسیده', 'value' => (int) ($row['sent'] ?? 0)],
            ['label' => 'دیده شد', 'value' => (int) ($row['seen'] ?? 0)],
            ['label' => 'پاسخ داد', 'value' => (int) ($row['answered'] ?? 0)],
            ['label' => 'مهلت گذشته', 'value' => (int) ($row['overdue'] ?? 0)],
        ];
    }
}
