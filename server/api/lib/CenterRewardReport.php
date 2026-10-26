<?php
declare(strict_types=1);

/**
 * سه گزارش پاداش از سطرهای آمار یک بازه، هر کدام یک مدل که اکسل رسمی (OfficialXlsx)،
 * Word رسمی (OfficialDocx) و اپ (forApp) از آن ساخته می‌شوند:
 *  - finder: پاداش عامل شناسایی (پرداخت توانیر)
 *  - enforcement: پاداش عوامل اجرایی — نیروی انتظامی و سپاه (پرداخت توانیر)
 *  - staff: «گزارش پاداش عوامل اداری»، درصد مبلغ صورتحساب با سهم مدیرعامل و کارکنان
 *
 * مقدمه هر گزارش دستورالعمل‌هایی را که در این بازه به کار رفت با شماره، شناسه و تاریخ نامه
 * و شرح محاسبه می‌آورد. مبلغ هر مرکز همان مقدار نهایی ستون آمار است.
 */
final class CenterRewardReport
{
    public const KINDS = ['finder' => 'پاداش عامل شناسایی', 'enforcement' => 'پاداش عوامل اجرایی',
        'staff' => 'گزارش پاداش عوامل اداری'];
    private const SIGNS = ['تهیه‌کننده', 'دبیر کمیسیون رمزارز', 'مدیر حراست', 'تأیید مدیرعامل'];

    public static function model(string $kind, array $rows, int $from, int $to): array
    {
        $model = match ($kind) {
            'enforcement' => self::enforcement($rows),
            'staff' => self::staff($rows),
            default => self::finder($rows),
        };
        $title = self::KINDS[$kind] ?? self::KINDS['finder'];
        return $model + [
            'title' => $title . ' — مراکز استخراج رمزارز',
            'sheet' => $title,
            'subtitle' => 'از ' . Jalali::format($from, false) . ' تا ' . Jalali::format($to, false),
            'signatures' => self::SIGNS,
        ];
    }

    private static function finder(array $rows): array
    {
        $lines = [];
        $people = [];
        $rules = [];
        $sum = ['miners' => 0, 'amount' => 0.0];
        foreach ($rows as $row) {
            $v = $row['values'];
            $amount = (float) ($v['finder_reward'] ?? 0);
            if ($amount <= 0) {
                continue;
            }
            $rule = CenterRewards::finder($v, $row['row_date'])['rule'] ?? null;
            $rule !== null && $rules[$rule['id']] = $rule;
            $name = (string) ($v['finder_name'] ?? '') ?: '—';
            $lines[] = [count($lines) + 1, self::text($v['subscription'] ?? null), self::text($v['subscriber'] ?? null),
                self::date($v['report_date'] ?? null), self::text($v['county'] ?? null), $name, self::organ($v),
                self::text($v['sheba'] ?? null), (int) ($v['miners'] ?? 0), $amount];
            $key = $name . '|' . ($v['sheba'] ?? '');
            $people[$key] ??= [$name, self::text($v['sheba'] ?? null), 0, 0.0];
            $people[$key][2]++;
            $people[$key][3] += $amount;
            $sum['miners'] += (int) ($v['miners'] ?? 0);
            $sum['amount'] += $amount;
        }
        return [
            'paragraphs' => self::intro('پاداش عامل شناسایی (گزارش‌دهنده) هر مرکز کشف‌شده بر اساس تعداد دستگاه‌های کشف‌شده و دستورالعمل جاری در تاریخ گزارش همان مرکز حساب شده و پرداخت آن با شرکت توانیر است.', $rules),
            'tables' => [
                ['header' => ['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', 'شهرستان / بخش', 'عامل شناسایی',
                    'ارگان شناسایی‌کننده', 'شماره شبا', 'تعداد ماینر', 'مبلغ پاداش (ریال)'],
                    'rows' => $lines, 'total' => ['جمع کل', '', '', '', '', '', '', '', $sum['miners'], $sum['amount']],
                    'widths' => [4, 9, 14, 8, 10, 13, 12, 18, 7, 12], 'right' => [2, 5]],
                ['caption' => 'جمع هر عامل شناسایی', 'header' => ['نام و نام خانوادگی', 'شماره شبا', 'تعداد مراکز', 'مبلغ پاداش (ریال)'],
                    'rows' => array_values($people), 'widths' => [16, 20, 8, 12], 'right' => [0]],
            ],
            'totals' => $sum,
        ];
    }

    private static function enforcement(array $rows): array
    {
        $attendees = CenterSystem::attendees(array_values(array_filter(array_column($rows, 'report_id'))));
        $orgNames = array_values(RewardRules::ENFORCERS);
        $lines = [];
        $people = [];
        $rules = [];
        $sum = ['amount' => 0.0] + array_fill_keys($orgNames, 0.0);
        foreach ($rows as $row) {
            $v = $row['values'];
            $amount = (float) ($v['police_reward'] ?? 0);
            if ($amount <= 0) {
                continue;
            }
            $calc = CenterRewards::enforcement($v, $row['row_date'], $attendees[$row['report_id']] ?? []);
            $calc !== null && $rules[$calc['rule']['id']] = $calc['rule'];
            // مبلغی که مدیر دستی نوشته، به همان نسبت محاسبه میان سازمان‌ها و افراد پخش می‌شود.
            $factor = ($calc['amount'] ?? 0) > 0 ? $amount / $calc['amount'] : 0.0;
            $line = [count($lines) + 1, self::text($v['subscription'] ?? null), self::text($v['subscriber'] ?? null),
                self::date($v['report_date'] ?? null), (int) ($v['miners'] ?? 0), $amount];
            foreach ($orgNames as $org) {
                $share = round(($calc['orgs'][$org] ?? 0) * $factor);
                $line[] = $share;
                $sum[$org] += $share;
            }
            $line[] = implode('، ', array_map(static fn ($p) => $p['name'] . ' (' . $p['org'] . ')', $calc['people'] ?? [])) ?: '—';
            $lines[] = $line;
            foreach ($calc['people'] ?? [] as $p) {
                $key = $p['name'] . '|' . $p['org'];
                $people[$key] ??= [$p['name'], $p['org'], 0, 0.0];
                $people[$key][2]++;
                $people[$key][3] += round($p['share'] * $factor);
            }
            $sum['amount'] += $amount;
        }
        return [
            'paragraphs' => self::intro('پاداش عوامل اجرایی فقط به نیروی انتظامی (پلیس امنیت اقتصادی) و اطلاعات سپاه حاضر در بازدید تعلق می‌گیرد: اگر هر دو سازمان حاضر بودند مبلغ مرکز میان دو سازمان برابر و سهم هر سازمان میان حاضران همان سازمان برابر تقسیم شده است. حاضران از صورت بازدید کارشناس پس از تأیید مدیر آمده‌اند و پرداخت با شرکت توانیر است.', $rules),
            'tables' => [
                ['header' => array_merge(['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', 'تعداد ماینر', 'مبلغ مرکز (ریال)'],
                    array_map(static fn ($o) => 'سهم ' . $o, $orgNames), ['حاضران']),
                    'rows' => $lines, 'total' => array_merge(['جمع کل', '', '', '', '', $sum['amount']],
                        array_map(static fn ($o) => $sum[$o], $orgNames), ['']),
                    'widths' => [4, 9, 14, 8, 7, 11, 11, 11, 24], 'right' => [2, 8]],
                ['caption' => 'سهم هر نفر', 'header' => ['نام و نام خانوادگی', 'سازمان', 'تعداد مراکز', 'مبلغ پاداش (ریال)'],
                    'rows' => array_values($people), 'widths' => [16, 16, 8, 12], 'right' => [0]],
            ],
            'totals' => $sum,
        ];
    }

    private static function staff(array $rows): array
    {
        $lines = [];
        $roles = [];
        $people = [];
        $rules = [];
        $sum = ['base' => 0.0, 'amount' => 0.0, 'ceo' => 0.0, 'staff' => 0.0];
        $calcs = [];
        foreach ($rows as $row) {
            $staff = CenterRewards::staff($row['values'], $row['row_date']);
            if ($staff !== null && $staff['base'] > 0) {
                $calcs[] = [$row, $staff];
                $rules[$staff['rule']['id']] = $staff['rule'];
                foreach ($staff['roles'] as $role) {
                    $roles[$role['label']] = true;
                }
            }
        }
        $roles = array_keys($roles);
        foreach ($calcs as [$row, $staff]) {
            $v = $row['values'];
            $byRole = array_column($staff['roles'], 'amount', 'label');
            $lines[] = array_merge([count($lines) + 1, self::text($v['subscription'] ?? null), self::text($v['subscriber'] ?? null),
                self::date($v['report_date'] ?? null), $staff['base'], $staff['amount'], $staff['ceo'], $staff['staff']],
                array_map(static fn ($r) => (float) ($byRole[$r] ?? 0), $roles));
            foreach ($staff['roles'] as $role) {
                foreach ($role['people'] as $name) {
                    $key = $name . '|' . $role['label'];
                    $people[$key] ??= [$name, $role['label'], 0, 0.0];
                    $people[$key][2]++;
                    $people[$key][3] += $role['each'];
                }
            }
            foreach (array_keys($sum) as $k) {
                $sum[$k] += $staff[$k];
            }
        }
        $roleTotals = array_map(static fn ($i) => array_sum(array_column($lines, 8 + $i)), array_keys($roles));
        return [
            'paragraphs' => self::intro('پاداش عوامل اداری درصدی از مبلغ صورتحساب هر مرکز است: سهم مدیرعامل جدا و سهم کارکنان به وزن هر نقش (اعضای کمیسیون رمزارز، مجری رمزارز، کارشناسان استخراج و تحلیل لاگ و…) و میان دارندگان هر نقش برابر تقسیم شده است.', $rules),
            'tables' => [
                ['header' => array_merge(['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', 'مبنای پاداش (ریال)', 'پاداش کل',
                    'سهم مدیرعامل', 'سهم کارکنان'], $roles),
                    'rows' => $lines, 'total' => array_merge(['جمع کل', '', '', '', $sum['base'], $sum['amount'], $sum['ceo'], $sum['staff']], $roleTotals),
                    'widths' => array_merge([4, 9, 14, 8, 12, 10, 10, 10], array_fill(0, count($roles), 9)), 'right' => [2]],
                ['caption' => 'جمع هر نفر', 'header' => ['نام و نام خانوادگی', 'نقش', 'تعداد مراکز', 'مبلغ پاداش (ریال)'],
                    'rows' => array_values($people), 'widths' => [16, 16, 8, 12], 'right' => [0, 1]],
            ],
            'totals' => $sum,
        ];
    }

    /** برای اپ: هر خانه همان متنی که نشان داده می‌شود. */
    public static function forApp(array $model): array
    {
        $show = static fn (array $line) => array_map([OfficialDocx::class, 'show'], array_values($line));
        $model['tables'] = array_map(static fn ($t) => ['caption' => $t['caption'] ?? null, 'header' => $t['header'],
            'rows' => array_map($show, $t['rows']), 'total' => isset($t['total']) ? $show($t['total']) : null], $model['tables']);
        unset($model['totals']);
        return $model;
    }

    /** @param array<string, array> $rules */
    private static function intro(string $lead, array $rules): array
    {
        $out = [$lead];
        foreach ($rules as $rule) {
            $out[] = RewardRules::explain($rule);
        }
        if ($rules === []) {
            $out[] = 'در این بازه دستورالعملی برای محاسبه به کار نرفته است؛ مبلغ‌ها همان است که در آمار ثبت شده.';
        }
        return $out;
    }

    private static function organ(array $v): string
    {
        $names = [];
        foreach (['org_sepah', 'org_vaja', 'org_faraja'] as $k) {
            if (!empty($v[$k])) {
                $names[] = CenterColumns::label($k);
            }
        }
        if (!empty($v['org_other'])) {
            $names[] = (string) $v['org_other'];
        }
        return implode('، ', $names) ?: '—';
    }

    private static function text(mixed $value): string
    {
        return $value === null || $value === '' ? '—' : (string) $value;
    }

    private static function date(mixed $millis): string
    {
        return $millis === null ? '—' : Jalali::latin(Jalali::format((int) $millis, false));
    }
}
