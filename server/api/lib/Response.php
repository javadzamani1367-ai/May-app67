<?php
declare(strict_types=1);

/** هر پاسخ JSON است و همیشه یک شکل دارد، تا کلاینت یک مسیر خطا داشته باشد. */
final class Response
{
    public static function json($data, int $status = 200): void
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        echo json_encode(
            ['ok' => $status < 400, 'data' => $data],
            JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
        );
        exit;
    }

    /**
     * خطا با کد ماشین‌خوان و پیام فارسی. پیام برای نمایش به کاربر است و
     * هرگز جزئیات داخلی مثل متن خطای پایگاه داده را بیرون نمی‌دهد.
     */
    public static function fail(int $status, string $code, string $message): void
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(
            ['ok' => false, 'error' => ['code' => $code, 'message' => $message]],
            JSON_UNESCAPED_UNICODE
        );
        exit;
    }
}
