<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';
require __DIR__ . '/_crew.php';

admin_require(CREW_ROLES);
$canApprove = admin_scope()->isManager();

// عملکرد اکیپ از لحظه‌های ثبت موقعیت تابلوها و تیرها (بدون ردیابی پیوسته):
// بازدید و بازرسی هر نفر، ساعت شروع و پایان، ساعات روز، اضافه‌کار و بیرون از شهر.
// اضافه‌کار تأییدشده هر نفر را مدیر همین‌جا برای همین بازه می‌نویسد.
$period = crew_period();
$member = trim((string) ($_GET['member'] ?? ''));
$error = null;
if ($canApprove && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $error = CrewReport::saveApproved((string) ($_POST['member_key'] ?? ''), $period['from_day'], $period['to_day'],
        (string) ($_POST['hours'] ?? ''), (string) ($_POST['note'] ?? ''), admin_user()['id'] ?? null);
    if ($error === null) {
        header('Location: crew.php?' . crew_query($period, $member));
        exit;
    }
}
$report = crew_report($period, $member);
$all = $member === '' ? $report : crew_report($period, '');
$approved = CrewReport::approved($period['from_day'], $period['to_day']);
$collected = CrewMap::collect($report['members']);
$inspections = array_values(array_filter($collected['events'], static fn (array $e): bool => $e['inspection']));
$calendar = CrewCalendar::fromSettings();
$buckets = array_fill_keys(array_map(static fn (array $b): string => $b[1], CrewCalendar::BUCKETS), 0);
$types = array_fill_keys(array_values(CrewWork::TYPE_NAMES), 0);
$overtime = 0;
$outside = 0;
foreach ($inspections as $e) {
    $buckets[CrewCalendar::BUCKETS[CrewCalendar::bucket($e['at'])][1]]++;
    $types[CrewWork::TYPE_NAMES[$e['type']]]++;
    $overtime += $calendar->overtime($e['at']) ? 1 : 0;
    $outside += ($e['outside'] ?? null) === true ? 1 : 0;
}
$geo = GeoPlaces::load(null, Storage::root() . '/cache');
$query = crew_query($period, $member);
$tile = static fn (string $value, string $label, string $tone = '') =>
    '<div class="kpi ' . $tone . '"><b>' . e($value) . '</b><span>' . e($label) . '</span></div>';

admin_header('عملکرد اکیپ');
echo case_styles();
?>
<div class="card">
  <h2>عملکرد اکیپ</h2>
  <p class="muted">از لحظه ثبت موقعیت هر تابلو و تیر؛ ردیابی پیوسته در کار نیست. هر کس در اکیپ یک کار نام برده شده،
    همه آن کار را به نام خود دارد. ساعت اداری در <a href="settings.php">تنظیمات</a> و تعطیلات در <a href="holidays.php">تعطیلات</a>.</p>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <label style="margin:0">از <input name="from" value="<?= e($period['from_text']) ?>" style="width:120px"></label>
    <label style="margin:0">تا <input name="to" value="<?= e($period['to_text']) ?>" style="width:120px"></label>
    <label style="margin:0">عضو <select name="member"><option value="">همه</option>
      <?php foreach ($all['members'] as $m): ?><option value="<?= e($m['key']) ?>" <?= $m['key'] === $member ? 'selected' : '' ?>><?= e($m['name']) ?></option><?php endforeach; ?>
    </select></label>
    <button type="submit">نمایش</button>
    <a href="crew_export.php?<?= e($query) ?>">اکسل</a>
    <a href="crew_docx.php?<?= e($query) ?>">گزارش Word</a>
  </form>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <div class="kpis">
    <?= $tile(fa_digits((string) count($report['members'])), 'نفر') ?>
    <?= $tile(fa_digits((string) count($collected['visits'])), 'بازدید') ?>
    <?= $tile(fa_digits((string) count($inspections)), 'بازرسی', 'ok') ?>
    <?= $tile(fa_digits((string) $overtime), 'بازرسی در اضافه‌کار', 'warn') ?>
    <?= $tile($report['geo'] ? fa_digits((string) $outside) : '—', 'بیرون از محدوده شهر') ?>
  </div>
  <?php if (!$report['geo']): ?>
    <p class="muted">فایل نقشه آفلاین در api/maps نیست؛ «بیرون از شهر» و زمینه نقشه حساب نمی‌شوند.</p>
  <?php endif; ?>
</div>

<?php if ($report['members'] === []): ?>
<div class="card"><p class="muted">در این بازه کاری با اکیپ ثبت نشده است.</p></div>
<?php else: ?>
<div class="card">
  <h3>هر نفر</h3>
  <table>
    <tr><th>نام</th><th>نقش</th><th>روز</th><th>بازدید</th><th>بازرسی</th><th>شروع</th><th>پایان</th>
      <th>اضافه‌کار محاسبه‌شده</th><th>اضافه‌کار تأییدشده (ساعت)</th><th>بیرون شهر</th><th></th></tr>
    <?php foreach ($report['members'] as $m): $t = $m['total']; $ok = $approved[$m['key']] ?? null; ?>
      <tr>
        <td><a href="crew.php?<?= e(crew_query($period, $m['key'])) ?>"><?= e($m['name']) ?></a></td>
        <td><?= e(implode('، ', $m['roles'])) ?></td>
        <td><?= fa_digits((string) $t['days']) ?><?= $t['holidays'] > 0 ? ' <small class="muted">(' . fa_digits((string) $t['holidays']) . ' تعطیل)</small>' : '' ?></td>
        <td><?= fa_digits((string) $t['visits']) ?></td>
        <td><?= fa_digits((string) $t['inspections']) ?></td>
        <td><?= e(CrewDays::clock($t['avg_start'])) ?></td>
        <td><?= e(CrewDays::clock($t['avg_end'])) ?></td>
        <td><?= fa_digits((string) $t['overtime']) ?> ساعت</td>
        <td>
          <?php if (!$canApprove): ?><?= $ok === null ? '—' : e(fa_digits((string) $ok['hours'])) . ' ساعت' ?><?php else: ?>
          <form method="post" style="display:flex;gap:6px;align-items:center;margin:0;flex-wrap:nowrap">
            <?= csrf_field() ?>
            <input type="hidden" name="member_key" value="<?= e($m['key']) ?>">
            <input name="hours" value="<?= $ok === null ? '' : e(fa_digits((string) $ok['hours'])) ?>" style="width:70px" dir="ltr">
            <input name="note" value="<?= e($ok['note'] ?? '') ?>" placeholder="توضیح" style="width:140px">
            <button type="submit" style="margin:0;padding:6px 12px">ثبت</button>
          </form><?php endif; ?>
        </td>
        <td><?= $report['geo'] ? fa_digits((string) $t['outside']) : '—' ?></td>
        <td class="nw"><a href="crew_docx.php?<?= e(crew_query($period, $m['key'])) ?>">Word</a></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>

<div class="section-grid">
  <div class="card"><h3>ساعات شبانه‌روز</h3><?= crew_bars($buckets) ?></div>
  <div class="card"><h3>نوع بازرسی‌ها</h3><?= crew_bars($types) ?></div>
</div>

<div class="card">
  <h3>نقشه بازدیدها و بازرسی‌ها</h3>
  <?= CrewMap::svg($collected, $geo) ?: '<p class="muted">هیچ کاری موقعیت ندارد.</p>' ?>
  <?= crew_legend() ?>
  <?= crew_visits_table($collected['visits']) ?>
</div>

<?php foreach ($member === '' ? [] : $report['members'] as $m): ?>
<div class="card">
  <h3>کار روزانه <?= e($m['name']) ?></h3>
  <?= crew_days_table($m) ?>
</div>
<?php endforeach; ?>
<?php endif; ?>
<?php portal_footer();
