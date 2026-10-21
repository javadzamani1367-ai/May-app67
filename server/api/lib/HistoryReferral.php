<?php
declare(strict_types=1);

require_once __DIR__ . '/FieldHistorySearch.php';

/**
 * ارجاع از سوابق یک تابلو یا تیر برای بازرسی رمزارز: پرونده تازه «گزارش همکار» در ناحیه
 * همان پلاک، با نشانی و موقعیت آخرین بازرسی، که همان لحظه به کارشناس رمزارز ناحیه می‌رسد.
 * فقط مدیر و مجری رمزارز همان ناحیه؛ چرخه ارجاع و مهلت‌ها همان CaseFlow است.
 */
final class HistoryReferral
{
    /** شناسه پرونده تازه، یا خطای فارسی. */
    public static function refer(Scope $scope, ?string $area, int $asset, string $plateKey, string $part, string $expertId,
                                 string $note, ?int $dueDays): string
    {
        if (!$scope->isManager() && !$scope->isExecutor()) {
            return 'ارجاع از سوابق برای مدیر و مجری رمزارز است.';
        }
        if ($area === null || $plateKey === '' || !FieldHistorySearch::canSeeKey($scope, $area, $asset, $plateKey, $part)) {
            return 'سابقه‌ای برای این پلاک در محدوده شما نیست.';
        }
        if (!in_array($expertId, array_column($scope->experts($area), 'id'), true)) {
            return 'یک کارشناس رمزارز همین ناحیه را انتخاب کنید.';
        }
        if ($dueDays !== null && ($dueDays < 1 || $dueDays > 60)) {
            return 'مهلت بازدید را بین ۱ تا ۶۰ روز کاری بنویسید.';
        }
        if (mb_strlen(trim($note)) > 1500) {
            return 'توضیح بیش از حد بلند است.';
        }
        $last = Db::one(
            'SELECT h.*, i.address, i.latitude, i.longitude, i.tracking_code FROM field_readings h JOIN field_items i ON i.id = h.item_id
             WHERE h.area_code = ? AND h.asset = ? AND h.plate_key = ? AND h.part = ? AND ' . FieldHistory::VALID . '
             ORDER BY h.measured_at DESC LIMIT 1',
            [$area, $asset, $plateKey, $part]
        );
        if ($last === null) {
            return 'سابقه‌ای برای این پلاک نیست.';
        }
        $label = FieldHistory::label($asset, (string) $last['plate'], $part);
        $why = 'ارجاع از سوابق بازرسی — ' . $label . ' (' . $last['tracking_code'] . ')'
            . ($last['anomaly_note'] ? ': افزایش نامتعارف ' . $last['anomaly_note'] : '')
            . (trim($note) === '' ? '' : "\n" . trim($note));
        $id = CaseIntake::create(['report_type' => 4, 'area_code' => $area, 'note' => $why,
            'address' => trim((string) ($last['address'] ?? '')) ?: $label], $scope);
        if ($last['latitude'] !== null) {
            Db::run('UPDATE reports SET latitude = ?, longitude = ? WHERE id = ?', [$last['latitude'], $last['longitude'], $id]);
        }
        // کارشناس و مهلت بالاتر سنجیده شده‌اند؛ اگر باز هم نشد، پرونده در چرخه عادی ناحیه می‌ماند.
        return CaseFlow::toExpert($id, $expertId, $dueDays, $why, $scope) ?? $id;
    }
}
