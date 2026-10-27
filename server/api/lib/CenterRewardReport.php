<?php
declare(strict_types=1);

/**
 * چهار گزارش تجمیعی پاداش از سطرهای آمار یک بازه، هر کدام یک مدل که اکسل رسمی (OfficialXlsx)،
 * Word رسمی (OfficialDocx) و اپ (forApp) از آن ساخته می‌شوند:
 *  - finder: پاداش عامل شناسایی (پرداخت توانیر)
 *  - collection: پاداش جمع‌آوری شرکت توزیع
 *  - enforcement: پاداش عوامل انتظامی — نیروی انتظامی (توانیر) و سپاه (شرکت توزیع)
 *  - staff: پاداش عوامل اداری، درصد مبلغ صورتحساب با سهم مدیرعامل و درصد هر نفر
 *
 * افراد و سهم‌ها همان فرم پاداش هر مرکز است (RewardPeople، RewardForms)؛ فرم تک‌صفحه‌ای هر مرکز
 * جداست (RewardFormModel). مقدمه هر گزارش دستورالعمل‌هایی را که در این بازه به کار رفت می‌آورد.
 */
final class CenterRewardReport
{
    public const KINDS = ['finder' => 'پاداش عامل شناسایی', 'collection' => 'پاداش جمع‌آوری شرکت توزیع',
        'enforcement' => 'پاداش عوامل انتظامی', 'staff' => 'پاداش عوامل اداری'];
    private const COLUMN = ['collection' => 'collection_reward', 'enforcement' => 'police_reward', 'staff' => 'staff_reward'];
    private const LEADS = [
        'finder' => 'پاداش عامل شناسایی (گزارش‌دهنده) هر مرکز کشف‌شده بر اساس تعداد دستگاه‌های کشف‌شده و دستورالعمل جاری در تاریخ گزارش همان مرکز حساب شده و پرداخت آن با شرکت توانیر است.',
        'collection' => 'پاداش جمع‌آوری هر مرکز به‌طور مساوی میان پرسنل حاضر در جمع‌آوری و افرادی که مدیر افزوده تقسیم شده و پرداخت آن با شرکت توزیع است.',
        'enforcement' => 'پاداش عوامل انتظامی هر مرکز به‌طور مساوی میان همه پرسنل حاضر نیروی انتظامی و سپاه تقسیم شده است (نه میان سازمان‌ها)؛ سهم نیروی انتظامی را شرکت توانیر و سهم سپاه را شرکت توزیع می‌پردازد.',
        'staff' => 'پاداش عوامل اداری درصدی از مبلغ صورتحساب هر مرکز است: سهم مدیرعامل جدا و سهم کارکنان به درصدی که مدیر برای هر نفر در فرم همان مرکز تعیین کرده تقسیم شده است.',
    ];

    public static function model(string $kind, array $rows, int $from, int $to): array
    {
        $kind = isset(self::KINDS[$kind]) ? $kind : 'finder';
        $model = $kind === 'finder' ? self::finder($rows) : self::shared($kind, $rows);
        return $model + [
            'title' => self::KINDS[$kind] . ' — مراکز استخراج رمزارز',
            'sheet' => self::KINDS[$kind],
            'subtitle' => 'از ' . Jalali::format($from, false) . ' تا ' . Jalali::format($to, false),
            'letterhead' => RewardFormModel::LETTERHEAD,
            'signatures' => RewardFormModel::SIGNATURES,
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
            $rule = $row['rewards'][RewardRules::FINDER]['calc']['rule'] ?? null;
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
            'paragraphs' => self::intro(self::LEADS['finder'], $rules),
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

    /** جمع‌آوری، عوامل انتظامی و عوامل اداری: یک سطر برای هر مرکز و جمع هر نفر. */
    private static function shared(string $kind, array $rows): array
    {
        $type = array_search($kind, RewardRules::KEYS, true);
        $groups = match ($kind) {
            'enforcement' => [RewardRules::ENFORCERS['faraja'] => 'faraja', RewardRules::ENFORCERS['sepah'] => 'sepah'],
            'staff' => ['سهم مدیرعامل' => 'ceo', 'سهم کارکنان' => 'staff'],
            default => [],
        };
        $lines = [];
        $people = [];
        $rules = [];
        $sum = ['count' => 0, 'amount' => 0.0, 'base' => 0.0] + array_fill_keys(array_values($groups), 0.0);
        foreach ($rows as $row) {
            $state = $row['rewards'][$type];
            $calc = $state['calc'];
            $amount = (float) ($row['values'][self::COLUMN[$kind]] ?? 0);
            if ($calc === null || $amount <= 0) {
                continue;
            }
            $rules[$calc['rule']['id']] = $calc['rule'];
            // مبلغی که مدیر در آمار دستی نوشته، به همان نسبت میان افراد پخش می‌شود.
            $factor = (float) $calc['amount'] > 0 ? $amount / (float) $calc['amount'] : 0.0;
            $v = $row['values'];
            $line = [count($lines) + 1, self::text($v['subscription'] ?? null), self::text($v['subscriber'] ?? null),
                self::date($v['report_date'] ?? null)];
            $line[] = $kind === 'staff' ? (float) $calc['base'] : (int) $calc['count'];
            $line[] = $amount;
            foreach ($groups as $g) {
                $part = $kind === 'staff' ? (float) $calc[$g]
                    : array_sum(array_column(array_filter($calc['people'], static fn ($p) => $p['org'] === $g), 'share'));
                $line[] = round($part * $factor);
                $sum[$g] += round($part * $factor);
            }
            $line[] = count($calc['people']);
            $line[] = self::text($state['final_at'] !== null ? $state['number'] : 'پیش‌نویس');
            $lines[] = $line;
            foreach ($calc['people'] as $p) {
                $group = $kind === 'enforcement' ? RewardRules::ENFORCERS[$p['org']] : ($p['post'] ?: '—');
                $key = $p['name'] . '|' . $group;
                $people[$key] ??= [$p['name'] ?: '—', $group, 0, 0.0];
                $people[$key][2]++;
                $people[$key][3] += round((float) $p['share'] * $factor);
            }
            $sum['count'] += $kind === 'staff' ? 0 : (int) $calc['count'];
            $sum['base'] += $kind === 'staff' ? (float) $calc['base'] : 0;
            $sum['amount'] += $amount;
        }
        $head = $kind === 'staff' ? 'مبنای پاداش (ریال)' : 'تعداد دستگاه';
        $header = array_merge(['ردیف', 'شماره اشتراک', 'نام مشترک متخلف', 'تاریخ گزارش', $head, 'مبلغ مرکز (ریال)'],
            array_map(static fn ($g) => $kind === 'enforcement' ? 'سهم ' . $g . ' (' . RewardRules::ORG_PAYER[$groups[$g]] . ')' : $g,
                array_keys($groups)), ['نفرات', 'شماره فرم']);
        $total = array_merge(['جمع کل', '', '', '', $kind === 'staff' ? $sum['base'] : $sum['count'], $sum['amount']],
            array_map(static fn ($g) => $sum[$g], array_values($groups)), ['', '']);
        return [
            'paragraphs' => self::intro(self::LEADS[$kind], $rules),
            'tables' => [
                ['header' => $header, 'rows' => $lines, 'total' => $total, 'right' => [2],
                    'widths' => array_merge([4, 9, 14, 8, 9, 11], array_fill(0, count($groups), 11), [5, 9])],
                ['caption' => 'جمع هر نفر', 'header' => ['نام و نام خانوادگی', $kind === 'enforcement' ? 'سازمان' : 'سمت / پست', 'تعداد مراکز',
                    'مبلغ پاداش (ریال)'], 'rows' => array_values($people), 'widths' => [16, 16, 8, 12], 'right' => [0, 1]],
            ],
            'totals' => $sum,
        ];
    }

    /** برای اپ: هر خانه همان متنی که نشان داده می‌شود. */
    public static function forApp(array $model): array
    {
        $show = static fn (array $line) => array_map([OfficialDocx::class, 'show'], array_values($line));
        $model['tables'] = array_map(static fn ($t) => ['caption' => $t['caption'] ?? null, 'header' => $t['header'] ?? null,
            'pairs' => !empty($t['pairs']), 'rows' => array_map($show, $t['rows']),
            'total' => isset($t['total']) ? $show($t['total']) : null], $model['tables']);
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
