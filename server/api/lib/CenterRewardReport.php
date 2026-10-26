<?php
declare(strict_types=1);

/**
 * گزارش‌های پاداش از سطرهای آمار یک بازه:
 *  - «گزارش پاداش عوامل اداری»: برای هر مرکز مبنا (مبلغ صورتحساب)، پاداش کل، سهم
 *    مدیرعامل، سهم کارکنان و سهم هر نقش؛ و جمع هر نفر در همه مراکز
 *  - «پاداش عامل شناسایی و عوامل اجرایی» (پرداخت توانیر): مبلغ هر مرکز و سهم هر حاضر
 *
 * هر سطر دستورالعملی را که با آن حساب شد نشان می‌دهد (شماره، شناسه و تاریخ نامه).
 */
final class CenterRewardReport
{
    /** @return array{lines:list<array>, roles:list<string>, people:list<array>, totals:array<string, float>} */
    public static function staff(array $rows): array
    {
        $lines = [];
        $roles = [];
        $people = [];
        $totals = ['base' => 0.0, 'amount' => 0.0, 'ceo' => 0.0, 'staff' => 0.0];
        foreach ($rows as $row) {
            $staff = CenterRewards::staff($row['values'], $row['row_date']);
            if ($staff === null || $staff['base'] <= 0) {
                continue;
            }
            $byRole = [];
            foreach ($staff['roles'] as $role) {
                $roles[$role['label']] = true;
                $byRole[$role['label']] = $role['amount'];
                foreach ($role['people'] as $name) {
                    $key = $name . '|' . $role['label'];
                    $people[$key] ??= ['name' => $name, 'role' => $role['label'], 'amount' => 0.0, 'centers' => 0];
                    $people[$key]['amount'] += $role['each'];
                    $people[$key]['centers']++;
                }
            }
            $lines[] = ['row' => $row['values']['row'] ?? null, 'id' => $row['id'], 'values' => $row['values'],
                'base' => $staff['base'], 'amount' => $staff['amount'], 'ceo' => $staff['ceo'], 'staff' => $staff['staff'],
                'roles' => $byRole, 'rule' => RewardRules::describe($staff['rule']),
                'pct' => $staff['rule']['spec']['pct'], 'ceo_pct' => $staff['rule']['spec']['ceo_pct'],
                'staff_pct' => $staff['rule']['spec']['staff_pct']];
            foreach (['base', 'amount', 'ceo', 'staff'] as $k) {
                $totals[$k] += $staff[$k];
            }
        }
        usort($people, static fn ($a, $b) => $b['amount'] <=> $a['amount']);
        return ['lines' => $lines, 'roles' => array_keys($roles), 'people' => array_values($people), 'totals' => $totals];
    }

    /** @return array{lines:list<array>, people:list<array>, totals:array<string, float>} */
    public static function agents(array $rows): array
    {
        $ids = array_values(array_filter(array_column($rows, 'report_id')));
        $attendees = CenterSystem::attendees($ids);
        $lines = [];
        $people = [];
        $totals = ['finder' => 0.0, 'police' => 0.0, 'miners' => 0];
        foreach ($rows as $row) {
            $v = $row['values'];
            $finder = CenterRewards::finder($v, $row['row_date']);
            $police = CenterRewards::enforcement($v, $row['row_date'], $attendees[$row['report_id']] ?? []);
            $finderAmount = (float) ($v['finder_reward'] ?? 0);
            $policeAmount = (float) ($v['police_reward'] ?? 0);
            if ($finderAmount <= 0 && $policeAmount <= 0) {
                continue;
            }
            foreach ($police['people'] ?? [] as $p) {
                $key = $p['name'] . '|' . $p['org'];
                $people[$key] ??= ['name' => $p['name'], 'org' => $p['org'], 'amount' => 0.0, 'centers' => 0];
                $people[$key]['amount'] += $p['share'];
                $people[$key]['centers']++;
            }
            $lines[] = ['row' => $v['row'] ?? null, 'values' => $v, 'finder' => $finderAmount, 'police' => $policeAmount,
                'people' => $police['people'] ?? [],
                'finder_rule' => RewardRules::describe($finder['rule'] ?? null),
                'police_rule' => RewardRules::describe($police['rule'] ?? null)];
            $totals['finder'] += $finderAmount;
            $totals['police'] += $policeAmount;
            $totals['miners'] += (int) ($v['miners'] ?? 0);
        }
        usort($people, static fn ($a, $b) => $b['amount'] <=> $a['amount']);
        return ['lines' => $lines, 'people' => array_values($people), 'totals' => $totals];
    }

    /** برگه‌های اکسل پاداش عوامل اداری. @return list<array{0:string, 1:list<string>, 2:list<list<mixed>>}> */
    public static function staffBook(array $report): array
    {
        $header = array_merge(['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', 'مبنای پاداش (ریال)', 'درصد کل',
            'پاداش کل (ریال)', 'سهم مدیرعامل (ریال)', 'سهم کارکنان (ریال)'], $report['roles'], ['دستورالعمل']);
        $rows = [];
        foreach ($report['lines'] as $line) {
            $rows[] = array_merge([$line['row'], $line['values']['subscription'] ?? null, $line['values']['subscriber'] ?? null,
                self::date($line['values']['report_date'] ?? null), $line['base'], $line['pct'], $line['amount'], $line['ceo'], $line['staff']],
                array_map(static fn ($r) => $line['roles'][$r] ?? null, $report['roles']), [$line['rule']]);
        }
        $t = $report['totals'];
        $rows[] = array_merge(['جمع کل', null, null, null, $t['base'], null, $t['amount'], $t['ceo'], $t['staff']],
            array_map(static fn ($r) => array_sum(array_map(static fn ($l) => $l['roles'][$r] ?? 0, $report['lines'])), $report['roles']), [null]);
        $people = array_map(static fn ($p) => [$p['name'], $p['role'], $p['centers'], $p['amount']], $report['people']);
        return [
            ['پاداش عوامل اداری', $header, $rows],
            ['جمع هر نفر', ['نام', 'نقش', 'تعداد مراکز', 'پاداش (ریال)'], $people],
        ];
    }

    /** برگه‌های اکسل پاداش عامل شناسایی و عوامل اجرایی. */
    public static function agentsBook(array $report): array
    {
        $rows = [];
        foreach ($report['lines'] as $line) {
            $v = $line['values'];
            $rows[] = [$line['row'], $v['subscription'] ?? null, $v['subscriber'] ?? null, self::date($v['report_date'] ?? null),
                $v['miners'] ?? null, $v['finder_name'] ?? null, $v['sheba'] ?? null, $line['finder'], $line['police'],
                implode('، ', array_map(static fn ($p) => $p['name'] . ' (' . $p['org'] . ')', $line['people'])),
                $line['finder_rule'], $line['police_rule']];
        }
        $t = $report['totals'];
        $rows[] = ['جمع کل', null, null, null, $t['miners'], null, null, $t['finder'], $t['police'], null, null, null];
        return [
            ['پاداش شناسایی و اجرایی', ['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', 'تعداد ماینر', 'عامل شناسایی',
                'شماره شبا', 'پاداش عامل شناسایی (ریال)', 'پاداش عوامل اجرایی (ریال)', 'عوامل اجرایی حاضر', 'دستورالعمل شناسایی',
                'دستورالعمل اجرایی'], $rows],
            ['سهم هر عامل اجرایی', ['نام', 'نهاد', 'تعداد مراکز', 'پاداش (ریال)'],
                array_map(static fn ($p) => [$p['name'], $p['org'], $p['centers'], $p['amount']], $report['people'])],
        ];
    }

    private static function date(mixed $millis): ?string
    {
        return $millis === null ? null : Jalali::latin(Jalali::format((int) $millis, false));
    }
}
