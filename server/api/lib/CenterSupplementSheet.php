<?php
declare(strict_types=1);

/**
 * برگه دوم اکسل آمار: «جدول اطلاعات تکمیلی» با سرستون و سبک همان برگه فرم؛ ستون ردیف
 * شماره سطر همان مرکز در برگه اول است.
 */
final class CenterSupplementSheet
{
    /** شماره ستون‌هایی که عدد بلندند (شناسه قبض، فابریک) یا متن راست‌چین (نام، آدرس). */
    private const LONG = [4, 23];
    private const RIGHT = [6, 7];

    /** @param list<list<mixed>> $lines */
    public static function xml(array $lines): string
    {
        $grid = new CenterGrid();
        $fits = [];
        foreach (CenterSupplement::LABELS as $c => $label) {
            $grid->put(2, $c, $label, CenterTemplate::SUPP_HEAD);
            $fits[$c] = CenterTemplate::fit($label, CenterTemplate::SUPP_HEAD) * 0.6;
        }
        $grid->height(2, 33);
        $r = 3;
        foreach ($lines as $line) {
            foreach (CenterSupplement::LABELS as $c => $label) {
                $value = $line[$c] ?? null;
                if (is_string($value) && preg_match('/^[1-9]\d{0,14}$/', $value) === 1) {
                    $value = (float) $value;
                }
                $style = in_array($c, self::LONG, true) ? CenterTemplate::SUPP_NUMBER
                    : (in_array($c, self::RIGHT, true) ? CenterTemplate::SUPP_RIGHT : CenterTemplate::SUPP_DATA);
                $grid->put($r, $c, $value, $style);
                if ($value !== null && $value !== '') {
                    $fits[$c] = max($fits[$c], CenterTemplate::fit(is_float($value) ? number_format($value, 0, '.', '') : (string) $value, 85) * 1.1);
                }
            }
            $grid->height($r, 21);
            $r++;
        }
        $widths = array_map(static fn ($w) => min(40.0, max(4.0, $w)), $fits);
        $grid->fitHeights($widths, 3, $r - 1);
        return $grid->sheet($widths, 2, null);
    }
}
