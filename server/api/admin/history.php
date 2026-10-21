<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_history_view.php';

// سوابق بازرسی تابلو و تیر: جستجو، رشته هر پلاک با نمودار، خروجی اکسل و Word، و ارجاع
// برای بازرسی رمزارز. مدیر، مجری ستاد، مجری رمزارز و مجری برق غیرمجاز شهرستان.
$manager = admin_require(CASE_ROLES);
$scope = admin_scope();
$filters = history_filters($_GET);
$view = isset($_GET['part']) ? [
    'area' => Areas::code((string) ($_GET['k_area'] ?? '')), 'asset' => (int) ($_GET['k_asset'] ?? 0),
    'plate_key' => FieldReadings::key((string) ($_GET['k_plate'] ?? '')), 'part' => (string) $_GET['part'],
] : null;
if ($view !== null && !FieldHistorySearch::canSeeKey($scope, $view['area'], $view['asset'], $view['plate_key'], $view['part'])) {
    http_response_code(404);
    exit('سابقه‌ای برای این پلاک در محدوده شما نیست.');
}

$referError = null;
$referDone = null;
if ($view !== null && ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST' && ($_POST['action'] ?? '') === 'refer') {
    csrf_check();
    $due = trim((string) ($_POST['due_days'] ?? ''));
    $result = HistoryReferral::refer($scope, $view['area'], $view['asset'], $view['plate_key'], $view['part'],
        (string) ($_POST['expert_id'] ?? ''), (string) ($_POST['note'] ?? ''), $due === '' ? null : (int) Jalali::latin($due));
    $referError = Field::isUuid($result) ? null : $result;
    $referDone = Field::isUuid($result) ? $result : null;
}

$keys = $view === null ? FieldHistorySearch::search($scope, $filters) : [];
$wholeProvince = $scope->wholeProvince();
admin_header('سوابق بازرسی');
?>
<div class="card">
  <h2>سوابق بازرسی تابلو و تیر</h2>
  <p class="muted">هر خوانش کلید کل، فیدرهای تابلو، هر تیر تیربه‌تیر و هر تصویر ترموویژن، از تازه‌ترین به قبل. «نامتعارف» یعنی افزایش یک فاز
    بیش از <?= fa_digits((string) FieldSettings::all()['history_rise_pct']) ?>٪ نسبت به بار قبل یا میانگین پنج بار قبل
    (<a href="settings.php">تنظیمات میدانی</a>).</p>
  <form method="get">
    <div class="grid">
      <?php if ($wholeProvince): ?>
      <label>ناحیه<select name="area"><option value="">همه</option>
        <?php foreach (Areas::NAMES as $code => $name): ?><option value="<?= e((string) $code) ?>" <?= $filters['area'] === (string) $code ? 'selected' : '' ?>><?= e(Areas::label((string) $code)) ?></option><?php endforeach; ?>
      </select></label>
      <?php endif; ?>
      <label>تابلو یا تیر<select name="asset"><option value="">هر دو</option>
        <option value="1" <?= $filters['asset'] === 1 ? 'selected' : '' ?>>تابلو</option><option value="0" <?= $filters['asset'] === 0 ? 'selected' : '' ?>>تیر</option></select></label>
      <label>نوع بازرسی<select name="kind"><option value="">همه</option>
        <?php foreach (Field::INSPECTION_KINDS as $k): ?><option value="<?= $k ?>" <?= $filters['kind'] === $k ? 'selected' : '' ?>><?= e(FieldAdmin::KIND_NAMES[$k]) ?></option><?php endforeach; ?>
      </select></label>
      <label>پلاک<input name="plate" value="<?= e($filters['plate']) ?>" dir="ltr"></label>
      <label>از تاریخ<input name="from" placeholder="۱۴۰۵/۰۱/۰۱" value="<?= e((string) ($_GET['from'] ?? '')) ?>"></label>
      <label>تا تاریخ<input name="to" placeholder="۱۴۰۵/۱۲/۲۹" value="<?= e((string) ($_GET['to'] ?? '')) ?>"></label>
    </div>
    <label class="check"><input type="checkbox" name="anomaly" value="1" <?= $filters['anomaly'] ? 'checked' : '' ?>> فقط پلاک‌های دارای افزایش نامتعارف</label>
    <button type="submit">نمایش</button>
    <a href="history_export.php<?= e(query_with($_GET, ['format' => 'xlsx', 'part' => null])) ?>"><button type="button" style="background:#15803d">خروجی اکسل</button></a>
    <a href="history_export.php<?= e(query_with($_GET, ['format' => 'docx', 'part' => null])) ?>"><button type="button" style="background:#1d4ed8">خروجی Word</button></a>
  </form>
</div>

<?php if ($view !== null):
    $rows = FieldHistory::series($view['area'], $view['asset'], $view['plate_key'], $view['part'], null, 200, true);
    $label = FieldHistory::label($view['asset'], (string) ($rows[0]['plate'] ?? $view['plate_key']), $view['part']); ?>
<div class="card">
  <div class="row"><div><h2><?= e($label) ?></h2><p class="muted"><?= e($view['area'] === null ? '—' : Areas::label($view['area'])) ?> ·
    <?= fa_digits((string) count($rows)) ?> خوانش</p></div><div><a href="history.php<?= e(query_with($_GET, ['part' => null, 'k_area' => null, 'k_asset' => null, 'k_plate' => null])) ?>">بازگشت به فهرست</a></div></div>
  <?= $view['part'] === FieldReadings::PART_THERMAL ? '' : history_chart($rows) ?>
  <?= history_table($rows, $view['part']) ?>
</div>
<?= history_refer_form($view, $scope, $referError, $referDone) ?>
<?php else: ?>
<div class="card">
  <?php if ($keys === []): ?>
    <p class="muted">سابقه‌ای با این فیلترها نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>تابلو یا تیر</th><th>ناحیه</th><th>خوانش‌ها</th><th>آخرین</th><th>آخرین R / S / T</th><th>نامتعارف</th></tr>
    <?php foreach ($keys as $k): $last = $k['last'] ?? []; ?>
    <tr>
      <td><a href="history.php<?= e(query_with($_GET, ['k_area' => $k['area'], 'k_asset' => $k['asset'], 'k_plate' => $k['plate_key'], 'part' => $k['part']])) ?>"><?= e($k['label']) ?></a></td>
      <td><?= e($k['area_label']) ?></td>
      <td><?= fa_digits((string) $k['count']) ?></td>
      <td class="nw"><?= e(Jalali::format($k['last_at'])) ?></td>
      <td><?= $k['part'] === FieldReadings::PART_THERMAL ? 'بیشینه ' . history_num($last['temp_max'] ?? null) . '°'
          : history_num($last['r'] ?? null) . ' / ' . history_num($last['s'] ?? null) . ' / ' . history_num($last['t'] ?? null) ?></td>
      <td><?= $k['anomalies'] > 0 ? '<span class="tag tag-overdue">' . fa_digits((string) $k['anomalies']) . '</span>' : '—' ?></td>
    </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>
<?php endif; ?>
<?php portal_footer();
