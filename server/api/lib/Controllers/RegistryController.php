<?php
declare(strict_types=1);

/**
 * بانک دستگاه‌های ماینر در اپ مدیر: جستجو، سابقه یک سریال، و هشدارهای تکراری.
 * فقط مدیر — هشدار تکراری عمداً به مجری‌ها نمی‌رسد.
 */
final class RegistryController
{
    public function search(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $get = [];
        foreach (['q', 'area', 'from', 'to', 'dup'] as $key) {
            $get[$key] = $request->str($key);
        }
        if ($request->str('format') === 'xlsx') {
            Xlsx::send('devices', 'بانک دستگاه‌ها', self::HEADER, array_map([self::class, 'row'], DeviceRegistry::search($get, 20000)));
            exit;
        }
        $page = max(0, $request->int('page', 0) ?? 0);
        $rows = DeviceRegistry::search($get, 101, $page * 100);
        Response::json(['devices' => array_map([DeviceRegistry::class, 'present'], array_slice($rows, 0, 100)),
            'more' => count($rows) > 100, 'unseen_alerts' => DeviceRegistry::unseenAlerts()]);
    }

    public function history(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $key = DeviceRegistry::key($request->str('serial'));
        Response::json(['serial_key' => $key,
            'history' => $key === null ? [] : array_map([DeviceRegistry::class, 'present'], DeviceRegistry::history($key))]);
    }

    public function alerts(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $alerts = DeviceRegistry::alerts(200);
        if ($request->str('mark_seen') === '1') {
            DeviceRegistry::markSeen();
        }
        Response::json(['alerts' => array_map(static fn ($a) => [
            'id' => $a['id'], 'serial' => $a['serial_key'], 'report_id' => $a['report_id'], 'other_report_id' => $a['other_report_id'],
            'at' => (int) $a['created_at'], 'seen' => $a['seen_at'] !== null, 'now' => $a['now'], 'before' => $a['before'],
        ], $alerts)]);
    }

    public const HEADER = ['سریال', 'مدل', 'توان (وات)', 'تاریخ کشف', 'کد رهگیری', 'ناحیه', 'بخش', 'آدرس', 'شماره اشتراک',
        'نام مشترک', 'دارنده (مالک یا متصرف)', 'کد ملی دارنده', 'کارشناس', 'دفعات دیده‌شدن'];

    public static function row(array $r): array
    {
        $p = DeviceRegistry::present($r);
        return [$p['serial'], $p['model'], $p['power_watt'], $p['found_at'] === null ? '' : Jalali::format($p['found_at'], false),
            $p['code'], $p['area'], $p['district'], $p['address'], $p['subscription'], $p['subscriber'], $p['owner'],
            $p['owner_national_id'], $p['expert_code'], $p['seen_in']];
    }
}
