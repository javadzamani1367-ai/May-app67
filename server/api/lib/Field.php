<?php
declare(strict_types=1);

/**
 * قواعد موردهای اپ بازرسی میدانی: نوع‌ها، وضعیت‌ها، نقش فایل‌ها، و بررسی
 * ورودی. سرور هر قاعده‌ای را که اپ اجرا می‌کند دوباره اجرا می‌کند — اپ قدیمی
 * یا دست‌کاری‌شده نباید بتواند گزارش بی‌شرح یا تصویر بی‌پلاک بفرستد.
 */
final class Field
{
    public const KIND_CRYPTO = 1;
    public const KIND_ILLEGAL = 2;
    public const KIND_THERMAL = 3;
    public const KIND_FEEDER = 4;

    /** پیشوند کد رهگیری و مجوز لازم برای هر نوع. */
    public const KINDS = [
        self::KIND_CRYPTO  => ['prefix' => 'RZ', 'perm' => Auth::PERM_REPORT],
        self::KIND_ILLEGAL => ['prefix' => 'GH', 'perm' => Auth::PERM_REPORT],
        self::KIND_THERMAL => ['prefix' => 'TV', 'perm' => Auth::PERM_INSPECT],
        self::KIND_FEEDER  => ['prefix' => 'FD', 'perm' => Auth::PERM_INSPECT],
    ];

    // چرخه وضعیت هر کد.
    public const STATUS_REGISTERED = 0;
    public const STATUS_REVIEWING = 1;
    public const STATUS_REFERRED = 2;
    public const STATUS_RESULT = 3;
    public const STATUS_CLOSED = 4;
    public const STATUS_REJECTED = 5;
    public const STATUS_REVISIT = 6;

    // نقش هر فایل، و پسوندی که روی سرور با آن ذخیره می‌شود.
    public const ROLE_PHOTO = 0;
    public const ROLE_PHOTO_STAMPED = 1;
    public const ROLE_VIDEO = 2;
    public const ROLE_AUDIO = 3;
    public const ROLE_THERMAL = 4;
    public const ROLE_THERMAL_EXIF = 5;
    public const ROLE_SIDECAR = 6;
    public const ROLE_TRACK = 7;

    public const ROLE_EXTENSIONS = [
        self::ROLE_PHOTO => ['image/jpeg' => 'jpg', 'image/png' => 'png'],
        self::ROLE_PHOTO_STAMPED => ['image/jpeg' => 'jpg'],
        self::ROLE_VIDEO => ['video/mp4' => 'mp4'],
        self::ROLE_AUDIO => ['audio/mp4' => 'm4a', 'audio/aac' => 'm4a'],
        self::ROLE_THERMAL => ['image/jpeg' => 'jpg', 'video/mp4' => 'mp4'],
        self::ROLE_THERMAL_EXIF => ['image/jpeg' => 'jpg'],
        self::ROLE_SIDECAR => ['application/json' => 'json'],
        self::ROLE_TRACK => ['application/gzip' => 'gz'],
    ];

    public const ASSET_POLE = 0;
    public const ASSET_PANEL = 1;

    /** بزرگ‌ترین payload پذیرفتنی؛ متن و چک‌لیست است، نه فایل. */
    public const MAX_PAYLOAD_BYTES = 262144;

    public static function isUuid(mixed $value): bool
    {
        return is_string($value)
            && preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i', $value) === 1;
    }

    /**
     * ایراد یک مورد، به فارسی، یا تهی اگر پذیرفتنی است.
     *
     * @param array<string, mixed> $item
     * @param array<int, array<string, mixed>> $files
     */
    public static function problem(array $item, array $files): ?string
    {
        if (!self::isUuid($item['id'] ?? null)) {
            return 'شناسه مورد معتبر نیست.';
        }
        $kind = (int) ($item['kind'] ?? 0);
        if (!isset(self::KINDS[$kind])) {
            return 'نوع مورد شناخته نیست.';
        }
        if ((int) ($item['created_at'] ?? 0) <= 0) {
            return 'زمان ثبت مورد نیامده است.';
        }
        $hasPosition = is_numeric($item['latitude'] ?? null) && is_numeric($item['longitude'] ?? null)
            && abs((float) $item['latitude']) <= 90 && abs((float) $item['longitude']) <= 180;
        $plate = trim((string) ($item['plate'] ?? ''));

        if ($kind === self::KIND_CRYPTO || $kind === self::KIND_ILLEGAL) {
            if (trim((string) ($item['description'] ?? '')) === '') {
                return 'توضیح گزارش اجباری است.';
            }
            if (!$hasPosition) {
                return 'موقعیت گزارش ثبت نشده است.';
            }
        }
        if ($kind === self::KIND_FEEDER) {
            if ($plate === '') {
                return 'شماره پلاک تابلو اجباری است.';
            }
            if (!$hasPosition) {
                return 'موقعیت تابلو ثبت نشده است.';
            }
        }
        if (isset($item['payload']) && strlen((string) json_encode($item['payload'])) > self::MAX_PAYLOAD_BYTES) {
            return 'اطلاعات مورد بیش از حد بزرگ است.';
        }

        foreach ($files as $file) {
            $problem = self::fileProblem($kind, $file);
            if ($problem !== null) {
                return $problem;
            }
        }
        return null;
    }

    /** @param array<string, mixed> $file */
    private static function fileProblem(int $kind, array $file): ?string
    {
        if (!self::isUuid($file['id'] ?? null)) {
            return 'شناسه فایل معتبر نیست.';
        }
        $role = (int) ($file['role'] ?? -1);
        $mime = (string) ($file['mime'] ?? '');
        if (!isset(self::ROLE_EXTENSIONS[$role][$mime])) {
            return 'نوع فایل پذیرفته نمی‌شود.';
        }
        if ((int) ($file['size'] ?? 0) <= 0 || preg_match('/^[0-9a-f]{64}$/', (string) ($file['sha256'] ?? '')) !== 1) {
            return 'حجم یا اثر انگشت فایل نیامده است.';
        }
        // هر تصویر و فیلم ترموویژن باید بگوید از کدام تیر یا تابلو است.
        if ($kind === self::KIND_THERMAL && $role === self::ROLE_THERMAL) {
            $asset = $file['asset_type'] ?? null;
            if (!in_array($asset, [self::ASSET_POLE, self::ASSET_PANEL], true)
                || trim((string) ($file['plate'] ?? '')) === '') {
                return 'برای هر فایل ترموویژن، نوع (تیر یا تابلو) و شماره پلاک اجباری است.';
            }
        }
        return null;
    }
}
