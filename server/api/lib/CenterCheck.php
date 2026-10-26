<?php
declare(strict_types=1);

/**
 * بررسی‌های آمار جامع: شماره شبا (ساختار و رقم کنترل)، جمع‌ها (وصول بیش از صورتحساب،
 * جمع‌آوری بیش از شناسایی)، و سطرهای تکراری یک سال. شبای نادرست ذخیره نمی‌شود؛
 * بقیه هشدارند، چون فرم توانیر هم سطر تکراری داشت و مدیر باید بتواند نگهش دارد.
 */
final class CenterCheck
{
    /** شبای ایران: IR و ۲۴ رقم، با رقم کنترل درست (ISO 13616، باقی‌مانده ۹۷). */
    public static function shebaValid(string $sheba): bool
    {
        if (preg_match('/^IR\d{24}$/', $sheba) !== 1) {
            return false;
        }
        $moved = substr($sheba, 4) . '1827' . substr($sheba, 2, 2);
        $rest = 0;
        foreach (str_split($moved, 7) as $chunk) {
            $rest = (int) ($rest . $chunk) % 97;
        }
        return $rest === 1;
    }

    /** @return list<array{key:string, text:string}> */
    public static function warnings(array $v): array
    {
        $out = [];
        $sheba = $v['sheba'] ?? null;
        if ($sheba !== null && !self::shebaValid((string) $sheba)) {
            $out[] = ['key' => 'sheba', 'text' => 'شماره شبا درست نیست.'];
        }
        $billed = (float) ($v['energy_amount'] ?? 0) + (float) ($v['damage_amount'] ?? 0);
        if ($billed > 0 && (float) ($v['collected_amount'] ?? 0) > $billed + 1) {
            $out[] = ['key' => 'collected_amount', 'text' => 'مبلغ وصول از جمع انرژی و خسارت بیشتر است.'];
        }
        foreach (['with', 'without'] as $group) {
            if ((float) ($v[$group . '_collected'] ?? 0) > (float) ($v[$group . '_found'] ?? 0) && ($v[$group . '_found'] ?? null) !== null) {
                $out[] = ['key' => $group . '_collected', 'text' => 'تعداد جمع‌آوری‌شده از شناسایی‌شده بیشتر است.'];
            }
        }
        $ticks = (int) (bool) ($v['org_sepah'] ?? null) + (int) (bool) ($v['org_vaja'] ?? null) + (int) (bool) ($v['org_faraja'] ?? null);
        if ($ticks > 1) {
            $out[] = ['key' => 'org_sepah', 'text' => 'بیش از یک ارگان شناسایی‌کننده تیک خورده است.'];
        }
        if ((float) ($v['finder_reward'] ?? 0) > 0 && (int) ($v['miners'] ?? 0) === 0) {
            $out[] = ['key' => 'miners', 'text' => 'پاداش ثبت شده ولی تعداد ماینر صفر است.'];
        }
        return $out;
    }

    /**
     * سطرهای هم‌اشتراک یا هم‌قبض در یک سال: هشدار روی هر دو، با شماره سطر دیگر.
     *
     * @param list<array<string, mixed>> $rows
     */
    public static function duplicates(array &$rows): void
    {
        $seen = [];
        foreach ($rows as $i => $row) {
            foreach (['subscription', 'bill_id'] as $field) {
                $key = CenterMatch::clean($row['values'][$field] ?? null);
                if ($key !== null) {
                    $seen[$row['year'] . ':' . $field . ':' . $key][] = $i;
                }
            }
        }
        foreach ($seen as $indexes) {
            if (count($indexes) < 2) {
                continue;
            }
            foreach ($indexes as $i) {
                $others = array_values(array_diff($indexes, [$i]));
                $text = 'هم‌اشتراک یا هم‌قبض با ' . count($others) . ' سطر دیگر همین سال.';
                if (!in_array($text, array_column($rows[$i]['warnings'], 'text'), true)) {
                    $rows[$i]['warnings'][] = ['key' => 'subscription', 'text' => Jalali::digits($text)];
                }
                $rows[$i]['duplicate'] = true;
            }
        }
    }
}
