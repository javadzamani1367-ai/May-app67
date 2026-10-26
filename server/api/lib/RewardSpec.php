<?php
declare(strict_types=1);

/**
 * بررسی و یکسان‌سازی بدنه هر دستورالعمل پاداش. خطا متن فارسی برمی‌گرداند تا پنل و
 * اپ همان را نشان دهند؛ درست، آرایه تمیز.
 *
 * پلکان (عامل شناسایی و عوامل اجرایی): mode، tiers [{from, to, amount}] که پشت هم و
 * بی‌هم‌پوشانی‌اند (آخرین پله می‌تواند بی‌انتها باشد)، cap سقف هر مرکز.
 * عوامل اجرایی: orgs کدام سازمان‌ها (نیروی انتظامی، سپاه)؛ مبلغ مرکز میان سازمان‌های حاضر برابر
 * و سهم هر سازمان میان حاضران همان سازمان برابر تقسیم می‌شود.
 * عوامل اداری: base، pct کل، ceo_pct، staff_pct (جمع این دو همان کل)، cap، و roles
 * [{label, weight, people}] که وزن‌ها جمعاً ۱۰۰ درصد سهم کارکنان است.
 */
final class RewardSpec
{
    /** نقش‌های پیش‌فرض عوامل اداری؛ مدیر وزن و دارندگان را می‌نویسد. */
    public const STAFF_ROLES = [
        'مدیر دفتر حقوقی', 'مدیر فروش و وصول مطالبات', 'رئیس دفتر لوازم اندازه‌گیری', 'مدیر حراست',
        'کارشناس حقوقی', 'معاون فروش و خدمات مشترکین', 'دبیر کمیسیون رمزارز', 'مجری رمزارز',
        'کارشناس استخراج لاگ', 'کارشناس تحلیل لاگ',
    ];

    /** @return array<string, mixed>|string */
    public static function clean(int $kind, array $spec): array|string
    {
        if ($kind === RewardRules::STAFF) {
            return self::staff($spec);
        }
        $tiers = self::tiers($spec['tiers'] ?? []);
        if (is_string($tiers)) {
            return $tiers;
        }
        $mode = (string) ($spec['mode'] ?? 'progressive');
        $out = [
            'mode' => isset(RewardRules::MODES[$mode]) ? $mode : 'progressive',
            'tiers' => $tiers,
            'cap' => self::money($spec['cap'] ?? null),
        ];
        if ($kind === RewardRules::ENFORCEMENT) {
            $orgs = array_values(array_intersect(array_keys(RewardRules::ENFORCERS), array_map('strval', (array) ($spec['orgs'] ?? []))));
            if ($orgs === []) {
                return 'دست‌کم یکی از دو سازمان اجرایی را انتخاب کنید.';
            }
            $out['orgs'] = $orgs;
        }
        return $out;
    }

    /** @return list<array{from:int, to:?int, amount:float}>|string */
    private static function tiers(mixed $raw): array|string
    {
        $tiers = [];
        foreach (is_array($raw) ? $raw : [] as $tier) {
            $amount = self::money($tier['amount'] ?? null);
            $from = CenterValues::number($tier['from'] ?? null);
            if ($amount === null && $from === null) {
                continue;
            }
            $to = CenterValues::number($tier['to'] ?? null);
            $tiers[] = ['from' => max(1, (int) ($from ?? 1)), 'to' => $to === null ? null : (int) $to, 'amount' => $amount ?? 0.0];
        }
        if ($tiers === []) {
            return 'دست‌کم یک پله با مبلغ بنویسید.';
        }
        usort($tiers, static fn ($a, $b) => $a['from'] <=> $b['from']);
        foreach ($tiers as $i => $tier) {
            if ($tier['to'] !== null && $tier['to'] < $tier['from']) {
                return 'در پله ' . Jalali::digits((string) ($i + 1)) . ' «تا» از «از» کمتر است.';
            }
            $next = $tiers[$i + 1] ?? null;
            if ($next !== null && ($tier['to'] === null || $tier['to'] >= $next['from'])) {
                return 'پله‌ها هم‌پوشانی دارند؛ هر پله باید پیش از شروع پله بعد تمام شود.';
            }
        }
        return $tiers;
    }

    /** @return array<string, mixed>|string */
    private static function staff(array $spec): array|string
    {
        $pct = self::percent($spec['pct'] ?? null);
        $ceo = self::percent($spec['ceo_pct'] ?? null) ?? 0.0;
        $staff = self::percent($spec['staff_pct'] ?? null) ?? 0.0;
        if ($pct === null || $pct <= 0) {
            return 'درصد کل پاداش عوامل اداری را بنویسید.';
        }
        if (abs($ceo + $staff - $pct) > 0.0001) {
            return 'جمع سهم مدیرعامل و سهم کارکنان باید همان درصد کل باشد.';
        }
        $roles = [];
        $weights = 0.0;
        foreach (is_array($spec['roles'] ?? null) ? $spec['roles'] : [] as $role) {
            $label = CenterValues::cleanText((string) ($role['label'] ?? ''));
            $weight = self::percent($role['weight'] ?? null) ?? 0.0;
            if ($label === '' || $weight <= 0) {
                continue;
            }
            $people = is_array($role['people'] ?? null) ? $role['people']
                : preg_split('/[\n،,]+/u', (string) ($role['people'] ?? ''));
            $people = array_values(array_filter(array_map(static fn ($p) => CenterValues::cleanText((string) $p), $people)));
            $roles[] = ['label' => mb_substr($label, 0, 100), 'weight' => $weight, 'people' => $people];
            $weights += $weight;
        }
        if ($roles !== [] && abs($weights - 100) > 0.01) {
            return 'وزن نقش‌ها باید جمعاً ۱۰۰ درصد سهم کارکنان باشد؛ اکنون ' . Jalali::digits((string) round($weights, 2)) . ' است.';
        }
        $base = (string) ($spec['base'] ?? 'energy');
        return [
            'base' => isset(RewardRules::BASES[$base]) ? $base : 'energy',
            'pct' => $pct, 'ceo_pct' => $ceo, 'staff_pct' => $staff,
            'cap' => self::money($spec['cap'] ?? null),
            'roles' => $roles,
        ];
    }

    private static function money(mixed $raw): ?float
    {
        $value = CenterValues::number($raw);
        return $value === null || $value < 0 ? null : $value;
    }

    private static function percent(mixed $raw): ?float
    {
        $value = CenterValues::number($raw);
        return $value === null || $value < 0 || $value > 100 ? null : $value;
    }
}
