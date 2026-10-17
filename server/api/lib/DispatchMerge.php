<?php
declare(strict_types=1);

/**
 * سطرهای ارسال به واحد که همراه پرونده از اپ مدیر یا مجری می‌رسند. هر سطر تازه
 * ناحیه پرونده را می‌گیرد و فقط به واحد همان ناحیه اعلان می‌دهد.
 */
final class DispatchMerge
{
    /**
     * ارسال‌هایی که گوشی به واحدها داشته است.
     *
     * تا پیش از این، گوشی این سطرها را همراه پرونده می‌فرستاد و سرور نادیده‌شان
     * می‌گرفت. نتیجه‌اش دو چیز بود که هیچ‌کدام خطا نمی‌داد: گزارش عملکرد
     * واحدها برای همیشه صفر بود، و صندوق واحد در پرتال هرگز چیزی دریافت
     * نمی‌کرد.
     *
     * ادغام، نه جایگزینی: وضعیت «دیده‌شده»، زمان پاسخ و متن پاسخ را واحد
     * مقصد روی همین سرور می‌نویسد و نسخه گوشی از آن‌ها خبر ندارد. جایگزین
     * کردن یعنی هر بار که کارشناس پرونده را دوباره بفرستد، پاسخ واحد پاک شود.
     */
    public static function merge(array $rows, string $reportId, array $user): void
    {
        foreach ($rows as $row) {
            if (!is_array($row) || empty($row['id']) || !isset($row['unit'])) {
                continue;
            }
            $id = (string) $row['id'];
            $deadline = isset($row['deadline_at']) ? (int) $row['deadline_at'] : null;
            $note = isset($row['note']) ? (string) $row['note'] : null;

            $known = Db::one('SELECT report_id FROM dispatches WHERE id = ?', [$id]);
            if ($known !== null) {
                // فقط آنچه گوشی مالکش است. سطری که به پرونده دیگری تعلق دارد
                // دست نمی‌خورد.
                Db::run(
                    'UPDATE dispatches SET note = ?, deadline_at = ? WHERE id = ? AND report_id = ?',
                    [$note, $deadline, $id, $reportId]
                );
                continue;
            }

            $items = $row['included_items'] ?? '[]';
            $unit = (int) $row['unit'];
            $area = Db::one('SELECT area_code FROM reports WHERE id = ?', [$reportId])['area_code'] ?? null;
            Db::run(
                'INSERT INTO dispatches (id, report_id, unit, included_items, note, output_format,
                                         dispatched_at, sent_by, channel, deadline_at, status, area_code)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
                [
                    $id,
                    $reportId,
                    $unit,
                    is_string($items) ? $items : json_encode($items, JSON_UNESCAPED_UNICODE),
                    $note,
                    (int) ($row['output_format'] ?? 0),
                    (int) ($row['dispatched_at'] ?? Db::now()),
                    $user['id'],
                    (int) ($row['channel'] ?? 0),
                    $deadline,
                    DispatchController::SENT,
                    Areas::code($area),
                ]
            );
            CaseFlow::onDispatched($reportId, $user, $unit);
            if ((int) ($row['channel'] ?? 0) === 0) {
                Notifications::toUnit(
                    $unit,
                    'dispatch',
                    'مدارک جدید دریافت شد',
                    'پرونده ' . self::codeOf($reportId) . ' برای واحد شما ارسال شد.',
                    $reportId,
                    $area
                );
            }
        }
    }

    private static function codeOf(string $reportId): string
    {
        $row = Db::one('SELECT tracking_code, temp_code FROM reports WHERE id = ?', [$reportId]);
        foreach (['tracking_code', 'temp_code'] as $column) {
            if (($row[$column] ?? '') !== '') {
                return (string) $row[$column];
            }
        }
        return $reportId;
    }
}
