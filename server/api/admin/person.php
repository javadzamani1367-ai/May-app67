<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_person_view.php';

$manager = admin_require();

// یک شخص: مشخصات (کد ملی معیار یکتا)، نقش‌هایش در هر ناحیه با کد و رمز جدا، کد نصب
// هر اپ، و کارش در هر شهرستان. قاعده‌ها در Persons و PersonAccounts، مشترک با اپ مدیر.
$id = (string) ($_GET['id'] ?? '');
$person = $id === '' ? null : Persons::find($id);
if ($id !== '' && $person === null) {
    http_response_code(404);
    exit('شخص پیدا نشد.');
}
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $action = (string) ($_POST['action'] ?? '');
    $made = null;
    if ($action === 'person') {
        $result = Persons::save([
            'full_name' => $_POST['full_name'] ?? '', 'national_id' => $_POST['national_id'] ?? '', 'phone' => $_POST['phone'] ?? '',
            'home_area' => $_POST['home_area'] ?? '', 'note' => $_POST['note'] ?? '', 'auto_bind' => isset($_POST['auto_bind']) ? 1 : 0,
            'active' => isset($_POST['active']) || $person === null ? 1 : 0,
        ], $person === null ? null : $id);
        if (is_array($result)) {
            $error = $result['error'];
            $existing = $result['person_id'] ?? null;
        } else {
            header('Location: person.php?id=' . urlencode($result) . '&saved=1');
            exit;
        }
    } elseif ($person !== null && $action === 'accounts') {
        $perms = [];
        foreach ((array) ($_POST['perm'] ?? []) as $key => $bits) {
            $perms[(string) $key] = array_sum(array_map('intval', (array) $bits));
        }
        foreach ((array) ($_POST['tick'] ?? []) as $tick) {
            if (str_contains((string) $tick, ':') && !isset($perms[(string) $tick])) {
                $perms[(string) $tick] = 0;
            }
        }
        $result = PersonAccounts::apply($id, array_map('strval', (array) ($_POST['tick'] ?? [])), $perms, $manager);
        $error = $result['error'];
        $made = $result['made'];
    } elseif ($person !== null && $action === 'password') {
        $account = Db::one('SELECT user_code, areas, role, unit, crew_role FROM users WHERE id = ? AND person_id = ?',
            [(string) ($_POST['user_id'] ?? ''), $id]);
        $password = $account === null ? null : PersonAccounts::resetPassword((string) $_POST['user_id'], $manager);
        if ($password !== null) {
            $made = [['user_code' => $account['user_code'], 'password' => $password, 'kind' => Persons::kindName(Persons::kindOf($account)),
                'area' => Areas::label(Areas::parse($account['areas'] ?? '')[0] ?? null) ?: 'استانی', 'full_name' => $person['full_name']]];
        }
    } elseif ($person !== null && $action === 'device') {
        $error = PersonDevices::set($id, (string) ($_POST['app'] ?? ''), (string) ($_POST['device_code'] ?? ''), (string) $manager['id']);
    } elseif ($person !== null && $action === 'attach') {
        $error = PersonAccounts::attach((string) ($_POST['user_code'] ?? ''), $id);
    }
    if ($error === null && $person !== null) {
        // رمزهای یک‌بارمصرف فقط همین یک بار، در همان صفحه بعد، دیده می‌شوند.
        $_SESSION['person_made'] = $made ?? [];
        header('Location: person.php?id=' . urlencode($id) . '&done=' . urlencode($action));
        exit;
    }
}
$made = $_SESSION['person_made'] ?? [];
unset($_SESSION['person_made']);
$accounts = $person === null ? [] : Persons::accounts($id);
$devices = $person === null ? [] : PersonDevices::of($id);
$fromText = trim((string) ($_GET['from'] ?? ''));
$toText = trim((string) ($_GET['to'] ?? ''));
$to = ($p = Jalali::parse($toText)) === null ? Db::now() : Jalali::startOfDay(...$p) + 86_400_000 - 1;
$from = ($p = Jalali::parse($fromText)) === null ? $to - 90 * 86_400_000 : Jalali::startOfDay(...$p);
$work = $person === null ? [] : PersonWork::of($id, $from, $to);
$value = static fn (string $k) => e((string) ($_POST[$k] ?? $person[$k] ?? ''));

admin_header($person === null ? 'شخص تازه' : (string) $person['full_name']);
?>
<div class="card">
  <div class="row">
    <h2><?= $person === null ? 'شخص تازه' : e((string) $person['full_name']) . ' <span class="muted">— شماره ' . fa_digits((string) $person['number']) . '</span>' ?></h2>
    <a href="persons.php"><button type="button" style="background:#64748b">بازگشت به فهرست</button></a>
  </div>
  <?php if (isset($_GET['saved']) || isset($_GET['done'])): ?><p class="notice">ذخیره شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?>
    <?php if (!empty($existing)): ?> <a href="person.php?id=<?= e((string) $existing) ?>">صفحه همان شخص</a><?php endif; ?></p><?php endif; ?>
  <?php if ($made !== []): ?>
    <div class="card" style="border:2px solid #b45309">
      <h3>کد و رمز کاربری‌های تازه — فقط همین یک بار دیده می‌شود</h3>
      <table><tr><th>ناحیه</th><th>نقش</th><th>کد کاربری</th><th>رمز</th></tr>
        <?php foreach ($made as $m): ?>
          <tr><td><?= e($m['area']) ?></td><td><?= e($m['kind']) ?></td>
            <td dir="ltr" style="text-align:right"><code><?= e($m['user_code']) ?></code></td>
            <td dir="ltr" style="text-align:right"><code><?= e($m['password']) ?></code></td></tr>
        <?php endforeach; ?></table>
      <button type="button" onclick="window.print()">چاپ</button>
    </div>
  <?php endif; ?>
  <form method="post">
    <?= csrf_field() ?><input type="hidden" name="action" value="person">
    <div class="grid">
      <label>نام و نام خانوادگی<input name="full_name" value="<?= $value('full_name') ?>" required></label>
      <label>کد ملی<input name="national_id" value="<?= $value('national_id') ?>" dir="ltr" inputmode="numeric" maxlength="10"></label>
      <label>تلفن همراه<input name="phone" value="<?= $value('phone') ?>" dir="ltr"></label>
      <label>محل خدمت اصلی<select name="home_area"><option value="">—</option>
        <?php foreach (Areas::options() as $code => $label): ?>
          <option value="<?= e((string) $code) ?>" <?= (string) ($person['home_area'] ?? '') === (string) $code ? 'selected' : '' ?>><?= e($label) ?></option>
        <?php endforeach; ?></select></label>
    </div>
    <label>یادداشت<textarea name="note" rows="2"><?= $value('note') ?></textarea></label>
    <?php if ($person !== null): ?>
      <label class="check"><input type="checkbox" name="active" <?= (int) $person['active'] === 1 ? 'checked' : '' ?>> فعال
        <span class="muted">(غیرفعال شدن شخص همه کاربری‌هایش را می‌بندد)</span></label>
      <label class="check"><input type="checkbox" name="auto_bind" <?= (int) $person['auto_bind'] === 1 ? 'checked' : '' ?>> اولین نصب هر اپ
        خودکار ثبت شود <span class="muted">(برای اشخاصی که پیش از قفل دستگاه ثبت شده‌اند)</span></label>
    <?php endif; ?>
    <button type="submit"><?= $person === null ? 'ثبت شخص' : 'ذخیره مشخصات' ?></button>
  </form>
</div>

<?php if ($person !== null): ?>
<div class="card">
  <h3>ناحیه‌ها و نقش‌ها</h3>
  <?= person_matrix($accounts) ?>
</div>

<div class="card">
  <h3>کاربری‌ها (<?= fa_digits((string) count($accounts)) ?>)</h3>
  <?php if ($accounts === []): ?><p class="muted">هنوز کاربری‌ای ندارد.</p><?php else: ?>
  <table><tr><th>ناحیه</th><th>نقش</th><th>کد کاربری</th><th>وضعیت</th><th></th></tr>
    <?php foreach ($accounts as $a): ?>
      <tr><td><?= e($a['area_label']) ?></td><td><?= e($a['kind_name']) ?></td>
        <td dir="ltr" style="text-align:right"><code><?= e($a['user_code']) ?></code></td>
        <td><?= (int) $a['active'] === 1 ? '<span class="tag tag-answered">فعال</span>' : '<span class="tag tag-overdue">غیرفعال</span>' ?></td>
        <td><form method="post" style="margin:0" onsubmit="return confirm('رمز تازه ساخته شود؟ رمز قبلی دیگر کار نمی‌کند.')">
          <?= csrf_field() ?><input type="hidden" name="action" value="password"><input type="hidden" name="user_id" value="<?= e($a['id']) ?>">
          <button type="submit" style="background:#64748b">رمز تازه</button></form></td></tr>
    <?php endforeach; ?></table>
  <?php endif; ?>
  <form method="post" class="row" style="justify-content:flex-start;gap:8px;margin-top:10px">
    <?= csrf_field() ?><input type="hidden" name="action" value="attach">
    <label style="margin:0">وصل کردن کاربری موجود<input name="user_code" dir="ltr" placeholder="کد کاربری"></label>
    <button type="submit" style="background:#7c3aed">وصل کن</button>
  </form>
</div>

<div class="card">
  <h3>کد نصب هر اپ</h3>
  <?= person_devices($id, $devices) ?>
</div>

<div class="card">
  <h3>کار در هر شهرستان</h3>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <input type="hidden" name="id" value="<?= e($id) ?>">
    <label style="margin:0">از<input name="from" value="<?= e($fromText ?: Jalali::format($from, false)) ?>"></label>
    <label style="margin:0">تا<input name="to" value="<?= e($toText ?: Jalali::format($to, false)) ?>"></label>
    <button type="submit">نمایش</button>
  </form>
  <?= person_work($work) ?>
</div>
<?php endif; ?>
<?php portal_footer();
