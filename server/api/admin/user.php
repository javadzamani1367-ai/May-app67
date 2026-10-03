<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$manager = admin_require();
$code = trim((string) ($_GET['code'] ?? ''));
$existing = $code === '' ? null : Db::one('SELECT * FROM users WHERE user_code = ?', [$code]);
$error = null;
$saved = isset($_GET['saved']);

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $data = [
        'user_code' => $existing['user_code'] ?? trim((string) ($_POST['user_code'] ?? '')),
        'full_name' => (string) ($_POST['full_name'] ?? ''),
        'role' => (int) ($_POST['role'] ?? Auth::ROLE_EXPERT),
        'unit' => $_POST['unit'] ?? null,
        'permissions' => (isset($_POST['perm_inspect']) ? Auth::PERM_INSPECT : 0)
            | (isset($_POST['perm_report']) ? Auth::PERM_REPORT : 0),
        'county' => (string) ($_POST['county'] ?? ''),
        'phone' => (string) ($_POST['phone'] ?? ''),
        'device_code' => (string) ($_POST['device_code'] ?? ''),
        'password' => (string) ($_POST['password'] ?? ''),
        'active' => isset($_POST['active']) ? 1 : 0,
        'note' => (string) ($_POST['note'] ?? ''),
    ];
    // مدیر نمی‌تواند حساب خودش را غیرفعال یا از مدیری خارج کند: آن وقت دیگر
    // کسی نمی‌ماند که برگرداندش.
    if ($existing !== null && $existing['id'] === $manager['id']) {
        $data['role'] = Auth::ROLE_MANAGER;
        $data['active'] = 1;
    }
    $self = $existing !== null && $existing['id'] === $manager['id'];
    if ($existing === null && Db::one('SELECT id FROM users WHERE user_code = ?', [$data['user_code']]) !== null) {
        $error = 'این کد کاربری از قبل وجود دارد.';
    } elseif ($data['permissions'] !== 0 && $data['role'] !== Auth::ROLE_FIELD) {
        // مجوز فقط روی نقش میدانی معنا دارد و برای نقش‌های دیگر ذخیره نمی‌شد؛
        // «ذخیره شد» گفتن و دور ریختن تیک‌ها همان چیزی بود که مدیر را گیج کرد.
        $error = $self
            ? 'حساب خود شما «مدیر» است و نقشش عوض نمی‌شود. برای اپ بازرسی میدانی یک کاربر جدا با نقش «کاربر میدانی» بسازید و با همان در اپ وارد شوید.'
            : 'مجوز بازرسی و گزارش فقط برای نقش «کاربر میدانی» است. نقش این کاربر «'
              . (ROLE_NAMES[$data['role']] ?? '—') . '» است؛ یا نقش را «کاربر میدانی» کنید یا تیک‌ها را بردارید.';
    } else {
        $error = Users::problem($data);
    }
    if ($error === null) {
        Users::save($data, (string) $manager['id']);
        header('Location: user.php?code=' . urlencode((string) $data['user_code']) . '&saved=1');
        exit;
    }
    $existing = array_merge($existing ?? [], $data);
}

$value = static fn (string $key): string => e((string) ($existing[$key] ?? ''));
$role = (int) ($existing['role'] ?? Auth::ROLE_FIELD);
$permissions = (int) ($existing['permissions'] ?? 0);

admin_header($existing === null ? 'کاربر جدید' : 'ویرایش کاربر');
?>
<div class="card">
  <h2><?= $existing === null || !isset($existing['id']) ? 'کاربر جدید' : e($existing['full_name']) ?></h2>
  <?php if ($saved): ?><p class="notice">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post">
    <?= csrf_field() ?>
    <div class="grid">
      <label>کد کاربری
        <input name="user_code" value="<?= $value('user_code') ?>" dir="ltr" required
               <?= isset($existing['id']) ? 'readonly' : '' ?>>
      </label>
      <label>نام و نام خانوادگی
        <input name="full_name" value="<?= $value('full_name') ?>" required>
      </label>
      <label>نقش
        <select name="role">
          <?php foreach (ROLE_NAMES as $v => $name): ?>
            <option value="<?= $v ?>" <?= $role === $v ? 'selected' : '' ?>><?= e($name) ?></option>
          <?php endforeach; ?>
        </select>
      </label>
      <label>واحد (فقط برای نقش واحد مقصد)
        <select name="unit">
          <option value="">—</option>
          <?php foreach (UNIT_NAMES as $v => $name): ?>
            <option value="<?= $v ?>" <?= (string) ($existing['unit'] ?? '') === (string) $v ? 'selected' : '' ?>><?= e($name) ?></option>
          <?php endforeach; ?>
        </select>
      </label>
      <label>شهرستان
        <input name="county" value="<?= $value('county') ?>">
      </label>
      <label>تلفن همراه
        <input name="phone" value="<?= $value('phone') ?>" dir="ltr">
      </label>
      <label>کد دستگاه (فقط کارشناس — قفل حساب به همان گوشی)
        <input name="device_code" value="<?= $value('device_code') ?>" dir="ltr">
      </label>
      <label><?= isset($existing['id']) ? 'رمز تازه (خالی یعنی بدون تغییر)' : 'رمز عبور' ?>
        <input name="password" type="password" autocomplete="new-password" minlength="8">
      </label>
    </div>

    <h3 style="margin-top:16px">مجوز کاربر میدانی</h3>
    <p class="muted">فقط برای نقش «کاربر میدانی». گزینه‌ای که فعال نباشد در اپ خاکستری و قفل دیده می‌شود
      و سرور هم ارسالش را نمی‌پذیرد.</p>
    <?php if (isset($existing['id']) && $role !== Auth::ROLE_FIELD): ?>
      <p class="error">نقش این کاربر «<?= e(ROLE_NAMES[$role] ?? '—') ?>» است، پس با این حساب نمی‌شود وارد اپ
        بازرسی میدانی شد و این تیک‌ها برایش ذخیره نمی‌شود. برای اپ میدانی، نقش را «کاربر میدانی» کنید یا یک
        کاربر جدا بسازید.</p>
    <?php endif; ?>
    <label class="check"><input type="checkbox" name="perm_inspect" <?= ($permissions & Auth::PERM_INSPECT) !== 0 ? 'checked' : '' ?>>
      بازرسی (ترموویژن و آمپرگیری فیدر)</label>
    <label class="check"><input type="checkbox" name="perm_report" <?= ($permissions & Auth::PERM_REPORT) !== 0 ? 'checked' : '' ?>>
      گزارش (رمزارز و برق غیرمجاز)</label>

    <label class="check"><input type="checkbox" name="active" <?= (int) ($existing['active'] ?? 1) === 1 ? 'checked' : '' ?>>
      حساب فعال است</label>
    <label>یادداشت
      <textarea name="note" rows="2"><?= $value('note') ?></textarea>
    </label>
    <button type="submit">ذخیره</button>
  </form>
  <p class="muted">غیرفعال کردن یا عوض کردن رمز، نشست‌های باز همان کاربر را همان لحظه می‌بندد.</p>
</div>
<?php portal_footer();
