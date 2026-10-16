<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// تعطیلات رسمی برای عملکرد اکیپ: جمعه‌ها و تعطیلات ثابت شمسی خودکار حساب می‌شوند؛
// تعطیلات قمری که هر سال جابه‌جا می‌شوند اینجا نوشته می‌شوند.
$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    csrf_check();
    $day = Jalali::parse((string) ($_POST['day'] ?? ''));
    if (($_POST['action'] ?? '') === 'delete' && $day !== null) {
        Db::run('DELETE FROM holidays WHERE day = ?', [sprintf('%04d-%02d-%02d', ...$day)]);
        header('Location: holidays.php');
        exit;
    }
    $title = mb_substr(trim((string) ($_POST['title'] ?? '')), 0, 191);
    if ($day === null) {
        $error = 'تاریخ را مثل ۱۴۰۵/۰۳/۲۵ بنویسید.';
    } elseif ($title === '') {
        $error = 'عنوان تعطیلی را بنویسید.';
    } else {
        Db::run('INSERT INTO holidays (day, title, created_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE title = VALUES(title)',
            [sprintf('%04d-%02d-%02d', ...$day), $title, Db::now()]);
        header('Location: holidays.php');
        exit;
    }
}
$rows = Db::all('SELECT day, title FROM holidays ORDER BY day DESC');
$show = static fn (string $day): string => fa_digits(str_replace('-', '/', $day));

admin_header('تعطیلات');
?>
<div class="card">
  <h2>تعطیلات رسمی</h2>
  <p class="muted">جمعه‌ها و تعطیلات ثابت شمسی (نوروز، ۱۲ و ۱۳ فروردین، ۱۴ و ۱۵ خرداد، ۲۲ بهمن، ۲۹ اسفند) خودکار
    حساب می‌شوند. تعطیلات قمری را هر سال اینجا بنویسید؛ کار اکیپ در این روزها اضافه‌کار و کار تعطیل حساب می‌شود.</p>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <form method="post" class="row" style="justify-content:flex-start;gap:12px">
    <?= csrf_field() ?>
    <label style="margin:0">تاریخ <input name="day" placeholder="۱۴۰۵/۰۳/۲۵" style="width:130px" required></label>
    <label style="margin:0">عنوان <input name="title" placeholder="عید فطر" style="width:220px" required></label>
    <button type="submit">افزودن</button>
  </form>
</div>
<div class="card">
  <?php if ($rows === []): ?>
    <p class="muted">هنوز تعطیلی نوشته نشده است.</p>
  <?php else: ?>
    <table>
      <tr><th>تاریخ</th><th>عنوان</th><th></th></tr>
      <?php foreach ($rows as $r): ?>
        <tr>
          <td><?= e($show((string) $r['day'])) ?></td>
          <td><?= e((string) $r['title']) ?></td>
          <td>
            <form method="post" style="margin:0">
              <?= csrf_field() ?>
              <input type="hidden" name="action" value="delete">
              <input type="hidden" name="day" value="<?= e((string) $r['day']) ?>">
              <button type="submit">حذف</button>
            </form>
          </td>
        </tr>
      <?php endforeach; ?>
    </table>
  <?php endif; ?>
</div>
<?php portal_footer();
