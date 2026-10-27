<?php
declare(strict_types=1);

/**
 * بررسی و یکسان‌سازی هر بخش دستورالعمل پاداش. خطا متن فارسی برمی‌گرداند تا پنل و اپ همان را
 * نشان دهند؛ بخش خالی تهی (این دستورالعمل آن پاداش را عوض نمی‌کند)؛ درست، آرایه تمیز.
 *
 * عامل شناسایی: mode، tiers [{from, to, amount}] پشت هم و بی‌هم‌پوشانی (آخرین پله می‌تواند
 * بی‌انتها باشد)، cap سقف هر مرکز.
 * جمع‌آوری و عوامل انتظامی: پلکانی نیستند؛ per_device مبلغ هر دستگاه، count (کشف‌شده یا
 * جمع‌آوری‌شده) و cap؛ عوامل انتظامی orgs کدام سازمان‌ها. دستورالعمل پلکانی نسخه پیش خوانده می‌شود.
 * عوامل اداری: base، pct کل، ceo_pct، staff_pct (جمع این دو همان کل)، cap، و roles [{label, weight}]
 * درصد پیش‌فرض هر پست از سهم کارکنان که جمعاً ۱۰۰ است.
 */
final class RewardSpec
{
    /** پست‌های پیش‌فرض عوامل اداری؛ مدیر درصد هر پست را می‌نویسد. */
    public const STAFF_ROLES = [
        'مدیر فروش و وصول مطالبات', 'مدیر دفتر حقوقی', 'رئیس دفتر لوازم اندازه‌گیری', 'مدیر حراست',
        'کارشناس حقوقی', 'معاون فروش و خدمات مشترکین', 'دبیر کمیسیون رمزارز', 'مجری نظارت و ساماندهی رمزارز',
        'کارشناس استخراج لاگ', 'کارشناس تحلیل لاگ',
    ];

    /** @return array<string, mixed>|string|null */
    public static function clean(int $kind, array $spec): array|string|null
    {
        return match ($kind) {
            RewardRules::STAFF => self::staff($spec),
            RewardRules::FINDER => self::finder($spec),
            default => self::perDevice($kind, $spec),
        };
    }

    /** @return array<string, mixed>|string|null */
    private static function finder(array $spec): array|string|null
    {
        $tiers = self::tiers($spec['tiers'] ?? []);
        if ($tiers === []) {
            return null;
        }
        if (is_string($tiers)) {
            return $tiers;
        }
        $mode = (string) ($spec['mode'] ?? 'progressive');
        return ['mode' => isset(RewardRules::MODES[$mode]) ? $mode : 'progressive', 'tiers' => $tiers,
            'cap' => self::money($spec['cap'] ?? null)];
    }

    /** @return array<string, mixed>|string|null */
    private static function perDevice(int $kind, array $spec): array|string|null
    {
        $rate = self::money($spec['per_device'] ?? null);
        $count = (string) ($spec['count'] ?? ($kind === RewardRules::COLLECTION ? 'collected' : 'found'));
        $out = ['count' => isset(RewardRules::COUNTS[$count]) ? $count : 'found', 'cap' => self::money($spec['cap'] ?? null)];
        if ($rate !== null) {
            $out['per_device'] = $rate;
        } else {
            $legacy = self::finder($spec);
            if (!is_array($legacy)) {
                return $legacy;
            }
            $out = $legacy + $out;
        }
        if ($kind === RewardRules::ENFORCEMENT) {
            $orgs = array_values(array_intersect(array_keys(RewardRules::ENFORCERS), array_map('strval', (array) ($spec['orgs'] ?? []))));
            if ($orgs === []) {
                return 'دست‌کم یکی از دو سازمان را انتخاب کنید.';
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

    /** @return array<string, mixed>|string|null */
    private static function staff(array $spec): array|string|null
    {
        if (trim((string) ($spec['pct'] ?? '')) === '') {
            return null;
        }
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
            $roles[] = ['label' => mb_substr($label, 0, 100), 'weight' => $weight];
            $weights += $weight;
        }
        if ($roles !== [] && abs($weights - 100) > 0.01) {
            return 'درصد پست‌ها باید جمعاً ۱۰۰ درصد سهم کارکنان باشد؛ اکنون ' . Jalali::digits((string) round($weights, 2)) . ' است.';
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
