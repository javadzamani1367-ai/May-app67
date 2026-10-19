<?php
declare(strict_types=1);

/**
 * ورود و نشست.
 *
 * رمز عبور با password_hash خود PHP نگه‌داری می‌شود و توکن هرگز خام ذخیره
 * نمی‌شود: فقط SHA-256 آن در پایگاه داده می‌نشیند، پس دسترسی به جدول هم
 * نشست کسی را نمی‌دزدد.
 */
final class Auth
{
    public const ROLE_EXPERT = 0;
    public const ROLE_MANAGER = 1;
    public const ROLE_UNIT = 2;
    /** کاربر اپ بازرسی میدانی. کارهایش را ستون permissions تعیین می‌کند. */
    public const ROLE_FIELD = 3;
    /** مجری رمزارز: اختیارات مدیر، فقط در ناحیه‌های خودش؛ کاربر نمی‌سازد. */
    public const ROLE_EXECUTOR = 4;
    /** مجری برق غیرمجاز ستاد: همه پرونده‌های برق غیرمجاز استان؛ پشتیبان مجری‌های شهرستان. */
    public const ROLE_ILLEGAL = 5;
    /** مجری برق غیرمجاز یک شهرستان: پرونده‌های غیرمجاز ناحیه خودش را می‌گرداند. */
    public const ROLE_ILLEGAL_AREA = 6;

    /** نقش‌هایی که پرونده را می‌گردانند: ارجاع، تأیید، ارسال به واحد (هر کدام در حوزه خودش). */
    public const CASE_MANAGERS = [self::ROLE_MANAGER, self::ROLE_EXECUTOR, self::ROLE_ILLEGAL, self::ROLE_ILLEGAL_AREA];

    /** مجوزهای کاربر میدانی، به صورت بیت: مدیر یکی یا هر دو را می‌دهد. */
    public const PERM_INSPECT = 1;
    public const PERM_REPORT = 2;

    /**
     * کاربر پشت توکن، اگر نقشش یکی از این‌هاست؛ وگرنه ۴۰۳. مسیرهایی که مدیر و
     * مجری هر دو دارند از این می‌گذرند و محدوده را Scope تعیین می‌کند.
     *
     * @param list<int> $roles
     */
    public static function requireAny(Request $request, array $roles): array
    {
        $user = self::require($request, null, in_array(self::ROLE_FIELD, $roles, true));
        if (!in_array((int) $user['role'], $roles, true)) {
            Response::fail(403, 'forbidden', 'این بخش برای نقش کاربری شما نیست.');
        }
        return $user;
    }

    public static function hashToken(string $token): string
    {
        return hash('sha256', $token . Config::get('app_key', ''));
    }

    public static function issueToken(string $userId, ?string $deviceCode, int $role = self::ROLE_EXPERT): string
    {
        $token = bin2hex(random_bytes(32));
        $now = Db::now();
        // نشست اپ میدانی کوتاه‌تر است: کاربرانش بیشترند و گوشی‌شان قفل دستگاه
        // ندارد، پس گوشی گم‌شده باید زودتر از سرور بریده شود.
        $days = $role === self::ROLE_FIELD
            ? (int) Config::get('field_token_days', 7)
            : (int) Config::get('token_days', 30);
        Db::run(
            'INSERT INTO tokens (token_hash, user_id, device_code, created_at, expires_at)
             VALUES (?, ?, ?, ?, ?)',
            [self::hashToken($token), $userId, $deviceCode, $now, $now + $days * 86400000]
        );
        return $token;
    }

    /**
     * کاربر پشت توکن، یا خطای ۴۰۱. هیچ مسیری بدون این فراخوانی محافظت نشده است.
     *
     * کاربر میدانی فقط به مسیری راه دارد که خودش را صریحاً بپذیرد ($role برابر
     * ROLE_FIELD یا $allowField). مسیرهای قدیمی بدون نقش نوشته شده‌اند چون آن
     * روز فقط کارشناس و مدیر و واحد بودند؛ اگر نقش تازه خودبه‌خود از آن‌ها رد
     * می‌شد، کاربر میدانی می‌توانست پرونده رسمی بفرستد یا ارسال‌ها را بخواند.
     */
    public static function require(Request $request, ?int $role = null, bool $allowField = false): array
    {
        $token = $request->bearer();
        if ($token === null || $token === '') {
            Response::fail(401, 'no_token', 'برای این درخواست باید وارد شوید.');
        }
        $row = Db::one(
            'SELECT u.* FROM tokens t
             JOIN users u ON u.id = t.user_id
             WHERE t.token_hash = ? AND t.expires_at > ? AND u.active = 1',
            [self::hashToken($token), Db::now()]
        );
        if ($row === null) {
            Response::fail(401, 'bad_token', 'نشست شما منقضی شده است. دوباره وارد شوید.');
        }
        if ($role !== null && (int) $row['role'] !== $role) {
            Response::fail(403, 'forbidden', 'این بخش برای نقش کاربری شما نیست.');
        }
        if ($role === null && !$allowField && (int) $row['role'] === self::ROLE_FIELD) {
            Response::fail(403, 'forbidden', 'این بخش برای نقش کاربری شما نیست.');
        }
        return $row;
    }

    /**
     * کد دستگاه، به یک شکل واحد.
     *
     * گوشی کد را با خط تیره و ارقام فارسی نشان می‌دهد تا خواندنش آسان باشد:
     * «FD۴۲-۰۲۴۳-ABAC-۴۸۵۶». مدیری که همان را عیناً تایپ کند، با صفحه‌کلید
     * فارسی، رشته‌ای ثبت می‌کرد که هرگز با کد خام گوشی یکی نمی‌شد و کارشناس
     * برای همیشه پیام «دستگاه دیگر» می‌گرفت. پس ارقام لاتین می‌شوند و هر چیزی
     * جز حرف و رقم کنار می‌رود.
     */
    public static function normaliseDevice(?string $code): string
    {
        $code = strtr((string) $code, [
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4',
            '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
        ]);
        return strtoupper((string) preg_replace('/[^A-Za-z0-9]/', '', $code));
    }

    /**
     * کد و رمز؛ قفل دستگاه جداست (PersonDevices) و بعد از بررسی اپ می‌آید، تا حسابی
     * که برای اپ دیگری است پیام «دستگاه» نگیرد.
     */
    public static function login(string $userCode, string $password): array
    {
        $userCode = self::latin(trim($userCode));
        // قفل پیش از هر چیز: حساب قفل‌شده حتی با رمز درست هم باز نمی‌شود، وگرنه
        // قفل فقط حدس‌زدن را کند می‌کرد و جلویش را نمی‌گرفت.
        LoginGuard::check($userCode);
        $user = self::userByCode($userCode);

        // پیام یکسان برای «کاربر نیست» و «رمز غلط»: وگرنه می‌شود فهرست
        // کدهای کاربری معتبر را از همین تفاوت پیام بیرون کشید.
        if ($user === null || !self::passwordMatches($user, $password)) {
            LoginGuard::failed($userCode);
            Response::fail(401, 'bad_credentials', 'کد کاربری یا رمز عبور درست نیست.');
        }
        LoginGuard::succeeded($userCode);
        return $user;
    }

    /**
     * کاربر فعال با این کد. کد با ارقام لاتین جستجو می‌شود، و برای کدهای قدیمی که با
     * صفحه‌کلید فارسی ثبت شده‌اند، به همان شکلی هم که تایپ شده.
     */
    public static function userByCode(string $typed): ?array
    {
        $typed = trim($typed);
        $latin = self::latin($typed);
        return Db::one('SELECT * FROM users WHERE user_code IN (?, ?) AND active = 1 ORDER BY user_code = ? DESC LIMIT 1',
            [$latin, $typed, $latin]);
    }

    /**
     * رمز، همان‌طور که تایپ شده یا با ارقام لاتین: یک صفحه‌کلید «۱۲۳» می‌دهد و دیگری
     * «123»، و آدم هر دو را یک رمز می‌داند. رمزهای تازه با ارقام لاتین ذخیره می‌شوند.
     */
    public static function passwordMatches(array $user, string $password): bool
    {
        $hash = (string) ($user['password_hash'] ?? '');
        if ($hash === '') {
            return false;
        }
        return password_verify($password, $hash) || password_verify(self::latin($password), $hash);
    }

    public static function hashPassword(string $password): string
    {
        return password_hash(self::latin($password), PASSWORD_DEFAULT);
    }

    /** ارقام فارسی و عربی به لاتین؛ بقیه دست نمی‌خورد. */
    public static function latin(string $text): string
    {
        return strtr($text, ['۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4', '۵' => '5', '۶' => '6',
            '۷' => '7', '۸' => '8', '۹' => '9', '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9']);
    }
}
