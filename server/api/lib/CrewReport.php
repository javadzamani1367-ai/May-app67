<?php
declare(strict_types=1);

/**
 * گزارش عملکرد اکیپ از پایگاه داده: موردهای اکیپ‌دار بازه (جز تکراری‌های ادغام‌شده
 * و ردشده‌ها)، فریم‌های ترموویژن، و اضافه‌کاری که مدیر برای هر نفر تأیید کرده.
 * محاسبه خودش در CrewWork و CrewDays است و پایگاه داده نمی‌خواهد.
 */
final class CrewReport
{
    /** موردی که پیش‌نویسش زودتر ساخته شده، کارش ممکن است در بازه افتاده باشد. */
    private const LOOKBACK_MILLIS = 3 * 86_400_000;

    /**
     * @param list<string>|null $areas مجری: فقط کار اکیپ در ناحیه‌های خودش؛ تهی یعنی همه
     * @return array{members: list<array>, from:int, to:int, geo:bool}
     */
    public static function build(int $from, int $to, ?array $areas = null): array
    {
        $geo = GeoPlaces::load(null, Storage::root() . '/cache');
        $members = CrewDays::build(self::items($from, $to, $areas), CrewCalendar::fromSettings(), $geo, $from, $to);
        return ['members' => $members, 'from' => $from, 'to' => $to, 'geo' => $geo !== null];
    }

    /** @return list<array> */
    public static function items(int $from, int $to, ?array $areas = null): array
    {
        $kinds = implode(',', array_map('intval', Field::CREW_KINDS));
        $area = $areas === null ? '' : ' AND area_code IN (' . ($areas === [] ? "''" : implode(',', array_fill(0, count($areas), '?'))) . ')';
        $rows = Db::all(
            "SELECT id, kind, tracking_code, plate, latitude, longitude, position_at, created_at, payload
             FROM field_items WHERE kind IN ($kinds) AND merged_into IS NULL AND status <> ?
               AND created_at BETWEEN ? AND ?$area",
            array_merge([Field::STATUS_REJECTED, $from - self::LOOKBACK_MILLIS, $to], $areas ?? [])
        );
        $thermal = array_column(array_filter($rows, static fn (array $r): bool => (int) $r['kind'] === Field::KIND_THERMAL), 'id');
        $frames = [];
        if ($thermal !== []) {
            $marks = implode(',', array_fill(0, count($thermal), '?'));
            foreach (Db::all("SELECT item_id, captured_at, latitude, longitude, asset_type, plate FROM field_files
                              WHERE role = ? AND item_id IN ($marks)", [Field::ROLE_THERMAL, ...$thermal]) as $f) {
                $frames[$f['item_id']][] = $f;
            }
        }
        return array_map(static fn (array $r): array => [
            'payload' => is_array($p = json_decode((string) $r['payload'], true)) ? $p : [],
            'frames' => $frames[$r['id']] ?? [],
        ] + $r, $rows);
    }

    /** اضافه‌کار تأییدشده هر نفر برای یک بازه: member_key => [hours, note]. */
    public static function approved(string $fromDay, string $toDay): array
    {
        $out = [];
        foreach (Db::all('SELECT member_key, hours, note FROM crew_overtime WHERE from_day = ? AND to_day = ?', [$fromDay, $toDay]) as $r) {
            $out[$r['member_key']] = ['hours' => (float) $r['hours'], 'note' => (string) ($r['note'] ?? '')];
        }
        return $out;
    }

    /** ساعت اضافه‌کار که مدیر برای یک نفر در یک بازه می‌نویسد؛ خطا به فارسی، یا تهی. */
    public static function saveApproved(string $memberKey, string $fromDay, string $toDay, string $hours, string $note, ?string $userId): ?string
    {
        $text = trim(Jalali::latin(str_replace(['٫', '/'], '.', $hours)));
        if ($text === '') {
            Db::run('DELETE FROM crew_overtime WHERE member_key = ? AND from_day = ? AND to_day = ?', [$memberKey, $fromDay, $toDay]);
            return null;
        }
        if (!is_numeric($text) || (float) $text < 0 || (float) $text > 9999) {
            return 'ساعت اضافه‌کار را با عدد بنویسید.';
        }
        Db::run(
            'INSERT INTO crew_overtime (member_key, from_day, to_day, hours, note, user_id, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE hours = VALUES(hours), note = VALUES(note), user_id = VALUES(user_id), updated_at = VALUES(updated_at)',
            [mb_substr($memberKey, 0, 200), $fromDay, $toDay, round((float) $text, 1), mb_substr(trim($note), 0, 500), $userId, Db::now()]
        );
        return null;
    }
}
