<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// خروجی آمار جامع: اکسل به شکل فرم توانیر (همه ستون‌های تیک‌خورده اکسل)، Word ستون‌های
// تیک‌خورده Word، و اکسل دو گزارش پاداش (فقط مدیر).
$user = admin_require(CASE_ROLES);
$access = CenterSettings::access($user);
if ($access === null) {
    http_response_code(403);
    exit('به آمار جامع دسترسی ندارید.');
}
[$from, $to] = center_range($_GET);
$rows = CenterRows::query($access, $from, $to, ($_GET['area'] ?? '') ?: null, (string) ($_GET['q'] ?? ''));
$format = (string) ($_GET['format'] ?? 'xlsx');
$title = CenterCharts::title($from, $to);
if (in_array($format, ['staff', 'agents'], true)) {
    if (!$access['manage']) {
        http_response_code(403);
        exit('فقط مدیر.');
    }
    Xlsx::sendBook($format === 'staff' ? 'staff-rewards' : 'agent-rewards', $format === 'staff'
        ? CenterRewardReport::staffBook(CenterRewardReport::staff($rows)) : CenterRewardReport::agentsBook(CenterRewardReport::agents($rows)));
    exit;
}
$keys = array_values(array_intersect(CenterSettings::columns($format === 'docx' ? 'docx' : 'xlsx'), $access['see']));
if ($format === 'docx') {
    CenterDocx::send('center-stats', $rows, $keys, 'آمار جامع مراکز استخراج و اقدامات — ' . $title,
        'از ' . Jalali::format($from, false) . ' تا ' . Jalali::format($to, false) . ' — ' . fa_digits((string) count($rows)) . ' مرکز');
} else {
    CenterXlsx::send('center-stats', $rows, $keys, $title);
}
