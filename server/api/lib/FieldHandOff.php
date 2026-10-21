<?php
declare(strict_types=1);

/**
 * سپردن یک مورد میدانی به مجری رمزارز یک ناحیه — کار مدیر. گزارش برق غیرمجاز اینجا
 * نیست: همان لحظه رسیدن، پرونده غیرمجاز می‌شود (IllegalFlow).
 *
 * مجری مورد را مثل پرونده خودش می‌گرداند: به کارشناس ناحیه ارجاع می‌دهد و نتیجه را
 * دنبال می‌کند. سپارنده هر قدم را در تاریخچه همان مورد می‌بیند. ناحیه مورد همان
 * ناحیه مجری می‌شود، تا مجری غیرمجاز بتواند گزارشی را به مجری ناحیه دیگری بدهد.
 */
final class FieldHandOff
{
    public static function hand(array $item, string $executorId, string $note, Scope $scope): ?string
    {
        if (!$scope->isManager()) {
            return 'سپردن مورد میدانی به مجری رمزارز کار مدیر است.';
        }
        if (!$scope->canSeeField($item)) {
            return 'این مورد در محدوده شما نیست.';
        }
        if ((int) $item['kind'] === Field::KIND_ILLEGAL) {
            return 'گزارش برق غیرمجاز در گردش پرونده غیرمجاز است و مجریان غیرمجاز آن را می‌گردانند.';
        }
        if ($item['merged_into'] !== null || in_array((int) $item['status'], [Field::STATUS_CLOSED, Field::STATUS_REJECTED], true)) {
            return 'این مورد بسته یا ادغام شده است.';
        }
        $executor = Db::one('SELECT id, full_name, areas FROM users WHERE id = ? AND role = ? AND active = 1',
            [$executorId, Auth::ROLE_EXECUTOR]);
        if ($executor === null) {
            return 'یک مجری رمزارز فعال انتخاب کنید.';
        }
        $areas = Areas::parse($executor['areas'] ?? '');
        $area = in_array((string) ($item['area_code'] ?? ''), $areas, true) ? (string) $item['area_code'] : ($areas[0] ?? null);
        if ($area === null) {
            return 'این مجری ناحیه‌ای ندارد.';
        }
        $note = mb_substr(trim($note), 0, 2000);
        $now = Db::now();
        $status = (int) $item['status'];
        $to = in_array($status, [Field::STATUS_REGISTERED, Field::STATUS_REVISIT], true) ? Field::STATUS_REVIEWING : $status;
        Db::run(
            'UPDATE field_items SET handler_id = ?, handled_at = ?, handler_action_at = NULL, expert_alert_at = NULL,
                    manager_alert_at = NULL, area_code = ?, status = ?, updated_at = ? WHERE id = ?',
            [$executor['id'], $now, $area, $to, $now, $item['id']]
        );
        Field::event((string) $item['id'], (string) $scope->user['id'], 'handed', $status, $to,
            trim('سپرده به مجری ' . $executor['full_name'] . ' (' . Areas::label($area) . ")\n" . $note));
        Notifications::toUser((string) $executor['id'], 'field_handed', 'مورد میدانی به شما سپرده شد: ' . $item['tracking_code'],
            (FieldAdmin::KIND_NAMES[(int) $item['kind']] ?? '') . ($note === '' ? '' : ' — ' . $note));
        return null;
    }

    /** یادداشت پیگیری روی یک مورد؛ برای مجری «اقدام» است. */
    public static function followUp(array $item, string $note, Scope $scope): ?string
    {
        if (!$scope->canActOnField($item)) {
            return 'این مورد در محدوده شما نیست.';
        }
        $note = trim($note);
        if ($note === '' || mb_strlen($note) > 2000) {
            return 'متن پیگیری را بنویسید (حداکثر ۲۰۰۰ نویسه).';
        }
        if ($scope->isExecutor()) {
            Db::run('UPDATE field_items SET handler_action_at = ? WHERE id = ?', [Db::now(), $item['id']]);
        }
        Field::event((string) $item['id'], (string) $scope->user['id'], 'followup', null, null, $note);
        return null;
    }

    /** نتیجه ارجاع رسید: گرداننده (مجری) و برای غیرمجاز، مجری غیرمجاز هم باخبر می‌شوند. */
    public static function onResult(array $item, string $text): void
    {
        if (($item['handler_id'] ?? null) !== null) {
            Notifications::toUser((string) $item['handler_id'], 'field_result', 'نتیجه ارجاع: ' . $item['tracking_code'], $text);
        }
        if ((int) $item['kind'] === Field::KIND_ILLEGAL) {
            Notifications::toRoleIn(Auth::ROLE_ILLEGAL, $item['area_code'] ?? null, 'field_result',
                'نتیجه ارجاع: ' . $item['tracking_code'], $text);
        }
    }
}
