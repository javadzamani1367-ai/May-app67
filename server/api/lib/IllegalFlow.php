<?php
declare(strict_types=1);

require_once __DIR__ . '/CaseKind.php';

/**
 * گردش پرونده برق غیرمجاز.
 *
 * ۱. گزارش برق غیرمجاز اپ میدانی می‌رسد: سرور همان لحظه پرونده‌ای (case_kind = ۱) با
 *    همان کد GH- می‌سازد و به مجری غیرمجاز همان شهرستان می‌سپارد؛ ستاد هم باخبر می‌شود.
 * ۲. مجری شهرستان به کارشناس همان شهرستان ارجاع می‌دهد. اگر تا مهلت (illegal_county_days)
 *    نداد، به ستاد هشدار می‌رود و ستاد ارجاع می‌دهد؛ اگر ستاد هم تا مهلتش
 *    (illegal_hq_days) نداد، پرونده خودکار به کارشناس آن شهرستان می‌رود.
 * ۳. کارشناس در اپ کارشناس بازرسی می‌کند و برای تأیید می‌فرستد؛ نتیجه به مجری شهرستان و
 *    ستاد می‌رسد. تأیید و ارسال به واحدهای همان شهرستان کار مجری شهرستان است؛ ستاد فقط
 *    وقتی جای او تصمیم می‌گیرد که مهلتش گذشته باشد یا شهرستان مجری نداشته باشد.
 * هشدار دیرکرد کارشناس و پیگیری نکردن مجری، همان پله‌های رمزارز است (FlowClock).
 */
final class IllegalFlow
{
    public const ORIGIN_FIELD = 3;
    public const ORIGIN_HQ = 4;

    /** پرونده غیرمجاز از یک گزارش میدانی؛ تکرارپذیر (گزارشی که پرونده دارد دوباره ساخته نمی‌شود). */
    public static function fromField(array $item): ?string
    {
        $item = Db::one('SELECT * FROM field_items WHERE id = ?', [$item['id']]) ?? $item;
        if ((int) $item['kind'] !== Field::KIND_ILLEGAL || ($item['report_id'] ?? null) !== null) {
            return $item['report_id'] ?? null;
        }
        $now = Db::now();
        $area = Areas::code($item['area_code'] ?? null);
        $code = (string) $item['tracking_code'];
        if (Db::one('SELECT id FROM reports WHERE tracking_code = ?', [$code]) !== null) {
            $code = null;
        }
        $id = Db::uuid();
        $reporter = Db::one('SELECT full_name FROM users WHERE id = ?', [$item['user_id']]);
        Db::run(
            'INSERT INTO reports (id, tracking_code, report_type, status, expert_code, report_date, created_at, updated_at,
                                  county, area_code, address, latitude, longitude, gps_accuracy, position_at,
                                  observer_latitude, observer_longitude, observer_accuracy, approval_state, case_kind)
             VALUES (?, ?, 4, 0, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1)',
            [$id, $code, (int) $item['created_at'], $now, $now, $area === null ? null : (Areas::NAMES[$area] ?? null), $area,
             $item['address'], $item['latitude'], $item['longitude'], $item['accuracy'], $item['position_at'],
             $item['observer_lat'], $item['observer_lon'], $item['observer_accuracy']]
        );
        $settings = FieldSettings::all();
        $calendar = CrewCalendar::fromSettings();
        $executor = Scope::executorOf($area, CaseKind::ILLEGAL);
        $note = trim(($reporter === null ? '' : 'گزارش‌دهنده: ' . $reporter['full_name'] . "\n") . (string) ($item['description'] ?? ''));
        CaseFlow::touch($id, [
            'origin' => self::ORIGIN_FIELD, 'intake_code' => $item['tracking_code'], 'intake_by' => $item['user_id'],
            'intake_at' => $now, 'intake_note' => $note === '' ? null : $note,
            'executor_id' => $executor['id'] ?? null, 'executor_at' => $executor === null ? null : $now,
            'county_due_at' => $executor === null ? null : $calendar->addWorkDays($now, (int) $settings['illegal_county_days']),
            'hq_alert_at' => $executor === null ? $now : null,
            'hq_due_at' => $executor === null ? $calendar->addWorkDays($now, (int) $settings['illegal_hq_days']) : null,
        ]);
        CaseFlow::event($id, $item['user_id'], 'field_intake', $executor === null ? 'این شهرستان مجری غیرمجاز ندارد؛ با ستاد' : $executor['name']);
        Db::run('UPDATE field_items SET report_id = ?, status = ?, updated_at = ? WHERE id = ?',
            [$id, Field::STATUS_REVIEWING, $now, $item['id']]);
        Field::event((string) $item['id'], null, 'case', (int) $item['status'], Field::STATUS_REVIEWING, 'پرونده بازرسی برق غیرمجاز ساخته شد');
        Notifications::toCaseLeads(['case_kind' => 1, 'area_code' => $area], 'illegal_new', 'گزارش برق غیرمجاز تازه: ' . $item['tracking_code'],
            Areas::label($area) . ' — ' . mb_substr((string) ($item['address'] ?? ''), 0, 200), $id);
        return $id;
    }

    /** ستاد اجازه دارد به جای شهرستان تأیید کند یا به واحد بفرستد؟ */
    public static function hqMayDecide(array $report): bool
    {
        if (Scope::executorOf($report['area_code'] ?? null, CaseKind::ILLEGAL) === null) {
            return true;
        }
        return (CaseFlow::row((string) $report['id'])['decide_alert_at'] ?? null) !== null;
    }

    /** خطای «هنوز نوبت ستاد نیست»، یا تهی. برای تأیید و ارسال به واحد. */
    public static function guardDecision(array $report, array $user): ?string
    {
        if (CaseKind::of($report) !== CaseKind::ILLEGAL || (int) $user['role'] !== Auth::ROLE_ILLEGAL || self::hqMayDecide($report)) {
            return null;
        }
        return 'تأیید و ارسال این پرونده تا پایان مهلت با مجری غیرمجاز شهرستان است.';
    }

    /** کارشناس پیش‌فرض غیرمجاز یک ناحیه، یا کسی که کار باز کمتری دارد. */
    public static function defaultExpert(?string $area): ?array
    {
        $experts = Scope::usersIn(Auth::ROLE_EXPERT, $area === null ? ['---'] : [$area]);
        if ($experts === []) {
            return null;
        }
        $chosen = Db::one('SELECT v FROM field_settings WHERE k = ?', ['illegal_expert:' . $area])['v'] ?? null;
        foreach ($experts as $expert) {
            if ($expert['id'] === $chosen) {
                return $expert;
            }
        }
        usort($experts, static fn ($a, $b) => self::openLoad($a['id']) <=> self::openLoad($b['id']));
        return $experts[0];
    }

    public static function setDefaultExpert(string $area, ?string $userId): void
    {
        if ($userId === null || $userId === '') {
            Db::run('DELETE FROM field_settings WHERE k = ?', ['illegal_expert:' . $area]);
            return;
        }
        Db::run('INSERT INTO field_settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)',
            ['illegal_expert:' . $area, $userId]);
    }

    private static function openLoad(string $expertId): int
    {
        return (int) (Db::one('SELECT COUNT(*) AS n FROM case_flow f JOIN reports r ON r.id = f.report_id
                               WHERE f.expert_id = ? AND r.status = 0', [$expertId])['n'] ?? 0);
    }

    /** کارهایی که با گذشت زمان انجام می‌شوند؛ از FlowClock. */
    public static function tick(int $now, CrewCalendar $calendar, array $settings): void
    {
        $rows = Db::all(
            'SELECT f.*, r.area_code, r.tracking_code, r.temp_code FROM case_flow f JOIN reports r ON r.id = f.report_id
             WHERE r.case_kind = 1 AND r.status = 0 AND f.expert_id IS NULL AND f.hq_alert_at IS NULL
               AND f.county_due_at IS NOT NULL AND f.county_due_at < ? LIMIT 200', [$now]);
        foreach ($rows as $row) {
            $id = (string) $row['report_id'];
            $text = CaseLabels::code($row) . ' — ' . Areas::label($row['area_code']) . ': مجری غیرمجاز شهرستان '
                . Jalali::digits((string) $settings['illegal_county_days']) . ' روز کاری پرونده را به کارشناس نسپرده است.';
            CaseFlow::touch($id, ['hq_alert_at' => $now, 'hq_due_at' => $calendar->addWorkDays($now, (int) $settings['illegal_hq_days'])]);
            CaseFlow::event($id, null, 'hq_alert', $text);
            Notifications::toRole(Auth::ROLE_ILLEGAL, 'county_late', 'ارجاع پرونده غیرمجاز معطل مانده', $text, $id);
        }
        $rows = Db::all(
            'SELECT f.*, r.area_code, r.tracking_code, r.temp_code FROM case_flow f JOIN reports r ON r.id = f.report_id
             WHERE r.case_kind = 1 AND r.status = 0 AND f.expert_id IS NULL AND f.hq_due_at IS NOT NULL AND f.hq_due_at < ?
             LIMIT 200', [$now]);
        foreach ($rows as $row) {
            self::autoExpert($row, $now, $calendar, $settings);
        }
        // تأییدی که در انتظار مانده، یا پرونده تأییدشده‌ای که به واحدی نرفته: نوبت ستاد.
        $rows = Db::all(
            'SELECT f.report_id, r.area_code, r.tracking_code, r.temp_code, r.approval_state, f.approved_at,
                    (SELECT MAX(a.submitted_at) FROM approvals a WHERE a.report_id = r.id AND a.decision = 0) AS pending_at
             FROM case_flow f JOIN reports r ON r.id = f.report_id
             WHERE r.case_kind = 1 AND f.decide_alert_at IS NULL AND (r.approval_state = 1 OR (r.approval_state = 2 AND f.dispatched_at IS NULL))
             LIMIT 300');
        foreach ($rows as $row) {
            $start = (int) ((int) $row['approval_state'] === 1 ? $row['pending_at'] : $row['approved_at']);
            if ($start <= 0 || $calendar->addWorkDays($start, (int) $settings['illegal_county_days']) >= $now) {
                continue;
            }
            $id = (string) $row['report_id'];
            $text = CaseLabels::code($row) . ' — ' . ((int) $row['approval_state'] === 1 ? 'نتیجه بازرسی تأیید نشده' : 'به واحدی ارسال نشده')
                . '؛ ستاد می‌تواند به جای مجری شهرستان اقدام کند.';
            CaseFlow::touch($id, ['decide_alert_at' => $now]);
            CaseFlow::event($id, null, 'decide_alert', $text);
            Notifications::toRole(Auth::ROLE_ILLEGAL, 'county_late', 'پرونده غیرمجاز معطل مانده', $text, $id);
        }
    }

    /** ستاد هم ارجاع نداد: کارشناس پیش‌فرض شهرستان، یا کسی که کار باز کمتری دارد. */
    private static function autoExpert(array $row, int $now, CrewCalendar $calendar, array $settings): void
    {
        $id = (string) $row['report_id'];
        $expert = self::defaultExpert(Areas::code($row['area_code']));
        if ($expert === null) {
            CaseFlow::touch($id, ['hq_due_at' => null]);
            CaseFlow::event($id, null, 'no_executor', Areas::label($row['area_code']) . ' کارشناس فعال ندارد.');
            Notifications::toRole(Auth::ROLE_ILLEGAL, 'no_expert', 'ارجاع خودکار انجام نشد',
                CaseLabels::code($row) . ' — ' . Areas::label($row['area_code']) . ' کارشناس فعال ندارد.', $id);
            return;
        }
        CaseFlow::touch($id, ['expert_id' => $expert['id'], 'assigned_at' => $now, 'assigned_by' => null, 'auto_referred' => 1,
            'due_at' => $calendar->addWorkDays($now, (int) $settings['expert_days']), 'hq_due_at' => null, 'county_due_at' => null]);
        Db::run('UPDATE reports SET expert_code = ?, updated_at = ? WHERE id = ?', [$expert['user_code'], $now, $id]);
        CaseFlow::event($id, null, 'auto_expert', $expert['name']);
        Notifications::toUser((string) $expert['id'], 'case_assigned', 'پرونده بازرسی برق غیرمجاز', CaseLabels::code($row), $id);
        Notifications::toCaseLeads(['case_kind' => 1, 'area_code' => $row['area_code']], 'auto_expert', 'ارجاع خودکار به کارشناس',
            CaseLabels::code($row) . ' — ' . $expert['name'], $id);
    }

    /**
     * پرونده غیرمجاز از گوشی رسید: بازدیدی که ثبت شد، نتیجه همان گزارش میدانی است و
     * گزارش‌دهنده در اپ میدانی می‌بیندش (FieldFeedback).
     */
    public static function onCasePushed(array $report, array $user): void
    {
        if (CaseKind::of($report) !== CaseKind::ILLEGAL || (int) ($report['status'] ?? 0) < 1) {
            return;
        }
        $item = Db::one('SELECT * FROM field_items WHERE report_id = ? AND status <> ? LIMIT 1', [$report['id'], Field::STATUS_RESULT]);
        if ($item === null) {
            return;
        }
        $result = ['outcome' => 'case', 'report_id' => (string) $report['id'], 'at' => Db::now(), 'by' => (string) $user['full_name']];
        Db::run('UPDATE field_items SET status = ?, result = ?, updated_at = ? WHERE id = ?',
            [Field::STATUS_RESULT, json_encode($result, JSON_UNESCAPED_UNICODE), Db::now(), $item['id']]);
        Field::event((string) $item['id'], (string) $user['id'], 'result', (int) $item['status'], Field::STATUS_RESULT,
            'بازرسی ثبت شد؛ پرونده ' . CaseLabels::code($report));
    }
}
