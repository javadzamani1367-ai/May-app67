<?php
declare(strict_types=1);

/**
 * امتیازها در اکسل (چهار برگه) و توضیح فرمول هر جدول برای صفحه و اپ. عددها عدد
 * می‌مانند تا در اکسل مرتب و جمع شوند.
 */
final class ScoreSheets
{
    public const TABLES = [
        'areas' => ['ناحیه‌ها', Scores::AREA],
        'executors' => ['مجریان رمزارز', Scores::EXECUTOR],
        'units' => ['واحدهای مقصد', Scores::UNIT],
        'experts' => ['کارشناسان', Scores::EXPERT],
    ];

    /** برای هر جدول: عنوان و اجزا با وزن و توضیح. */
    public static function spec(): array
    {
        $out = [];
        foreach (self::TABLES as $key => [$title, $parts]) {
            $out[$key] = ['title' => $title, 'parts' => array_map(static fn ($p) => ['key' => $p[0], 'weight' => $p[1],
                'label' => $p[2]], $parts)];
        }
        return $out;
    }

    /** @return list<array{0:string, 1:list<string>, 2:list<list<string|int|float|null>>}> */
    public static function book(array $board): array
    {
        $sheets = [];
        foreach (self::TABLES as $key => [$title, $parts]) {
            $header = array_merge(['رتبه', 'نام', 'ناحیه', 'امتیاز از ۱۰۰'],
                array_map(static fn ($p) => $p[2] . ' (وزن ' . $p[1] . ')', $parts));
            $rows = [];
            foreach ($board[$key] ?? [] as $row) {
                $rows[] = array_merge([$row['rank'], $row['name'] . (isset($row['unit']) ? ' — ' . $row['unit'] : ''),
                    $row['area'] ?? $row['name'], $row['score']],
                    array_map(static fn ($p) => $row['parts'][$p[0]] ?? null, $parts));
            }
            $sheets[] = [$title, $header, $rows];
        }
        return $sheets;
    }
}
