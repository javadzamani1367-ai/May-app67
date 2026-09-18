<?php
declare(strict_types=1);

/**
 * ارسال مدارک به واحدها و پاسخ آن‌ها.
 *
 * هر ارسال یک مهلت دارد: واحد مقصد از زمان دریافت این‌قدر فرصت دارد که نتیجه
 * اقدامش را ثبت کند. مهلت گذشته با محاسبه روی همین داده معلوم می‌شود، نه با
 * یک وضعیت جداگانه که باید کسی به‌روزش نگه دارد.
 */
final class DispatchController
{
    public const SENT = 0;
    public const SEEN = 1;
    public const ANSWERED = 2;

    public function create(Request $request): void
    {
        $user = Auth::require($request);
        $reportId = $request->str('report_id');
        $report = Db::one('SELECT id, tracking_code FROM reports WHERE id = ?', [$reportId]);
        if ($report === null) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }

        $unit = $request->int('unit');
        if ($unit === null) {
            Response::fail(400, 'missing_unit', 'واحد مقصد انتخاب نشده است.');
        }

        $id = $request->str('id') ?: Db::uuid();
        $deadlineDays = $request->int('deadline_days');
        $now = Db::now();

        Db::run(
            'INSERT INTO dispatches (id, report_id, unit, included_items, note, output_format,
                                     dispatched_at, sent_by, channel, deadline_at, status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE note = VALUES(note), deadline_at = VALUES(deadline_at)',
            [
                $id,
                $reportId,
                $unit,
                json_encode($request->arr('included_items'), JSON_UNESCAPED_UNICODE),
                $request->str('note'),
                $request->int('output_format', 0),
                $request->int('dispatched_at', $now),
                $user['id'],
                $request->int('channel', 0),
                $deadlineDays !== null ? $now + $deadlineDays * 86400000 : null,
                self::SENT,
            ]
        );

        Notifications::toUnit(
            $unit,
            'dispatch',
            'مدارک جدید دریافت شد',
            'پرونده ' . ($report['tracking_code'] ?? $reportId) . ' برای واحد شما ارسال شد.',
            $reportId
        );

        Response::json(['id' => $id]);
    }

    /** سوابق ارسال یک پرونده: چه چیزی، کِی، به کدام واحد، و پاسخش چه بود. */
    public function index(Request $request): void
    {
        Auth::require($request);
        $reportId = $request->str('report_id');
        if ($reportId === '') {
            Response::fail(400, 'missing_report', 'شناسه پرونده ارسال نشده است.');
        }

        $rows = Db::all(
            'SELECT d.*, u.full_name AS sent_by_name
             FROM dispatches d LEFT JOIN users u ON u.id = d.sent_by
             WHERE d.report_id = ? ORDER BY d.dispatched_at DESC',
            [$reportId]
        );
        foreach ($rows as &$row) {
            $row['responses'] = $this->responsesOf((string) $row['id']);
            $row['overdue'] = $this->isOverdue($row);
        }
        Response::json(['dispatches' => $rows]);
    }

    /** صندوق ورودی یک واحد مقصد. */
    public function inbox(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_UNIT);
        $rows = Db::all(
            'SELECT d.*, r.tracking_code, r.county, r.address, r.owner_name
             FROM dispatches d JOIN reports r ON r.id = d.report_id
             WHERE d.unit = ? ORDER BY d.dispatched_at DESC LIMIT 200',
            [(int) $user['unit']]
        );
        foreach ($rows as &$row) {
            $row['responses'] = $this->responsesOf((string) $row['id']);
            $row['overdue'] = $this->isOverdue($row);
        }
        Response::json(['dispatches' => $rows]);
    }

    public function markSeen(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_UNIT);
        $id = $request->str('id');
        Db::run(
            'UPDATE dispatches SET status = GREATEST(status, ?), seen_at = COALESCE(seen_at, ?)
             WHERE id = ? AND unit = ?',
            [self::SEEN, Db::now(), $id, (int) $user['unit']]
        );
        Response::json(['done' => true]);
    }

    /** پاسخ واحد، با مستندات اقدام. */
    public function respond(Request $request): void
    {
        $user = Auth::require($request);
        $dispatchId = $request->str('dispatch_id');
        $dispatch = Db::one('SELECT * FROM dispatches WHERE id = ?', [$dispatchId]);
        if ($dispatch === null) {
            Response::fail(404, 'not_found', 'ارسالی با این شناسه نیست.');
        }
        if ((int) $user['role'] === Auth::ROLE_UNIT && (int) $user['unit'] !== (int) $dispatch['unit']) {
            Response::fail(403, 'forbidden', 'این ارسال برای واحد شما نیست.');
        }

        $now = Db::now();
        $responseId = Db::uuid();
        Db::run(
            'INSERT INTO dispatch_responses (id, dispatch_id, responder_id, body, created_at)
             VALUES (?, ?, ?, ?, ?)',
            [$responseId, $dispatchId, $user['id'], $request->str('body'), $now]
        );

        foreach ($_FILES as $file) {
            if (!is_array($file) || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
                continue;
            }
            $stored = Storage::put($file, 'responses/' . date('Y/m'));
            Db::run(
                'INSERT INTO response_files (id, response_id, file_path, file_name, mime_type,
                                             size_bytes, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)',
                [
                    Db::uuid(), $responseId, $stored['path'], (string) ($file['name'] ?? ''),
                    (string) ($file['type'] ?? ''), $stored['size'], $now,
                ]
            );
        }

        Db::run(
            'UPDATE dispatches SET status = ?, answered_at = COALESCE(answered_at, ?) WHERE id = ?',
            [self::ANSWERED, $now, $dispatchId]
        );

        Notifications::toRole(
            Auth::ROLE_MANAGER,
            'response',
            'پاسخ واحد ثبت شد',
            'برای یکی از ارسال‌های شما پاسخ ثبت شد.',
            (string) $dispatch['report_id']
        );

        Response::json(['id' => $responseId]);
    }

    private function responsesOf(string $dispatchId): array
    {
        $rows = Db::all(
            'SELECT r.*, u.full_name AS responder_name
             FROM dispatch_responses r LEFT JOIN users u ON u.id = r.responder_id
             WHERE r.dispatch_id = ? ORDER BY r.created_at',
            [$dispatchId]
        );
        foreach ($rows as &$row) {
            $row['files'] = Db::all(
                'SELECT id, file_path, file_name, mime_type, size_bytes FROM response_files
                 WHERE response_id = ?',
                [$row['id']]
            );
        }
        return $rows;
    }

    private function isOverdue(array $dispatch): bool
    {
        $deadline = $dispatch['deadline_at'] ?? null;
        if ($deadline === null) {
            return false;
        }
        return (int) $dispatch['status'] !== self::ANSWERED && (int) $deadline < Db::now();
    }
}
