<?php
declare(strict_types=1);

require_once __DIR__ . '/CaseKind.php';

/**
 * یک پرونده بازدید کامل، و هفت بخش فرم رسمی‌اش (CLAUDE.md بخش ۷) به صورت
 * ردیف‌های [عنوان، مقدار]. صفحه پرونده، برگه چاپی (PDF) و خروجی Word هر سه از
 * همین ساخته می‌شوند — مثل ReportFields.cs ویندوز و TechnicalRows.kt گوشی.
 * ردیف بی‌مقدار کنار گذاشته می‌شود تا فرم پرسشی را پاسخ‌داده نشان ندهد.
 */
final class CaseReport
{
    /** @return array<string, mixed>|null */
    public static function load(string $id): ?array
    {
        $report = Db::one(
            'SELECT r.*, u.full_name AS expert_name FROM reports r
             LEFT JOIN users u ON u.user_code = r.expert_code WHERE r.id = ?',
            [$id]
        );
        if ($report === null) {
            return null;
        }
        return [
            'report' => $report,
            'devices' => Db::all('SELECT * FROM devices WHERE report_id = ? ORDER BY `row_number`', [$id]),
            'attendees' => Db::all('SELECT * FROM attendees WHERE report_id = ? ORDER BY organization, full_name', [$id]),
            'media' => Db::all('SELECT * FROM media WHERE report_id = ? ORDER BY captured_at', [$id]),
            'attachments' => Db::all('SELECT * FROM attachments WHERE report_id = ? ORDER BY added_at', [$id]),
            'dispatches' => Db::all('SELECT * FROM dispatches WHERE report_id = ? ORDER BY dispatched_at', [$id]),
        ];
    }

    /** @return array<int, array{0:string, 1:string}> */
    private static function keep(array $rows): array
    {
        return array_values(array_filter($rows, static fn ($r) => $r[1] !== null && trim((string) $r[1]) !== ''));
    }

    public static function caseRows(array $r): array
    {
        $expert = trim((string) ($r['expert_name'] ?? ''));
        return self::keep([
            ['کد رهگیری', CaseLabels::code($r)],
            ['نوع گزارش', CaseLabels::of(CaseLabels::REPORT_TYPES, $r['report_type'])],
            ['تاریخ گزارش', Jalali::format((int) $r['report_date'], false)],
            ['تاریخ بازدید', $r['visit_date'] === null ? null : Jalali::format((int) $r['visit_date'], false)],
            ['کارشناس', self::expert($expert, (string) $r['expert_code'])],
            ['وضعیت پرونده', CaseLabels::of(CaseLabels::STATUSES, $r['status'])],
            ['تأیید مدیر', CaseLabels::of(CaseLabels::APPROVALS, $r['approval_state'])],
        ]);
    }

    /** «نام (کد)»، یا هر کدام که هست. */
    public static function expert(string $name, string $code): string
    {
        $code = Jalali::digits(trim($code));
        return $name === '' ? $code : ($code === '' ? $name : "$name ($code)");
    }

    public static function locationRows(array $r): array
    {
        return self::keep([
            ['شهرستان', CaseLabels::countyWithArea($r['county'], $r['area_code'])],
            ['بخش', $r['district']],
            ['آدرس', $r['address']],
            ['کد پستی', Jalali::digits((string) $r['postal_code'])],
            ['شماره پرونده', Jalali::digits((string) $r['file_number'])],
            ['شماره قبض', Jalali::digits((string) $r['bill_number'])],
            ['شماره اشتراک', Jalali::digits((string) $r['subscription_number'])],
            ['نام مشترک', $r['subscriber_name'] ?? null],
            ['نوع کاربری', $r['usage_type']],
            ['مختصات', self::coordinates($r)],
        ]);
    }

    public static function ownerRows(array $r): array
    {
        return self::keep([
            ['نام و نام خانوادگی', $r['owner_name']],
            ['کد ملی', Jalali::digits((string) $r['owner_national_id'])],
            ['تلفن', Jalali::digits((string) $r['owner_phone'])],
            ['نسبت با محل', $r['owner_relation']],
        ]);
    }

    /**
     * بخش چهار، همان‌طور که گوشی می‌سازد: هر فاز نسبت به نول با توان خودش،
     * بعد مجموع‌ها. توان هر فاز = ولتاژ × جریان همان فاز (بی √۳؛ CLAUDE.md بخش ۷).
     */
    public static function technicalRows(array $r): array
    {
        $rows = [
            ['محل برداشت برق', CaseLabels::of(CaseLabels::TAP_POINTS, $r['tap_point'])],
            ['نوع انشعاب', CaseLabels::of(CaseLabels::PHASE_TYPES, $r['phase_type'])],
        ];
        $single = $r['phase_type'] !== null && (int) $r['phase_type'] === 0;
        $phases = $single ? ['r' => ''] : ($r['phase_type'] !== null ? ['r' => 'R', 's' => 'S', 't' => 'T'] : []);
        foreach ($phases as $key => $name) {
            $amps = $r['amperage_' . $key];
            $volts = $r['voltage_' . $key];
            $rows[] = [$single ? 'آمپر' : "آمپر فاز $name", CaseLabels::number($amps)];
            $rows[] = [$single ? 'ولتاژ' : "ولتاژ فاز $name", CaseLabels::number($volts)];
            if (!$single && is_numeric($amps) && is_numeric($volts)) {
                $rows[] = ["توان فاز $name", CaseLabels::grouped((float) $amps * (float) $volts) . ' وات'];
            }
        }
        if (is_numeric($r['measured_amperage'])) {
            $rows[] = ['مجموع آمپر مصرفی', CaseLabels::number($r['measured_amperage']) . ' آمپر'];
        }
        if (is_numeric($r['total_watt'])) {
            $rows[] = ['مجموع توان', CaseLabels::grouped($r['total_watt']) . ' وات'];
            $rows[] = ['مجموع توان (کیلووات)', CaseLabels::number((float) $r['total_watt'] / 1000, 2) . ' کیلووات'];
        }
        array_push($rows,
            ['نوع تعرفه در محل', CaseLabels::of(CaseLabels::TARIFFS, $r['tariff_type'])],
            ['نوع کنتور', CaseLabels::of(CaseLabels::METERS, $r['meter_type'])],
            ['آیا کنتور پلمب خارجی دارد؟', CaseLabels::yesNo($r['seal_external'])],
            ['سریال پلمب', Jalali::digits((string) $r['seal_external_serial'])],
            ['آیا کنتور پلمب داخلی دارد؟', CaseLabels::yesNo($r['seal_internal'])],
            ['وضعیت ظاهری کنتور سالم است؟', CaseLabels::yesNo($r['meter_appearance_ok'])],
            ['دستکاری در کنتور وجود دارد؟', CaseLabels::yesNo($r['meter_tampered'])],
        );
        return self::keep($rows);
    }

    /** @return list<list<string>> ردیف، مدل، سریال، توان، روش ثبت */
    public static function deviceTable(array $devices): array
    {
        return array_map(static fn ($d) => [
            Jalali::digits((string) $d['row_number']), (string) $d['model'], (string) $d['serial_number'],
            CaseLabels::grouped($d['power_watt']) ?? '', CaseLabels::of(CaseLabels::ENTRY_METHODS, $d['entry_method']) ?? '',
        ], $devices);
    }

    /** @return list<list<string>> ردیف، نام، سمت، سازمان */
    public static function attendeeTable(array $attendees): array
    {
        $rows = [];
        foreach (array_values($attendees) as $i => $a) {
            $org = trim((string) $a['org_name']) !== '' ? (string) $a['org_name']
                : (CaseLabels::of(CaseLabels::ORGANIZATIONS, $a['organization']) ?? '');
            $rows[] = [Jalali::digits((string) ($i + 1)), (string) $a['full_name'], (string) $a['position'], $org];
        }
        return $rows;
    }

    public static function coordinates(array $r): ?string
    {
        if (!is_numeric($r['latitude']) || !is_numeric($r['longitude'])) {
            return null;
        }
        $text = sprintf('%.6f , %.6f', (float) $r['latitude'], (float) $r['longitude']);
        if (is_numeric($r['gps_accuracy'])) {
            $text .= sprintf(' (± %d متر)', (int) round((float) $r['gps_accuracy']));
        }
        return Jalali::digits($text);
    }

    /** شرح زیر هر تصویر پیوست: شرح، زمان، مختصات و کد پرونده. */
    public static function photoCaption(array $report, array $photo): string
    {
        $parts = array_filter([
            trim((string) $photo['caption']),
            Jalali::format((int) $photo['captured_at']),
            is_numeric($photo['latitude']) ? Jalali::digits(sprintf('%.5f , %.5f', $photo['latitude'], $photo['longitude'])) : '',
            CaseLabels::code($report),
        ], static fn ($p) => $p !== '');
        return implode(' | ', $parts);
    }

    /**
     * فرم رسمی به شکل بلوک‌های Word (Docx): هفت بخش، جدول‌ها، امضاها و پیوست تصاویر.
     * پنل و بسته ارسال هر دو از همین می‌سازند؛ نام نرم‌افزار در آن نیست.
     */
    /** عنوان فرم رسمی: بازدید رمزارز، یا بازرسی برق غیرمجاز. */
    public static function title(array $r): string
    {
        return CaseKind::of($r) === CaseKind::ILLEGAL ? 'فرم گزارش بازرسی برق غیرمجاز' : 'فرم گزارش بازدید مراکز استخراج رمزارز';
    }

    /**
     * عنوان بخش‌های فرم به ترتیب؛ پرونده برق غیرمجاز فهرست دستگاه ندارد و شماره بخش‌های
     * بعدش یکی جلو می‌آید.
     *
     * @return array<string, string>
     */
    public static function headings(array $r): array
    {
        $illegal = CaseKind::of($r) === CaseKind::ILLEGAL;
        $names = ['case' => 'مشخصات پرونده', 'place' => 'مشخصات مکان', 'owner' => 'مشخصات مالک یا متصرف', 'tech' => 'مشخصات فنی',
            'devices' => 'فهرست دستگاه‌ها', 'people' => 'حاضرین در محل', 'text' => $illegal ? 'شرح بازرسی و اقدامات' : 'شرح بازدید و اقدامات'];
        if ($illegal) {
            unset($names['devices']);
        }
        $out = [];
        $n = 0;
        foreach ($names as $key => $name) {
            $out[$key] = Jalali::digits((string) ++$n) . ' — ' . $name;
        }
        return $out;
    }

    public static function wordBlocks(array $case): array
    {
        $r = $case['report'];
        $code = CaseLabels::code($r);
        $h = self::headings($r);
        $blocks = [
            ['h', self::title($r)],
            ['p', 'شرکت توزیع نیروی برق استان ایلام — ' . $code],
            ['h', $h['case']], ['table', CaseReport::caseRows($r)],
            ['h', $h['place']], ['table', CaseReport::locationRows($r)],
            ['h', $h['owner']], ['table', CaseReport::ownerRows($r)],
            ['h', $h['tech']], ['table', CaseReport::technicalRows($r)],
        ];
        if (isset($h['devices'])) {
            $blocks[] = ['h', $h['devices']];
            $blocks[] = ['grid', [['ردیف', 'مدل', 'شماره سریال', 'توان (وات)', 'روش ثبت'], CaseReport::deviceTable($case['devices'])]];
        }
        array_push($blocks,
            ['h', $h['people']],
            ['grid', [['ردیف', 'نام و نام خانوادگی', 'سمت', 'سازمان'], CaseReport::attendeeTable($case['attendees'])]],
            ['h', $h['text']],
            ['table', [['شرح', (string) ($r['description'] ?: '—')], ['اقدامات انجام‌شده', (string) ($r['actions_taken'] ?: '—')]]]);
        if ($case['attachments'] !== []) {
            $blocks[] = ['h', 'مستندات پیوست'];
            $blocks[] = ['grid', [['دسته', 'عنوان', 'تاریخ'], array_map(static fn ($a) => [
                (string) CaseLabels::of(CaseLabels::ATTACHMENTS, $a['category']), (string) $a['title'],
                Jalali::format((int) $a['added_at'], false)], $case['attachments'])]];
        }
        $blocks[] = ['table', [['نام و امضای کارشناس', ' '], ['نام و امضای حاضرین', ' '], ['مهر و امضای مدیر', ' ']]];
        $photos = array_values(array_filter($case['media'], static fn ($m) => (int) $m['type'] === 0));
        if ($photos !== []) {
            $blocks[] = ['h', 'پیوست تصاویر'];
            foreach ($photos as $m) {
                $blocks[] = ['image', [(string) Storage::resolve((string) $m['file_path']), CaseReport::photoCaption($r, $m)]];
            }
        }
        return $blocks;
    }
}
