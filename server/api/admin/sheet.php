<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_item_view.php';

admin_require(ADMIN_ROLES);

// برگه ارجاع برای اکیپ: چاپی، و با «ذخیره به PDF» مرورگر همان فایل PDF است.
// سند اداری است؛ نام نرم‌افزار در آن نیست.
$id = (string) ($_GET['id'] ?? '');
$item = Field::isUuid($id) ? Db::one(
    'SELECT i.*, u.full_name AS reporter, a.full_name AS expert
     FROM field_items i JOIN users u ON u.id = i.user_id LEFT JOIN users a ON a.id = i.assigned_to WHERE i.id = ?',
    [$id]
) : null;
if ($item === null || !admin_scope()->canSeeField($item)) {
    http_response_code(404);
    exit('مورد پیدا نشد.');
}
$payload = json_decode((string) $item['payload'], true);
$rows = payload_rows((int) $item['kind'], is_array($payload) ? $payload : []);
$settings = FieldSettings::all();
// به اکیپ نام گزارش‌دهنده فقط وقتی گفته می‌شود که مدیر خواسته باشد.
$reporter = (int) $settings['show_reporter_to_team'] === 1 ? (string) $item['reporter'] : 'گزارش همکار';
$lat = $item['latitude'] === null ? null : (float) $item['latitude'];
$lon = $item['longitude'] === null ? null : (float) $item['longitude'];
$geo = $lat === null || $lon === null ? null : sprintf('geo:%.6f,%.6f?q=%.6f,%.6f', $lat, $lon, $lat, $lon);
$info = [
    ['کد رهگیری', (string) $item['tracking_code']],
    ['نوع', FieldAdmin::KIND_NAMES[(int) $item['kind']] ?? '—'],
    ['اولویت', $item['priority'] === null ? '—' : (FieldAdmin::PRIORITY_NAMES[(int) $item['priority']] ?? '—')],
    ['تاریخ ثبت', Jalali::format((int) $item['created_at'])],
    ['گزارش‌دهنده', $reporter],
    ['ارجاع به', (string) ($item['expert'] ?? '—')],
    ['مهلت اعلام نتیجه', $item['due_at'] === null ? 'ندارد' : Jalali::format((int) $item['due_at'], false)],
    ['آدرس', (string) ($item['address'] ?? '—')],
    ['پلاک', (string) ($item['plate'] ?? '')],
    ['مختصات', $lat === null ? '—' : sprintf('%.6f, %.6f', $lat, $lon)],
    ['شرح', (string) ($item['description'] ?? '')],
];
?><!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>برگه ارجاع <?= e($item['tracking_code']) ?></title>
<style>
  @page { size: A4; margin: 14mm; }
  body { font-family: "Vazirmatn", "IRANSans", Tahoma, sans-serif; color: #000; margin: 0 auto; max-width: 190mm;
         line-height: 1.7; font-size: 11pt; }
  h1 { font-size: 15pt; margin: 0 0 4px; }
  .top { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;
         border-bottom: 2px solid #000; padding-bottom: 8px; margin-bottom: 10px; }
  .codes { display: flex; gap: 14px; text-align: center; font-size: 8.5pt; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
  th, td { border: 1px solid #555; padding: 5px 8px; text-align: right; vertical-align: top; }
  th { width: 30%; background: #eee; font-weight: normal; }
  h2 { font-size: 12pt; margin: 12px 0 6px; }
  .box { border: 1px solid #555; min-height: 70px; padding: 6px 8px; }
  .signs { display: flex; gap: 10px; } .signs .box { flex: 1; min-height: 60px; }
  .ltr { direction: ltr; text-align: right; }
  .actions { margin: 14px 0; }
  .actions button { font: inherit; padding: 8px 18px; }
  @media print { .actions { display: none; } }
</style>
</head>
<body>
<div class="actions"><button onclick="window.print()">چاپ یا ذخیره PDF</button> <a href="item.php?id=<?= e($id) ?>">بازگشت</a></div>
<div class="top">
  <div>
    <h1>برگه ارجاع بازدید میدانی</h1>
    <div class="ltr"><strong><?= e($item['tracking_code']) ?></strong></div>
    <div>تاریخ چاپ: <?= e(Jalali::format(Db::now(), false)) ?></div>
  </div>
  <div class="codes">
    <?php if ($geo !== null): ?><div><?= Qr::svg($geo, 110) ?><br>مسیریابی</div><?php endif; ?>
    <div><?= Qr::svg(Field::qrPayload($id), 110) ?><br>اپ کارشناس</div>
  </div>
</div>
<table>
  <?php foreach ($info as [$label, $value]): if (trim($value) === '') { continue; } ?>
    <tr><th><?= e($label) ?></th><td<?= $label === 'کد رهگیری' || $label === 'مختصات' ? ' class="ltr"' : '' ?>><?= nl2br(e($value)) ?></td></tr>
  <?php endforeach; ?>
</table>
<?php if ($rows !== []): ?>
<h2>جزئیات گزارش</h2>
<table>
  <?php foreach ($rows as [$label, $value]): ?><tr><th><?= e($label) ?></th><td><?= nl2br(e($value)) ?></td></tr><?php endforeach; ?>
</table>
<?php endif; ?>
<h2>نتیجه بازدید</h2>
<div class="box"></div>
<h2>اقدامات انجام‌شده</h2>
<div class="box"></div>
<div class="signs" style="margin-top:12px">
  <div class="box">نام و امضای سرپرست اکیپ</div>
  <div class="box">نام و امضای همراهان</div>
  <div class="box">تاریخ و ساعت بازدید</div>
</div>
</body>
</html>
