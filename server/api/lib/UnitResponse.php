<?php
declare(strict_types=1);

/**
 * پاسخ واحد به یک ارسال: شرح اقدام، مرحله‌ای که کار به آن رسیده، و مدارک. یک
 * قاعده برای پرتال و API.
 *
 * مرحله فقط جلو می‌رود: پاسخی که مرحله پایین‌تری بگوید، توضیح است و مرحله ثبت‌شده
 * را عقب نمی‌برد. آخرین مرحله واحد یعنی پرونده در آن واحد بسته شد.
 */
final class UnitResponse
{
    /**
     * @param list<array<string, mixed>> $files فایل‌های بارگذاری‌شده به شکل معمول PHP
     * @return string|array{id:string} خطای فارسی، یا شناسه پاسخ
     */
    public static function record(array $dispatch, array $user, string $body, ?int $stage, array $files): string|array
    {
        $body = trim($body);
        $unit = (int) $dispatch['unit'];
        $stages = UnitStages::of($unit);
        if ($stage !== null && ($stage < 0 || $stage >= count($stages))) {
            return 'مرحله اقدام نامعتبر است.';
        }
        if ($stage === null) {
            return 'مرحله‌ای را که کار به آن رسیده انتخاب کنید.';
        }
        if ($body === '') {
            return 'شرح اقدام انجام‌شده را بنویسید.';
        }
        if (mb_strlen($body) > 5000) {
            return 'شرح اقدام بیش از حد بلند است.';
        }
        $now = Db::now();
        $responseId = Db::uuid();
        Db::run(
            'INSERT INTO dispatch_responses (id, dispatch_id, responder_id, body, stage, created_at) VALUES (?, ?, ?, ?, ?, ?)',
            [$responseId, $dispatch['id'], $user['id'], $body, $stage, $now]
        );
        foreach ($files as $file) {
            if ((int) ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
                continue;
            }
            $stored = Storage::put($file, 'responses/' . date('Y/m'));
            Db::run(
                'INSERT INTO response_files (id, response_id, file_path, file_name, mime_type, size_bytes, created_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?)',
                [Db::uuid(), $responseId, $stored['path'], (string) ($file['name'] ?? ''), (string) ($file['type'] ?? ''),
                 $stored['size'], $now]
            );
        }
        $forward = $dispatch['stage'] === null || $stage > (int) $dispatch['stage'];
        Db::run(
            'UPDATE dispatches SET status = 2, answered_at = COALESCE(answered_at, ?), seen_at = COALESCE(seen_at, ?),
                    answer = ?' . ($forward ? ', stage = ?, stage_at = ?' : '') . ' WHERE id = ?',
            array_merge([$now, $now, mb_substr($body, 0, 500)], $forward ? [$stage, $now] : [], [$dispatch['id']])
        );
        $report = Db::one('SELECT id, tracking_code, temp_code, area_code FROM reports WHERE id = ?', [$dispatch['report_id']]) ?? [];
        $text = (UnitStages::NAMES[$unit] ?? '') . ' — پرونده ' . CaseLabels::code($report) . ': ' . $stages[$stage];
        CaseFlow::event((string) $dispatch['report_id'], (string) $user['id'], 'unit_stage', $text . "\n" . $body);
        Notifications::toRole(Auth::ROLE_MANAGER, 'response', 'اقدام واحد ثبت شد', $text, (string) $dispatch['report_id']);
        Notifications::toExecutors($report['area_code'] ?? null, 'response', 'اقدام واحد ثبت شد', $text,
            (string) $dispatch['report_id']);
        return ['id' => $responseId];
    }

    /** فایل‌های چندتایی فرم (files[]) به شکل آرایه‌ای از فایل‌ها. */
    public static function uploaded(string $field = 'files'): array
    {
        $out = [];
        $names = $_FILES[$field]['name'] ?? [];
        if (!is_array($names)) {
            return isset($_FILES[$field]) ? [$_FILES[$field]] : [];
        }
        foreach (array_keys($names) as $i) {
            $out[] = [
                'name' => $_FILES[$field]['name'][$i] ?? '', 'type' => $_FILES[$field]['type'][$i] ?? '',
                'tmp_name' => $_FILES[$field]['tmp_name'][$i] ?? '', 'error' => $_FILES[$field]['error'][$i] ?? UPLOAD_ERR_NO_FILE,
                'size' => $_FILES[$field]['size'][$i] ?? 0,
            ];
        }
        return $out;
    }

    /** پاسخ‌های یک ارسال با فایل‌ها و نام مرحله. */
    public static function list(array $dispatch): array
    {
        $rows = Db::all(
            'SELECT r.*, u.full_name AS responder_name FROM dispatch_responses r LEFT JOIN users u ON u.id = r.responder_id
             WHERE r.dispatch_id = ? ORDER BY r.created_at',
            [$dispatch['id']]
        );
        foreach ($rows as &$row) {
            $row['stage_name'] = UnitStages::name((int) $dispatch['unit'], $row['stage'] === null ? null : (int) $row['stage']);
            $row['files'] = Db::all('SELECT id, file_path, file_name, mime_type, size_bytes FROM response_files WHERE response_id = ?',
                [$row['id']]);
        }
        return $rows;
    }
}
