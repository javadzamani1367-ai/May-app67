<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require(CASE_ROLES);

// ثبت گزارش (نه بازدید): نوع، ناحیه، نشانی و آنچه از گزارش معلوم است. مدیر می‌تواند
// همان‌جا به مجری ناحیه یا کارشناسی بسپارد؛ اگر نسپرد، پس از مهلت تنظیمات خودکار به
// مجری ناحیه می‌رسد. مجری فقط در ناحیه‌های خودش ثبت می‌کند و پرونده نزد خود اوست.
$scope = admin_scope();
$error = null;
$keys = ['report_type', 'area_code', 'district', 'address', 'postal_code', 'subscription_number', 'subscriber_name',
    'owner_name', 'owner_phone', 'report_date', 'tracking_code', 'note'];
$data = array_fill_keys($keys, '');
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    foreach ($keys as $key) {
        $data[$key] = trim((string) ($_POST[$key] ?? ''));
    }
    $error = CaseIntake::problem($data, $scope);
    if ($error === null) {
        $id = CaseIntake::create($data, $scope);
        $target = (string) ($_POST['target'] ?? '');
        $warning = null;
        if (str_starts_with($target, 'expert:')) {
            $warning = CaseFlow::toExpert($id, substr($target, 7), null, '', $scope);
        } elseif ($target === 'executor' && $scope->isManager()) {
            $warning = CaseFlow::toExecutor($id, null, '', $scope);
        }
        header('Location: case.php?id=' . urlencode($id) . ($warning === null ? '&done=flow' : '') . '#flow');
        exit;
    }
}
$areas = array_filter(Areas::options(), static fn ($code) => $scope->coversArea((string) $code), ARRAY_FILTER_USE_KEY);
$settings = FieldSettings::all();
$field = static fn (string $name, string $label, string $extra = '') => '<label>' . e($label) . '<input name="' . $name . '" value="'
    . e($data[$name]) . '" ' . $extra . '></label>';

admin_header('ثبت گزارش');
?>
<div class="card">
  <h2>ثبت گزارش تازه</h2>
  <p class="muted">گزارش ثبت می‌شود و پرونده «در دست اقدام» ساخته می‌شود؛ بازدید را کارشناس روی گوشی انجام می‌دهد.
    <?php if ($scope->isManager()): ?>اگر به کسی نسپارید، پس از <?= fa_digits((string) $settings['refer_days']) ?> روز کاری خودکار به مجری ناحیه می‌رسد.<?php endif; ?></p>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?>
    <div class="grid">
      <label>نوع گزارش<select name="report_type" required><option value="">—</option>
        <?php foreach (CaseLabels::REPORT_TYPES as $code => $name): ?>
          <option value="<?= $code ?>" <?= $data['report_type'] === (string) $code ? 'selected' : '' ?>><?= e($name) ?></option>
        <?php endforeach; ?></select></label>
      <label>ناحیه<select name="area_code" required><option value="">—</option>
        <?php foreach ($areas as $code => $label): ?>
          <option value="<?= e((string) $code) ?>" <?= $data['area_code'] === (string) $code ? 'selected' : '' ?>><?= e($label) ?></option>
        <?php endforeach; ?></select></label>
      <?= $field('report_date', 'تاریخ گزارش (خالی: امروز)', 'placeholder="۱۴۰۵/۰۷/۲۰"') ?>
      <?= $field('tracking_code', 'کد رهگیری سامانه سراغ (فقط سراغ)', 'dir="ltr"') ?>
      <?= $field('district', 'بخش') ?>
      <?= $field('postal_code', 'کد پستی', 'dir="ltr" inputmode="numeric"') ?>
      <?= $field('subscription_number', 'شماره اشتراک (اگر معلوم است)', 'dir="ltr" inputmode="numeric"') ?>
      <?= $field('subscriber_name', 'نام مشترک') ?>
      <?= $field('owner_name', 'نام مالک یا متصرف') ?>
      <?= $field('owner_phone', 'تلفن', 'dir="ltr"') ?>
    </div>
    <label>آدرس یا نشانی محل<textarea name="address" rows="2" required><?= e($data['address']) ?></textarea></label>
    <label>شرح گزارش و توضیح برای مجری و کارشناس<textarea name="note" rows="3"><?= e($data['note']) ?></textarea></label>
    <label>سپردن
      <select name="target">
        <option value=""><?= $scope->isManager() ? 'فعلاً نه (پس از مهلت خودکار به مجری ناحیه)' : 'فعلاً نزد خودم' ?></option>
        <?php if ($scope->isManager()): ?><option value="executor">به مجری ناحیه پرونده</option><?php endif; ?>
        <?php foreach ($scope->experts() as $x): ?>
          <option value="expert:<?= e($x['id']) ?>">به کارشناس: <?= e($x['name']) ?> (<?= e(implode('، ', array_map([Areas::class, 'label'], $x['areas'])) ?: 'بی‌ناحیه') ?>)</option>
        <?php endforeach; ?>
      </select>
    </label>
    <button type="submit">ثبت گزارش</button>
  </form>
</div>
<?php portal_footer();
