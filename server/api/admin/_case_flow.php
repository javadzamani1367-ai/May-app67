<?php
declare(strict_types=1);

/*
 * گردش پرونده در صفحه case.php: چه کسی دارد، تا کی، خط زمانی، و فرم‌های سپردن
 * به مجری (فقط مدیر)، ارجاع به کارشناس و پیگیری. قاعده‌ها در CaseFlow، مشترک با API.
 */

/** @return array{flow:?string} */
function case_flow_handle(string $id): array
{
    $error = ['flow' => null];
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        return $error;
    }
    $action = (string) ($_POST['action'] ?? '');
    if (!in_array($action, ['to_executor', 'to_expert', 'followup'], true)) {
        return $error;
    }
    $scope = admin_scope();
    $note = (string) ($_POST['note'] ?? '');
    $days = trim(Jalali::latin((string) ($_POST['due_days'] ?? '')));
    $error['flow'] = match ($action) {
        'to_executor' => CaseFlow::toExecutor($id, (string) ($_POST['executor_id'] ?? '') ?: null, $note, $scope),
        'to_expert' => CaseFlow::toExpert($id, (string) ($_POST['expert_id'] ?? ''), $days === '' ? null : (int) $days, $note, $scope),
        default => CaseFlow::followUp($id, $note, $scope),
    };
    if ($error['flow'] === null) {
        header('Location: case.php?id=' . urlencode($id) . '&done=flow#flow');
        exit;
    }
    return $error;
}

function case_flow_card(string $id, array $r, ?string $error): string
{
    $scope = admin_scope();
    $flow = CaseFlow::summary($id);
    $date = static fn (?int $t) => $t === null ? '—' : e(Jalali::format($t));
    $html = '<div class="card" id="flow"><h3>گردش پرونده — ' . e($flow['origin_name']) . '</h3>';
    if (($_GET['done'] ?? '') === 'flow') {
        $html .= '<p class="notice">ثبت شد و به گیرنده اعلان رفت.</p>';
    }
    if ($error !== null) {
        $html .= '<p class="error">' . e($error) . '</p>';
    }
    $html .= '<table>'
        . ($flow['intake_code'] ? '<tr><th>شماره ثبت</th><td>' . e(ltr((string) $flow['intake_code'])) . ' — ' . e((string) $flow['intake_by'])
            . ($flow['intake_note'] ? '<br>' . nl2br(e((string) $flow['intake_note'])) : '') . '</td></tr>' : '')
        . ($flow['refer_due_at'] !== null ? '<tr><th>مهلت ارجاع مدیر</th><td>' . $date($flow['refer_due_at'])
            . ' <span class="muted">— پس از آن خودکار به مجری ناحیه می‌رسد</span></td></tr>' : '')
        . '<tr><th>مجری ناحیه</th><td>' . e($flow['executor'] ?? '—') . ($flow['auto_referred'] ? ' <span class="tag tag-seen">ارجاع خودکار</span>' : '') . '</td></tr>'
        . '<tr><th>کارشناس</th><td>' . e($flow['expert'] ?? ($r['expert_code'] ? (string) $r['expert_code'] : '—'))
        . ($flow['due_at'] !== null ? ' — مهلت بازدید ' . $date($flow['due_at']) : '')
        . ($flow['expert_late'] ? ' <span class="tag tag-overdue">مهلت گذشته</span>' : '') . '</td></tr>'
        . '<tr><th>بازدید / تأیید / ارسال به واحد</th><td>' . $date($flow['visited_at']) . ' / ' . $date($flow['approved_at'])
        . ' / ' . $date($flow['dispatched_at']) . '</td></tr></table>';

    $open = (int) $r['status'] === 0;
    $form = static fn (string $action, string $fields, string $button, string $color = '') => '<form method="post" style="margin-top:10px">'
        . csrf_field() . '<input type="hidden" name="action" value="' . $action . '">' . $fields
        . '<button type="submit"' . ($color ? ' style="background:' . $color . '"' : '') . '>' . $button . '</button></form>';
    $html .= '<div class="section-grid">';
    if ($scope->isManager() && $open) {
        $options = '<option value="">مجری ناحیه پرونده</option>';
        foreach ($scope->executors($r['area_code']) as $x) {
            $options .= '<option value="' . e($x['id']) . '">' . e($x['name']) . '</option>';
        }
        $html .= '<div>' . $form('to_executor', '<label>سپردن به مجری<select name="executor_id">' . $options . '</select></label>'
            . '<label>توضیح<input name="note"></label>', 'سپردن به مجری', '#7c3aed') . '</div>';
    }
    if ($open) {
        $options = '<option value="">—</option>';
        foreach ($scope->experts($r['area_code']) as $x) {
            $options .= '<option value="' . e($x['id']) . '">' . e($x['name']) . '</option>';
        }
        $html .= '<div>' . $form('to_expert', '<label>ارجاع به کارشناس ناحیه<select name="expert_id" required>' . $options . '</select></label>'
            . '<label>مهلت بازدید (روز کاری؛ خالی: تنظیمات)<input name="due_days" inputmode="numeric"></label>'
            . '<label>توضیح برای کارشناس<input name="note"></label>', 'ارجاع به کارشناس', '#0e7490') . '</div>';
    }
    $html .= '<div>' . $form('followup', '<label>یادداشت پیگیری<textarea name="note" rows="2" required></textarea></label>', 'ثبت پیگیری', '#64748b')
        . '</div></div>';

    if ($flow['timeline'] !== []) {
        $html .= '<h4 style="margin:14px 0 4px">خط زمانی</h4><table><tr><th>زمان</th><th>چه شد</th><th>چه کسی</th><th>توضیح</th></tr>';
        foreach ($flow['timeline'] as $t) {
            $html .= '<tr><td class="nw">' . e(Jalali::format($t['at'])) . '</td><td>' . e($t['what']) . '</td><td>' . e($t['who'] ?? 'سامانه')
                . '</td><td>' . nl2br(e(mb_substr((string) $t['note'], 0, 400))) . '</td></tr>';
        }
        $html .= '</table>';
    }
    return $html . '</div>';
}
