<?php
declare(strict_types=1);

/**
 * ناحیه‌های رسمی شرکت توزیع (CLAUDE.md بخش ۶)، همان که گوشی در county_codes دارد.
 *
 * هر کاربر مجری، واحد، کارشناس و میدانی به یک یا چند ناحیه بسته است و پرونده
 * با ناحیه‌اش به دست همان‌ها می‌رسد. ناحیه کلید است نه نام شهرستان، چون ایلام دو
 * ناحیه دارد و با نام نمی‌شد این دو را از هم جدا کرد.
 */
final class Areas
{
    public const NAMES = [
        '401' => 'ایلام', '402' => 'ایلام', '403' => 'ملکشاهی', '404' => 'چوار', '405' => 'ایوان',
        '406' => 'دهلران', '407' => 'زرین‌آباد', '408' => 'موسیان', '409' => 'آبدانان', '410' => 'دره‌شهر',
        '411' => 'بدره', '412' => 'مهران', '413' => 'صالح‌آباد', '414' => 'سرابله', '415' => 'سیروان',
        '416' => 'هلیلان', '419' => 'مورموری',
    ];

    /** «ایلام — ناحیه ۴۰۱»؛ کد ناشناخته همان‌طور، و تهی برای «بی‌ناحیه». */
    public static function label(?string $code): string
    {
        $code = self::code($code);
        if ($code === null) {
            return 'بی‌ناحیه';
        }
        return (self::NAMES[$code] ?? '') . ' — ناحیه ' . Jalali::digits($code);
    }

    /** کد معتبر، با ارقام لاتین؛ تهی برای هر چیز دیگر. */
    public static function code(mixed $value): ?string
    {
        $digits = preg_replace('/\D/', '', strtr((string) $value, ['۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3',
            '۴' => '4', '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9']));
        return isset(self::NAMES[$digits]) ? $digits : null;
    }

    /**
     * فهرست ناحیه‌های یک کاربر از ستون areas («401,402»)؛ تکراری و نامعتبر کنار می‌رود.
     *
     * @return list<string>
     */
    public static function parse(mixed $value): array
    {
        $list = is_array($value) ? $value : explode(',', (string) $value);
        $codes = [];
        foreach ($list as $item) {
            $code = self::code($item);
            if ($code !== null && !in_array($code, $codes, true)) {
                $codes[] = $code;
            }
        }
        sort($codes);
        return $codes;
    }

    /** @param list<string> $codes */
    public static function store(array $codes): ?string
    {
        $codes = self::parse($codes);
        return $codes === [] ? null : implode(',', $codes);
    }

    /**
     * ناحیه پرونده‌ای که فقط نام شهرستان دارد (پرونده‌های پیش از ثبت ناحیه). نامی که
     * دو ناحیه دارد (ایلام) جواب ندارد؛ مدیر آن را خودش تعیین می‌کند.
     */
    public static function fromCounty(?string $county): ?string
    {
        $name = trim(str_replace(['ي', 'ك', "\u{200C}"], ['ی', 'ک', ''], (string) $county));
        if ($name === '') {
            return null;
        }
        $found = [];
        foreach (self::NAMES as $code => $areaName) {
            if (str_replace("\u{200C}", '', $areaName) === $name) {
                $found[] = (string) $code;
            }
        }
        return count($found) === 1 ? $found[0] : null;
    }

    /** برای فهرست انتخاب: کد => برچسب. */
    public static function options(): array
    {
        $out = [];
        foreach (array_keys(self::NAMES) as $code) {
            $out[(string) $code] = self::label((string) $code);
        }
        return $out;
    }
}
