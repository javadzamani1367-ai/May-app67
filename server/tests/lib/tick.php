<?php
declare(strict_types=1);

/**
 * برای تست یکپارچه: کارهای زمان‌دار گردش پرونده را در یک لحظه دلخواه اجرا می‌کند،
 * روی همان پایگاه داده آزمایشی (INSPECTION_CONFIG).
 *
 *     php tick.php <میلی‌ثانیه یونیکس>
 */
$lib = __DIR__ . '/../../api/lib/';
foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'Storage', 'Notifications', 'Jalali', 'Field', 'FieldSettings',
          'CaseLabels', 'Areas', 'Scope', 'CrewCalendar', 'CaseIntake', 'CaseFlow', 'FlowClock', 'FieldFlowClock',
          'UnitStages', 'FieldAdmin'] as $class) {
    require_once $lib . $class . '.php';
}
FlowClock::tick((int) ($argv[1] ?? Db::now()));
echo "ok\n";
