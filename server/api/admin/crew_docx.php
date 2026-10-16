<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_crew.php';

admin_require();

// گزارش Word عملکرد اکیپ برای مدیر و مدیرعامل، با نقشه کوچک بازدیدها.
$period = crew_period();
$member = trim((string) ($_GET['member'] ?? ''));
$report = crew_report($period, $member);
$map = tempnam(sys_get_temp_dir(), 'crewmap');
$hasMap = CrewMap::jpeg($map, CrewMap::collect($report['members']), GeoPlaces::load(null, Storage::root() . '/cache'));
$blocks = CrewDocx::blocks($report, CrewReport::approved($period['from_day'], $period['to_day']), $hasMap ? $map : null,
    CrewCalendar::fromSettings());
Docx::send('crew-' . str_replace('-', '', $period['from_day']) . '-' . str_replace('-', '', $period['to_day']), $blocks);
@unlink($map);
