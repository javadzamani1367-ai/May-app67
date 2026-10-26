<?php
declare(strict_types=1);

/**
 * Word آمار جامع: برگه A4 افقی با قلم ریز، فقط ستون‌هایی که مدیر برای Word تیک زده
 * (همه ستون‌های فرم در یک برگه Word جا نمی‌شوند؛ اکسل همه را دارد)، و سطر جمع.
 */
final class CenterDocx
{
    public static function send(string $name, array $rows, array $keys, string $title, string $subtitle): void
    {
        $path = tempnam(sys_get_temp_dir(), 'cdx');
        self::write($path, $rows, $keys, $title, $subtitle);
        header('Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document');
        header('Content-Disposition: attachment; filename="' . $name . '.docx"');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        @unlink($path);
    }

    public static function write(string $path, array $rows, array $keys, string $title, string $subtitle): void
    {
        $header = array_map([CenterColumns::class, 'label'], $keys);
        $lines = [];
        $longest = array_map(static fn ($h) => min(14, mb_strlen($h) / 2), $header);
        foreach ($rows as $row) {
            $line = [];
            foreach ($keys as $i => $key) {
                $text = CenterValues::display(CenterColumns::get($key)['type'], $row['values'][$key] ?? null);
                $line[] = $text;
                $longest[$i] = max($longest[$i], min(30, mb_strlen($text)));
            }
            $lines[] = $line;
        }
        $totals = CenterRows::totals($rows);
        $sum = [];
        foreach ($keys as $i => $key) {
            $type = CenterColumns::get($key)['type'];
            $sum[] = isset($totals[$key]) ? CenterValues::display($type === 'tick' || $type === 'text' ? 'int' : $type, $totals[$key])
                : ($i === 0 ? 'جمع کل' : '');
        }
        $lines[] = $sum;
        Docx::write($path, [
            ['h', $title],
            ['p', $subtitle],
            ['grid', [$header, $lines, array_map(static fn ($n) => max(3, $n), $longest)]],
        ], ['landscape' => true, 'size' => count($keys) > 16 ? 13 : 15]);
    }
}
