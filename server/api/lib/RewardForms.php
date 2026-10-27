<?php
declare(strict_types=1);

/**
 * فرم پاداش هر مرکز: ذخیره افراد، ثبت نهایی با شماره و نسخه ثابت محاسبه، و باز کردن. فرم ثبت‌شده
 * دیگر با تغییر دستورالعمل یا مقدار آمار عوض نمی‌شود؛ گزارش‌های تجمیعی هم همان را می‌خوانند.
 * فقط مدیر.
 *
 * شماره فرم: سال آمار، حرف نوع پاداش و شماره ترتیبی همان سال و نوع (۱۴۰۴/ش/۱۲).
 */
final class RewardForms
{
    public const LETTERS = [RewardRules::FINDER => 'ش', RewardRules::COLLECTION => 'ج', RewardRules::ENFORCEMENT => 'ن',
        RewardRules::STAFF => 'د'];
    private const PREFIX = [RewardRules::FINDER => 'PF', RewardRules::COLLECTION => 'PC', RewardRules::ENFORCEMENT => 'PE',
        RewardRules::STAFF => 'PS'];

    /** @return array<string, array<int, array<string, mixed>>> */
    public static function forRows(array $rowIds): array
    {
        $rowIds = array_values(array_unique(array_filter($rowIds)));
        if ($rowIds === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($rowIds), '?'));
        $out = [];
        foreach (Db::all("SELECT * FROM reward_forms WHERE row_id IN ($in)", $rowIds) as $f) {
            $out[$f['row_id']][(int) $f['kind']] = [
                'people' => $f['people_json'] === null ? null : CenterValues::decode($f['people_json']),
                'number' => $f['number'], 'final_at' => $f['final_at'] === null ? null : (int) $f['final_at'],
                'snapshot' => $f['snapshot_json'] === null ? null : CenterValues::decode($f['snapshot_json']),
            ];
        }
        return $out;
    }

    /** محاسبه یک پاداش برای سطر: نسخه ثابت فرم ثبت‌شده، وگرنه از افراد فعلی. */
    public static function calc(int $kind, array $values, ?int $date, array $state): ?array
    {
        $form = $state['form'] ?? null;
        if ($form !== null && $form['final_at'] !== null && is_array($form['snapshot'])) {
            return $form['snapshot'];
        }
        return CenterRewards::of($kind, $values, $date, $state['people'] ?? []);
    }

    /** ذخیره افراد؛ خطا متن فارسی. */
    public static function save(array $row, int $kind, array $people, array $user): ?string
    {
        if ($kind === RewardRules::FINDER) {
            return 'عامل شناسایی از ستون‌های آمار همین مرکز است؛ آن‌جا ویرایش کنید.';
        }
        $state = $row['rewards'][$kind] ?? null;
        if (($state['final_at'] ?? null) !== null) {
            return 'این فرم ثبت نهایی شده؛ برای تغییر نخست بازش کنید.';
        }
        if ($row['locked']) {
            return 'سال این مرکز قفل است.';
        }
        $clean = RewardPeople::clean($kind, $people);
        self::upsert($row['id'], $kind, ['people_json' => json_encode($clean, JSON_UNESCAPED_UNICODE)], $user);
        CenterEdit::log($row['id'], 'reward:' . RewardRules::KEYS[$kind], null, self::summary($clean), 'reward', $user);
        return null;
    }

    /** ثبت نهایی: بررسی، شماره (یک بار) و نسخه ثابت محاسبه. */
    public static function finalize(array $row, int $kind, array $user): ?string
    {
        $state = $row['rewards'][$kind] ?? null;
        if ($state === null || ($state['final_at'] ?? null) !== null) {
            return $state === null ? 'فرم پیدا نشد.' : 'این فرم پیش‌تر ثبت نهایی شده است.';
        }
        $calc = $state['calc'];
        if ($calc === null) {
            return 'برای تاریخ این مرکز دستورالعملی از این پاداش ثبت نشده است.';
        }
        $problem = self::problem($kind, $row, $calc);
        if ($problem !== null) {
            return $problem;
        }
        $number = $state['number'] ?? self::nextNumber($kind, (int) $row['year']);
        $fields = ['number' => $number, 'final_at' => Db::now(), 'final_by' => $user['id'] ?? null,
            'snapshot_json' => json_encode($calc, JSON_UNESCAPED_UNICODE)];
        if (!$state['saved'] && $kind !== RewardRules::FINDER) {
            $fields['people_json'] = json_encode($state['people'], JSON_UNESCAPED_UNICODE);
        }
        self::upsert($row['id'], $kind, $fields, $user);
        CenterEdit::log($row['id'], 'reward:' . RewardRules::KEYS[$kind], null, $number, 'final', $user);
        return null;
    }

    /** باز کردن فرم ثبت‌شده؛ شماره‌اش می‌ماند. */
    public static function unlock(array $row, int $kind, array $user): ?string
    {
        if (($row['rewards'][$kind]['final_at'] ?? null) === null) {
            return 'این فرم ثبت نهایی نشده است.';
        }
        Db::run('UPDATE reward_forms SET final_at = NULL, final_by = NULL, snapshot_json = NULL, updated_by = ?, updated_at = ?
                 WHERE row_id = ? AND kind = ?', [$user['id'] ?? null, Db::now(), $row['id'], $kind]);
        CenterEdit::log($row['id'], 'reward:' . RewardRules::KEYS[$kind], $row['rewards'][$kind]['number'], null, 'unlock', $user);
        return null;
    }

    /** چرا فرم هنوز ثبت نهایی نمی‌شود. */
    private static function problem(int $kind, array $row, array $calc): ?string
    {
        if ($kind === RewardRules::FINDER) {
            return trim((string) ($row['values']['finder_name'] ?? '')) === '' ? 'نام عامل شناسایی در آمار این مرکز نوشته نشده است.' : null;
        }
        if ($calc['people'] === []) {
            return 'هیچ فردی تیک نخورده است.';
        }
        foreach ($calc['people'] as $p) {
            if (trim((string) $p['name']) === '') {
                return 'نام ' . ($p['post'] !== '' ? 'دارنده «' . $p['post'] . '»' : 'یک فرد تیک‌خورده') . ' را برگزینید یا تیکش را بردارید.';
            }
        }
        if ($kind === RewardRules::STAFF && $calc['staff'] > 0 && abs($calc['pct_sum'] - 100) > 0.01) {
            return 'درصد کارکنان باید جمعاً ۱۰۰ باشد؛ اکنون ' . Jalali::digits((string) $calc['pct_sum']) . ' است.';
        }
        return null;
    }

    private static function nextNumber(int $kind, int $year): string
    {
        Db::run('INSERT INTO code_counters (prefix, year, last) VALUES (?, ?, LAST_INSERT_ID(1))
                 ON DUPLICATE KEY UPDATE last = LAST_INSERT_ID(last + 1)', [self::PREFIX[$kind], $year]);
        return $year . '/' . self::LETTERS[$kind] . '/' . (int) Db::conn()->lastInsertId();
    }

    private static function upsert(string $rowId, int $kind, array $fields, array $user): void
    {
        $fields += ['updated_by' => $user['id'] ?? null, 'updated_at' => Db::now()];
        $sets = implode(', ', array_map(static fn ($k) => Db::col($k) . ' = VALUES(' . Db::col($k) . ')', array_keys($fields)));
        Db::run(
            'INSERT INTO reward_forms (id, row_id, kind, ' . Db::cols(array_keys($fields)) . ') VALUES (?, ?, ?'
            . str_repeat(', ?', count($fields)) . ") ON DUPLICATE KEY UPDATE $sets",
            [Db::uuid(), $rowId, $kind, ...array_values($fields)]
        );
    }

    /** «۳ نفر: الف، ب، ج» برای تاریخچه. */
    private static function summary(array $people): string
    {
        $on = array_values(array_filter($people, static fn ($p) => $p['on']));
        return Jalali::digits(count($on) . ' نفر: ' . implode('، ', array_map(static fn ($p) => $p['name'] ?: $p['post'], $on)));
    }
}
