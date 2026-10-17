<?php
declare(strict_types=1);

/**
 * عملکرد اکیپ در اپ مدیر: همان گزارش صفحه crew.php پنل وب، اضافه‌کار تأییدشده،
 * و همان اکسل و Word. بازه را گوشی به یونیکس میلی‌ثانیه می‌فرستد (آغاز روز اول
 * و پایان روز آخر به وقت ایران). فقط نقش مدیر.
 */
final class CrewController
{
    public function report(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR]));
        [$from, $to] = self::range($request);
        $report = CrewReport::build($from, $to, $scope->isManager() ? null : $scope->areas);
        $approved = CrewReport::approved(CrewCalendar::day($from), CrewCalendar::day($to));
        $members = array_map(static function (array $m) use ($approved): array {
            $m['approved'] = $approved[$m['key']] ?? null;
            $m['days'] = array_map(static function (array $d): array {
                unset($d['visit_list']);
                $d['points'] = array_map(static fn (array $p): array => array_intersect_key($p,
                    array_flip(['at', 'type', 'lat', 'lon', 'plate', 'inspection'])), $d['points']);
                return $d;
            }, $m['days']);
            return $m;
        }, $report['members']);
        $visits = array_map(static fn (array $v): array => array_intersect_key($v,
            array_flip(['n', 'label', 'first', 'last', 'inspections', 'lat', 'lon', 'place'])), CrewMap::collect($report['members'])['visits']);
        Response::json(['can_approve' => $scope->isManager(), 'members' => $members, 'visits' => $visits, 'geo' => $report['geo'], 'from' => $from, 'to' => $to,
            'type_names' => CrewWork::TYPE_NAMES,
            'bucket_names' => array_map(static fn (array $b): string => $b[1], CrewCalendar::BUCKETS)]);
    }

    public function overtime(Request $request): void
    {
        $manager = Auth::require($request, Auth::ROLE_MANAGER);
        [$from, $to] = self::range($request);
        $error = CrewReport::saveApproved($request->str('member_key'), CrewCalendar::day($from), CrewCalendar::day($to),
            $request->str('hours'), $request->str('note'), $manager['id'] ?? null);
        if ($error !== null) {
            Response::fail(422, 'invalid', $error);
        }
        Response::json(['saved' => true]);
    }

    /** اکسل (format=xlsx) یا Word (format=docx)؛ با member فقط همان نفر. */
    public function export(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR]));
        [$from, $to] = self::range($request);
        $report = CrewReport::build($from, $to, $scope->isManager() ? null : $scope->areas);
        $member = $request->str('member');
        if ($member !== '') {
            $report['members'] = array_values(array_filter($report['members'], static fn (array $m): bool => $m['key'] === $member));
        }
        $approved = CrewReport::approved(CrewCalendar::day($from), CrewCalendar::day($to));
        $name = 'crew-' . str_replace('-', '', CrewCalendar::day($from)) . '-' . str_replace('-', '', CrewCalendar::day($to));
        if ($request->str('format') === 'docx') {
            $map = tempnam(sys_get_temp_dir(), 'crewmap');
            $hasMap = CrewMap::jpeg($map, CrewMap::collect($report['members']), GeoPlaces::load(null, Storage::root() . '/cache'));
            Docx::send($name, CrewDocx::blocks($report, $approved, $hasMap ? $map : null, CrewCalendar::fromSettings()));
            @unlink($map);
        } else {
            Xlsx::sendBook($name, CrewSheets::book($report, $approved));
        }
        exit;
    }

    /** @return array{0:int, 1:int} بازه، حداکثر یک سال؛ بی بازه، از ۳۰ روز پیش تا اکنون. */
    private static function range(Request $request): array
    {
        $to = $request->int('to') ?? Db::now();
        $from = $request->int('from') ?? $to - 30 * 86_400_000;
        if ($to < $from) {
            [$from, $to] = [$to, $from];
        }
        return [max($from, $to - 366 * 86_400_000), $to];
    }
}
