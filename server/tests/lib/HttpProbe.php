<?php
declare(strict_types=1);

/**
 * وب‌سرور داخلی PHP، جلوی سرویس، برای تست.
 *
 * درخواست‌ها واقعاً از روی HTTP می‌روند و نه با صدا زدن مستقیم کلاس‌ها، چون
 * چیزهایی که می‌شکنند اغلب همان‌هایی هستند که فقط در مسیر واقعی دیده می‌شوند:
 * هدر Authorization، بدنه multipart، و کد وضعیت.
 */
final class HttpProbe
{
    private $process = null;
    private array $pipes = [];

    public function __construct(
        private string $docRoot,
        private string $configPath,
        private int $port = 8791
    ) {
    }

    public function start(): bool
    {
        $this->process = proc_open(
            ['php', '-S', "127.0.0.1:$this->port", '-t', $this->docRoot],
            [1 => ['pipe', 'w'], 2 => ['pipe', 'w']],
            $this->pipes,
            null,
            ['INSPECTION_CONFIG' => $this->configPath] + $_ENV
        );
        if (!is_resource($this->process)) {
            return false;
        }

        for ($attempt = 0; $attempt < 50; $attempt++) {
            usleep(100_000);
            $socket = @fsockopen('127.0.0.1', $this->port, $errno, $error, 0.2);
            if ($socket !== false) {
                fclose($socket);
                return true;
            }
        }
        return false;
    }

    public function stop(): void
    {
        foreach ($this->pipes as $pipe) {
            if (is_resource($pipe)) {
                fclose($pipe);
            }
        }
        if (is_resource($this->process)) {
            proc_terminate($this->process);
            proc_close($this->process);
        }
    }

    /** @return array{status: int, body: array, raw: string} */
    public function json(string $method, string $route, array $body = [], ?string $token = null): array
    {
        $headers = ['Accept: application/json'];
        $options = [CURLOPT_CUSTOMREQUEST => $method];
        if ($body !== []) {
            $headers[] = 'Content-Type: application/json';
            $options[CURLOPT_POSTFIELDS] = json_encode($body, JSON_UNESCAPED_UNICODE);
        }
        return $this->send($route, $headers, $options, $token);
    }

    /** یک فایل واقعی، به صورت multipart — همان کاری که گوشی می‌کند. */
    public function upload(string $route, string $field, string $path, ?string $token): array
    {
        return $this->send(
            $route,
            ['Accept: application/json'],
            [
                CURLOPT_POST => true,
                CURLOPT_POSTFIELDS => [$field => new CURLFile($path)],
            ],
            $token
        );
    }

    /** بایت‌های خام، برای مسیر دانلود که JSON برنمی‌گرداند. */
    public function raw(string $route, ?string $token): array
    {
        $result = $this->send($route, [], [], $token);
        return ['status' => $result['status'], 'bytes' => $result['raw']];
    }

    private function send(string $route, array $headers, array $options, ?string $token): array
    {
        if ($token !== null) {
            $headers[] = "Authorization: Bearer $token";
        }
        $handle = curl_init("http://127.0.0.1:$this->port/index.php?route=$route");
        curl_setopt_array($handle, $options + [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_TIMEOUT => 20,
        ]);
        $raw = (string) curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_HTTP_CODE);
        curl_close($handle);

        $decoded = json_decode($raw, true);
        return [
            'status' => $status,
            'body' => is_array($decoded) ? $decoded : [],
            'raw' => $raw,
        ];
    }

    /** فرم HTML، برای setup.php که JSON نیست. */
    public function form(string $path, array $fields): int
    {
        $handle = curl_init("http://127.0.0.1:$this->port/$path");
        curl_setopt_array($handle, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query($fields),
            CURLOPT_TIMEOUT => 20,
        ]);
        curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_HTTP_CODE);
        curl_close($handle);
        return $status;
    }
}
