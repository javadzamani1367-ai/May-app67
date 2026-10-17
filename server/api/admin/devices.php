<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// بانک دستگاه‌های ماینر همه ناحیه‌ها: سریال، مدل، تاریخ کشف، مشترک و دارنده. سریالی
// که در بیش از یک پرونده آمده، یعنی دستگاه جابه‌جا شده — فقط مدیر این‌ها را می‌بیند.
$filter = DeviceRegistry::filters($_GET);
$values = $filter['values'];
if (($_GET['format'] ?? '') === 'xlsx') {
    Xlsx::send('devices', 'بانک دستگاه‌ها', DeviceRegistry::EXCEL_HEADER,
        array_map([DeviceRegistry::class, 'excelRow'], DeviceRegistry::search($_GET, 20000)));
    exit;
}
$serial = trim((string) ($_GET['serial'] ?? ''));
$history = $serial === '' ? [] : DeviceRegistry::history((string) DeviceRegistry::key($serial));
$alerts = DeviceRegistry::alerts(30);
DeviceRegistry::markSeen();
$rows = DeviceRegistry::search($_GET, 300);
$total = (int) (Db::one('SELECT COUNT(*) AS n FROM devices d JOIN reports r ON r.id = d.report_id WHERE ' . $filter['where'],
    $filter['params'])['n'] ?? 0);

$table = static function (array $rows): string {
    $html = '<table><tr><th>سریال</th><th>مدل</th><th>توان</th><th>تاریخ کشف</th><th>پرونده</th><th>ناحیه</th><th>مشترک</th><th>دارنده</th><th>دفعات</th></tr>';
    foreach ($rows as $r) {
        $p = DeviceRegistry::present($r);
        $html .= '<tr><td dir="ltr" style="text-align:right"><a href="devices.php?serial=' . urlencode((string) $p['serial_key']) . '#history">'
            . e((string) $p['serial']) . '</a></td><td>' . e((string) $p['model']) . '</td><td>' . e(CaseLabels::grouped($p['power_watt']) ?? '—')
            . '</td><td class="nw">' . e($p['found_at'] === null ? '—' : Jalali::format($p['found_at'], false)) . '</td><td dir="ltr" style="text-align:right">'
            . '<a href="case.php?id=' . e($p['report_id']) . '">' . e($p['code']) . '</a></td><td>' . e($p['area']) . '</td><td>'
            . e((string) $p['subscriber']) . '</td><td>' . e((string) $p['owner']) . '</td><td>'
            . ($p['seen_in'] > 1 ? '<span class="tag tag-overdue">' . fa_digits((string) $p['seen_in']) . ' پرونده</span>' : '۱') . '</td></tr>';
    }
    return $html . '</table>';
};

admin_header('بانک دستگاه‌ها');
?>
<div class="card">
  <div class="row">
    <div><h2>بانک دستگاه‌های ماینر</h2><p class="muted"><?= fa_digits((string) $total) ?> دستگاه</p></div>
    <a href="devices.php<?= e(query_with($values, ['format' => 'xlsx'])) ?>"><button type="button">اکسل</button></a>
  </div>
  <form method="get"><div class="grid">
    <label>جستجو<input name="q" value="<?= e($values['q'] ?? '') ?>" placeholder="سریال، مدل، مشترک، دارنده، اشتراک، کد"></label>
    <label>ناحیه<select name="area"><option value="">همه</option>
      <?php foreach (Areas::options() as $code => $label): ?><option value="<?= e((string) $code) ?>" <?= ($values['area'] ?? '') === (string) $code ? 'selected' : '' ?>><?= e($label) ?></option><?php endforeach; ?>
    </select></label>
    <label>کشف از<input name="from" value="<?= e($values['from'] ?? '') ?>" placeholder="۱۴۰۵/۰۱/۰۱"></label>
    <label>تا<input name="to" value="<?= e($values['to'] ?? '') ?>"></label>
    <label class="check"><input type="checkbox" name="dup" value="1" <?= isset($values['dup']) ? 'checked' : '' ?>> فقط سریال‌های تکراری</label>
  </div><button type="submit">جستجو</button></form>
</div>

<?php if ($alerts !== []): ?>
<div class="card">
  <h3>هشدار سریال تکراری</h3>
  <table><tr><th>زمان</th><th>سریال</th><th>پیش‌تر</th><th>اکنون</th></tr>
    <?php foreach ($alerts as $a): ?>
      <tr><td class="nw"><?= e(Jalali::format((int) $a['created_at'])) ?><?= $a['seen_at'] === null ? ' <span class="tag tag-overdue">تازه</span>' : '' ?></td>
        <td dir="ltr" style="text-align:right"><a href="devices.php?serial=<?= urlencode((string) $a['serial_key']) ?>#history"><?= e($a['serial_key']) ?></a></td>
        <td><a href="case.php?id=<?= e($a['other_report_id']) ?>"><?= e($a['before']) ?></a></td>
        <td><a href="case.php?id=<?= e($a['report_id']) ?>"><?= e($a['now']) ?></a></td></tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>

<?php if ($serial !== ''): ?>
<div class="card" id="history">
  <h3>سابقه دستگاه <span dir="ltr"><?= e($serial) ?></span> — قدیمی‌ترین اول</h3>
  <?= $history === [] ? '<p class="muted">این سریال در هیچ پرونده‌ای نیست.</p>' : $table($history) ?>
</div>
<?php endif; ?>

<div class="card"><?= $rows === [] ? '<p class="muted">دستگاهی پیدا نشد.</p>' : $table($rows) ?></div>
<?php portal_footer();
