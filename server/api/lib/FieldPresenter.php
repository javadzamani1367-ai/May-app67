<?php
declare(strict_types=1);

/**
 * چک‌لیست یک مورد میدانی به زبان آدم: ردیف‌های [عنوان، مقدار] با همان
 * برچسب‌های فارسی گوشی. پنل مدیر (صفحه، برگه ارجاع، Word) و API اپ کارشناس
 * همه از همین می‌خوانند، تا یک گزارش در دو جا دو جور خوانده نشود.
 */
final class FieldPresenter
{
    /** کلیدهای چک‌لیست به برچسب فارسی؛ کلید ناشناخته همان‌طور نوشته می‌شود تا گم نشود. */
    public static function labels(array $keys, array $names): string
    {
        return implode('، ', array_map(static fn ($k) => $names[$k] ?? (string) $k, $keys));
    }

    public static function numberText(mixed $value): ?string
    {
        return is_numeric($value) ? Jalali::digits((string) $value) : null;
    }

    /**
     * چک‌لیست هر نوع گزارش، از payload، به صورت ردیف‌های [عنوان، مقدار]؛ صفحه،
     * برگه ارجاع و خروجی Word همه از همین ساخته می‌شوند. مقدار خالی کنار گذاشته می‌شود.
     *
     * @return array<int, array{0:string, 1:string}>
     */
    public static function rows(int $kind, array $p): array
    {
        $rows = [];
        $add = static function (string $label, ?string $value) use (&$rows): void {
            if ($value !== null && trim($value) !== '') {
                $rows[] = [$label, $value];
            }
        };
        if ($kind === Field::KIND_CRYPTO) {
            $add('آمپر اندازه‌گیری‌شده', self::numberText($p['amperage'] ?? null));
            $add('تعداد تقریبی دستگاه', self::numberText($p['miner_count'] ?? null));
            $add('نوع دستگاه', $p['miner_type'] ?? null);
            $add('نشانه‌ها', self::labels((array) ($p['signs'] ?? []), FieldLabels::SIGNS));
        }
        if ($kind === Field::KIND_ILLEGAL) {
            $consumers = [];
            foreach ((array) ($p['consumers'] ?? []) as $key => $count) {
                $consumers[] = (FieldLabels::CONSUMERS[$key] ?? $key) . ' × ' . Jalali::digits((string) $count);
            }
            $add('مصرف‌کننده‌ها', implode('، ', $consumers));
            $add('مصرف‌کننده دیگر', $p['other_consumer'] ?? null);
            $add('کاربری', isset($p['usage']) ? (FieldLabels::USAGES[$p['usage']] ?? (string) $p['usage']) : null);
            $add('تخلف', self::labels((array) ($p['violations'] ?? []), FieldLabels::VIOLATIONS));
            $add('شماره کنتور یا قبض', $p['meter_or_bill'] ?? null);
        }
        if ($kind === Field::KIND_BURN) {
            $equipment = [];
            foreach ((array) ($p['equipment'] ?? []) as $key => $count) {
                $equipment[] = (FieldLabels::EQUIPMENT[$key] ?? $key)
                    . ((int) $count > 1 ? ' × ' . Jalali::digits((string) (int) $count) : '');
            }
            $add('تجهیزات سوخته', implode('، ', $equipment));
            $add('تجهیز دیگر', $p['other_equipment'] ?? null);
            $add('فازهای آسیب‌دیده', self::labels((array) ($p['phases'] ?? []), FieldLabels::PHASES));
            $add('زمان تقریبی سوختن', isset($p['occurred']) ? (FieldLabels::OCCURRED[$p['occurred']] ?? null) : null);
            $add('برق مشترکین قطع است', is_bool($p['outage'] ?? null) ? ($p['outage'] ? 'بله' : 'خیر') : null);
            $add('علت احتمالی', self::labels((array) ($p['causes'] ?? []), FieldLabels::CAUSES));
            $add('آمپر اندازه‌گیری‌شده', self::numberText($p['amperage'] ?? null));
        }
        if (in_array($kind, Field::REPORT_KINDS, true)) {
            $team = (array) ($p['team'] ?? []);
            $yes = array_keys(array_filter($team, static fn ($v) => $v === true));
            $no = array_keys(array_filter($team, static fn ($v) => $v === false));
            $add('برای اکیپ: بله', self::labels($yes, FieldLabels::TEAM_QUESTIONS));
            $add('برای اکیپ: خیر', self::labels($no, FieldLabels::TEAM_QUESTIONS));
            $add('بهترین زمان', isset($team['best_time']) ? (FieldLabels::BEST_TIMES[$team['best_time']] ?? null) : null);
            $add('تعداد ورودی', self::numberText($team['entrances'] ?? null));
            $add('توضیح ورودی', $team['entrance_note'] ?? null);
        }
        if ($kind === Field::KIND_FEEDER) {
            foreach (self::feederRows($p) as [$label, $value]) {
                $add($label, $value);
            }
        }
        if ($kind === Field::KIND_LINE) {
            foreach (FieldLine::rows($p) as [$label, $value]) {
                $add($label, $value);
            }
        }
        if ($kind === Field::KIND_THERMAL) {
            $add('شروع مسیر', isset($p['started_at']) ? Jalali::format((int) $p['started_at']) : null);
            $add('پایان مسیر', isset($p['ended_at']) ? Jalali::format((int) $p['ended_at']) : null);
            $add('نقطه‌های مسیر', self::numberText($p['points'] ?? null));
            $add('اختلاف ساعت گوشی با GPS (ثانیه)', self::numberText($p['clock_skew_s'] ?? null));
        }
        return $rows;
    }

    public static function phasesText(mixed $phases): string
    {
        $p = (array) $phases;
        return 'R ' . (self::numberText($p['r'] ?? null) ?? '—') . ' / S ' . (self::numberText($p['s'] ?? null) ?? '—')
            . ' / T ' . (self::numberText($p['t'] ?? null) ?? '—');
    }

    /** @return array<int, array{0:string, 1:?string}> */
    public static function feederRows(array $p): array
    {
        $rows = [['کلید کل (آمپر)', self::phasesText($p['main'] ?? [])]];
        $states = ['measured' => 'اندازه‌گیری شد', 'absent' => 'وجود ندارد یا قطع است', 'not_measured' => 'اندازه‌گیری نشد'];
        foreach ((array) ($p['feeders'] ?? []) as $name => $feeder) {
            $feeder = (array) $feeder;
            $state = (string) ($feeder['state'] ?? '');
            $text = $states[$state] ?? '—';
            if ($state === 'measured') {
                $text .= ': ' . self::phasesText($feeder['phases'] ?? []);
            } elseif ($state === 'not_measured') {
                $text .= ' — ' . (string) ($feeder['reason'] ?? '');
            }
            $rows[] = ['فیدر ' . $name, $text];
        }
        $check = (array) ($p['check'] ?? []);
        if ($check !== []) {
            $verdict = ($check['possible'] ?? false) !== true ? 'کنترل مجموع ممکن نبود'
                : (($check['ok'] ?? false) === true ? 'مجموع فیدرها با کلید کل می‌خواند' : 'اختلاف بیش از حد مجاز');
            $verdict .= ' (حد مجاز ' . Jalali::digits((string) ($check['tolerance_pct'] ?? '—')) . '٪، کف '
                . Jalali::digits((string) ($check['tolerance_min_a'] ?? '—')) . ' آمپر)';
            $rows[] = ['کنترل مجموع', $verdict];
            $rows[] = ['عدم تعادل فازها (٪)', self::numberText($check['imbalance_pct'] ?? null)];
        }
        $rows[] = ['توضیح', $p['note'] ?? null];
        return $rows;
    }
}
