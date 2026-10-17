<?php
declare(strict_types=1);

/**
 * امتیاز ۱۰۰ ناحیه‌ها، مجریان، واحدهای مقصد و کارشناسان در یک بازه — از همان
 * ستون‌های گردش پرونده، تأییدها و ارسال‌ها که خط زمانی را می‌سازند. فرمول هر
 * جزء در Scores است. مدیر همه را می‌بیند، مجری فقط ناحیه‌های خودش را.
 */
final class ScoreBoard
{
    private array $flows;
    private array $decisions;
    private array $dispatches;
    private CrewCalendar $calendar;
    private int $executorDays;

    public function __construct(private readonly int $from, private readonly int $to, private readonly Scope $scope,
                                private readonly int $now)
    {
        $this->calendar = CrewCalendar::fromSettings();
        $this->executorDays = (int) FieldSettings::all()['executor_days'];
        $p = [$from, $to];
        $this->flows = Db::all(
            'SELECT f.*, r.area_code, r.expert_code, r.created_at AS report_created FROM case_flow f JOIN reports r ON r.id = f.report_id
             WHERE f.executor_at BETWEEN ? AND ? OR f.assigned_at BETWEEN ? AND ? OR f.visited_at BETWEEN ? AND ?
                OR f.approved_at BETWEEN ? AND ? OR f.dispatched_at BETWEEN ? AND ?',
            array_merge($p, $p, $p, $p, $p)
        );
        $this->decisions = Db::all(
            'SELECT a.decision, a.submitted_at, a.decided_at, r.area_code, r.expert_code FROM approvals a
             JOIN reports r ON r.id = a.report_id WHERE a.decision <> 0 AND a.decided_at BETWEEN ? AND ?', $p);
        $this->dispatches = Db::all('SELECT * FROM dispatches WHERE channel = 0 AND dispatched_at BETWEEN ? AND ?', $p);
    }

    /** همه جدول‌ها، هر کدام رتبه‌بندی‌شده. */
    public function all(): array
    {
        $users = Db::all('SELECT id, user_code, full_name, role, unit, areas FROM users WHERE active = 1 AND role IN (?, ?, ?)',
            [Auth::ROLE_EXPERT, Auth::ROLE_UNIT, Auth::ROLE_EXECUTOR]);
        $experts = $executors = $units = [];
        foreach ($users as $u) {
            $areas = Areas::parse($u['areas'] ?? '');
            $visible = $this->scope->isManager() || array_intersect($areas, $this->scope->areas) !== [];
            if (!$visible) {
                continue;
            }
            $label = $areas === [] ? 'همه استان' : implode('، ', array_map([Areas::class, 'label'], $areas));
            $base = ['id' => $u['id'], 'name' => $u['full_name'], 'areas' => $areas, 'area' => $label];
            match ((int) $u['role']) {
                Auth::ROLE_EXPERT => $experts[] = $base + $this->expert($u),
                Auth::ROLE_EXECUTOR => $executors[] = $base + $this->executor($areas, (string) $u['id']),
                default => $units[] = $base + ['unit' => UnitStages::NAMES[(int) $u['unit']] ?? '']
                    + $this->unit($this->dispatchesFor((int) $u['unit'], $areas)),
            };
        }
        $max = max([0, ...array_map(static fn ($e) => $e['counts']['visits'], $experts)]);
        foreach ($experts as &$e) {
            $e['parts']['volume'] = $max === 0 ? null : round($e['counts']['visits'] / $max * 100, 1);
            $e['score'] = Scores::combine(Scores::EXPERT, $e['parts']);
        }
        unset($e);
        $areas = [];
        foreach ($this->scope->isManager() ? array_keys(Areas::NAMES) : $this->scope->areas as $code) {
            $code = (string) $code;
            $areas[] = $this->area($code, $executors, $experts);
        }
        return ['areas' => Scores::rank($areas), 'executors' => Scores::rank($executors), 'units' => Scores::rank($units),
            'experts' => Scores::rank($experts)];
    }

    private function expert(array $u): array
    {
        $visits = $onTime = $resolved = 0;
        foreach ($this->flows as $f) {
            if ($f['expert_code'] === $u['user_code'] && $this->inside($f['visited_at'])) {
                $visits++;
            }
            if ($f['expert_id'] === $u['id'] && $this->inside($f['assigned_at']) && $f['due_at'] !== null) {
                $done = $f['visited_at'] !== null;
                if ($done || (int) $f['due_at'] < $this->now) {
                    $resolved++;
                    $onTime += $done && (int) $f['visited_at'] <= (int) $f['due_at'] ? 1 : 0;
                }
            }
        }
        $approved = $decided = 0;
        foreach ($this->decisions as $d) {
            if ($d['expert_code'] === $u['user_code']) {
                $decided++;
                $approved += (int) $d['decision'] === 1 ? 1 : 0;
            }
        }
        return ['parts' => ['timely' => Scores::ratio($onTime, $resolved), 'quality' => Scores::ratio($approved, $decided)],
            'counts' => ['visits' => $visits, 'assigned' => $resolved, 'on_time' => $onTime, 'decided' => $decided, 'approved' => $approved]];
    }

    /** @param list<string> $areas */
    private function executor(array $areas, string $id): array
    {
        $handled = $good = $judged = $alerted = 0;
        $hours = [];
        $approved = $sent = 0;
        foreach ($this->flows as $f) {
            if ($f['executor_id'] === $id && $this->inside($f['executor_at'])) {
                $handled++;
                $alerted += $f['manager_alert_at'] !== null ? 1 : 0;
                $deadline = $this->calendar->addWorkDays((int) $f['executor_at'], $this->executorDays);
                $assigned = $f['assigned_at'] === null ? null : (int) $f['assigned_at'];
                if ($assigned !== null || $deadline < $this->now) {
                    $judged++;
                    $good += $assigned !== null && $assigned <= $deadline ? 1 : 0;
                }
            }
            if (in_array((string) $f['area_code'], $areas, true) && $this->inside($f['approved_at'])) {
                $approved++;
                $sent += $f['dispatched_at'] !== null ? 1 : 0;
            }
        }
        foreach ($this->decisions as $d) {
            if (in_array((string) $d['area_code'], $areas, true)) {
                $hours[] = ((int) $d['decided_at'] - (int) $d['submitted_at']) / 3_600_000;
            }
        }
        $parts = [
            'refer' => Scores::ratio($good, $judged),
            'followup' => $handled === 0 ? null : round(100 - $alerted / $handled * 100, 1),
            'approval' => Scores::linear(Scores::median($hours), 24, 168),
            'closure' => Scores::ratio($sent, $approved),
        ];
        return ['parts' => $parts, 'score' => Scores::combine(Scores::EXECUTOR, $parts),
            'counts' => ['handled' => $handled, 'alerted' => $alerted, 'decided' => count($hours), 'approved' => $approved, 'sent' => $sent]];
    }

    /** @param list<string> $areas تهی یعنی واحد استانی */
    private function dispatchesFor(int $unit, array $areas): array
    {
        return array_values(array_filter($this->dispatches, static fn ($d) => (int) $d['unit'] === $unit
            && ($areas === [] || in_array((string) $d['area_code'], $areas, true))));
    }

    private function unit(array $rows): array
    {
        $onTime = $judged = $seen = 0;
        $progress = [];
        foreach ($rows as $d) {
            $progress[] = UnitStages::progress((int) $d['unit'], $d['stage'] === null ? null : (int) $d['stage']);
            $seen += $d['seen_at'] !== null && (int) $d['seen_at'] <= $this->calendar->addWorkDays((int) $d['dispatched_at'], 1) ? 1 : 0;
            if ($d['deadline_at'] !== null && ($d['answered_at'] !== null || (int) $d['deadline_at'] < $this->now)) {
                $judged++;
                $onTime += $d['answered_at'] !== null && (int) $d['answered_at'] <= (int) $d['deadline_at'] ? 1 : 0;
            }
        }
        $parts = ['ontime' => Scores::ratio($onTime, $judged), 'progress' => Scores::average($progress),
            'seen' => Scores::ratio($seen, count($rows))];
        return ['parts' => $parts, 'score' => Scores::combine(Scores::UNIT, $parts),
            'counts' => ['received' => count($rows), 'on_time' => $onTime, 'judged' => $judged, 'seen' => $seen]];
    }

    private function area(string $code, array $executors, array $experts): array
    {
        $of = static fn (array $rows) => array_map(static fn ($r) => $r['score'],
            array_filter($rows, static fn ($r) => in_array($code, $r['areas'], true)));
        $days = [];
        foreach ($this->flows as $f) {
            if ((string) $f['area_code'] === $code && $this->inside($f['dispatched_at'])) {
                $start = (int) ($f['intake_at'] ?? $f['report_created'] ?? $f['dispatched_at']);
                $days[] = max(0, ((int) $f['dispatched_at'] - $start) / 86_400_000);
            }
        }
        $unit = $this->unit(array_values(array_filter($this->dispatches, static fn ($d) => (string) $d['area_code'] === $code)));
        $parts = ['executor' => Scores::average($of($executors)), 'expert' => Scores::average($of($experts)),
            'unit' => $unit['score'], 'cycle' => Scores::linear(Scores::median($days), 10, 40)];
        return ['id' => $code, 'name' => Areas::label($code), 'parts' => $parts, 'score' => Scores::combine(Scores::AREA, $parts),
            'counts' => ['dispatched' => count($days), 'received' => $unit['counts']['received']]];
    }

    private function inside(mixed $millis): bool
    {
        return $millis !== null && (int) $millis >= $this->from && (int) $millis <= $this->to;
    }
}
