<?php
declare(strict_types=1);

/**
 * حساب پاداش هر مرکز با دستورالعمل جاری در تاریخ گزارشش (RewardRules).
 *
 * تعداد دستگاه و مبلغ صورتحساب از مقدار نهایی سطر آمار می‌آیند و افراد از فرم پاداش همان
 * مرکز (RewardPeople): فقط کسانی که تیک دارند سهم می‌برند.
 *  - جمع‌آوری: مبلغ مرکز میان همه پرسنل تیک‌خورده برابر
 *  - عوامل انتظامی: مبلغ مرکز میان همه پرسنل تیک‌خورده نیروی انتظامی و سپاه برابر — یک نفر سپاه و
 *    چهار نفر انتظامی یعنی هر نفر یک پنجم؛ نه نیمی برای هر سازمان
 *  - عوامل اداری: سهم مدیرعامل جدا و سهم کارکنان به درصدی که برای هر نفر نوشته شده
 */
final class CenterRewards
{
    public static function finder(array $values, ?int $date): ?array
    {
        $rule = RewardRules::at(RewardRules::FINDER, $date);
        if ($rule === null) {
            return null;
        }
        $count = (int) ($values['miners'] ?? 0);
        return ['amount' => RewardRules::amount($rule['spec'], $count), 'pot' => RewardRules::amount($rule['spec'], $count),
            'count' => $count, 'rule' => $rule, 'people' => []];
    }

    /** @param list<array<string, mixed>> $people */
    public static function collection(array $values, ?int $date, array $people): ?array
    {
        $rule = RewardRules::at(RewardRules::COLLECTION, $date);
        return $rule === null ? null : self::equal($rule, $values, array_values(array_filter($people, [self::class, 'paid'])));
    }

    /** @param list<array<string, mixed>> $people */
    public static function enforcement(array $values, ?int $date, array $people): ?array
    {
        $rule = RewardRules::at(RewardRules::ENFORCEMENT, $date);
        if ($rule === null) {
            return null;
        }
        $orgs = $rule['spec']['orgs'] ?? array_keys(RewardRules::ENFORCERS);
        $eligible = array_values(array_filter($people, static fn ($p) => self::paid($p) && in_array($p['org'] ?? '', $orgs, true)));
        $out = self::equal($rule, $values, $eligible);
        $out['orgs'] = [];
        $out['payers'] = [];
        foreach ($out['people'] as $p) {
            $label = RewardRules::ENFORCERS[$p['org']];
            $out['orgs'][$label] = ($out['orgs'][$label] ?? 0) + $p['share'];
            $out['payers'][$p['payer']] = ($out['payers'][$p['payer']] ?? 0) + $p['share'];
        }
        return $out;
    }

    /**
     * عوامل اداری: کل = درصد مبنا (با سقف)، سهم مدیرعامل جدا و سهم کارکنان به درصد هر نفر.
     * @param list<array<string, mixed>> $people
     */
    public static function staff(array $values, ?int $date, array $people): ?array
    {
        $rule = RewardRules::at(RewardRules::STAFF, $date);
        if ($rule === null) {
            return null;
        }
        $spec = $rule['spec'];
        $base = match ($spec['base'] ?? 'energy') {
            'energy_damage' => (float) ($values['energy_amount'] ?? 0) + (float) ($values['damage_amount'] ?? 0),
            'collected' => (float) ($values['collected_amount'] ?? 0),
            default => (float) ($values['energy_amount'] ?? 0),
        };
        $pct = (float) $spec['pct'];
        $amount = $base * $pct / 100;
        $cap = (float) ($spec['cap'] ?? 0);
        if ($cap > 0) {
            $amount = min($amount, $cap);
        }
        $ceo = $pct > 0 ? $amount * (float) $spec['ceo_pct'] / $pct : 0.0;
        $staff = $amount - $ceo;
        $paid = [];
        $sum = 0.0;
        foreach (array_filter($people, [self::class, 'paid']) as $p) {
            $isCeo = ($p['key'] ?? '') === 'ceo';
            $share = $isCeo ? $ceo : $staff * (float) ($p['pct'] ?? 0) / 100;
            $sum += $isCeo ? 0 : (float) ($p['pct'] ?? 0);
            $paid[] = $p + ['share' => round($share), 'payer' => RewardRules::COMPANY];
        }
        return ['base' => $base, 'amount' => round($amount), 'pot' => round($amount), 'ceo' => round($ceo), 'staff' => round($staff),
            'pct_sum' => round($sum, 2), 'people' => $paid, 'rule' => $rule];
    }

    /** پاداش هر کدام از چهار نوع با کلید. */
    public static function of(int $kind, array $values, ?int $date, array $people): ?array
    {
        return match ($kind) {
            RewardRules::FINDER => self::finder($values, $date),
            RewardRules::COLLECTION => self::collection($values, $date, $people),
            RewardRules::ENFORCEMENT => self::enforcement($values, $date, $people),
            default => self::staff($values, $date, $people),
        };
    }

    /** تعداد دستگاه مبنای مبلغ: جمع‌آوری‌شده (دارای و فاقد انشعاب) یا کشف‌شده؛ جمع‌آوری‌شده ننوشته یعنی کشف‌شده. */
    public static function count(array $spec, array $values): array
    {
        if (($spec['count'] ?? 'found') === 'collected') {
            $collected = (int) ($values['with_collected'] ?? 0) + (int) ($values['without_collected'] ?? 0);
            if ($collected > 0) {
                return [$collected, 'collected'];
            }
        }
        return [(int) ($values['miners'] ?? 0), 'found'];
    }

    /** مبلغ مرکز میان افراد برابر؛ کسی نباشد مبلغی هم پرداخت نمی‌شود. */
    private static function equal(array $rule, array $values, array $people): array
    {
        [$count, $from] = self::count($rule['spec'], $values);
        $pot = round(RewardRules::amount($rule['spec'], $count));
        $n = count($people);
        $each = $n > 0 ? round($pot / $n) : 0.0;
        $paid = array_map(static fn ($p) => $p + ['share' => $each, 'payer' => RewardRules::payer($rule['kind'], $p['org'] ?? null)], $people);
        return ['amount' => $n > 0 ? $pot : 0.0, 'pot' => $pot, 'count' => $count, 'count_from' => $from, 'each' => $each,
            'people' => $paid, 'rule' => $rule];
    }

    private static function paid(array $person): bool
    {
        return !empty($person['on']);
    }

    /** سازمان اجرایی یک حاضر: نیروی انتظامی یا سپاه؛ کارکنان شرکت توزیع و دیگران تهی. */
    public static function enforcer(array $attendee): ?string
    {
        $org = (int) ($attendee['org'] ?? -1);
        $text = CenterValues::cleanText(($attendee['org_name'] ?? '') . ' ' . ($attendee['position'] ?? ''));
        if (str_contains($text, 'سپاه')) {
            return 'sepah';
        }
        if ($org === 1 || ($org === 2 && (str_contains($text, 'فراجا') || str_contains($text, 'انتظامی')
            || str_contains($text, 'امنیت اقتصادی') || str_contains($text, 'پلیس')))) {
            return 'faraja';
        }
        return null;
    }
}
