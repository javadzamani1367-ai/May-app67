<?php
declare(strict_types=1);

/**
 * بارگذاری قابل ادامه فایل‌های اپ میدانی.
 *
 * فیلم و تصویر از روستایی با یک خط آنتن بالا می‌رود؛ قطع شدن وسط یک فایل
 * شصت‌مگابایتی نباید یعنی شروع دوباره از صفر. گوشی فایل را تکه‌تکه می‌فرستد و
 * هر تکه می‌گوید از کجای فایل است. سرور فقط تکه‌ای را می‌پذیرد که دقیقاً از
 * انتهای آنچه دارد شروع شود، و همیشه می‌گوید تا کجا رسیده — پس گوشی بعد از هر
 * قطعی از همان‌جا ادامه می‌دهد.
 *
 * تکه‌ها کوچک‌اند (پیش‌فرض یک مگابایت) چون هاست اشتراکی بدنه درخواست را
 * محدود می‌کند و یک فایل بزرگ در یک درخواست، همان‌جا رد می‌شد.
 *
 * وقتی آخرین تکه رسید، SHA-256 کل فایل با آنچه گوشی اعلام کرده مقایسه می‌شود.
 * فایلی که نخواند دور ریخته می‌شود؛ هرگز فایل خراب به جای مدرک نمی‌نشیند.
 */
final class FieldUploadController
{
    public function chunk(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_FIELD);
        // پارامترها فقط از نشانی خوانده می‌شوند: بدنه، خود تکه فایل است، و تکه‌ای
        // از یک فایل JSON همراه می‌تواند خودش JSON معتبر باشد و کلید هم‌نام داشته باشد.
        $fileId = is_string($_GET['file_id'] ?? null) ? $_GET['file_id'] : '';
        if (!Field::isUuid($fileId)) {
            Response::fail(400, 'bad_file', 'شناسه فایل معتبر نیست.');
        }
        $file = Db::one(
            'SELECT f.*, i.user_id FROM field_files f JOIN field_items i ON i.id = f.item_id WHERE f.id = ?',
            [$fileId]
        );
        if ($file === null || $file['user_id'] !== $user['id']) {
            Response::fail(404, 'no_file', 'این فایل روی سرور ثبت نشده است. اول خود مورد ارسال شود.');
        }
        $size = (int) $file['size'];
        if ((int) $file['complete'] === 1) {
            Response::json(['received' => $size, 'complete' => true]);
        }

        $part = self::partPath($fileId);
        clearstatcache(true, $part);
        $received = is_file($part) ? (int) filesize($part) : 0;
        $offset = isset($_GET['offset']) && ctype_digit((string) $_GET['offset']) ? (int) $_GET['offset'] : null;
        $body = (string) file_get_contents('php://input');

        // بدون بدنه یعنی «تا کجا رسیده‌ای؟» — گوشی بعد از هر قطعی اول همین را می‌پرسد.
        if ($body === '' || $offset === null) {
            Response::json(['received' => $received, 'complete' => false]);
        }
        if ($offset !== $received) {
            Response::json(['received' => $received, 'complete' => false, 'accepted' => false]);
        }
        $maxChunk = max(64, (int) Config::get('field_chunk_kb', 1024)) * 1024;
        if (strlen($body) > $maxChunk) {
            Response::fail(413, 'chunk_too_large', 'تکه فایل بزرگ‌تر از حد مجاز است.');
        }
        if ($received + strlen($body) > $size) {
            @unlink($part);
            Response::fail(400, 'size_mismatch', 'فایل از حجم اعلام‌شده بزرگ‌تر شد؛ از ابتدا ارسال شود.');
        }

        if (!is_dir(dirname($part)) && !mkdir(dirname($part), 0750, true)) {
            Response::fail(500, 'storage_unwritable', 'پوشه ذخیره‌سازی قابل نوشتن نیست.');
        }
        if (file_put_contents($part, $body, FILE_APPEND | LOCK_EX) === false) {
            Response::fail(500, 'storage_failed', 'ذخیره فایل روی سرور ناموفق بود.');
        }
        $received += strlen($body);
        if ($received < $size) {
            Response::json(['received' => $received, 'complete' => false, 'accepted' => true]);
        }
        $this->finish($file, $part);
    }

    private function finish(array $file, string $part): void
    {
        if (!hash_equals((string) $file['sha256'], (string) hash_file('sha256', $part))) {
            @unlink($part);
            Response::json(['received' => 0, 'complete' => false, 'accepted' => false, 'error' => 'hash_mismatch']);
        }
        $extension = Field::ROLE_EXTENSIONS[(int) $file['role']][(string) $file['mime']] ?? 'bin';
        $relative = 'field/' . $file['item_id'] . '/' . $file['id'] . '.' . $extension;
        $target = Storage::root() . '/' . $relative;
        if (!is_dir(dirname($target)) && !mkdir(dirname($target), 0750, true)) {
            Response::fail(500, 'storage_unwritable', 'پوشه ذخیره‌سازی قابل نوشتن نیست.');
        }
        if (!rename($part, $target)) {
            Response::fail(500, 'storage_failed', 'ذخیره فایل روی سرور ناموفق بود.');
        }
        Db::run('UPDATE field_files SET path = ?, complete = 1 WHERE id = ?', [$relative, $file['id']]);
        Response::json(['received' => (int) $file['size'], 'complete' => true, 'accepted' => true]);
    }

    private static function partPath(string $fileId): string
    {
        return Storage::root() . '/field-uploads/' . $fileId . '.part';
    }
}
