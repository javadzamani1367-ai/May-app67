<?php
declare(strict_types=1);

/**
 * ارجاع‌های میدانی از نگاه کارشناس: آنچه به او ارجاع شده، به شکلی که اپ
 * کارشناس بی‌اینترنت هم نشانش دهد، و نتیجه‌ای که برمی‌گرداند.
 *
 * دو راه برای نتیجه:
 * - رمزارز: کارشناس پرونده «گزارش همکار» با همان کد RZ- می‌سازد و بازدید
 *   کامل می‌کند. وقتی آن پرونده با وضعیت «بازدید شده» به سرور می‌رسد، ارجاع
 *   خودش «نتیجه ثبت شد» می‌شود (onCasePushed) — با شناسه پرونده یا، اگر
 *   پذیرش هنوز نرسیده، با همان کد رهگیری.
 * - بقیه: فرم سبک نتیجه (یکی از OUTCOMES و توضیح).
 */
final class FieldReferrals
{
    public const OUTCOMES = [
        'confirmed' => 'تخلف تأیید شد',
        'not_found' => 'تخلفی دیده نشد',
        'no_access' => 'دسترسی به محل نشد',
        'other' => 'سایر',
    ];

    /** ارجاع‌های بسته‌شده تا این مدت هنوز در گوشی می‌مانند، برای سابقه. */
    private const RECENT_DAYS = 30;

    /**
     * ارجاع‌های این کارشناس: باز، و آن‌هایی که در ۳۰ روز اخیر نتیجه گرفته‌اند.
     * فهرست کامل است نه افزایشی، تا ارجاعی که به کارشناس دیگری رفته از گوشی پاک شود.
     *
     * @return array<int, array<string, mixed>>
     */
    public static function forExpert(array $user): array
    {
        $rows = Db::all(
            'SELECT i.*, u.full_name AS reporter FROM field_items i JOIN users u ON u.id = i.user_id
             WHERE i.assigned_to = ? AND i.merged_into IS NULL
               AND (i.status = ? OR (i.status IN (?, ?) AND i.updated_at > ?))
             ORDER BY i.due_at IS NULL, i.due_at, i.created_at LIMIT 500',
            [$user['id'], Field::STATUS_REFERRED, Field::STATUS_RESULT, Field::STATUS_CLOSED,
             Db::now() - self::RECENT_DAYS * 86_400_000]
        );
        $showReporter = (int) FieldSettings::all()['show_reporter_to_team'] === 1;
        return array_map(static fn (array $row): array => self::publicItem($row, $showReporter), $rows);
    }

    /** یک ارجاع این کارشناس (برای QR)، یا تهی اگر به او ارجاع نشده. */
    public static function oneForExpert(array $user, string $id): ?array
    {
        $row = Field::isUuid($id) ? Db::one(
            'SELECT i.*, u.full_name AS reporter FROM field_items i JOIN users u ON u.id = i.user_id
             WHERE i.id = ? AND i.assigned_to = ? AND i.merged_into IS NULL',
            [$id, $user['id']]
        ) : null;
        return $row === null ? null
            : self::publicItem($row, (int) FieldSettings::all()['show_reporter_to_team'] === 1);
    }

    /** آنچه گوشی کارشناس از یک ارجاع می‌گیرد؛ چک‌لیست از پیش به فارسی. */
    public static function publicItem(array $row, bool $showReporter): array
    {
        $payload = json_decode((string) $row['payload'], true);
        $photos = Db::all(
            'SELECT id, captured_at FROM field_files WHERE item_id = ? AND complete = 1 AND role IN (?, ?)
             ORDER BY role DESC, captured_at LIMIT 12',
            [$row['id'], Field::ROLE_PHOTO_STAMPED, Field::ROLE_PHOTO]
        );
        $result = json_decode((string) ($row['result'] ?? ''), true);
        return [
            'id' => $row['id'],
            'kind' => (int) $row['kind'],
            'kind_name' => FieldAdmin::KIND_NAMES[(int) $row['kind']] ?? '',
            'tracking_code' => (string) $row['tracking_code'],
            'status' => (int) $row['status'],
            'status_name' => FieldAdmin::STATUS_NAMES[(int) $row['status']] ?? '',
            'priority' => $row['priority'] === null ? null : (int) $row['priority'],
            'created_at' => (int) $row['created_at'],
            'due_at' => $row['due_at'] === null ? null : (int) $row['due_at'],
            'updated_at' => (int) $row['updated_at'],
            'address' => $row['address'],
            'plate' => $row['plate'],
            'latitude' => $row['latitude'] === null ? null : (float) $row['latitude'],
            'longitude' => $row['longitude'] === null ? null : (float) $row['longitude'],
            'accuracy' => $row['accuracy'] === null ? null : (float) $row['accuracy'],
            'description' => $row['description'],
            'reporter' => $showReporter ? (string) $row['reporter'] : null,
            'details' => FieldPresenter::rows((int) $row['kind'], is_array($payload) ? $payload : []),
            'photos' => array_map(static fn ($p) => ['id' => $p['id'], 'captured_at' => $p['captured_at'] === null
                ? null : (int) $p['captured_at']], $photos),
            'report_id' => $row['report_id'],
            'result' => is_array($result) ? $result : null,
        ];
    }

    /** کارشناس ارجاع را برداشت و پرونده‌اش را ساخت. تکرارپذیر. */
    public static function accept(array $user, string $id, string $reportId): ?string
    {
        $item = self::mine($user, $id);
        if ($item === null) {
            return 'این ارجاع به شما نیست.';
        }
        if (!Field::isUuid($reportId)) {
            return 'شناسه پرونده معتبر نیست.';
        }
        if ($item['report_id'] !== null) {
            return $item['report_id'] === $reportId ? null : 'برای این ارجاع قبلاً پرونده دیگری ساخته شده است.';
        }
        Db::run('UPDATE field_items SET report_id = ?, updated_at = ? WHERE id = ?', [$reportId, Db::now(), $id]);
        Field::event($id, (string) $user['id'], 'accepted', null, null, 'کارشناس پرونده بازدید را ساخت');
        return null;
    }

    /** نتیجه فرم سبک. فقط روی ارجاع باز. */
    public static function result(array $user, string $id, string $outcome, string $note): ?string
    {
        $item = self::mine($user, $id);
        if ($item === null) {
            return 'این ارجاع به شما نیست.';
        }
        if (!isset(self::OUTCOMES[$outcome])) {
            return 'نتیجه انتخاب نشده است.';
        }
        $note = mb_substr(trim($note), 0, 2000);
        if ($outcome === 'other' && $note === '') {
            return 'برای «سایر»، توضیح بنویسید.';
        }
        if ((int) $item['status'] !== Field::STATUS_REFERRED) {
            // همان نتیجه دوباره رسید (گوشی پاسخ اول را نگرفته بود): پذیرفته است.
            $previous = json_decode((string) ($item['result'] ?? ''), true);
            return is_array($previous) && ($previous['outcome'] ?? null) === $outcome ? null
                : 'این ارجاع دیگر باز نیست.';
        }
        self::close($item, (string) $user['id'], ['outcome' => $outcome, 'note' => $note, 'at' => Db::now(),
            'by' => (string) $user['full_name']], self::OUTCOMES[$outcome] . ($note === '' ? '' : "\n" . $note));
        return null;
    }

    /**
     * پرونده‌ای که از سرور گذشت: اگر پرونده ارجاعی باز است و بازدیدش ثبت شده،
     * ارجاع نتیجه می‌گیرد. اتصال با شناسه پرونده، یا با کد RZ- اگر پذیرش نرسیده.
     */
    public static function onCasePushed(array $report, array $user): void
    {
        $status = (int) ($report['status'] ?? 0);
        if ($status < 1) {
            return;
        }
        $item = Db::one(
            'SELECT * FROM field_items WHERE status = ? AND assigned_to = ? AND merged_into IS NULL
             AND (report_id = ? OR (report_id IS NULL AND tracking_code = ?)) LIMIT 1',
            [Field::STATUS_REFERRED, $user['id'], (string) $report['id'], (string) ($report['tracking_code'] ?? '')]
        );
        if ($item === null) {
            return;
        }
        if ($item['report_id'] === null) {
            Db::run('UPDATE field_items SET report_id = ? WHERE id = ?', [$report['id'], $item['id']]);
        }
        self::close($item, (string) $user['id'], ['outcome' => 'case', 'report_id' => (string) $report['id'],
            'at' => Db::now(), 'by' => (string) $user['full_name']],
            'بازدید ثبت شد؛ پرونده ' . (string) ($report['tracking_code'] ?? ''));
    }

    private static function close(array $item, string $userId, array $result, string $note): void
    {
        $now = Db::now();
        Db::run('UPDATE field_items SET status = ?, result = ?, updated_at = ? WHERE id = ?',
            [Field::STATUS_RESULT, json_encode($result, JSON_UNESCAPED_UNICODE), $now, $item['id']]);
        Field::event((string) $item['id'], $userId, 'result', Field::STATUS_REFERRED, Field::STATUS_RESULT, $note);
        Notifications::toRole(Auth::ROLE_MANAGER, 'field_result', 'نتیجه ارجاع: ' . (string) $item['tracking_code'],
            strtok($note, "\n") ?: '');
        FieldHandOff::onResult($item, strtok($note, "\n") ?: '');
    }

    private static function mine(array $user, string $id): ?array
    {
        return Field::isUuid($id) ? Db::one(
            'SELECT * FROM field_items WHERE id = ? AND assigned_to = ? AND merged_into IS NULL',
            [$id, $user['id']]
        ) : null;
    }
}
