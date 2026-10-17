<?php
declare(strict_types=1);

/**
 * ثبت گزارش روی سرور، به دست مدیر یا مجری رمزارز.
 *
 * گزارشی که مدیر ثبت کرد و تا مهلت تنظیمات (refer_days روز کاری) به مجری یا
 * کارشناسی نسپرد، خودکار به مجری ناحیه می‌رسد (FlowClock). گزارشی که مجری ثبت
 * کرد از همان لحظه در دست خود اوست. ثبت فقط «گزارش» است، نه بازدید: پرونده با
 * وضعیت «در دست اقدام» ساخته می‌شود و بازدیدش را کارشناس روی گوشی انجام می‌دهد.
 */
final class CaseIntake
{
    public const ORIGIN_EXPERT = 0;
    public const ORIGIN_MANAGER = 1;
    public const ORIGIN_EXECUTOR = 2;

    public const ORIGIN_NAMES = [0 => 'ثبت کارشناس', 1 => 'ثبت مدیر', 2 => 'ثبت مجری'];

    /**
     * ایراد فرم ثبت، یا تهی.
     *
     * @param array<string, mixed> $data
     */
    public static function problem(array $data, Scope $scope): ?string
    {
        if (!$scope->managesCases()) {
            return 'ثبت گزارش برای مدیر و مجری رمزارز است.';
        }
        $type = (int) ($data['report_type'] ?? 0);
        if (!isset(CaseLabels::REPORT_TYPES[$type])) {
            return 'نوع گزارش را انتخاب کنید.';
        }
        $area = Areas::code($data['area_code'] ?? null);
        if ($area === null) {
            return 'ناحیه را انتخاب کنید.';
        }
        if (!$scope->coversArea($area)) {
            return 'این ناحیه در محدوده شما نیست.';
        }
        if (trim((string) ($data['address'] ?? '')) === '') {
            return 'آدرس یا نشانی محل را بنویسید.';
        }
        if ($type === 1 && trim((string) ($data['tracking_code'] ?? '')) === '') {
            return 'برای سامانه سراغ، کد رهگیری خود سامانه را بنویسید.';
        }
        foreach (['address' => 500, 'district' => 100, 'note' => 2000, 'owner_name' => 191, 'subscriber_name' => 191] as $key => $max) {
            if (mb_strlen(trim((string) ($data[$key] ?? ''))) > $max) {
                return 'متن یکی از فیلدها بیش از حد بلند است.';
            }
        }
        $code = trim((string) ($data['tracking_code'] ?? ''));
        if ($code !== '' && Db::one('SELECT id FROM reports WHERE tracking_code = ?', [Jalali::latin($code)]) !== null) {
            return 'پرونده‌ای با همین کد رهگیری از قبل هست.';
        }
        return null;
    }

    /**
     * پرونده تازه؛ شناسه‌اش را برمی‌گرداند. ارجاع (به مجری یا کارشناس) جداست و از
     * CaseFlow می‌گذرد تا قاعده‌اش یکی بماند.
     *
     * @param array<string, mixed> $data
     */
    public static function create(array $data, Scope $scope): string
    {
        $problem = self::problem($data, $scope);
        if ($problem !== null) {
            Response::fail(400, 'invalid_intake', $problem);
        }
        $user = $scope->user;
        $now = Db::now();
        $type = (int) $data['report_type'];
        $area = (string) Areas::code($data['area_code']);
        $reportDate = self::date((string) ($data['report_date'] ?? '')) ?? $now;
        $subscription = Jalali::latin(trim((string) ($data['subscription_number'] ?? '')));
        [$intakeCode, $sequence] = CaseCodes::nextIntake($now);
        $tracking = $type === 1 ? Jalali::latin(trim((string) $data['tracking_code']))
            : CaseCodes::final($type, $area, $reportDate, $subscription);
        if ($tracking !== null && Db::one('SELECT id FROM reports WHERE tracking_code = ?', [$tracking]) !== null) {
            $tracking = null;
        }
        $temp = $tracking === null ? CaseCodes::temporary($type, $area, $reportDate, $sequence) : null;
        $id = Db::uuid();
        $text = static fn (string $k): ?string => ($v = trim((string) ($data[$k] ?? ''))) === '' ? null : $v;

        Db::run(
            'INSERT INTO reports (id, tracking_code, temp_code, report_type, status, expert_code, report_date,
                                  created_at, updated_at, county, area_code, district, address, postal_code,
                                  subscription_number, owner_name, owner_phone, subscriber_name, approval_state)
             VALUES (?, ?, ?, ?, 0, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)',
            [$id, $tracking, $temp, $type, $reportDate, $now, $now, Areas::NAMES[$area] ?? null, $area,
             $text('district'), $text('address'), $text('postal_code'), $subscription === '' ? null : $subscription,
             $text('owner_name'), $text('owner_phone'), $text('subscriber_name')]
        );
        $byManager = $scope->isManager();
        CaseFlow::touch($id, [
            'origin' => $byManager ? self::ORIGIN_MANAGER : self::ORIGIN_EXECUTOR,
            'intake_code' => $intakeCode,
            'intake_by' => $user['id'],
            'intake_at' => $now,
            'intake_note' => $text('note'),
            'refer_due_at' => $byManager
                ? CrewCalendar::fromSettings()->addWorkDays($now, (int) FieldSettings::all()['refer_days']) : null,
            'executor_id' => $byManager ? null : $user['id'],
            'executor_at' => $byManager ? null : $now,
        ]);
        CaseFlow::event($id, (string) $user['id'], 'intake', $text('note'));
        return $id;
    }

    /** «۱۴۰۵/۰۷/۱۲» یا «1405-07-12» به میلی‌ثانیه ظهر همان روز؛ تهی برای چیز دیگر. */
    private static function date(string $text): ?int
    {
        $parts = Jalali::parse($text);
        return $parts === null ? null : Jalali::startOfDay(...$parts) + 12 * 3_600_000;
    }
}
