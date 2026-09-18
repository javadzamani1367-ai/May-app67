<?php
declare(strict_types=1);

/** ورودی درخواست: مسیر، بدنه JSON، و فایل‌های بارگذاری‌شده. */
final class Request
{
    public string $method;
    public string $path;
    public array $body;

    public function __construct()
    {
        $this->method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
        $this->path = $this->resolvePath();
        $this->body = $this->readBody();
    }

    private function resolvePath(): string
    {
        // با .htaccess مسیر در پارامتر route می‌آید؛ اگر بازنویسی کار نکرد،
        // مسیر از خود URL در می‌آید تا سرویس روی هاست‌های سخت‌گیر هم بالا بیاید.
        $route = $_GET['route'] ?? null;
        if (is_string($route) && $route !== '') {
            return '/' . trim($route, '/');
        }
        $uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
        $base = rtrim(dirname($_SERVER['SCRIPT_NAME'] ?? ''), '/');
        if ($base !== '' && str_starts_with($uri, $base)) {
            $uri = substr($uri, strlen($base));
        }
        return '/' . trim($uri, '/');
    }

    private function readBody(): array
    {
        $raw = file_get_contents('php://input');
        if ($raw === false || $raw === '') {
            return $_POST;
        }
        $decoded = json_decode($raw, true);
        return is_array($decoded) ? $decoded : $_POST;
    }

    public function str(string $key, string $fallback = ''): string
    {
        $value = $this->body[$key] ?? $_GET[$key] ?? $fallback;
        return is_scalar($value) ? trim((string) $value) : $fallback;
    }

    public function int(string $key, ?int $fallback = null): ?int
    {
        $value = $this->body[$key] ?? $_GET[$key] ?? null;
        if ($value === null || $value === '') {
            return $fallback;
        }
        return is_numeric($value) ? (int) $value : $fallback;
    }

    public function arr(string $key): array
    {
        $value = $this->body[$key] ?? null;
        return is_array($value) ? $value : [];
    }

    public function bearer(): ?string
    {
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
        if ($header === '' && function_exists('apache_request_headers')) {
            $headers = apache_request_headers();
            $header = $headers['Authorization'] ?? $headers['authorization'] ?? '';
        }
        if (stripos($header, 'Bearer ') === 0) {
            return trim(substr($header, 7));
        }
        return null;
    }
}
