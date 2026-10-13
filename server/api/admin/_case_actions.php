<?php
declare(strict_types=1);

/*
 * کارهای مدیر در صفحه یک پرونده: تأیید یا برگشت، و ارسال به واحد. فقط از
 * case.php خوانده می‌شود؛ قاعده‌ها در CaseActions است، مشترک با API.
 */

/**
 * فرم‌های POST صفحه پرونده. موفق → بازگشت به همان صفحه با پیام؛ خطا → متن خطا.
 *
 * @return array{approval:?string, dispatch:?string}
 */
function case_actions_handle(string $id, array $manager): array
{
    $errors = ['approval' => null, 'dispatch' => null];
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        return $errors;
    }
    csrf_check();
    $action = (string) ($_POST['action'] ?? '');
    if ($action === 'decide') {
        $errors['approval'] = CaseActions::decide($id, (int) ($_POST['decision'] ?? -1), (string) ($_POST['comment'] ?? ''), $manager);
        $done = $errors['approval'] === null ? 'decided' : null;
    } elseif ($action === 'dispatch') {
        $days = trim((string) ($_POST['deadline_days'] ?? ''));
        $errors['dispatch'] = CaseActions::dispatch($id, (int) ($_POST['unit'] ?? -1), (array) ($_POST['items'] ?? []),
            (string) ($_POST['note'] ?? ''), $days === '' ? null : (int) Jalali::latin($days), (int) ($_POST['format'] ?? 0), $manager);
        $done = $errors['dispatch'] === null ? 'dispatched' : null;
    } else {
        $done = null;
    }
    if ($done !== null) {
        header('Location: case.php?id=' . urlencode($id) . '&done=' . $done . '#' . ($done === 'decided' ? 'approval' : 'dispatch'));
        exit;
    }
    return $errors;
}

/** تأیید مدیر: وضعیت، رفت‌وبرگشت‌ها، و اگر درخواستی در انتظار است، فرم تصمیم. */
function case_approval_card(array $r, array $history, ?string $error): string
{
    $pending = $history !== [] && (int) $history[0]['decision'] === CaseActions::PENDING;
    $html = '<div class="card" id="approval"><h3>تأیید مدیر — ' . e((string) CaseLabels::of(CaseLabels::APPROVALS, $r['approval_state'])) . '</h3>';
    if (($_GET['done'] ?? '') === 'decided') {
        $html .= '<p class="notice">تصمیم ثبت شد و به کارشناس اعلان رفت.</p>';
    }
    if ($error !== null) {
        $html .= '<p class="error">' . e($error) . '</p>';
    }
    if ($pending) {
        $html .= '<form method="post">' . csrf_field() . '<input type="hidden" name="action" value="decide">'
            . '<label>توضیح برای کارشناس (برای برگشت، اجباری)<textarea name="comment" rows="3">'
            . e((string) ($_POST['comment'] ?? '')) . '</textarea></label><div class="actions-bar">'
            . '<button type="submit" name="decision" value="' . CaseActions::APPROVED . '" style="background:#15803d">تأیید پرونده</button>'
            . '<button type="submit" name="decision" value="' . CaseActions::RETURNED . '" style="background:#b45309">برگشت برای اصلاح</button>'
            . '</div></form>';
    } else {
        $html .= '<p class="muted">درخواست تأییدی در انتظار نیست. کارشناس پرونده را از اپ برای تأیید می‌فرستد.</p>';
    }
    if ($history !== []) {
        $html .= '<table><tr><th>فرستاده</th><th>کارشناس</th><th>تصمیم</th><th>مدیر</th><th>توضیح</th></tr>';
        foreach ($history as $a) {
            $decision = (int) $a['decision'];
            $html .= '<tr><td class="nw">' . e(Jalali::format((int) $a['submitted_at'])) . '</td><td>' . e($a['submitted_by_name'] ?? '—')
                . '</td><td><span class="tag ' . ($decision === CaseActions::APPROVED ? 'tag-answered' : ($decision === CaseActions::RETURNED ? 'tag-overdue' : 'tag-seen'))
                . '">' . ($decision === CaseActions::APPROVED ? 'تأیید' : ($decision === CaseActions::RETURNED ? 'برگشت' : 'در انتظار'))
                . '</span></td><td>' . e($a['decided_by_name'] ?? '—') . '</td><td>' . nl2br(e((string) $a['comment'])) . '</td></tr>';
        }
        $html .= '</table>';
    }
    return $html . '</div>';
}

/** فرم ارسال به یک واحد: واحد، مهلت، توضیح، و تصاویر و پیوست‌هایی که همراهش می‌روند. */
function case_dispatch_form(array $case, ?string $error): string
{
    $html = '<div class="card" id="dispatch"><h3>ارسال به واحد</h3>'
        . '<p class="muted">واحد مقصد پرونده را در پرتال واحدها می‌بیند، مدارک انتخاب‌شده را دریافت می‌کند و پاسخش را همان‌جا ثبت می‌کند.</p>';
    if (($_GET['done'] ?? '') === 'dispatched') {
        $html .= '<p class="notice">ارسال شد و به واحد اعلان رفت.</p>';
    }
    if ($error !== null) {
        $html .= '<p class="error">' . e($error) . '</p>';
    }
    $html .= '<form method="post">' . csrf_field() . '<input type="hidden" name="action" value="dispatch"><div class="grid">'
        . '<label>واحد مقصد<select name="unit" required><option value="">—</option>';
    foreach (UNIT_NAMES as $value => $name) {
        $html .= '<option value="' . $value . '">' . e($name) . '</option>';
    }
    $html .= '</select></label><label>مهلت اعلام نتیجه (روز، خالی یعنی بدون مهلت)'
        . '<input name="deadline_days" inputmode="numeric" placeholder="۷"></label>'
        . '<label>قالب گزارش<select name="format"><option value="0">PDF</option><option value="1">Word</option></select></label></div>'
        . '<label>توضیح برای واحد<textarea name="note" rows="2"></textarea></label>';
    $choices = [];
    foreach ($case['media'] as $m) {
        $choices[] = [$m['id'], ((int) $m['type'] === 0 ? 'تصویر ' : 'فیلم ') . Jalali::format((int) $m['captured_at'])
            . ($m['caption'] ? ' — ' . $m['caption'] : '')];
    }
    foreach ($case['attachments'] as $a) {
        $choices[] = [$a['id'], CaseLabels::of(CaseLabels::ATTACHMENTS, $a['category']) . ($a['title'] ? ' — ' . $a['title'] : '')];
    }
    if ($choices !== []) {
        $html .= '<p style="margin:10px 0 4px"><strong>مدارک همراه</strong></p><div class="grid">';
        foreach ($choices as [$value, $label]) {
            $html .= '<label style="font-weight:normal"><input type="checkbox" name="items[]" value="' . e((string) $value)
                . '" checked> ' . e((string) $label) . '</label>';
        }
        $html .= '</div>';
    }
    return $html . '<button type="submit">ارسال به واحد</button></form></div>';
}
