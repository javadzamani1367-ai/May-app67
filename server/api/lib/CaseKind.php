<?php
declare(strict_types=1);

/**
 * دو حوزه پرونده: رمزارز و برق غیرمجاز (ستون reports.case_kind). هر حوزه گرداننده
 * شهرستان و بالادست خودش را دارد؛ گردش، هشدارها، تأیید، ارسال به واحد و امتیاز در هر
 * دو یک قاعده است و فقط این نقش‌ها جابه‌جا می‌شوند.
 *
 * - رمزارز: مجری رمزارز شهرستان، و بالای او مدیر.
 * - برق غیرمجاز: مجری غیرمجاز شهرستان، و بالای او مجری غیرمجاز ستاد. مدیر در این حوزه
 *   فقط ناظر است: می‌بیند و گزارش می‌گیرد، اقدامی نمی‌کند.
 */
final class CaseKind
{
    public const CRYPTO = 0;
    public const ILLEGAL = 1;
    public const NAMES = [self::CRYPTO => 'رمزارز', self::ILLEGAL => 'برق غیرمجاز'];

    public static function of(array $report): int
    {
        return (int) ($report['case_kind'] ?? self::CRYPTO) === self::ILLEGAL ? self::ILLEGAL : self::CRYPTO;
    }

    /** گرداننده شهرستان. */
    public static function executorRole(int $kind): int
    {
        return $kind === self::ILLEGAL ? Auth::ROLE_ILLEGAL_AREA : Auth::ROLE_EXECUTOR;
    }

    /** بالادست، که هشدار بی‌اقدامی گرداننده به او می‌رسد. */
    public static function topRole(int $kind): int
    {
        return $kind === self::ILLEGAL ? Auth::ROLE_ILLEGAL : Auth::ROLE_MANAGER;
    }

    /** حوزه‌ای که این نقش می‌گرداند؛ تهی برای مدیر (همه را می‌بیند) و نقش‌های دیگر. */
    public static function ofRole(int $role): ?int
    {
        return match ($role) {
            Auth::ROLE_EXECUTOR => self::CRYPTO,
            Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA => self::ILLEGAL,
            default => null,
        };
    }

    public static function label(array $report): string
    {
        return self::NAMES[self::of($report)];
    }
}
