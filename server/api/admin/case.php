<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require();

// یک پرونده بازدید کامل، به ترتیب هفت بخش فرم رسمی، با تصاویر، مدارک و
// سابقه ارسال به واحدها. چاپ و Word همان بخش‌ها را می‌سازند (CaseReport).
$id = (string) ($_GET['id'] ?? '');
$case = Field::isUuid($id) ? CaseReport::load($id) : null;
if ($case === null) {
    http_response_code(404);
    admin_header('یافت نشد');
    echo '<div class="card"><p class="error">پرونده پیدا نشد.</p></div>';
    portal_footer();
    exit;
}
$r = $case['report'];
$code = CaseLabels::code($r);
$photos = array_values(array_filter($case['media'], static fn ($m) => (int) $m['type'] === 0));
$videos = array_values(array_filter($case['media'], static fn ($m) => (int) $m['type'] !== 0));
$referral = Db::one('SELECT id, tracking_code FROM field_items WHERE report_id = ? OR tracking_code = ? LIMIT 1', [$id, $code]);

/** ردیف‌های [عنوان، مقدار] به جدول؛ مقدار بی حرف فارسی (کد، مختصات) چپ‌به‌راست. */
function case_table(array $rows): string
{
    $html = '<table>';
    foreach ($rows as [$label, $value]) {
        $ltr = preg_match('/\p{Arabic}/u', (string) $value) !== 1;
        $html .= '<tr><th style="width:40%">' . e($label) . '</th><td' . ($ltr ? ' dir="ltr" style="text-align:right"' : '')
            . '>' . nl2br(e((string) $value)) . '</td></tr>';
    }
    return $html . '</table>';
}

/** بخشی از فرم: عنوان و ردیف‌ها؛ بخش خالی گفته می‌شود که خالی است. */
function case_section(string $title, array $rows): string
{
    return '<div class="card"><h3>' . e($title) . '</h3>'
        . ($rows === [] ? '<p class="muted">ثبت نشده.</p>' : case_table($rows)) . '</div>';
}

/** بخش بلند (مشخصات فنی) در دو ستون، در عرض کامل صفحه. */
function case_section2(string $title, array $rows): string
{
    if ($rows === []) {
        return case_section($title, $rows);
    }
    $half = (int) ceil(count($rows) / 2);
    return '<div class="card"><h3>' . e($title) . '</h3><div class="section-grid">'
        . '<div>' . case_table(array_slice($rows, 0, $half)) . '</div>'
        . '<div>' . case_table(array_slice($rows, $half)) . '</div></div></div>';
}

/** جدول چندستونی (دستگاه‌ها، حاضرین). */
function case_grid(string $title, array $header, array $rows): string
{
    $html = '<div class="card"><h3>' . e($title) . '</h3>';
    if ($rows === []) {
        return $html . '<p class="muted">ثبت نشده.</p></div>';
    }
    $html .= '<table><tr>' . implode('', array_map(static fn ($h) => '<th>' . e($h) . '</th>', $header)) . '</tr>';
    foreach ($rows as $row) {
        $html .= '<tr>' . implode('', array_map(static fn ($v) => '<td>' . e((string) $v) . '</td>', $row)) . '</tr>';
    }
    return $html . '</table></div>';
}

admin_header('پرونده ' . $code);
echo case_styles();
?>
<div class="card">
  <div class="row">
    <div>
      <h2 dir="ltr" style="text-align:right"><?= e($code ?: '—') ?></h2>
      <p><?= case_type_tag((int) $r['report_type']) ?> <?= case_status_tag($r) ?></p>
      <p class="muted"><?= e(CaseLabels::countyWithArea($r['county'], $r['area_code'])) ?>
        <?= $r['address'] ? '— ' . e(mb_substr((string) $r['address'], 0, 90)) : '' ?></p>
    </div>
    <div class="actions-bar">
      <a href="case_print.php?id=<?= e($id) ?>" target="_blank"><button type="button">فرم رسمی (چاپ و PDF)</button></a>
      <a href="case_docx.php?id=<?= e($id) ?>"><button type="button">خروجی Word</button></a>
      <a href="cases.php"><button type="button" style="background:#64748b">بازگشت به آرشیو</button></a>
    </div>
  </div>
  <?php if ($r['approval_comment']): ?>
    <p class="error"><strong>نظر مدیر:</strong> <?= nl2br(e((string) $r['approval_comment'])) ?></p>
  <?php endif; ?>
  <?php if ($referral !== null): ?>
    <p class="muted">این پرونده از مورد میدانی
      <a href="item.php?id=<?= e($referral['id']) ?>"><?= e(ltr((string) $referral['tracking_code'])) ?></a> ساخته شده.</p>
  <?php endif; ?>
</div>

<div class="section-grid">
  <?= case_section('۱ — مشخصات پرونده', CaseReport::caseRows($r)) ?>
  <?= case_section('۲ — مشخصات مکان', CaseReport::locationRows($r)) ?>
  <?= case_section('۳ — مشخصات مالک یا متصرف', CaseReport::ownerRows($r)) ?>
</div>
<?= case_section2('۴ — مشخصات فنی', CaseReport::technicalRows($r)) ?>
<?php if (is_numeric($r['latitude']) && is_numeric($r['longitude'])): $lat = (float) $r['latitude']; $lon = (float) $r['longitude']; ?>
<div class="card"><p dir="ltr" style="text-align:right;margin:0">
  <a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=<?= $lat ?>&amp;mlon=<?= $lon ?>#map=18/<?= $lat ?>/<?= $lon ?>">OpenStreetMap</a>
  — <a target="_blank" rel="noopener" href="https://www.google.com/maps?q=<?= $lat ?>,<?= $lon ?>">Google Maps</a></p></div>
<?php endif; ?>
<?= case_grid('۵ — فهرست دستگاه‌ها', ['ردیف', 'مدل', 'شماره سریال', 'توان (وات)', 'روش ثبت'], CaseReport::deviceTable($case['devices'])) ?>
<?= case_grid('۶ — حاضرین در محل', ['ردیف', 'نام و نام خانوادگی', 'سمت', 'سازمان'], CaseReport::attendeeTable($case['attendees'])) ?>
<?= case_section('۷ — شرح بازدید و اقدامات', array_values(array_filter([
    ['شرح بازدید', (string) $r['description']], ['اقدامات انجام‌شده', (string) $r['actions_taken']],
], static fn ($row) => trim($row[1]) !== ''))) ?>

<div class="card">
  <h3>تصاویر (<?= fa_digits((string) count($photos)) ?>)<?= $videos !== [] ? ' و فیلم‌ها (' . fa_digits((string) count($videos)) . ')' : '' ?></h3>
  <?php if ($photos === [] && $videos === []): ?><p class="muted">تصویری به سرور نرسیده.</p><?php endif; ?>
  <div class="gallery">
    <?php foreach ($photos as $m): $url = 'case_file.php?kind=media&id=' . urlencode((string) $m['id']); ?>
      <figure><a href="<?= e($url) ?>" target="_blank"><img src="<?= e($url) ?>" loading="lazy" alt=""></a>
        <figcaption><?= e(CaseReport::photoCaption($r, $m)) ?></figcaption></figure>
    <?php endforeach; ?>
  </div>
  <?php foreach ($videos as $m): ?>
    <p><a href="case_file.php?kind=media&amp;id=<?= e(urlencode((string) $m['id'])) ?>&amp;download=1">فیلم
      <?= e(Jalali::format((int) $m['captured_at'])) ?></a></p>
  <?php endforeach; ?>
</div>

<div class="card">
  <h3>مدارک پیوست (<?= fa_digits((string) count($case['attachments'])) ?>)</h3>
  <?php if ($case['attachments'] === []): ?><p class="muted">مدرکی پیوست نشده.</p><?php else: ?>
  <table><tr><th>دسته</th><th>عنوان</th><th>تاریخ</th><th>توضیح</th></tr>
    <?php foreach ($case['attachments'] as $a): ?>
      <tr><td><?= e(CaseLabels::of(CaseLabels::ATTACHMENTS, $a['category'])) ?></td>
        <td><a href="case_file.php?kind=attachment&amp;id=<?= e(urlencode((string) $a['id'])) ?>&amp;download=1"><?= e($a['title'] ?: 'دریافت') ?></a></td>
        <td><?= e(Jalali::format((int) $a['added_at'], false)) ?></td><td><?= e((string) $a['note']) ?></td></tr>
    <?php endforeach; ?>
  </table><?php endif; ?>
</div>

<div class="card">
  <h3>ارسال به واحدها (<?= fa_digits((string) count($case['dispatches'])) ?>)</h3>
  <?php if ($case['dispatches'] === []): ?><p class="muted">به واحدی ارسال نشده.</p><?php else: ?>
  <table><tr><th>واحد</th><th>تاریخ</th><th>قالب و راه</th><th>مهلت</th><th>وضعیت</th><th>پاسخ</th></tr>
    <?php foreach ($case['dispatches'] as $d): $overdue = $d['deadline_at'] !== null && (int) $d['status'] < 2 && (int) $d['deadline_at'] < Db::now(); ?>
      <tr><td><?= e(UNIT_NAMES[(int) $d['unit']] ?? '—') ?></td>
        <td><?= e(Jalali::format((int) $d['dispatched_at'], false)) ?></td>
        <td><?= e(CaseLabels::of(CaseLabels::FORMATS, $d['output_format']) . ' · ' . CaseLabels::of(CaseLabels::CHANNELS, $d['channel'])) ?></td>
        <td><?= $d['deadline_at'] === null ? '—' : '<span class="tag ' . ($overdue ? 'tag-overdue' : 'tag-sent') . '">'
            . e(Jalali::format((int) $d['deadline_at'], false)) . '</span>' ?></td>
        <td><span class="tag <?= CaseLabels::DISPATCH_TAGS[(int) $d['status']] ?? 'tag-sent' ?>"><?= e(CaseLabels::of(CaseLabels::DISPATCH_STATUSES, $d['status'])) ?></span></td>
        <td><?= nl2br(e(mb_substr((string) $d['answer'], 0, 300))) ?></td></tr>
    <?php endforeach; ?>
  </table><?php endif; ?>
</div>
<p class="muted">آخرین تغییر روی گوشی: <?= e(Jalali::format((int) $r['updated_at'])) ?></p>
<?php portal_footer();
