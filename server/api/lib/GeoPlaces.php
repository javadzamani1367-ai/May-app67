<?php
declare(strict_types=1);

/**
 * شهرها، روستاها و مرز شهرستان‌های ایلام، از همان فایل نشانی آفلاینی که
 * اپ میدانی دارد (ilam.geo درون api/maps/tavankav-ilam-map.zip؛ قالبش در
 * tools/map/build_geo.py). سرور از آن دو چیز می‌خواهد: «بیرون از محدوده
 * شهر» برای عملکرد اکیپ، و زمینه نقشه کوچک گزارش Word. بی آن فایل، هر دو
 * بی‌صدا کنار می‌روند.
 */
final class GeoPlaces
{
    public const KIND_CITY = 0;
    public const KIND_TOWN = 1;
    public const KIND_VILLAGE = 2;
    /** شعاعی که تا آن، نقطه درون شهر یا شهرک حساب می‌شود (متر). */
    public const CITY_RADIUS_M = 4000.0;
    public const TOWN_RADIUS_M = 2000.0;
    /** بیشترین نقطه هر مرز برای کشیدن؛ بیشتر از این، نقشه کوچک چیزی نمی‌افزاید. */
    private const RING_POINTS = 300;

    /**
     * @param list<array{0:string,1:int,2:float,3:float}> $places نام، نوع، عرض، طول
     * @param list<array{0:string,1:list<list<array{0:float,1:float}>>}> $counties نام و حلقه‌های بیرونی
     */
    public function __construct(public readonly array $places, public readonly array $counties)
    {
    }

    /** از فایل zip نقشه، با یک نسخه حاضر در storage تا هر گزارش آن را دوباره نخواند. */
    public static function load(?string $zip = null, ?string $cacheDir = null): ?self
    {
        $zip ??= __DIR__ . '/../maps/tavankav-ilam-map.zip';
        if (!is_file($zip) || !class_exists(ZipArchive::class)) {
            return null;
        }
        $cache = $cacheDir === null ? null : $cacheDir . '/geo-places-' . md5($zip . ':' . filemtime($zip)) . '.json';
        if ($cache !== null && is_file($cache)) {
            $data = json_decode((string) file_get_contents($cache), true);
            if (is_array($data)) {
                return new self($data['places'], $data['counties']);
            }
        }
        $archive = new ZipArchive();
        if ($archive->open($zip) !== true) {
            return null;
        }
        $bytes = $archive->getFromName('ilam.geo');
        $archive->close();
        $geo = is_string($bytes) ? self::parse($bytes) : null;
        if ($geo !== null && $cache !== null && (is_dir($cacheDir) || @mkdir($cacheDir, 0750, true))) {
            @file_put_contents($cache, json_encode(['places' => $geo->places, 'counties' => $geo->counties], JSON_UNESCAPED_UNICODE));
        }
        return $geo;
    }

    /** خواندن قالب TKGEO نسخه ۱؛ فایل ناقص یا ناشناخته تهی می‌شود. */
    public static function parse(string $b): ?self
    {
        if (strlen($b) < 26 || substr($b, 0, 5) !== 'TKGEO' || ord($b[5]) !== 1) {
            return null;
        }
        $at = 6 + 16;
        $int = static function () use ($b, &$at): int {
            $v = unpack('N', $b, $at)[1];
            $at += 4;
            return $v > 0x7FFFFFFF ? $v - 0x100000000 : $v;
        };
        $byte = static function () use ($b, &$at): int {
            return ord($b[$at++]);
        };
        try {
            $strings = [];
            for ($i = 0, $n = $int(); $i < $n; $i++) {
                $len = unpack('n', $b, $at)[1];
                $strings[] = substr($b, $at + 2, $len);
                $at += 2 + $len;
            }
            $places = [];
            for ($i = 0, $n = $int(); $i < $n; $i++) {
                [$name, $kind, $lat, $lon] = [$int(), $byte(), $int(), $int()];
                if ($kind <= self::KIND_VILLAGE) {
                    $places[] = [$strings[$name] ?? '', $kind, $lat / 1e6, $lon / 1e6];
                }
            }
            for ($i = 0, $n = $int(); $i < $n; $i++) {
                $int();
                $byte();
                $at += 8 * $int();
            }
            $counties = [];
            for ($i = 0, $n = $int(); $i < $n; $i++) {
                [$name, $level, $rings] = [$int(), $byte(), $int()];
                $outer = [];
                for ($r = 0; $r < $rings; $r++) {
                    [$isOuter, $count] = [$byte(), $int()];
                    $step = max(1, intdiv($count, self::RING_POINTS));
                    $ring = [];
                    for ($p = 0; $p < $count; $p++) {
                        [$lat, $lon] = [$int(), $int()];
                        if ($p % $step === 0) {
                            $ring[] = [$lat / 1e6, $lon / 1e6];
                        }
                    }
                    if ($isOuter === 1 && $level === 5) {
                        $outer[] = $ring;
                    }
                }
                if ($outer !== []) {
                    $counties[] = [$strings[$name] ?? '', $outer];
                }
            }
        } catch (Throwable) {
            return null;
        }
        return new self($places, $counties);
    }

    /**
     * نزدیک‌ترین آبادی و اینکه نقطه بیرون از محدوده شهر است یا نه: درون شعاع
     * یک شهر یا شهرک نیست. روستا هم «بیرون از شهر» است.
     *
     * @return array{outside:bool, place:?string, kind:?int, distance:?float}
     */
    public function locate(float $lat, float $lon): array
    {
        $best = null;
        $inside = false;
        foreach ($this->places as [$name, $kind, $plat, $plon]) {
            $d = PositionAudit::distance($lat, $lon, $plat, $plon);
            if ($kind === self::KIND_CITY && $d <= self::CITY_RADIUS_M || $kind === self::KIND_TOWN && $d <= self::TOWN_RADIUS_M) {
                $inside = true;
            }
            if ($best === null || $d < $best[2]) {
                $best = [$name, $kind, $d];
            }
        }
        return ['outside' => !$inside, 'place' => $best[0] ?? null, 'kind' => $best[1] ?? null,
            'distance' => $best === null ? null : round($best[2])];
    }

    /** شهرها و شهرک‌ها درون یک قاب، برای نام‌نوشتن روی نقشه. */
    public function citiesIn(float $south, float $west, float $north, float $east): array
    {
        return array_values(array_filter($this->places, static fn (array $p): bool => $p[1] <= self::KIND_TOWN
            && $p[2] >= $south && $p[2] <= $north && $p[3] >= $west && $p[3] <= $east));
    }
}
