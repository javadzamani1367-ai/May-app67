<?php
declare(strict_types=1);

/**
 * برای Cron Job سی‌پنل (اختیاری): ارجاع خودکار و هشدارهای گردش پرونده را سر وقت
 * انجام می‌دهد، بی آنکه منتظر باز شدن پنل یا اپ‌ها بماند. نمونه دستور، هر ۱۵ دقیقه:
 *   php /home/USER/public_html/api/cron.php
 *
 * فقط از خط فرمان اجرا می‌شود؛ از مرورگر پاسخی نمی‌دهد.
 */
if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'FieldSchema', 'Migrations', 'Storage', 'Notifications', 'Jalali',
          'Field', 'FieldSettings', 'CaseLabels', 'Areas', 'Scope', 'CrewCalendar', 'CaseIntake', 'CaseFlow', 'FlowClock',
          'FieldFlowClock', 'DeviceRegistry'] as $class) {
    require_once __DIR__ . '/lib/' . $class . '.php';
}
Migrations::ensure();
FlowClock::tick(Db::now());
echo "ok\n";
