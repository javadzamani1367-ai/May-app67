<?php
declare(strict_types=1);

/**
 * اپ بازرسی میدانی: مجوزها، ارسال مورد، و وضعیت موردهای خود کاربر.
 *
 * گوشی آفلاین کار می‌کند و هر مورد را با شناسه UUID خودش می‌فرستد؛ ارسال
 * دوباره همان مورد هرگز مورد دوم نمی‌سازد. کد رهگیری را سرور در اولین دریافت
 * می‌دهد و از آن به بعد ثابت است.
 */
final class FieldController
{
    /** کاربر، مجوزها و تنظیمات — گوشی بعد از هر همگام‌سازی همین را می‌خواند. */
    public function me(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        Response::json([
            'user' => AuthController::publicUser($user),
            'settings' => FieldSettings::forPhone(),
            'server_time' => Db::now(),
        ]);
    }

    public function push(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        $item = $request->arr('item');
        $files = array_values(array_filter($request->arr('files'), 'is_array'));

        $problem = Field::problem($item, $files);
        if ($problem !== null) {
            Response::fail(422, 'invalid', $problem);
        }
        $kind = (int) $item['kind'];
        // مجوز روی سرور هم بررسی می‌شود، نه فقط با پنهان کردن دکمه در اپ.
        if (((int) $user['permissions'] & Field::KINDS[$kind]['perm']) === 0) {
            Response::fail(403, 'no_permission', 'مجوز این نوع کار برای حساب شما فعال نیست.');
        }

        $id = (string) $item['id'];
        $existing = Db::one('SELECT user_id, kind, status, client_updated_at FROM field_items WHERE id = ?', [$id]);
        if ($existing !== null && ($existing['user_id'] !== $user['id'] || (int) $existing['kind'] !== $kind)) {
            Response::fail(403, 'forbidden', 'این مورد متعلق به حساب دیگری است.');
        }

        $pdo = Db::conn();
        $pdo->beginTransaction();
        try {
            if ($existing === null) {
                $this->insert($item, $user);
            } elseif ((int) $existing['status'] === Field::STATUS_REGISTERED
                && (int) ($item['updated_at'] ?? 0) > (int) $existing['client_updated_at']) {
                // تا مدیر بررسی را شروع نکرده، کاربر می‌تواند مورد خودش را اصلاح
                // کند. بعد از آن، آنچه مدیر می‌خواند زیر دستش عوض نمی‌شود.
                $this->update($item);
            }
            foreach ($files as $file) {
                $this->saveFile($id, $file);
            }
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }

        $row = Db::one('SELECT tracking_code, status FROM field_items WHERE id = ?', [$id]);
        Response::json([
            'tracking_code' => $row['tracking_code'],
            'status' => (int) $row['status'],
            'missing_files' => array_column(
                Db::all('SELECT id FROM field_files WHERE item_id = ? AND complete = 0', [$id]),
                'id'
            ),
        ]);
    }

    /** موردهای خود کاربر که از `since` به بعد روی سرور تغییر کرده‌اند. */
    public function items(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        $since = max(0, (int) $request->int('since', 0));
        $rows = Db::all(
            'SELECT id, kind, tracking_code, status, updated_at FROM field_items
             WHERE user_id = ? AND updated_at > ? ORDER BY updated_at LIMIT 500',
            [$user['id'], $since]
        );
        foreach ($rows as &$row) {
            $row['kind'] = (int) $row['kind'];
            $row['status'] = (int) $row['status'];
            $row['updated_at'] = (int) $row['updated_at'];
        }
        Response::json(['items' => $rows, 'server_time' => Db::now()]);
    }

    /** @param array<string, mixed> $item */
    private function insert(array $item, array $user): void
    {
        $now = Db::now();
        $kind = (int) $item['kind'];
        $code = FieldCodes::next($kind, (int) $item['created_at']);
        Db::run(
            'INSERT INTO field_items (id, kind, tracking_code, user_id, device_code, created_at,
                client_updated_at, received_at, updated_at, status, priority, latitude, longitude,
                accuracy, address, plate, description, payload)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
            array_merge(
                [$item['id'], $kind, $code, $user['id'], self::text($item['device_code'] ?? null, 64),
                 (int) $item['created_at'], (int) ($item['updated_at'] ?? $item['created_at']),
                 $now, $now, Field::STATUS_REGISTERED],
                self::content($item)
            )
        );
        self::event((string) $item['id'], (string) $user['id'], 'registered', null, Field::STATUS_REGISTERED);
    }

    /** @param array<string, mixed> $item */
    private function update(array $item): void
    {
        Db::run(
            'UPDATE field_items SET priority = ?, latitude = ?, longitude = ?, accuracy = ?, address = ?,
                plate = ?, description = ?, payload = ?, client_updated_at = ?, updated_at = ?
             WHERE id = ?',
            array_merge(self::content($item), [(int) $item['updated_at'], Db::now(), $item['id']])
        );
    }

    /**
     * فراداده یک فایل. تا فایل کامل نرسیده، اثر انگشت و حجمش قابل اصلاح است؛
     * بعد از آن فقط تخصیصش (تیر یا تابلو، پلاک، شرح) — محتوای رسیده دیگر عوض
     * نمی‌شود.
     *
     * @param array<string, mixed> $file
     */
    private function saveFile(string $itemId, array $file): void
    {
        $owner = Db::one('SELECT item_id FROM field_files WHERE id = ?', [$file['id']]);
        if ($owner !== null && $owner['item_id'] !== $itemId) {
            Response::fail(403, 'forbidden', 'این فایل متعلق به مورد دیگری است.');
        }
        $asset = isset($file['asset_type']) ? (int) $file['asset_type'] : null;
        Db::run(
            'INSERT INTO field_files (id, item_id, role, mime, size, sha256, captured_at, latitude,
                longitude, accuracy, location_uncertain, asset_type, plate, note, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE
                mime = IF(complete = 1, mime, VALUES(mime)),
                size = IF(complete = 1, size, VALUES(size)),
                sha256 = IF(complete = 1, sha256, VALUES(sha256)),
                captured_at = VALUES(captured_at), latitude = VALUES(latitude),
                longitude = VALUES(longitude), accuracy = VALUES(accuracy),
                location_uncertain = VALUES(location_uncertain), asset_type = VALUES(asset_type),
                plate = VALUES(plate), note = VALUES(note)',
            [
                $file['id'], $itemId, (int) $file['role'], (string) $file['mime'], (int) $file['size'],
                strtolower((string) $file['sha256']), self::intOrNull($file['captured_at'] ?? null),
                self::floatOrNull($file['latitude'] ?? null), self::floatOrNull($file['longitude'] ?? null),
                self::floatOrNull($file['accuracy'] ?? null), empty($file['location_uncertain']) ? 0 : 1,
                $asset, self::text($file['plate'] ?? null, 64), self::text($file['note'] ?? null, 2000), Db::now(),
            ]
        );
    }

    public static function event(string $itemId, ?string $userId, string $action, ?int $from, ?int $to, ?string $note = null): void
    {
        Db::run(
            'INSERT INTO field_events (item_id, at, user_id, action, from_status, to_status, note)
             VALUES (?, ?, ?, ?, ?, ?, ?)',
            [$itemId, Db::now(), $userId, $action, $from, $to, $note]
        );
    }

    /** @param array<string, mixed> $item */
    private static function content(array $item): array
    {
        $priority = isset($item['priority']) && in_array((int) $item['priority'], [0, 1, 2], true)
            ? (int) $item['priority'] : null;
        return [
            $priority,
            self::floatOrNull($item['latitude'] ?? null),
            self::floatOrNull($item['longitude'] ?? null),
            self::floatOrNull($item['accuracy'] ?? null),
            self::text($item['address'] ?? null, 500),
            self::text($item['plate'] ?? null, 64),
            self::text($item['description'] ?? null, 20000),
            isset($item['payload']) ? json_encode($item['payload'], JSON_UNESCAPED_UNICODE) : null,
        ];
    }

    private static function text(mixed $value, int $max): ?string
    {
        if (!is_scalar($value)) {
            return null;
        }
        $text = trim((string) $value);
        return $text === '' ? null : mb_substr($text, 0, $max);
    }

    private static function floatOrNull(mixed $value): ?float
    {
        return is_numeric($value) ? (float) $value : null;
    }

    private static function intOrNull(mixed $value): ?int
    {
        return is_numeric($value) ? (int) $value : null;
    }
}
