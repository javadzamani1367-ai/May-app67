<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require(CASE_ROLES);

// فرم رسمی گزارش بازدید (CLAUDE.md بخش ۷)، برای چاپ؛ «ذخیره به PDF» مرورگر
// همان فایل PDF است. سند اداری است و به سازمان‌های دیگر می‌رود: نام نرم‌افزار
// و سازنده‌اش در آن نیست.
$id = (string) ($_GET['id'] ?? '');
$case = admin_case($id);
$r = $case['report'];
$h = CaseReport::headings($r);
$code = CaseLabels::code($r);
$photos = array_values(array_filter($case['media'], static fn ($m) => (int) $m['type'] === 0));

function print_rows(array $rows): string
{
    if ($rows === []) {
        return '<p class="empty">—</p>';
    }
    $html = '<table class="kv">';
    foreach ($rows as [$label, $value]) {
        $ltr = preg_match('/\p{Arabic}/u', (string) $value) !== 1;
        $html .= '<tr><th>' . e($label) . '</th><td' . ($ltr ? ' class="ltr"' : '') . '>' . nl2br(e((string) $value)) . '</td></tr>';
    }
    return $html . '</table>';
}

/** ردیف‌های بلند (مشخصات فنی) در دو ستون کنار هم، تا فرم کوتاه بماند. */
function print_rows2(array $rows): string
{
    if ($rows === []) {
        return '<p class="empty">—</p>';
    }
    $half = (int) ceil(count($rows) / 2);
    $html = '<table class="kv kv2">';
    for ($i = 0; $i < $half; $i++) {
        $html .= '<tr>';
        foreach ([$rows[$i] ?? null, $rows[$i + $half] ?? null] as $row) {
            $html .= $row === null ? '<th></th><td></td>'
                : '<th>' . e($row[0]) . '</th><td>' . e((string) $row[1]) . '</td>';
        }
        $html .= '</tr>';
    }
    return $html . '</table>';
}

function print_grid(array $header, array $rows): string
{
    if ($rows === []) {
        return '<p class="empty">—</p>';
    }
    $html = '<table class="grid"><tr>' . implode('', array_map(static fn ($h) => '<th>' . e($h) . '</th>', $header)) . '</tr>';
    foreach ($rows as $row) {
        $html .= '<tr>' . implode('', array_map(static fn ($v) => '<td>' . e((string) $v) . '</td>', $row)) . '</tr>';
    }
    return $html . '</table>';
}
?><!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>گزارش بازدید <?= e($code) ?></title>
<style>
  @page { size: A4; margin: 13mm 12mm; }
  body { font-family: "Vazirmatn", "IRANSans", Tahoma, sans-serif; color: #000; margin: 0 auto; max-width: 190mm;
         line-height: 1.65; font-size: 10.5pt; }
  .head { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 2px solid #000;
          padding-bottom: 6px; margin-bottom: 8px; }
  .head h1 { font-size: 14.5pt; margin: 2px 0 0; } .head .org { font-size: 10pt; }
  .head .meta { text-align: left; font-size: 9.5pt; }
  h2 { font-size: 11.5pt; margin: 12px 0 5px; padding: 3px 8px; background: #e5e7eb; border-inline-start: 4px solid #111; }
  table { width: 100%; border-collapse: collapse; page-break-inside: auto; }
  tr { page-break-inside: avoid; }
  th, td { border: 1px solid #666; padding: 4px 7px; text-align: right; vertical-align: top; }
  .kv th { width: 34%; background: #f3f4f6; font-weight: normal; }
  .kv2 th { width: 27%; } .kv2 td { width: 23%; }
  .grid th { background: #f3f4f6; font-weight: normal; }
  .two { display: grid; grid-template-columns: 1fr 1fr; gap: 0 10px; }
  .ltr { direction: ltr; text-align: right; } .empty { margin: 2px 6px; color: #444; }
  .box { border: 1px solid #666; min-height: 64px; padding: 6px 8px; white-space: pre-wrap; }
  .signs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 10px; }
  .signs .box { min-height: 74px; }
  .appendix { page-break-before: always; }
  .photos { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  .photos figure { margin: 0; border: 1px solid #999; page-break-inside: avoid; }
  .photos img { width: 100%; max-height: 105mm; object-fit: contain; display: block; background: #f3f4f6; }
  .photos figcaption { font-size: 8.5pt; padding: 3px 6px; }
  .actions { margin: 12px 0; } .actions button { font: inherit; padding: 8px 18px; }
  @media print { .actions { display: none; } }
</style>
</head>
<body>
<div class="actions"><button onclick="window.print()">چاپ یا ذخیره PDF</button>
  <a href="case.php?id=<?= e($id) ?>">بازگشت به پرونده</a></div>
<div class="head">
  <div><div class="org">شرکت توزیع نیروی برق استان ایلام</div><h1><?= e(CaseReport::title($r)) ?></h1></div>
  <div class="meta"><div class="ltr"><strong><?= e($code) ?></strong></div>
    <div>تاریخ تهیه: <?= e(Jalali::format(Db::now(), false)) ?></div></div>
</div>

<div class="two">
  <div><h2><?= e($h['case']) ?></h2><?= print_rows(CaseReport::caseRows($r)) ?></div>
  <div><h2><?= e($h['owner']) ?></h2><?= print_rows(CaseReport::ownerRows($r)) ?></div>
</div>
<h2><?= e($h['place']) ?></h2><?= print_rows(CaseReport::locationRows($r)) ?>
<h2><?= e($h['tech']) ?></h2><?= print_rows2(CaseReport::technicalRows($r)) ?>
<?php if (isset($h['devices'])): ?>
<h2><?= e($h['devices']) ?></h2>
<?= print_grid(['ردیف', 'مدل', 'شماره سریال', 'توان (وات)', 'روش ثبت'], CaseReport::deviceTable($case['devices'])) ?>
<?php endif; ?>
<h2><?= e($h['people']) ?></h2>
<?= print_grid(['ردیف', 'نام و نام خانوادگی', 'سمت', 'سازمان'], CaseReport::attendeeTable($case['attendees'])) ?>
<h2><?= e($h['text']) ?></h2>
<div class="box"><?= e((string) ($r['description'] ?: '—')) ?></div>
<p style="margin:6px 0 2px"><strong>اقدامات انجام‌شده</strong></p>
<div class="box"><?= e((string) ($r['actions_taken'] ?: '—')) ?></div>
<?php if ($case['attachments'] !== []): ?>
<p style="margin:6px 0 2px"><strong>مستندات پیوست</strong></p>
<?= print_grid(['دسته', 'عنوان', 'تاریخ'], array_map(static fn ($a) => [
    (string) CaseLabels::of(CaseLabels::ATTACHMENTS, $a['category']), (string) $a['title'],
    Jalali::format((int) $a['added_at'], false)], $case['attachments'])) ?>
<?php endif; ?>
<div class="signs">
  <div class="box">نام و امضای کارشناس</div>
  <div class="box">نام و امضای حاضرین</div>
  <div class="box">مهر و امضای مدیر</div>
</div>

<?php if ($photos !== []): ?>
<div class="appendix"><h2>پیوست تصاویر</h2>
<div class="photos">
  <?php foreach ($photos as $m): ?>
    <figure><img src="case_file.php?kind=media&amp;id=<?= e(urlencode((string) $m['id'])) ?>" alt="">
      <figcaption><?= e(CaseReport::photoCaption($r, $m)) ?></figcaption></figure>
  <?php endforeach; ?>
</div></div>
<?php endif; ?>
</body>
</html>
