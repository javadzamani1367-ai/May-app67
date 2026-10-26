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
     * عوامل اجرایی حاضر و سهم هر نفر: مبلغ مرکز (پلکان و سقف) میان سازمان‌های حاضر برابر، و
     * سهم هر سازمان میان حاضران همان سازمان برابر. @param list<array<string, mixed>> $attendees
     * @return array{amount:float, rule:array, people:list<array{name:string, org:string, share:float}>,
     *   orgs:array<string, float>}|null
     */
    public static function enforcement(array $values, ?int $date, array $attendees): ?array
    {
        $rule = RewardRules::at(RewardRules::ENFORCEMENT, $date);
        if ($rule === null) {
            return null;
        }
        $allowed = $rule['spec']['orgs'] ?? array_keys(RewardRules::ENFORCERS);
        $members = [];
        foreach ($attendees as $a) {
            $org = self::enforcer($a);
            if ($org !== null && in_array($org, $allowed, true)) {
                $members[$org][] = $a['name'] ?? '—';
            }
        }
        $pot = RewardRules::amount($rule['spec'], (int) ($values['miners'] ?? 0));
        if ($members === [] || $pot <= 0) {
            return ['amount' => 0.0, 'rule' => $rule, 'people' => [], 'orgs' => []];
        }
        $perOrg = $pot / count($members);
        $people = [];
        $orgs = [];
        foreach ($members as $org => $names) {
            $orgs[RewardRules::ENFORCERS[$org]] = round($perOrg);
            foreach ($names as $name) {
                $people[] = ['name' => $name, 'org' => RewardRules::ENFORCERS[$org], 'share' => round($perOrg / count($names))];
            }
        }
        return ['amount' => round($pot), 'rule' => $rule, 'people' => $people, 'orgs' => $orgs];
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
