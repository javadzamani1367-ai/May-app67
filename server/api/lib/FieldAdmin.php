<?php
declare(strict_types=1);

/**
 * آنچه پنل مدیر با موردهای میدانی می‌کند: نام‌ها، فیلتر فهرست (مشترک میان
 * صفحه، خروجی اکسل و داشبورد)، و چرخه وضعیت.
 *
 * هر تغییر وضعیت یک سطر در field_events است و updated_at را جلو می‌برد، تا
 * گوشی کاربر در همگام‌سازی بعدی وضعیت تازه را بگیرد. «مهلت گذشته» مثل
 * dispatches ذخیره نمی‌شود: از due_at و وضعیت ارجاع‌شده محاسبه می‌شود.
 */
final class FieldAdmin
{
    public const KIND_NAMES = [
        Field::KIND_CRYPTO => 'گزارش رمزارز',
        Field::KIND_ILLEGAL => 'برق غیرمجاز',
        Field::KIND_THERMAL => 'ترموویژن',
        Field::KIND_FEEDER => 'آمپرگیری فیدر',
    ];

    public const STATUS_NAMES = [
        Field::STATUS_REGISTERED => 'ثبت‌شده',
        Field::STATUS_REVIEWING => 'در حال بررسی',
        Field::STATUS_REFERRED => 'ارجاع‌شده',
        Field::STATUS_RESULT => 'نتیجه ثبت شد',
        Field::STATUS_CLOSED => 'بسته‌شده',
        Field::STATUS_REJECTED => 'ردشده',
        Field::STATUS_REVISIT => 'بازدید دوباره',
    ];

    /** رنگ برچسب هر وضعیت، از همان کلاس‌های پرتال. */
    public const STATUS_TAGS = [
        Field::STATUS_REGISTERED => 'tag-sent',
        Field::STATUS_REVIEWING => 'tag-seen',
        Field::STATUS_REFERRED => 'tag-seen',
        Field::STATUS_RESULT => 'tag-answered',
        Field::STATUS_CLOSED => 'tag-answered',
        Field::STATUS_REJECTED => 'tag-overdue',
        Field::STATUS_REVISIT => 'tag-overdue',
    ];

    public const PRIORITY_NAMES = [0 => 'فوری', 1 => 'عادی', 2 => 'کم'];

    /** از هر وضعیت به کجا می‌شود رفت. ارجاع دوباره (به کارشناس دیگر) هم مجاز است. */
    public const NEXT = [
        Field::STATUS_REGISTERED => [Field::STATUS_REVIEWING, Field::STATUS_REFERRED, Field::STATUS_REJECTED, Field::STATUS_CLOSED],
        Field::STATUS_REVIEWING => [Field::STATUS_REFERRED, Field::STATUS_RESULT, Field::STATUS_REVISIT, Field::STATUS_REJECTED, Field::STATUS_CLOSED],
        Field::STATUS_REFERRED => [Field::STATUS_REFERRED, Field::STATUS_RESULT, Field::STATUS_REVISIT, Field::STATUS_CLOSED],
        Field::STATUS_RESULT => [Field::STATUS_CLOSED, Field::STATUS_REVISIT],
        Field::STATUS_REVISIT => [Field::STATUS_REVIEWING, Field::STATUS_REFERRED, Field::STATUS_CLOSED],
        Field::STATUS_CLOSED => [Field::STATUS_REVIEWING],
        Field::STATUS_REJECTED => [Field::STATUS_REVIEWING],
    ];

    /** این وضعیت‌ها بی‌دلیل ثبت نمی‌شوند: کاربر میدانی باید بداند چرا. */
    private const NEED_NOTE = [Field::STATUS_REJECTED, Field::STATUS_REVISIT];

    /**
     * فیلترهای فهرست از پارامترهای صفحه. هر مقدار نامعتبر نادیده گرفته می‌شود.
     *
     * @param array<string, mixed> $get
     * @return array{where:string, params:array<int, mixed>, values:array<string, string>}
     */
    public static function filters(array $get): array
    {
        $where = ['i.merged_into IS NULL'];
        $params = [];
        $values = [];
        $text = static fn (string $k): string => trim((string) ($get[$k] ?? ''));

        if (isset(self::KIND_NAMES[(int) $text('kind')])) {
            $where[] = 'i.kind = ?';
            $params[] = (int) $text('kind');
            $values['kind'] = $text('kind');
        }
        if ($text('status') !== '' && isset(self::STATUS_NAMES[(int) $text('status')])) {
            $where[] = 'i.status = ?';
            $params[] = (int) $text('status');
            $values['status'] = $text('status');
        }
        foreach (['user' => 'i.user_id', 'assigned' => 'i.assigned_to'] as $key => $column) {
            if (Field::isUuid($text($key))) {
                $where[] = "$column = ?";
                $params[] = $text($key);
                $values[$key] = $text($key);
            }
        }
        if ($text('q') !== '') {
            $like = '%' . str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], mb_substr($text('q'), 0, 64)) . '%';
            $where[] = '(i.tracking_code LIKE ? OR i.plate LIKE ? OR i.address LIKE ?)';
            array_push($params, $like, $like, $like);
            $values['q'] = $text('q');
        }
        $from = Jalali::parse($text('from'));
        if ($from !== null) {
            $where[] = 'i.created_at >= ?';
            $params[] = Jalali::startOfDay(...$from);
            $values['from'] = $text('from');
        }
        $to = Jalali::parse($text('to'));
        if ($to !== null) {
            $where[] = 'i.created_at < ?';
            $params[] = Jalali::startOfDay(...$to) + 86_400_000;
            $values['to'] = $text('to');
        }
        if ($text('overdue') === '1') {
            $where[] = self::OVERDUE_SQL;
            $params[] = Db::now();
            $values['overdue'] = '1';
        }
        return ['where' => implode(' AND ', $where), 'params' => $params, 'values' => $values];
    }

    /** فهرست موردها با نام گزارش‌دهنده و کارشناس ارجاع؛ جدیدترین اول. */
    public static function listSql(string $where): string
    {
        return "SELECT i.*, u.full_name AS reporter, u.user_code AS reporter_code, a.full_name AS expert
                FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to
                WHERE $where ORDER BY i.created_at DESC";
    }

    /** ارجاعی که مهلتش گذشته و هنوز نتیجه ندارد. پارامتر: اکنون. */
    public const OVERDUE_SQL = '(i.status = ' . Field::STATUS_REFERRED . ' AND i.due_at IS NOT NULL AND i.due_at < ?)';

    public static function isOverdue(array $item, ?int $now = null): bool
    {
        return (int) $item['status'] === Field::STATUS_REFERRED && $item['due_at'] !== null
            && (int) $item['due_at'] < ($now ?? Db::now());
    }

    /**
     * یک تغییر وضعیت از طرف مدیر. خطا را به فارسی برمی‌گرداند، یا تهی اگر انجام شد.
     *
     * @param array<string, mixed> $item سطر field_items
     */
    public static function transition(array $item, int $to, array $manager, string $note,
                                      ?string $assignee = null, ?int $dueAt = null): ?string
    {
        $from = (int) $item['status'];
        if (!in_array($to, self::NEXT[$from] ?? [], true)) {
            return 'این تغییر وضعیت از وضعیت فعلی ممکن نیست.';
        }
        $note = trim($note);
        if (in_array($to, self::NEED_NOTE, true) && $note === '') {
            return 'برای رد یا بازدید دوباره، دلیل را بنویسید.';
        }
        if (mb_strlen($note) > 2000) {
            return 'توضیح بیش از حد بلند است.';
        }
        $expert = null;
        if ($to === Field::STATUS_REFERRED) {
            $expert = $assignee === null ? null : Db::one(
                'SELECT id, full_name FROM users WHERE id = ? AND role = ? AND active = 1',
                [$assignee, Auth::ROLE_EXPERT]
            );
            if ($expert === null) {
                return 'برای ارجاع، یک کارشناس فعال انتخاب کنید.';
            }
            if ($dueAt !== null && $dueAt <= Db::now()) {
                return 'مهلت باید روزی بعد از امروز باشد.';
            }
        }

        $now = Db::now();
        $pdo = Db::conn();
        $pdo->beginTransaction();
        try {
            if ($expert !== null) {
                Db::run('UPDATE field_items SET status = ?, assigned_to = ?, due_at = ?, updated_at = ? WHERE id = ?',
                    [$to, $expert['id'], $dueAt, $now, $item['id']]);
            } else {
                Db::run('UPDATE field_items SET status = ?, updated_at = ? WHERE id = ?', [$to, $now, $item['id']]);
            }
            $text = $note === '' ? null : $note;
            if ($expert !== null) {
                $text = trim('ارجاع به ' . $expert['full_name']
                    . ($dueAt === null ? '' : '، مهلت ' . Jalali::format($dueAt, false)) . "\n" . $note);
            }
            Field::event((string) $item['id'], (string) $manager['id'], 'status', $from, $to, $text);
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
        if ($expert !== null) {
            Notifications::toUser((string) $expert['id'], 'field_referral', 'ارجاع تازه: ' . (string) $item['tracking_code'],
                self::KIND_NAMES[(int) $item['kind']] . ($item['address'] ? ' — ' . $item['address'] : ''));
        }
        return null;
    }

    /**
     * کارنامه هر کاربر میدانی: چند مورد، چند در ۳۰ روز اخیر، آخرین رسیدن.
     *
     * @return array<int, array<string, mixed>>
     */
    public static function userStats(): array
    {
        return Db::all(
            'SELECT u.id, u.full_name, u.user_code, u.active, COUNT(i.id) AS total,
                    COALESCE(SUM(i.created_at >= ?), 0) AS recent,
                    COALESCE(SUM(i.status = ?), 0) AS rejected,
                    MAX(i.received_at) AS last_at
             FROM users u LEFT JOIN field_items i ON i.user_id = u.id AND i.merged_into IS NULL
             WHERE u.role = ? GROUP BY u.id, u.full_name, u.user_code, u.active
             ORDER BY total DESC, u.full_name',
            [Db::now() - 30 * 86_400_000, Field::STATUS_REJECTED, Auth::ROLE_FIELD]
        );
    }

    /**
     * ارجاع‌های هر کارشناس: باز، مهلت‌گذشته، و نتیجه‌گرفته.
     *
     * @return array<int, array<string, mixed>>
     */
    public static function expertStats(): array
    {
        return Db::all(
            'SELECT u.id, u.full_name, u.user_code,
                    COALESCE(SUM(i.status = ?), 0) AS open_count,
                    COALESCE(SUM(' . self::OVERDUE_SQL . '), 0) AS overdue,
                    COALESCE(SUM(i.status IN (?, ?)), 0) AS done
             FROM users u JOIN field_items i ON i.assigned_to = u.id AND i.merged_into IS NULL
             WHERE u.role = ? GROUP BY u.id, u.full_name, u.user_code ORDER BY overdue DESC, open_count DESC',
            [Field::STATUS_REFERRED, Db::now(), Field::STATUS_RESULT, Field::STATUS_CLOSED, Auth::ROLE_EXPERT]
        );
    }
}
