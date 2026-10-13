<?php
declare(strict_types=1);

/**
 * آنچه گزارش‌دهنده میدانی از سرنوشت مورد خودش می‌بیند. همراه هر سطر
 * `GET /field/items` می‌رود و گوشی در صفحه همان مورد نشانش می‌دهد.
 *
 * - دلیل رد یا مراجعه مجدد همیشه می‌رود: مدیر نوشتنش را اجباری کرده چون
 *   خطاب به خود گزارش‌دهنده است.
 * - نتیجه به اندازه تنظیم `reporter_result_detail`: ۰ هیچ (فقط وضعیت)، ۱ خود
 *   نتیجه، ۲ نتیجه با توضیح کارشناس و، برای رمزارز، شمار دستگاه و توان کشف‌شده.
 *   نام کارشناس هرگز نمی‌رود؛ همان‌طور که نام گزارش‌دهنده جز به خواست مدیر به
 *   اکیپ نمی‌رسد.
 */
final class FieldFeedback
{
    /** @return array<string, mixed>|null */
    public static function forReporter(array $row, int $detail): ?array
    {
        $info = [];
        $status = (int) $row['status'];
        if ($status === Field::STATUS_REJECTED || $status === Field::STATUS_REVISIT) {
            $event = Db::one(
                'SELECT at, note FROM field_events WHERE item_id = ? AND to_status = ? ORDER BY at DESC, id DESC LIMIT 1',
                [$row['id'], $status]
            );
            if ($event !== null && trim((string) $event['note']) !== '') {
                $info['note'] = trim((string) $event['note']);
                $info['note_at'] = (int) $event['at'];
            }
        }
        $result = json_decode((string) ($row['result'] ?? ''), true);
        if ($detail >= 1 && is_array($result) && isset($result['outcome'])) {
            $outcome = (string) $result['outcome'];
            $shown = [
                'outcome' => $outcome,
                'outcome_name' => $outcome === 'case' ? 'بازدید کارشناس ثبت شد'
                    : (FieldReferrals::OUTCOMES[$outcome] ?? ''),
                'at' => isset($result['at']) ? (int) $result['at'] : null,
            ];
            if ($detail >= 2) {
                $note = trim((string) ($result['note'] ?? ''));
                if ($note !== '') {
                    $shown['note'] = $note;
                }
                if ($outcome === 'case' && is_string($result['report_id'] ?? null)) {
                    $case = self::caseFigures($result['report_id']);
                    if ($case !== null) {
                        $shown['case'] = $case;
                    }
                }
            }
            $info['result'] = $shown;
        }
        return $info === [] ? null : $info;
    }

    /** کد پرونده، شمار دستگاه‌ها و توان کشف‌شده؛ بی نام مالک و هیچ داده شخصی. */
    private static function caseFigures(string $reportId): ?array
    {
        $report = Db::one('SELECT tracking_code, total_watt FROM reports WHERE id = ?', [$reportId]);
        if ($report === null) {
            return null;
        }
        $devices = (int) (Db::one('SELECT COUNT(*) AS n FROM devices WHERE report_id = ?', [$reportId])['n'] ?? 0);
        return [
            'tracking_code' => $report['tracking_code'],
            'devices' => $devices,
            'total_watt' => $report['total_watt'] === null ? null : (float) $report['total_watt'],
        ];
    }
}
