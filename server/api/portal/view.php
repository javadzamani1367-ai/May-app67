<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

$user = portal_require_user();
$unit = (int) $user['unit'];
$id = (string) ($_GET['id'] ?? '');

$dispatch = Db::one(
    'SELECT d.*, r.tracking_code, r.county, r.district, r.address, r.owner_name,
            r.subscription_number, r.total_watt, r.description, r.actions_taken
     FROM dispatches d JOIN reports r ON r.id = d.report_id
     WHERE d.id = ? AND d.unit = ?',
    [$id, $unit]
);
if ($dispatch === null) {
    http_response_code(404);
    portal_header('یافت نشد');
    echo '<div class="card"><p class="error">این ارسال برای واحد شما نیست.</p></div>';
    portal_footer();
    exit;
}

// باز کردن یعنی دیده شد. تاریخ اولین بازکردن نگه داشته می‌شود، نه آخرین.
Db::run(
    'UPDATE dispatches SET status = GREATEST(status, 1), seen_at = COALESCE(seen_at, ?) WHERE id = ?',
    [Db::now(), $id]
);

$error = null;
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $body = trim((string) ($_POST['body'] ?? ''));
    if ($body === '' && empty($_FILES['files']['name'][0])) {
        $error = 'متن پاسخ یا دست‌کم یک مدرک لازم است.';
    } else {
        $now = Db::now();
        $responseId = Db::uuid();
        Db::run(
            'INSERT INTO dispatch_responses (id, dispatch_id, responder_id, body, created_at)
             VALUES (?, ?, ?, ?, ?)',
            [$responseId, $id, $user['id'], $body, $now]
        );

        // فایل‌های چندتایی در PHP به صورت آرایه‌ای از ستون‌ها می‌آیند، نه
        // آرایه‌ای از فایل‌ها؛ این حلقه آن را به شکل معمول برمی‌گرداند.
        $names = $_FILES['files']['name'] ?? [];
        foreach (array_keys((array) $names) as $index) {
            $file = [
                'name' => $_FILES['files']['name'][$index] ?? '',
                'type' => $_FILES['files']['type'][$index] ?? '',
                'tmp_name' => $_FILES['files']['tmp_name'][$index] ?? '',
                'error' => $_FILES['files']['error'][$index] ?? UPLOAD_ERR_NO_FILE,
                'size' => $_FILES['files']['size'][$index] ?? 0,
            ];
            if ((int) $file['error'] !== UPLOAD_ERR_OK) {
                continue;
            }
            $stored = Storage::put($file, 'responses/' . date('Y/m'));
            Db::run(
                'INSERT INTO response_files (id, response_id, file_path, file_name, mime_type,
                                             size_bytes, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)',
                [Db::uuid(), $responseId, $stored['path'], (string) $file['name'],
                 (string) $file['type'], $stored['size'], $now]
            );
        }

        Db::run(
            'UPDATE dispatches SET status = 2, answered_at = COALESCE(answered_at, ?) WHERE id = ?',
            [$now, $id]
        );
        Notifications::toRole(Auth::ROLE_MANAGER, 'response', 'پاسخ واحد ثبت شد',
            unit_name($unit) . ' برای پرونده ' . ($dispatch['tracking_code'] ?? '') . ' پاسخ ثبت کرد.',
            (string) $dispatch['report_id']);

        header('Location: view.php?id=' . urlencode($id) . '&saved=1');
        exit;
    }
}

$responses = Db::all(
    'SELECT r.*, u.full_name AS responder_name FROM dispatch_responses r
     LEFT JOIN users u ON u.id = r.responder_id
     WHERE r.dispatch_id = ? ORDER BY r.created_at',
    [$id]
);
$items = json_decode((string) $dispatch['included_items'], true);
$files = Db::all(
    'SELECT id, file_path, caption AS title, "media" AS kind FROM media WHERE report_id = ?
     UNION ALL
     SELECT id, file_path, title, "attachment" AS kind FROM attachments WHERE report_id = ?',
    [$dispatch['report_id'], $dispatch['report_id']]
);
$selected = is_array($items) ? $items : [];

portal_header('پرونده ' . (string) $dispatch['tracking_code']);
?>
<div class="card">
  <h2>پرونده <?= fa_digits(e($dispatch['tracking_code'] ?? '—')) ?></h2>
  <table>
    <tr><th>شهرستان</th><td><?= e($dispatch['county'] ?? '—') ?></td></tr>
    <tr><th>آدرس</th><td><?= e($dispatch['address'] ?? '—') ?></td></tr>
    <tr><th>مالک یا متصرف</th><td><?= e($dispatch['owner_name'] ?? '—') ?></td></tr>
    <tr><th>شماره اشتراک</th><td><?= fa_digits(e($dispatch['subscription_number'] ?? '—')) ?></td></tr>
    <tr><th>تاریخ ارسال</th><td><?= e(jalali((int) $dispatch['dispatched_at'])) ?></td></tr>
    <tr><th>مهلت اعلام نتیجه</th>
        <td><?= $dispatch['deadline_at'] === null ? '—' : e(jalali((int) $dispatch['deadline_at'])) ?></td></tr>
  </table>
  <?php if (!empty($dispatch['note'])): ?>
    <p><strong>توضیح ارسال:</strong> <?= nl2br(e($dispatch['note'])) ?></p>
  <?php endif; ?>
</div>

<div class="card">
  <h3>مدارک این ارسال</h3>
  <?php
  $sent = array_values(array_filter($files, static fn(array $f): bool => in_array($f['id'], $selected, true)));
  if ($sent === []): ?>
    <p class="muted">فایلی همراه این ارسال ثبت نشده است.</p>
  <?php else: ?>
    <ul>
      <?php foreach ($sent as $file): ?>
        <li><a href="file.php?id=<?= e((string) $file['id']) ?>&amp;kind=<?= e((string) $file['kind']) ?>">
          <?= e($file['title'] ?: 'فایل بدون عنوان') ?></a></li>
      <?php endforeach; ?>
    </ul>
  <?php endif; ?>
</div>

<div class="card">
  <h3>پاسخ و اقدامات</h3>
  <?php if (isset($_GET['saved'])): ?><p class="tag tag-answered">پاسخ شما ثبت شد.</p><?php endif; ?>
  <?php if ($error !== null): ?><p class="error"><?= e($error) ?></p><?php endif; ?>
  <?php foreach ($responses as $response): ?>
    <div style="border-top:1px solid #d8dee9; padding-top:10px; margin-top:10px">
      <p class="muted"><?= e($response['responder_name'] ?? '') ?> — <?= e(jalali((int) $response['created_at'])) ?></p>
      <p><?= nl2br(e($response['body'] ?? '')) ?></p>
      <?php foreach (Db::all('SELECT * FROM response_files WHERE response_id = ?', [$response['id']]) as $rf): ?>
        <a href="file.php?id=<?= e((string) $rf['id']) ?>&amp;kind=response"><?= e($rf['file_name']) ?></a><br>
      <?php endforeach; ?>
    </div>
  <?php endforeach; ?>

  <form method="post" enctype="multipart/form-data">
    <label>شرح اقدام انجام‌شده
      <textarea name="body" rows="5"></textarea>
    </label>
    <label>مستندات اقدام (تصویر، PDF، ورد — چند فایل قابل انتخاب است)
      <input type="file" name="files[]" multiple>
    </label>
    <button type="submit">ثبت پاسخ</button>
  </form>
</div>
<?php portal_footer();
