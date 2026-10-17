<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_cases_view.php';

admin_require(CASE_ROLES);

// صف تأیید پرونده‌ها، مثل صفحه «تأییدها» در اپ مدیر: قدیمی‌ترین درخواست اول.
// تصمیم در صفحه خود پرونده گرفته می‌شود، جایی که همه بخش‌ها و تصاویرش پیداست.
$pending = CaseActions::pendingApprovals(admin_scope());
$recent = CaseActions::recentDecisions(30, admin_scope());
$now = Db::now();

admin_header('تأییدها');
echo case_styles();
?>
<div class="card">
  <h2>در انتظار تأیید (<?= fa_digits((string) count($pending)) ?>)</h2>
  <p class="muted">پرونده‌هایی که کارشناس برای تأیید فرستاده. با باز کردن پرونده، پایین صفحه تأیید یا برگشت برای اصلاح را ثبت کنید؛
    کارشناس همان لحظه اعلان می‌گیرد.</p>
  <?php if ($pending === []): ?>
    <p class="muted">درخواستی در انتظار نیست.</p>
  <?php else: ?>
  <table>
    <tr><th>کد رهگیری</th><th>نوع</th><th>شهرستان</th><th>کارشناس</th><th>فرستاده‌شده</th><th>انتظار</th><th>یادداشت کارشناس</th></tr>
    <?php foreach ($pending as $row): $days = intdiv($now - (int) $row['submitted_at'], 86_400_000); ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="case.php?id=<?= e($row['report_id']) ?>#approval"><?= e(CaseLabels::code($row) ?: '—') ?></a></td>
        <td><?= case_type_tag((int) $row['report_type']) ?></td>
        <td><?= e(CaseLabels::countyWithArea($row['county'], $row['area_code'])) ?></td>
        <td><?= e($row['submitted_by_name'] ?? Jalali::digits((string) $row['expert_code'])) ?></td>
        <td class="nw"><?= e(Jalali::format((int) $row['submitted_at'])) ?></td>
        <td><span class="tag <?= $days >= 3 ? 'tag-overdue' : 'tag-seen' ?>"><?= fa_digits((string) $days) ?> روز</span></td>
        <td><?= e(mb_substr((string) $row['comment'], 0, 120)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
  <?php endif; ?>
</div>

<?php if ($recent !== []): ?>
<div class="card">
  <h3>آخرین تصمیم‌ها</h3>
  <table>
    <tr><th>کد رهگیری</th><th>تصمیم</th><th>زمان</th><th>مدیر</th><th>توضیح</th></tr>
    <?php foreach ($recent as $row): $ok = (int) $row['decision'] === CaseActions::APPROVED; ?>
      <tr>
        <td dir="ltr" style="text-align:right"><a href="case.php?id=<?= e($row['report_id']) ?>"><?= e(CaseLabels::code($row) ?: '—') ?></a></td>
        <td><span class="tag <?= $ok ? 'tag-answered' : 'tag-overdue' ?>"><?= $ok ? 'تأیید شد' : 'برگشت برای اصلاح' ?></span></td>
        <td class="nw"><?= e(Jalali::format($row['decided_at'] === null ? null : (int) $row['decided_at'])) ?></td>
        <td><?= e($row['decided_by_name'] ?? '—') ?></td>
        <td><?= e(mb_substr((string) $row['comment'], 0, 160)) ?></td>
      </tr>
    <?php endforeach; ?>
  </table>
</div>
<?php endif; ?>
<?php portal_footer();
