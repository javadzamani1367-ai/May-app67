<?php
declare(strict_types=1);

require_once __DIR__ . '/CaseKind.php';
require_once __DIR__ . '/IllegalFlow.php';

/**
 * کارهایی که با گذشت زمان انجام می‌شوند: ارجاع خودکار و هشدارها.
 *
 * هاست اشتراکی پروسه دائمی ندارد. پس این کارها با هر درخواست به API، پنل و پرتال
 * — حداکثر هر پنج دقیقه یک بار — انجام می‌شوند، و اگر Cron Job سی‌پنل تعریف شده
 * باشد (cron.php) دقیق سر وقت. هر قاعده تکرارپذیر است: ستون زمان هشدار نشان می‌دهد
 * که کاری یک بار انجام شده و دوباره انجام نمی‌شود.
 *
 * ۱. گزارشی که مدیر ثبت کرد و تا مهلت به مجری یا کارشناس نسپرد، به مجری ناحیه می‌رسد.
 * ۲. کارشناسی که تا مهلت بازدید نکرده، به مجری ناحیه هشدار می‌دهد.
 * ۳. مجری‌ای که پس از آن هشدار (یا پس از سپرده شدن پرونده) تا مهلتش اقدامی نکرده،
 *    به مدیر هشدار می‌دهد — با شمار روزهای بی‌اقدامی هر دو.
 * همین قواعد ۲ و ۳ برای موردهای میدانی ارجاع‌شده هم هست. در پرونده برق غیرمجاز
 * «مجری» مجری غیرمجاز شهرستان است و «مدیر» ستاد؛ قواعد خاص آن حوزه در IllegalFlow.
 */
final class FlowClock
{
    private const EVERY_MILLIS = 300_000;

    /** با هر درخواست؛ هرگز درخواست را به خاطر خودش از کار نمی‌اندازد. */
    public static function maybeTick(): void
    {
        try {
            $mark = Storage::root() . '/cache/flowclock';
            if (is_file($mark) && (time() - (int) filemtime($mark)) * 1000 < self::EVERY_MILLIS) {
                return;
            }
            if (!is_dir(dirname($mark))) {
                @mkdir(dirname($mark), 0750, true);
            }
            @touch($mark);
            self::tick(Db::now());
        } catch (Throwable $e) {
            error_log('[inspection] flow clock: ' . $e->getMessage());
        }
    }

    /** همه قواعد در لحظه $now. برای تست هم صدا زده می‌شود. */
    public static function tick(int $now): void
    {
        $settings = FieldSettings::all();
        $calendar = CrewCalendar::fromSettings();
        self::autoRefer($now);
        self::expertLate($now, (int) $settings['expert_days']);
        self::executorLate($now, $calendar, (int) $settings['executor_days'], (int) $settings['expert_days']);
        IllegalFlow::tick($now, $calendar, $settings);
        FieldFlowClock::tick($now, $calendar, $settings);
    }

    private static function autoRefer(int $now): void
    {
        $rows = Db::all(
            'SELECT f.report_id, r.area_code, r.tracking_code, r.temp_code, r.case_kind FROM case_flow f JOIN reports r ON r.id = f.report_id
             WHERE f.refer_due_at IS NOT NULL AND f.refer_due_at < ? AND f.executor_id IS NULL AND f.expert_id IS NULL LIMIT 200',
            [$now]
        );
        foreach ($rows as $row) {
            $id = (string) $row['report_id'];
            $kind = CaseKind::of($row);
            $executor = Scope::executorOf($row['area_code'], $kind);
            if ($executor === null) {
                CaseFlow::touch($id, ['refer_due_at' => null]);
                CaseFlow::event($id, null, 'no_executor', Areas::label($row['area_code']));
                Notifications::toRole(CaseKind::topRole($kind), 'no_executor', 'ارجاع خودکار انجام نشد',
                    CaseLabels::code($row) . ' — ' . Areas::label($row['area_code']) . ' مجری فعال ندارد.', $id);
                continue;
            }
            CaseFlow::touch($id, ['executor_id' => $executor['id'], 'executor_at' => $now, 'auto_referred' => 1,
                'refer_due_at' => null, 'county_due_at' => $kind === CaseKind::ILLEGAL
                    ? CrewCalendar::fromSettings()->addWorkDays($now, (int) FieldSettings::all()['illegal_county_days']) : null]);
            CaseFlow::event($id, null, 'auto_executor', $executor['name']);
            Notifications::toUser((string) $executor['id'], 'case_referred', 'پرونده خودکار به شما سپرده شد',
                CaseLabels::code($row) . ' — مهلت ارجاع مدیر گذشت؛ پیگیری با شماست.', $id);
        }
    }

    private static function expertLate(int $now, int $expertDays): void
    {
        $rows = Db::all(
            'SELECT f.*, r.area_code, r.tracking_code, r.temp_code, r.case_kind, p.full_name AS expert_name
             FROM case_flow f JOIN reports r ON r.id = f.report_id LEFT JOIN users p ON p.id = f.expert_id
             WHERE f.expert_id IS NOT NULL AND f.visited_at IS NULL AND f.expert_alert_at IS NULL
               AND f.due_at IS NOT NULL AND f.due_at < ? AND r.status = 0 LIMIT 200',
            [$now]
        );
        foreach ($rows as $row) {
            $id = (string) $row['report_id'];
            $text = CaseLabels::code($row) . ' — کارشناس ' . $row['expert_name'] . ' ' . Jalali::digits((string) $expertDays)
                . ' روز کاری اقدامی نکرده است.';
            CaseFlow::touch($id, ['expert_alert_at' => $now]);
            CaseFlow::event($id, null, 'expert_alert', $text);
            $kind = CaseKind::of($row);
            $executor = $row['executor_id'] ?? (Scope::executorOf($row['area_code'], $kind)['id'] ?? null);
            if ($executor !== null) {
                Notifications::toUser((string) $executor, 'expert_late', 'کارشناس بازدید را انجام نداده', $text, $id);
            } else {
                Notifications::toRole(CaseKind::topRole($kind), 'expert_late', 'کارشناس بازدید را انجام نداده', $text, $id);
            }
        }
    }

    /**
     * مجری بی‌اقدام: یا پس از هشدار کارشناس پیگیری نکرده، یا پرونده‌ای که به او
     * سپرده شده را به کارشناسی نداده. اقدام = هر ارجاع یا یادداشت پیگیری پس از آن لحظه.
     */
    private static function executorLate(int $now, CrewCalendar $calendar, int $executorDays, int $expertDays): void
    {
        $rows = Db::all(
            'SELECT f.*, r.area_code, r.tracking_code, r.temp_code, r.case_kind, x.full_name AS executor_name, p.full_name AS expert_name
             FROM case_flow f JOIN reports r ON r.id = f.report_id LEFT JOIN users x ON x.id = f.executor_id
             LEFT JOIN users p ON p.id = f.expert_id
             WHERE f.manager_alert_at IS NULL AND f.visited_at IS NULL AND r.status = 0 AND f.executor_id IS NOT NULL
               AND ((f.expert_alert_at IS NOT NULL AND (f.executor_action_at IS NULL OR f.executor_action_at < f.expert_alert_at))
                 OR (r.case_kind = 0 AND f.expert_id IS NULL AND (f.executor_action_at IS NULL OR f.executor_action_at < f.executor_at)))
             LIMIT 300'
        );
        foreach ($rows as $row) {
            $start = $row['expert_alert_at'] !== null ? (int) $row['expert_alert_at'] : (int) $row['executor_at'];
            if ($calendar->addWorkDays($start, $executorDays) >= $now) {
                continue;
            }
            $id = (string) $row['report_id'];
            $text = CaseLabels::code($row) . ' — مجری ' . $row['executor_name'] . ' ' . Jalali::digits((string) $executorDays) . ' روز';
            $text .= $row['expert_alert_at'] !== null
                ? ' و کارشناس ' . $row['expert_name'] . ' ' . Jalali::digits((string) ($expertDays + $executorDays)) . ' روز کاری اقدامی نکرده‌اند.'
                : ' کاری اقدامی نکرده و پرونده را به کارشناسی نسپرده است.';
            $kind = CaseKind::of($row);
            CaseFlow::touch($id, ['manager_alert_at' => $now]);
            CaseFlow::event($id, null, $kind === CaseKind::ILLEGAL ? 'hq_late' : 'manager_alert', $text);
            Notifications::toRole(CaseKind::topRole($kind), 'executor_late', 'پرونده بی‌اقدام مانده', $text, $id);
        }
    }
}
