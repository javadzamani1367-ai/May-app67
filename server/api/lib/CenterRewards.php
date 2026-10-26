<?php
declare(strict_types=1);

/**
 * حساب پاداش هر مرکز با دستورالعمل جاری در تاریخ گزارشش (RewardRules).
 *
 * ورودی‌ها از مقدار نهایی همان سطر می‌آیند: تعداد ماینر (ستون L)، مبلغ انرژی و خسارت
 * و وصول، و حاضران بازدید که کارشناس در اپ ثبت کرده و مدیر تأیید کرده است.
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
        return ['amount' => RewardRules::amount($rule['spec'], $count), 'rule' => $rule];
    }

    /**
     * عوامل اجرایی حاضر و سهم هر نفر. @param list<array<string, mixed>> $attendees
     * @return array{amount:float, rule:array, people:list<array{name:string, org:string, share:float}>}|null
     */
    public static function enforcement(array $values, ?int $date, array $attendees): ?array
    {
        $rule = RewardRules::at(RewardRules::ENFORCEMENT, $date);
        if ($rule === null) {
            return null;
        }
        $spec = $rule['spec'];
        $people = [];
        foreach ($attendees as $a) {
            $org = self::enforcer($a);
            if ($org !== null && in_array($org, $spec['orgs'] ?? [], true)) {
                $people[] = ['name' => $a['name'] ?? '—', 'org' => $org];
            }
        }
        $count = (int) ($values['miners'] ?? 0);
        $pot = RewardRules::amount($spec, $count);
        if ($people === [] || $pot <= 0) {
            return ['amount' => 0.0, 'rule' => $rule, 'people' => []];
        }
        $cap = (float) ($spec['cap'] ?? 0);
        $n = count($people);
        $split = $spec['split'] ?? 'person';
        if ($split === 'each') {
            // هر نفر همان مبلغ؛ سقف برای کل مرکز است.
            $each = RewardRules::amount(['tiers' => $spec['tiers'], 'mode' => $spec['mode']], $count);
            $total = $cap > 0 ? min($each * $n, $cap) : $each * $n;
            $shares = array_fill(0, $n, $total / $n);
        } elseif ($split === 'org') {
            $orgs = array_count_values(array_column($people, 'org'));
            $total = $pot;
            $shares = array_map(static fn ($p) => $pot / count($orgs) / $orgs[$p['org']], $people);
        } else {
            $total = $pot;
            $shares = array_fill(0, $n, $pot / $n);
        }
        foreach ($people as $i => &$person) {
            $person['share'] = round($shares[$i]);
            $person['org'] = RewardRules::ENFORCERS[$person['org']];
        }
        unset($person);
        return ['amount' => round($total), 'rule' => $rule, 'people' => $people];
    }

    /**
     * پاداش عوامل اداری یک مرکز. @return array{base:float, amount:float, ceo:float, staff:float,
     *   roles:list<array{label:string, weight:float, amount:float, people:list<string>, each:float}>, rule:array}|null
     */
    public static function staff(array $values, ?int $date): ?array
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
        $roles = [];
        foreach ($spec['roles'] ?? [] as $role) {
            $share = $staff * (float) $role['weight'] / 100;
            $roles[] = ['label' => $role['label'], 'weight' => (float) $role['weight'], 'amount' => round($share),
                'people' => $role['people'], 'each' => $role['people'] === [] ? round($share) : round($share / count($role['people']))];
        }
        return ['base' => $base, 'amount' => round($amount), 'ceo' => round($ceo), 'staff' => round($staff),
            'roles' => $roles, 'rule' => $rule];
    }

    /** نهاد اجرایی یک حاضر: پلیس امنیت، سپاه، یا مأمور وصول شرکت؛ دیگران تهی. */
    public static function enforcer(array $attendee): ?string
    {
        $org = (int) ($attendee['org'] ?? -1);
        $text = CenterValues::cleanText(($attendee['org_name'] ?? '') . ' ' . ($attendee['position'] ?? ''));
        if ($org === 1 || str_contains($text, 'فراجا') || str_contains($text, 'امنیت اقتصادی')) {
            return 'faraja';
        }
        if (str_contains($text, 'سپاه')) {
            return 'sepah';
        }
        if (str_contains($text, 'وصول')) {
            return 'collection';
        }
        return null;
    }
}
