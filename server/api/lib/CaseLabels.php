<?php
declare(strict_types=1);

/**
 * نام مقدارهای کدی پرونده‌های بازدید، همان که گوشی و آرشیو ویندوز می‌نویسند
 * (windows/…/Resources/Strings.xaml و CLAUDE.md بخش‌های ۵، ۶ و ۹). آرشیو وب
 * و خروجی‌هایش همه از همین می‌خوانند تا یک کد در دو جا دو نام نگیرد.
 */
final class CaseLabels
{
    public const REPORT_TYPES = [1 => 'سامانه سراغ', 2 => 'سامانه ۱۲۱', 3 => 'گزارش مردمی', 4 => 'گزارش همکار',
        5 => 'گزارش توانیر', 6 => 'بازدید میدانی'];
    public const STATUSES = [0 => 'در دست اقدام', 1 => 'بازدید شده', 2 => 'بایگانی'];
    public const STATUS_TAGS = [0 => 'tag-seen', 1 => 'tag-answered', 2 => 'tag-sent'];
    public const APPROVALS = [0 => 'پیش‌نویس', 1 => 'در انتظار تأیید', 2 => 'تأیید شد', 3 => 'برگشت برای اصلاح'];
    public const TAP_POINTS = [0 => 'قبل از کنتور', 1 => 'بعد از کنتور'];
    public const PHASE_TYPES = [0 => 'تکفاز', 1 => 'سه فاز'];
    public const TARIFFS = [0 => 'خانگی', 1 => 'صنعتی', 2 => 'کشاورزی', 3 => 'عمومی', 4 => 'سایر مصارف',
        5 => 'بدون انشعاب — غیرمجاز از شبکه'];
    public const METERS = [0 => 'کنتور مکانیکی', 1 => 'کنتور دیجیتال'];
    public const ENTRY_METHODS = [0 => 'بارکد', 1 => 'دستی'];
    public const ORGANIZATIONS = [0 => 'شرکت برق', 1 => 'پلیس امنیت', 2 => 'سایر نهاد'];
    public const ATTACHMENTS = [0 => 'لاگ ماینرها', 1 => 'نتایج تحلیل ماینرها', 2 => 'صورتجلسه کمیسیون رمزارز',
        3 => 'صورتحساب انرژی', 4 => 'صورتحساب خسارت', 5 => 'نامه اقدام به واحد فروش',
        6 => 'نامه اقدام به واحد حقوقی', 7 => 'نامه به برق شهرستان جهت تعویض', 8 => 'سایر'];
    public const CHANNELS = [0 => 'سامانه', 1 => 'شبکه اجتماعی', 2 => 'بسته آفلاین'];
    public const DISPATCH_STATUSES = [0 => 'ارسال شد', 1 => 'دیده شد', 2 => 'پاسخ داده شد'];
    public const DISPATCH_TAGS = [0 => 'tag-sent', 1 => 'tag-seen', 2 => 'tag-answered'];
    public const FORMATS = [0 => 'PDF', 1 => 'Word'];

    /** نام یک کد، یا تهی اگر کد نیامده؛ کد ناشناخته همان‌طور نوشته می‌شود تا گم نشود. */
    public static function of(array $names, mixed $code): ?string
    {
        if ($code === null || $code === '') {
            return null;
        }
        return $names[(int) $code] ?? (string) $code;
    }

    /** «ایلام — ناحیه ۴۰۱»؛ پرونده‌ای که ناحیه ندارد فقط با نام شهرستان. */
    public static function countyWithArea(?string $county, ?string $area): string
    {
        $name = trim((string) $county);
        $digits = preg_replace('/\D/', '', (string) $area);
        if ($name === '' || $digits === '') {
            return $name;
        }
        return $name . ' — ناحیه ' . Jalali::digits($digits);
    }

    /** پرسش بی‌پاسخ خالی می‌ماند؛ «خیر» ساختن از هیچ، ادعایی است که کسی نکرده. */
    public static function yesNo(mixed $value): ?string
    {
        return $value === null || $value === '' ? null : ((int) $value === 1 ? 'بله' : 'خیر');
    }

    /** کدی که انسان می‌بیند: کد نهایی، یا کد موقت تا شماره اشتراک نیامده. */
    public static function code(array $report): string
    {
        return (string) ($report['tracking_code'] ?? '') !== '' ? (string) $report['tracking_code']
            : (string) ($report['temp_code'] ?? '');
    }

    /** عدد برای نمایش: بی صفر اضافه، با ارقام فارسی. */
    public static function number(mixed $value, int $decimals = 1): ?string
    {
        if (!is_numeric($value)) {
            return null;
        }
        $rounded = round((float) $value, $decimals);
        $text = $decimals > 0 ? rtrim(rtrim(number_format($rounded, $decimals, '.', ''), '0'), '.') : (string) (int) $rounded;
        return str_replace('.', '٫', Jalali::digits($text));
    }

    /** وات با جداکننده هزارگان: «۱۲٬۵۰۰». */
    public static function grouped(mixed $value): ?string
    {
        return is_numeric($value) ? Jalali::digits(number_format((float) $value, 0, '.', '٬')) : null;
    }
}
