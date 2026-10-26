<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_centers_view.php';

// خروجی آمار جامع: اکسل به شکل فرم توانیر و Word، هر کدام با ستون‌هایی که در فرم خروجی تیک
// خورده؛ و اکسل و Word رسمی سه گزارش پاداش (فقط مدیر).
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
$reward = (string) ($_GET['reward'] ?? '');
if (isset(CenterRewardReport::KINDS[$reward])) {
    if (!$access['manage']) {
        http_response_code(403);
        exit('فقط مدیر.');
    }
    $model = CenterRewardReport::model($reward, $rows, $from, $to);
    $format === 'docx' ? OfficialDocx::send($reward . '-rewards', $model) : OfficialXlsx::send($reward . '-rewards', $model);
    exit;
}
// ستون‌هایی که همین حالا در فرم خروجی تیک خورده؛ بی تیک، پیش‌فرض تنظیمات همان قالب.
$asked = array_map('strval', (array) ($_GET['cols'] ?? []));
$keys = array_values(array_intersect($asked === [] ? CenterSettings::columns($format === 'docx' ? 'docx' : 'xlsx')
    : CenterColumns::pick($asked), $access['see']));
if ($format === 'docx') {
    CenterDocx::send('center-stats', $rows, $keys, 'آمار جامع مراکز استخراج و اقدامات — ' . $title,
        'از ' . Jalali::format($from, false) . ' تا ' . Jalali::format($to, false) . ' — ' . fa_digits((string) count($rows)) . ' مرکز');
} else {
    CenterXlsx::send('center-stats', $rows, $keys, $title);
}
