<?php
declare(strict_types=1);

/**
 * ساخت یک‌جای کاربرهای ناحیه‌ها: برای هر ناحیه یک مجری رمزارز و چهار واحد مقصد.
 *
 * کدها قابل حدس‌اند (401-legal) چون باید به خاطر بمانند؛ امنیت با رمز است که
 * تصادفی ساخته می‌شود و فقط یک بار، همان لحظه، نشان داده می‌شود. کدی که از قبل
 * هست دست نمی‌خورد — ساختن دوباره، رمز کسی را که کار می‌کند عوض نمی‌کرد.
 */
final class UserBatch
{
    public const EXECUTOR = 'exec';

    /** بدون حرف و رقم هم‌شکل (۰ و O، ۱ و l)، تا از روی برگه چاپی درست تایپ شود. */
    private const ALPHABET = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';

    /**
     * آنچه ساخته می‌شود: هر سطر یک کاربر، و اینکه از قبل هست یا نه.
     *
     * @param list<string> $areas
     * @param list<string> $kinds exec یا نام واحد (sales، security، legal، power)
     * @return list<array<string, mixed>>
     */
    public static function plan(array $areas, array $kinds): array
    {
        $rows = [];
        foreach (Areas::parse($areas) as $area) {
            foreach ($kinds as $kind) {
                $unit = array_search($kind, UnitStages::SLUGS, true);
                if ($kind !== self::EXECUTOR && $unit === false) {
                    continue;
                }
                $code = $area . '-' . $kind;
                $title = $kind === self::EXECUTOR ? 'مجری رمزارز' : UnitStages::NAMES[(int) $unit];
                $rows[] = [
                    'user_code' => $code,
                    'full_name' => $title . ' ' . Areas::label($area),
                    'role' => $kind === self::EXECUTOR ? Auth::ROLE_EXECUTOR : Auth::ROLE_UNIT,
                    'unit' => $kind === self::EXECUTOR ? null : (int) $unit,
                    'areas' => $area,
                    'exists' => Db::one('SELECT id FROM users WHERE user_code = ?', [$code]) !== null,
                ];
            }
        }
        return $rows;
    }

    /**
     * کاربرهای تازه را می‌سازد و کد و رمز هر کدام را برمی‌گرداند.
     *
     * @param list<array<string, mixed>> $plan
     * @return list<array{user_code:string, full_name:string, password:string}>
     */
    public static function create(array $plan, string $managerId): array
    {
        $made = [];
        foreach ($plan as $row) {
            if ($row['exists']) {
                continue;
            }
            $password = self::password();
            Users::save([
                'user_code' => $row['user_code'], 'full_name' => $row['full_name'], 'role' => $row['role'],
                'unit' => $row['unit'], 'areas' => $row['areas'], 'password' => $password,
            ], $managerId);
            $made[] = ['user_code' => $row['user_code'], 'full_name' => $row['full_name'], 'password' => $password];
        }
        return $made;
    }

    public static function password(int $length = 10): string
    {
        $out = '';
        $max = strlen(self::ALPHABET) - 1;
        for ($i = 0; $i < $length; $i++) {
            $out .= self::ALPHABET[random_int(0, $max)];
        }
        return $out;
    }
}
