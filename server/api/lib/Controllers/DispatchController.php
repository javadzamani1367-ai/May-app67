<?php
declare(strict_types=1);

/**
 * ارسال مدارک به واحدها و پاسخ آن‌ها.
 *
 * فقط گرداننده پرونده می‌فرستد — رمزارز: مدیر و مجری رمزارز؛ برق غیرمجاز: مجری
 * شهرستان، و ستاد پس از مهلت او — نه کارشناس. ارسال به واحد همان ناحیه پرونده می‌رسد. هر ارسال مهلت دارد و واحد در هر پاسخ مرحله اقدامش را می‌گوید.
 * مهلت گذشته با محاسبه روی همین داده معلوم می‌شود، نه با وضعیتی جداگانه.
 */
final class DispatchController
{
    public const SENT = 0;
    public const SEEN = 1;
    public const ANSWERED = 2;

    public function create(Request $request): void
    {
        $user = Auth::requireAny($request, Auth::CASE_MANAGERS);
        $reportId = $request->str('report_id');
        $report = Db::one('SELECT id, tracking_code, temp_code, area_code, case_kind FROM reports WHERE id = ?', [$reportId]);
        if ($report === null || !(new Scope($user))->canSeeReport($report)) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }
        $channel = $request->int('channel', 0) ?? 0;
        $refusal = CaseActions::dispatchRefusal($report, $user, $channel);
        if ($refusal !== null) {
            Response::fail(403, 'not_your_turn', $refusal);
        }

        $unit = $request->int('unit');
        if ($unit === null || !isset(UnitStages::NAMES[$unit])) {
            Response::fail(400, 'missing_unit', 'واحد مقصد انتخاب نشده است.');
        }

        $id = $request->str('id') ?: Db::uuid();
        $deadlineDays = $request->int('deadline_days');
        $now = Db::now();
        $known = Db::one('SELECT id FROM dispatches WHERE id = ?', [$id]) !== null;

        Db::run(
            'INSERT INTO dispatches (id, report_id, unit, included_items, note, output_format,
                                     dispatched_at, sent_by, channel, deadline_at, status, area_code)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE note = VALUES(note), deadline_at = VALUES(deadline_at)',
            [
                $id, $reportId, $unit, json_encode($request->arr('included_items'), JSON_UNESCAPED_UNICODE),
                $request->str('note'), $request->int('output_format', 0), $request->int('dispatched_at', $now), $user['id'],
                $channel, $deadlineDays !== null ? $now + $deadlineDays * 86400000 : null, self::SENT,
                Areas::code($report['area_code']),
            ]
        );
        if (!$known) {
            CaseFlow::onDispatched($reportId, $user, $unit);
            if ($channel === 0) {
                Notifications::toUnit($unit, 'dispatch', 'مدارک جدید دریافت شد',
                    'پرونده ' . CaseLabels::code($report) . ' برای واحد شما ارسال شد.', $reportId, $report['area_code']);
            }
        }
        Response::json(['id' => $id]);
    }

    /** سوابق ارسال یک پرونده، برای مدیر و مجری ناحیه: چه چیزی، کِی، به کدام واحد، تا کدام مرحله. */
    public function index(Request $request): void
    {
        $user = Auth::requireAny($request, Auth::CASE_MANAGERS);
        $reportId = $request->str('report_id');
        $report = $reportId === '' ? null : Db::one('SELECT * FROM reports WHERE id = ?', [$reportId]);
        if ($report === null || !(new Scope($user))->canSeeReport($report)) {
            Response::fail(400, 'missing_report', 'شناسه پرونده ارسال نشده است.');
        }

        $rows = Db::all(
            'SELECT d.*, u.full_name AS sent_by_name
             FROM dispatches d LEFT JOIN users u ON u.id = d.sent_by
             WHERE d.report_id = ? ORDER BY d.dispatched_at DESC',
            [$reportId]
        );
        Response::json(['dispatches' => array_map([self::class, 'present'], $rows)]);
    }

    /** صندوق ورودی یک واحد مقصد: فقط ناحیه خودش، یا همه اگر واحد استانی است. */
    public function inbox(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_UNIT);
        [$where, $params] = Scope::unitWhere($user);
        $rows = Db::all(
            "SELECT d.*, r.tracking_code, r.temp_code, r.county, r.address, r.owner_name, r.case_kind
             FROM dispatches d JOIN reports r ON r.id = d.report_id
             WHERE $where ORDER BY d.dispatched_at DESC LIMIT 200",
            $params
        );
        Response::json(['dispatches' => array_map([self::class, 'present'], $rows),
            'stages' => UnitStages::of((int) $user['unit'])]);
    }

    public function markSeen(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_UNIT);
        $dispatch = Db::one('SELECT * FROM dispatches WHERE id = ?', [$request->str('id')]);
        if ($dispatch !== null && Scope::unitSees($user, $dispatch)) {
            Db::run('UPDATE dispatches SET status = GREATEST(status, ?), seen_at = COALESCE(seen_at, ?) WHERE id = ?',
                [self::SEEN, Db::now(), $dispatch['id']]);
        }
        Response::json(['done' => true]);
    }

    /** پاسخ واحد، با مرحله اقدام و مستندات. */
    public function respond(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_UNIT);
        $dispatch = Db::one('SELECT * FROM dispatches WHERE id = ?', [$request->str('dispatch_id')]);
        if ($dispatch === null || !Scope::unitSees($user, $dispatch)) {
            Response::fail(404, 'not_found', 'این ارسال برای واحد شما نیست.');
        }
        $result = UnitResponse::record($dispatch, $user, $request->str('body'), $request->int('stage'), array_values(
            array_filter($_FILES, 'is_array')));
        if (is_string($result)) {
            Response::fail(400, 'invalid_response', $result);
        }
        Response::json($result);
    }

    /** یک ارسال برای API: پاسخ‌ها، مرحله و پیشرفت، و اینکه مهلتش گذشته یا نه. */
    public static function present(array $row): array
    {
        $unit = (int) $row['unit'];
        $stage = $row['stage'] === null ? null : (int) $row['stage'];
        $row['responses'] = UnitResponse::list($row);
        $row['stage_name'] = UnitStages::name($unit, $stage);
        $row['progress'] = UnitStages::progress($unit, $stage);
        $row['closed'] = UnitStages::isFinal($unit, $stage);
        $row['overdue'] = self::isOverdue($row);
        if (array_key_exists('case_kind', $row)) {
            $row['case_kind'] = (int) $row['case_kind'];
            $row['kind_name'] = CaseKind::NAMES[$row['case_kind']] ?? '';
        }
        return $row;
    }

    public static function isOverdue(array $dispatch): bool
    {
        return UnitStages::overdue($dispatch);
    }
}
