<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_crew.php';

admin_require();

// اکسل عملکرد اکیپ با همان بازه و عضو صفحه.
$period = crew_period();
$member = trim((string) ($_GET['member'] ?? ''));
$report = crew_report($period, $member);
Xlsx::sendBook('crew-' . str_replace('-', '', $period['from_day']) . '-' . str_replace('-', '', $period['to_day']),
    CrewSheets::book($report, CrewReport::approved($period['from_day'], $period['to_day'])));
