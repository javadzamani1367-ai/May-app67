<?php
declare(strict_types=1);

/**
 * فایل‌های یک پرونده: تصویر، ویدیو و مدرک پیوست.
 *
 * فایل با **شناسه سطر** خواسته و فرستاده می‌شود، نه با مسیر. نسخه اول مسیر را
 * از کلاینت می‌گرفت و همین یعنی هر کاربر وارد‌شده — از جمله حساب یک واحد —
 * می‌توانست با حدس زدن مسیر، فایل هر پرونده‌ای را بردارد. با شناسه، همان قاعده
 * دسترسی پرونده اعمال می‌شود: کارشناس فقط پرونده‌های خودش.
 *
 * مسیر ذخیره، همان مسیر نسبی گوشی است (`media/<report_id>/<millis>.jpg`) تا هر
 * سه انبار — گوشی، سرور، آرشیو ویندوز — یک چیدمان داشته باشند.
 */
final class SyncFileController
{
    private const KINDS = [
        'media' => ['table' => 'media', 'sized' => true],
        'attachment' => ['table' => 'attachments', 'sized' => false],
    ];

    public function upload(Request $request): void
    {
        $user = Auth::require($request);
        [$table, $sized, $row] = $this->locate($request, $user);

        $file = $_FILES['file'] ?? null;
        if (!is_array($file)) {
            Response::fail(400, 'no_file', 'فایلی ارسال نشده است.');
        }

        // مسیر مقصد از ستون خود سطر می‌آید، نه از چیزی که کلاینت فرستاده، تا
        // بارگذاری نتواند فایل سطر دیگری را عوض کند.
        $stored = Storage::putAt($file, (string) $row['file_path']);
        if ($sized) {
            Db::run('UPDATE media SET size_bytes = ? WHERE id = ?', [$stored['size'], $row['id']]);
        }

        Response::json(['stored' => true, 'path' => $stored['path'], 'size' => $stored['size']]);
    }

    public function download(Request $request): void
    {
        $user = Auth::require($request);
        [, , $row] = $this->locate($request, $user);

        $path = Storage::resolve((string) $row['file_path']);
        if ($path === null || !is_file($path)) {
            Response::fail(404, 'no_file', 'این فایل روی سرور نیست.');
        }

        header('Content-Type: application/octet-stream');
        header('Content-Length: ' . filesize($path));
        header('Content-Disposition: attachment; filename="' . basename($path) . '"');
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        exit;
    }

    /**
     * سطر را پیدا می‌کند و اجازه دسترسی را می‌سنجد. خروجی: نام جدول، اینکه
     * حجم را نگه می‌دارد یا نه، و خود سطر همراه کد کارشناس پرونده.
     *
     * @return array{0: string, 1: bool, 2: array<string, mixed>}
     */
    private function locate(Request $request, array $user): array
    {
        $kind = $request->str('kind');
        if (!isset(self::KINDS[$kind])) {
            Response::fail(400, 'bad_kind', 'نوع فایل مشخص نیست.');
        }
        $table = self::KINDS[$kind]['table'];

        $row = Db::one(
            "SELECT c.id, c.file_path, r.expert_code
             FROM $table c JOIN reports r ON r.id = c.report_id
             WHERE c.id = ?",
            [$request->str('id')]
        );
        if ($row === null) {
            Response::fail(404, 'not_found', 'این فایل در پرونده‌ها ثبت نشده است.');
        }
        if ((int) $user['role'] === Auth::ROLE_EXPERT && $row['expert_code'] !== $user['user_code']) {
            Response::fail(403, 'forbidden', 'این پرونده برای شما نیست.');
        }
        if (trim((string) $row['file_path']) === '') {
            Response::fail(409, 'no_path', 'این سطر مسیر فایلی ندارد.');
        }

        return [$table, (bool) self::KINDS[$kind]['sized'], $row];
    }
}
