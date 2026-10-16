<?php
declare(strict_types=1);

/**
 * کار هر مورد میدانی به زبان عملکرد اکیپ: رویدادها (هر نقطه‌ای که اکیپ در
 * آن کاری ثبت کرده، با لحظه ثبت موقعیتش) و اکیپ مورد. «بازرسی» هر آمپرگیری
 * تابلو یا تیر، هر تابلو یا تیری که ترموویژن شده، و هر بازرسی رمزارز یا برق
 * غیرمجاز است؛ «بازدید» رفتن اکیپ سر یک تابلو و همه بازرسی‌های همان تابلو و
 * فیدرهای فشار ضعیفش (CrewDays). ردیابی پیوسته‌ای در کار نیست.
 */
final class CrewWork
{
    public const TYPE_NAMES = [
        'panel' => 'آمپرگیری تابلو', 'pole' => 'آمپرگیری تیر', 'thermal' => 'ترموویژن',
        'crypto' => 'بازرسی رمزارز', 'illegal' => 'بازرسی برق غیرمجاز',
    ];

    /**
     * رویدادهای یک مورد. $item: id، kind، tracking_code، plate، latitude، longitude،
     * position_at، created_at، payload (آرایه)، و برای ترموویژن frames (captured_at،
     * latitude، longitude، asset_type، plate).
     *
     * @return list<array{at:int, type:string, plate:string, panel:?string, lat:?float, lon:?float,
     *   item:string, code:string, inspection:bool, subs:int}>
     */
    public static function events(array $item): array
    {
        $p = (array) ($item['payload'] ?? []);
        $at = (int) ($item['position_at'] ?? 0) ?: (int) ($item['created_at'] ?? 0);
        $plate = trim((string) ($item['plate'] ?? ''));
        $base = ['item' => (string) $item['id'], 'code' => (string) ($item['tracking_code'] ?? ''), 'subs' => 0];
        $here = ['lat' => self::num($item['latitude'] ?? null), 'lon' => self::num($item['longitude'] ?? null)];
        $events = [];
        switch ((int) $item['kind']) {
            case Field::KIND_FEEDER:
                $events[] = ['at' => $at, 'type' => 'panel', 'plate' => $plate, 'panel' => self::key($plate),
                    'inspection' => true] + $here + $base;
                break;
            case Field::KIND_LINE:
                $start = (array) ($p['start'] ?? []);
                $measured = static fn (array $ph): bool => is_numeric($ph['r'] ?? null) && is_numeric($ph['s'] ?? null)
                    && is_numeric($ph['t'] ?? null);
                // The panel the walk starts from: a visit's anchor, an inspection when its feeder was read there.
                $events[] = ['at' => $at, 'type' => 'panel', 'plate' => $plate, 'panel' => self::key($plate),
                    'inspection' => $measured($start)] + $here + $base;
                foreach (array_values((array) ($p['poles'] ?? [])) as $pole) {
                    $pole = (array) $pole;
                    $read = ($pole['state'] ?? 'measured') === 'measured' && $measured((array) ($pole['phases'] ?? []));
                    $lat = self::num($pole['lat'] ?? null);
                    if (!$read && $lat === null) {
                        continue;
                    }
                    $events[] = ['at' => (int) ($pole['at'] ?? 0) ?: $at, 'type' => 'pole',
                        'plate' => trim((string) ($pole['plate'] ?? '')), 'panel' => self::key($plate), 'inspection' => $read,
                        'lat' => $lat, 'lon' => self::num($pole['lon'] ?? null),
                        'subs' => count((array) ($pole['subscribers'] ?? []))] + $base;
                }
                break;
            case Field::KIND_THERMAL:
                // One inspection per panel or pole photographed, at its first frame.
                $assets = [];
                foreach ((array) ($item['frames'] ?? []) as $f) {
                    $assetPlate = trim((string) ($f['plate'] ?? ''));
                    if ($assetPlate === '' || $f['asset_type'] === null || $f['captured_at'] === null) {
                        continue;
                    }
                    $k = (int) $f['asset_type'] . ':' . self::key($assetPlate);
                    if (!isset($assets[$k]) || (int) $f['captured_at'] < $assets[$k]['at']) {
                        $assets[$k] = ['at' => (int) $f['captured_at'], 'plate' => $assetPlate,
                            'panel' => (int) $f['asset_type'] === Field::ASSET_PANEL,
                            'lat' => self::num($f['latitude'] ?? null), 'lon' => self::num($f['longitude'] ?? null)];
                    }
                }
                $panels = array_values(array_filter($assets, static fn (array $a): bool => $a['panel']));
                $routePanel = $panels === [] ? null : self::key($panels[0]['plate']);
                foreach ($assets as $a) {
                    $events[] = ['at' => $a['at'], 'type' => 'thermal', 'plate' => $a['plate'],
                        'panel' => $a['panel'] ? self::key($a['plate']) : $routePanel, 'inspection' => true,
                        'lat' => $a['lat'], 'lon' => $a['lon']] + $base;
                }
                break;
            case Field::KIND_CRYPTO:
            case Field::KIND_ILLEGAL:
                $events[] = ['at' => $at, 'type' => (int) $item['kind'] === Field::KIND_CRYPTO ? 'crypto' : 'illegal',
                    'plate' => $plate, 'panel' => null, 'inspection' => true] + $here + $base;
                break;
        }
        return array_values(array_filter($events, static fn (array $e): bool => $e['at'] > 0));
    }

    /**
     * اکیپ مورد: هر کس نام برده شده، با کلیدی که یک نفر را همیشه یکی می‌شمارد —
     * شناسه فهرست مدیر، یا نام وقتی فهرست هنوز به گوشی نرسیده بود.
     *
     * @return list<array{key:string, name:string, role:int}>
     */
    public static function crew(array $payload): array
    {
        $crew = (array) ($payload['crew'] ?? []);
        $people = [];
        foreach (FieldRoster::CREW_KEYS as $slot => $role) {
            $pick = $crew[$slot] ?? null;
            $name = is_array($pick) ? trim((string) ($pick['name'] ?? '')) : '';
            if ($name === '') {
                continue;
            }
            $id = trim((string) ($pick['id'] ?? ''));
            $people[] = ['key' => $id !== '' ? 'id:' . $id : 'name:' . preg_replace('/\s+/u', ' ', $name),
                'name' => $name, 'role' => $role];
        }
        return $people;
    }

    /** پلاک یکسان‌شده برای کنار هم گذاشتن: رقم لاتین، بی فاصله و خط تیره، حروف بزرگ. */
    public static function key(string $plate): ?string
    {
        $k = strtoupper(preg_replace('/[\s\-_\/]+/u', '', Jalali::latin($plate)) ?? '');
        return $k === '' ? null : $k;
    }

    private static function num(mixed $v): ?float
    {
        return is_numeric($v) ? (float) $v : null;
    }
}
