<?php
declare(strict_types=1);

require_once __DIR__ . '/PositionAudit.php';

/**
 * مشترکان نامتعارف هر تیر در آمپرگیری تیربه‌تیر (payload نسخه ۲): موقعیت منزل،
 * آمپر، کدهای پستی، شماره پرونده و اشتراک، و عکس و فیلم. «نامتعارف» بودن را
 * گوشی با حد مجاز همان روز (abnormal_a) داوری کرده و همراه فرستاده است.
 */
final class FieldSubscribers
{
    public const MAX_POSTAL_CODES = 20;

    /** آنچه سرور هم نمی‌پذیرد، حتی اگر گوشی قدیمی یا دستکاری‌شده فرستاده باشد. */
    public static function problem(array $p): ?string
    {
        foreach (array_values((array) ($p['poles'] ?? [])) as $i => $pole) {
            $pole = (array) $pole;
            $subs = array_values((array) ($pole['subscribers'] ?? []));
            if ($subs === []) {
                continue;
            }
            $n = Jalali::digits((string) ($i + 1));
            $branches = (int) ($pole['b1'] ?? 0) + (int) ($pole['b3'] ?? 0);
            if (count($subs) > $branches) {
                return 'تعداد مشترکان نامتعارف تیر ' . $n . ' از انشعاب‌های آن بیشتر است.';
            }
            foreach ($subs as $sub) {
                $sub = (array) $sub;
                if (!is_numeric($sub['lat'] ?? null) || !is_numeric($sub['lon'] ?? null)) {
                    return 'موقعیت منزل مشترک نامتعارف تیر ' . $n . ' ثبت نشده است.';
                }
                $codes = (array) ($sub['postal_codes'] ?? []);
                if (count($codes) > self::MAX_POSTAL_CODES) {
                    return 'بیش از ' . Jalali::digits((string) self::MAX_POSTAL_CODES) . ' کد پستی برای یک مشترک تیر ' . $n . ' آمده است.';
                }
                foreach ($codes as $code) {
                    if (!preg_match('/^\d{10}$/', (string) $code)) {
                        return 'کد پستی مشترک تیر ' . $n . ' باید ۱۰ رقم باشد.';
                    }
                }
            }
        }
        return null;
    }

    /** آمپر به متن: تکفاز یک عدد، سه فاز R/S/T. */
    public static function ampsText(array $sub): string
    {
        $a = (array) ($sub['amps'] ?? []);
        if ((int) ($sub['phase'] ?? 1) !== 3) {
            return 'تکفاز ' . (FieldPresenter::numberText($a['r'] ?? null) ?? '—');
        }
        return 'سه فاز ' . FieldPresenter::phasesText($a);
    }

    /**
     * هر مشترک یک ردیف: تیر، شماره، نام، آمپر، داوری، کدهای پستی، پرونده و
     * اشتراک، مختصات، زمان ثبت موقعیت، و تعداد عکس و فیلم.
     *
     * @return list<list<string>>
     */
    public static function table(array $p): array
    {
        $rows = [];
        foreach (array_values((array) ($p['poles'] ?? [])) as $i => $pole) {
            foreach (array_values((array) (((array) $pole)['subscribers'] ?? [])) as $j => $sub) {
                $sub = (array) $sub;
                $abnormal = $sub['abnormal'] ?? null;
                $ids = array_filter([
                    trim((string) ($sub['file_number'] ?? '')) === '' ? null : 'پرونده ' . $sub['file_number'],
                    trim((string) ($sub['subscription'] ?? '')) === '' ? null : 'اشتراک ' . $sub['subscription'],
                ]);
                $media = array_filter([
                    self::count($sub, 'amp_photos', 'عکس آمپراژ'),
                    self::count($sub, 'amp_videos', 'فیلم آمپراژ'),
                    self::count($sub, 'building_photos', 'عکس ساختمان'),
                ]);
                $rows[] = [
                    Jalali::digits((string) ($i + 1)),
                    Jalali::digits((string) ($j + 1)),
                    (string) ($sub['name'] ?? ''),
                    self::ampsText($sub),
                    $abnormal === true ? 'نامتعارف' : ($abnormal === false ? 'زیر حد' : '—'),
                    implode('، ', array_map('strval', (array) ($sub['postal_codes'] ?? []))),
                    implode(' · ', $ids),
                    is_numeric($sub['lat'] ?? null) ? sprintf('%.6f, %.6f', (float) $sub['lat'], (float) $sub['lon']) : '—',
                    (string) PositionAudit::text($sub['at'] ?? null, $sub['lat'] ?? null, $sub['lon'] ?? null,
                        $sub['olat'] ?? null, $sub['olon'] ?? null, $sub['oacc'] ?? null),
                    implode('، ', $media),
                ];
            }
        }
        return $rows;
    }

    /** حد مجاز روز ارسال، برای سرستون جدول. */
    public static function limitsText(array $p): ?string
    {
        $a = (array) ($p['abnormal_a'] ?? []);
        if (!is_numeric($a['single'] ?? null) || !is_numeric($a['three'] ?? null)) {
            return null;
        }
        return 'حد مجاز: تکفاز ' . Jalali::digits((string) $a['single']) . ' آمپر، سه فاز '
            . Jalali::digits((string) $a['three']) . ' آمپر هر فاز';
    }

    private static function count(array $sub, string $key, string $label): ?string
    {
        $n = count((array) ($sub[$key] ?? []));
        return $n === 0 ? null : $label . ' ' . Jalali::digits((string) $n);
    }
}
