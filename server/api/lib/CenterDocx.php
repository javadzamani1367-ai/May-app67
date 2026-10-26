<?php
declare(strict_types=1);

/**
 * Word آمار جامع: برگه A4 افقی با قلم‌های فرم (OfficialDocx)، فقط ستون‌های تیک‌خورده (همه
 * ستون‌های فرم در یک برگه Word خوانا جا نمی‌شوند؛ اکسل همه را دارد)، و سطر جمع.
 */
final class CenterDocx
{
    public static function send(string $name, array $rows, array $keys, string $title, string $subtitle): void
    {
        OfficialDocx::send($name, self::model($rows, $keys, $title, $subtitle));
    }

    public static function write(string $path, array $rows, array $keys, string $title, string $subtitle): void
    {
        OfficialDocx::write($path, self::model($rows, $keys, $title, $subtitle));
    }

    private static function model(array $rows, array $keys, string $title, string $subtitle): array
    {
        $header = array_map([CenterColumns::class, 'label'], $keys);
        $longest = array_map(static fn ($h) => min(14, mb_strlen($h) / 2), $header);
        $lines = [];
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
        return ['title' => $title, 'subtitle' => $subtitle,
            'tables' => [['header' => $header, 'rows' => $lines, 'total' => $sum, 'widths' => array_map(static fn ($n) => max(3, $n), $longest)]],
            'cell_size' => count($keys) > 16 ? 15 : (count($keys) > 10 ? 18 : 22)];
    }
}
