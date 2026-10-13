<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// همان فرم رسمی، به Word: هفت بخش، جدول دستگاه‌ها و حاضرین، و پیوست تصاویر.
// مثل فرم چاپی، نام نرم‌افزار در آن نیست.
$id = (string) ($_GET['id'] ?? '');
$case = Field::isUuid($id) ? CaseReport::load($id) : null;
if ($case === null) {
    http_response_code(404);
    exit('پرونده پیدا نشد.');
}
$r = $case['report'];
$code = CaseLabels::code($r);
$blocks = [
    ['h', 'فرم گزارش بازدید مراکز استخراج رمزارز'],
    ['p', 'شرکت توزیع نیروی برق استان ایلام — ' . $code],
    ['h', '۱ — مشخصات پرونده'], ['table', CaseReport::caseRows($r)],
    ['h', '۲ — مشخصات مکان'], ['table', CaseReport::locationRows($r)],
    ['h', '۳ — مشخصات مالک یا متصرف'], ['table', CaseReport::ownerRows($r)],
    ['h', '۴ — مشخصات فنی'], ['table', CaseReport::technicalRows($r)],
    ['h', '۵ — فهرست دستگاه‌ها'],
    ['grid', [['ردیف', 'مدل', 'شماره سریال', 'توان (وات)', 'روش ثبت'], CaseReport::deviceTable($case['devices'])]],
    ['h', '۶ — حاضرین در محل'],
    ['grid', [['ردیف', 'نام و نام خانوادگی', 'سمت', 'سازمان'], CaseReport::attendeeTable($case['attendees'])]],
    ['h', '۷ — شرح بازدید و اقدامات'],
    ['table', [['شرح بازدید', (string) ($r['description'] ?: '—')], ['اقدامات انجام‌شده', (string) ($r['actions_taken'] ?: '—')]]],
];
if ($case['attachments'] !== []) {
    $blocks[] = ['h', 'مستندات پیوست'];
    $blocks[] = ['grid', [['دسته', 'عنوان', 'تاریخ'], array_map(static fn ($a) => [
        (string) CaseLabels::of(CaseLabels::ATTACHMENTS, $a['category']), (string) $a['title'],
        Jalali::format((int) $a['added_at'], false)], $case['attachments'])]];
}
$blocks[] = ['table', [['نام و امضای کارشناس', ' '], ['نام و امضای حاضرین', ' '], ['مهر و امضای مدیر', ' ']]];
$photos = array_values(array_filter($case['media'], static fn ($m) => (int) $m['type'] === 0));
if ($photos !== []) {
    $blocks[] = ['h', 'پیوست تصاویر'];
    foreach ($photos as $m) {
        $blocks[] = ['image', [(string) Storage::resolve((string) $m['file_path']), CaseReport::photoCaption($r, $m)]];
    }
}
Docx::send('case-' . preg_replace('/[^A-Za-z0-9-]/', '', $code ?: $id), $blocks);
