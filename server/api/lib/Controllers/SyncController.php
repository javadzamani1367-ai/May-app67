<?php
declare(strict_types=1);

/**
 * همگام‌سازی پرونده‌ها بین گوشی، سرور و ویندوز.
 *
 * انتقال افزایشی است: فقط پرونده‌هایی که `updated_at` آن‌ها از آخرین
 * همگام‌سازی جدیدتر است جابه‌جا می‌شوند. آخرین نسخه برنده است، چون هر پرونده
 * را در عمل یک کارشناس در دست دارد و ادغام ستون‌به‌ستون پیچیدگی بی‌دلیل است.
 */
final class SyncController
{
    /** ستون‌های جدول reports، همان‌هایی که گوشی و ویندوز دارند. */
    private const REPORT_COLUMNS = [
        'id', 'tracking_code', 'temp_code', 'report_type', 'status', 'expert_code',
        'report_date', 'visit_date', 'created_at', 'updated_at', 'county', 'area_code', 'district',
        'address', 'postal_code', 'latitude', 'longitude', 'gps_accuracy', 'file_number',
        'bill_number', 'subscription_number', 'usage_type', 'owner_name', 'owner_national_id',
        'owner_phone', 'owner_relation', 'meter_amperage', 'connection_type', 'seal_status',
        'measured_amperage', 'tap_point', 'phase_type', 'amperage_r', 'amperage_s', 'amperage_t',
        'voltage_r', 'voltage_s', 'voltage_t', 'total_watt', 'tariff_type', 'meter_type',
        'seal_external', 'seal_external_serial', 'seal_internal', 'meter_appearance_ok',
        'meter_tampered', 'approval_state', 'approval_comment', 'approval_at',
        'description', 'actions_taken',
    ];

    public function manifest(Request $request): void
    {
        $user = Auth::require($request);
        $since = $request->int('since', 0) ?? 0;

        // کارشناس فقط پرونده‌های خودش را می‌بیند؛ مدیر همه را.
        $sql = 'SELECT id, updated_at, tracking_code, status FROM reports WHERE updated_at > ?';
        $params = [$since];
        if ((int) $user['role'] === Auth::ROLE_EXPERT) {
            $sql .= ' AND expert_code = ?';
            $params[] = $user['user_code'];
        }
        $sql .= ' ORDER BY updated_at LIMIT 500';

        Response::json(['reports' => Db::all($sql, $params), 'server_time' => Db::now()]);
    }

    public function push(Request $request): void
    {
        $user = Auth::require($request);
        $report = $request->arr('report');
        if (!isset($report['id'])) {
            Response::fail(400, 'missing_report', 'پرونده‌ای ارسال نشده است.');
        }

        $existing = Db::one('SELECT updated_at FROM reports WHERE id = ?', [$report['id']]);
        if ($existing !== null && (int) $existing['updated_at'] > (int) ($report['updated_at'] ?? 0)) {
            // نسخه سرور تازه‌تر است: نوشتن روی آن یعنی دور ریختن کاری که
            // جای دیگری انجام شده. ولی فهرست فایل‌های نرسیده همراهش می‌رود،
            // وگرنه تصویری که هنوز بالا نرفته هرگز فرصت دیگری پیدا نمی‌کند.
            Response::json([
                'skipped' => true,
                'reason' => 'server_newer',
                'missing_files' => $this->missingFiles((string) $report['id']),
            ]);
        }

        $columns = self::REPORT_COLUMNS;
        $placeholders = implode(', ', array_fill(0, count($columns), '?'));
        $updates = implode(', ', array_map(
            static fn(string $c): string => Db::col($c) . ' = VALUES(' . Db::col($c) . ')',
            array_slice($columns, 1)
        ));
        $values = array_map(static fn(string $c) => $report[$c] ?? null, $columns);

        Db::run(
            'INSERT INTO reports (' . Db::cols($columns) . ", synced_at)
             VALUES ($placeholders, " . Db::now() . ")
             ON DUPLICATE KEY UPDATE $updates, synced_at = " . Db::now(),
            $values
        );

        $this->replaceChildren('devices', $report['devices'] ?? [], (string) $report['id'], [
            'id', 'report_id', 'row_number', 'model', 'serial_number', 'power_watt',
            'entry_method', 'note',
        ]);
        $this->replaceChildren('attendees', $report['attendees'] ?? [], (string) $report['id'], [
            'id', 'report_id', 'organization', 'full_name', 'position', 'org_name',
        ]);
        $this->replaceChildren('media', $report['media'] ?? [], (string) $report['id'], [
            'id', 'report_id', 'type', 'file_path', 'caption', 'captured_at', 'latitude',
            'longitude', 'size_bytes',
        ]);
        $this->replaceChildren('attachments', $report['attachments'] ?? [], (string) $report['id'], [
            'id', 'report_id', 'category', 'title', 'file_path', 'mime_type', 'added_at', 'note',
        ]);
        $this->mergeDispatches($report['dispatches'] ?? [], (string) $report['id'], $user);

        Db::run(
            'INSERT INTO sync_log (device_code, user_id, last_seen_at, last_updated_at, report_count)
             VALUES (?, ?, ?, ?, 1)
             ON DUPLICATE KEY UPDATE last_seen_at = VALUES(last_seen_at),
                                     last_updated_at = GREATEST(last_updated_at, VALUES(last_updated_at)),
                                     report_count = report_count + 1',
            [$request->str('device_code'), $user['id'], Db::now(), (int) ($report['updated_at'] ?? 0)]
        );

        Response::json([
            'saved' => true,
            'id' => $report['id'],
            // گوشی از این فهرست می‌فهمد کدام فایل‌ها را باید بفرستد. بدون آن
            // هر همگام‌سازی همه تصاویر را دوباره بالا می‌برد، که روی اینترنت
            // یک روستا یعنی هیچ‌وقت تمام نمی‌شود.
            'missing_files' => $this->missingFiles((string) $report['id']),
        ]);
    }

    /**
     * سطرهای رسانه و پیوست این پرونده که فایلشان روی سرور نیست.
     *
     * «هست یا نیست» از روی وجود خود فایل روی دیسک سنجیده می‌شود، نه از یک ستون
     * دیگر: یک ستون باید همیشه درست نگه داشته شود و اولین بار که نشد، سرور
     * می‌گوید فایلی را دارد که ندارد.
     *
     * @return list<array{kind: string, id: string}>
     */
    private function missingFiles(string $reportId): array
    {
        $missing = [];
        foreach (['media' => 'media', 'attachment' => 'attachments'] as $kind => $table) {
            $rows = Db::all("SELECT id, file_path FROM $table WHERE report_id = ?", [$reportId]);
            foreach ($rows as $row) {
                $path = trim((string) $row['file_path']);
                if ($path === '' || Storage::resolve($path) === null) {
                    $missing[] = ['kind' => $kind, 'id' => (string) $row['id']];
                }
            }
        }
        return $missing;
    }

    public function pull(Request $request): void
    {
        $user = Auth::require($request);
        $id = $request->str('id');
        $report = Db::one('SELECT * FROM reports WHERE id = ?', [$id]);
        if ($report === null) {
            Response::fail(404, 'not_found', 'پرونده پیدا نشد.');
        }
        if ((int) $user['role'] === Auth::ROLE_EXPERT && $report['expert_code'] !== $user['user_code']) {
            Response::fail(403, 'forbidden', 'این پرونده برای شما نیست.');
        }

        // `synced_at` مال همین سرور است و معنایش روی گوشی مقصد چیز دیگری است:
        // «آخرین بار که این گوشی فرستاد». فرستادنش یعنی گوشی مقصد فکر کند
        // پرونده‌ای را فرستاده که هرگز نفرستاده.
        unset($report['synced_at']);

        $report['devices'] = Db::all(
            'SELECT * FROM devices WHERE report_id = ? ORDER BY `row_number`',
            [$id]
        );
        $report['attendees'] = Db::all('SELECT * FROM attendees WHERE report_id = ?', [$id]);
        $report['media'] = Db::all('SELECT * FROM media WHERE report_id = ? ORDER BY captured_at', [$id]);
        $report['attachments'] = Db::all('SELECT * FROM attachments WHERE report_id = ? ORDER BY added_at', [$id]);
        Response::json(['report' => $report]);
    }

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
    private function mergeDispatches(array $rows, string $reportId, array $user): void
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
            Db::run(
                'INSERT INTO dispatches (id, report_id, unit, included_items, note, output_format,
                                         dispatched_at, sent_by, channel, deadline_at, status)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
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
                ]
            );
            Notifications::toUnit(
                $unit,
                'dispatch',
                'مدارک جدید دریافت شد',
                'پرونده ' . $this->codeOf($reportId) . ' برای واحد شما ارسال شد.',
                $reportId
            );
        }
    }

    private function codeOf(string $reportId): string
    {
        $row = Db::one('SELECT tracking_code, temp_code FROM reports WHERE id = ?', [$reportId]);
        foreach (['tracking_code', 'temp_code'] as $column) {
            if (($row[$column] ?? '') !== '') {
                return (string) $row[$column];
            }
        }
        return $reportId;
    }

    /**
     * سطرهای فرزند یک پرونده، یک‌جا جایگزین می‌شوند. حذف یک دستگاه روی گوشی
     * باید روی سرور هم حذف شود، و با ادغام سطر‌به‌سطر چنین چیزی هرگز اتفاق
     * نمی‌افتاد.
     */
    private function replaceChildren(string $table, array $rows, string $reportId, array $columns): void
    {
        Db::run("DELETE FROM $table WHERE report_id = ?", [$reportId]);
        if ($rows === []) {
            return;
        }
        $placeholders = implode(', ', array_fill(0, count($columns), '?'));
        $sql = "INSERT INTO $table (" . Db::cols($columns) . ") VALUES ($placeholders)";
        foreach ($rows as $row) {
            if (!is_array($row)) {
                continue;
            }
            $row['report_id'] = $reportId;
            Db::run($sql, array_map(static fn(string $c) => $row[$c] ?? null, $columns));
        }
    }
}
