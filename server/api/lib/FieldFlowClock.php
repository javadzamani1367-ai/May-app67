<?php
declare(strict_types=1);

/**
 * همان هشدارهای FlowClock برای موردهای اپ میدانی که ارجاع شده‌اند.
 *
 * کارشناسی که تا مهلت ارجاع نتیجه نداده، به کسی که مورد را می‌گرداند هشدار
 * می‌دهد: مجری رمزارزی که مورد به او سپرده شده، یا برای برق غیرمجاز مجری
 * غیرمجاز، وگرنه مدیر. مجری‌ای که پس از آن تا مهلتش اقدامی نکرده، به مدیر.
 */
final class FieldFlowClock
{
    private const OPEN = [Field::STATUS_REGISTERED, Field::STATUS_REVIEWING, Field::STATUS_REVISIT, Field::STATUS_REFERRED];

    public static function tick(int $now, CrewCalendar $calendar, array $settings): void
    {
        $rows = Db::all(
            'SELECT i.id, i.kind, i.area_code, i.tracking_code, i.handler_id, p.full_name AS expert_name
             FROM field_items i LEFT JOIN users p ON p.id = i.assigned_to
             WHERE i.status = ? AND i.merged_into IS NULL AND i.due_at IS NOT NULL AND i.due_at < ?
               AND i.expert_alert_at IS NULL LIMIT 200',
            [Field::STATUS_REFERRED, $now]
        );
        foreach ($rows as $row) {
            $text = $row['tracking_code'] . ' — کارشناس ' . $row['expert_name'] . ' تا مهلت ارجاع نتیجه نداده است.';
            Db::run('UPDATE field_items SET expert_alert_at = ? WHERE id = ?', [$now, $row['id']]);
            Field::event((string) $row['id'], null, 'expert_alert', null, null, $text);
            self::tell($row, 'expert_late', 'کارشناس به ارجاع رسیدگی نکرده', $text, false);
        }

        $days = (int) $settings['executor_days'];
        $placeholders = implode(', ', array_fill(0, count(self::OPEN), '?'));
        $rows = Db::all(
            "SELECT i.*, x.full_name AS executor_name, p.full_name AS expert_name
             FROM field_items i JOIN users x ON x.id = i.handler_id LEFT JOIN users p ON p.id = i.assigned_to
             WHERE i.merged_into IS NULL AND i.manager_alert_at IS NULL AND i.status IN ($placeholders)
               AND ((i.expert_alert_at IS NOT NULL AND (i.handler_action_at IS NULL OR i.handler_action_at < i.expert_alert_at))
                 OR (i.assigned_to IS NULL AND (i.handler_action_at IS NULL OR i.handler_action_at < i.handled_at)))
             LIMIT 300",
            self::OPEN
        );
        foreach ($rows as $row) {
            $start = $row['expert_alert_at'] !== null ? (int) $row['expert_alert_at'] : (int) $row['handled_at'];
            if ($calendar->addWorkDays($start, $days) >= $now) {
                continue;
            }
            $text = $row['tracking_code'] . ' — مجری ' . $row['executor_name'] . ' ' . Jalali::digits((string) $days)
                . ' روز کاری اقدامی نکرده'
                . ($row['expert_alert_at'] !== null ? ' و کارشناس ' . $row['expert_name'] . ' هم نتیجه نداده است.' : ' و مورد را به کارشناسی نسپرده است.');
            Db::run('UPDATE field_items SET manager_alert_at = ? WHERE id = ?', [$now, $row['id']]);
            Field::event((string) $row['id'], null, 'manager_alert', null, null, $text);
            self::tell($row, 'executor_late', 'مورد میدانی بی‌اقدام مانده', $text, true);
        }
    }

    /** به گرداننده مورد؛ برای سطح دوم همیشه مدیر هم. */
    private static function tell(array $row, string $kind, string $title, string $text, bool $manager): void
    {
        if (!$manager && $row['handler_id'] !== null) {
            Notifications::toUser((string) $row['handler_id'], $kind, $title, $text);
            return;
        }
        if ((int) $row['kind'] === Field::KIND_ILLEGAL) {
            Notifications::toRoleIn(Auth::ROLE_ILLEGAL, $row['area_code'] ?? null, $kind, $title, $text);
        }
        if ($manager || (int) $row['kind'] !== Field::KIND_ILLEGAL) {
            Notifications::toRole(Auth::ROLE_MANAGER, $kind, $title, $text);
        }
    }
}
