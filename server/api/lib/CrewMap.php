<?php
declare(strict_types=1);

/**
 * نقشه کوچک کار اکیپ: مرز شهرستان‌ها و شهرها از GeoPlaces زمینه است، بازرسی‌ها
 * نقطه‌هایی به رنگ نوعشان، و هر بازدید دایره‌ای شماره‌دار. برای صفحه وب SVG
 * با نام‌های فارسی، برای Word تصویر JPEG — GD حروف فارسی را به هم نمی‌چسباند،
 * پس روی تصویر فقط شماره است و توضیح هر شماره در جدول زیر آن می‌آید.
 */
final class CrewMap
{
    public const COLORS = ['panel' => '#2563EB', 'pole' => '#0D9488', 'thermal' => '#EA580C', 'crypto' => '#DC2626', 'illegal' => '#7C3AED'];
    private const VISIT = '#1E3A8A';

    /**
     * رویدادهای یکتا (هر کار یک بار، هرچند چند نفر در اکیپش بودند) و بازدیدهای
     * شماره‌خورده به ترتیب زمان.
     *
     * @return array{events: list<array>, visits: list<array>}
     */
    public static function collect(array $members): array
    {
        $events = [];
        $visits = [];
        foreach ($members as $m) {
            foreach ($m['days'] as $d) {
                foreach ($d['points'] as $p) {
                    $events[$p['code'] . '|' . $p['at'] . '|' . $p['type'] . '|' . $p['plate']] = $p;
                }
                foreach ($d['visit_list'] as $v) {
                    $visits[$v['key']] ??= $v;
                }
            }
        }
        $events = array_values($events);
        usort($events, static fn (array $a, array $b): int => $a['at'] <=> $b['at']);
        $visits = array_values($visits);
        usort($visits, static fn (array $a, array $b): int => $a['first'] <=> $b['first']);
        foreach ($visits as $i => &$v) {
            $v['n'] = $i + 1;
        }
        unset($v);
        return ['events' => $events, 'visits' => $visits];
    }

    /** تبدیل عرض و طول به نقطه تصویر، با قابی که همه نقطه‌ها را با حاشیه بگیرد. */
    private static function frame(array $events, int $w, int $h): ?array
    {
        $located = array_values(array_filter($events, static fn (array $e): bool => $e['lat'] !== null));
        if ($located === []) {
            return null;
        }
        $lats = array_column($located, 'lat');
        $lons = array_column($located, 'lon');
        $midLat = (min($lats) + max($lats)) / 2;
        $k = cos(deg2rad($midLat));
        $spanLat = max(max($lats) - min($lats), 0.01) * 1.3;
        $spanLon = max((max($lons) - min($lons)) * $k, 0.01) * 1.3;
        $scale = min($w / $spanLon, $h / $spanLat);
        $cx = (min($lons) + max($lons)) / 2;
        $cy = $midLat;
        $project = static fn (float $lat, float $lon): array => [
            $w / 2 + ($lon - $cx) * $k * $scale,
            $h / 2 - ($lat - $cy) * $scale,
        ];
        $box = [$cy - $h / 2 / $scale, $cx - $w / 2 / $scale / $k, $cy + $h / 2 / $scale, $cx + $w / 2 / $scale / $k];
        // Metres per pixel, for the scale bar.
        return [$project, $box, 111_320 / $scale];
    }

    /**
     * جای هر دایره بازدید: روی خود نقطه، یا اگر دایره دیگری آنجاست، کمی آن‌طرف‌تر
     * روی یک مارپیچ، تا شماره‌ها روی هم نیفتند؛ خطی کوتاه به جای واقعی برمی‌گردد.
     *
     * @return list<array{0:array, 1:float, 2:float, 3:float, 4:float}> [بازدید، x، y، xواقعی، yواقعی]
     */
    private static function place(array $visits, callable $project, float $gap): array
    {
        $placed = [];
        foreach ($visits as $v) {
            if ($v['lat'] === null) {
                continue;
            }
            [$x0, $y0] = $project($v['lat'], $v['lon']);
            [$x, $y] = [$x0, $y0];
            for ($i = 1; $i < 40; $i++) {
                $clear = true;
                foreach ($placed as [, $px, $py]) {
                    if (hypot($px - $x, $py - $y) < $gap) {
                        $clear = false;
                        break;
                    }
                }
                if ($clear) {
                    break;
                }
                $angle = $i * 2.4;
                [$x, $y] = [$x0 + cos($angle) * $gap * (0.6 + $i * 0.12), $y0 + sin($angle) * $gap * (0.6 + $i * 0.12)];
            }
            $placed[] = [$v, $x, $y, $x0, $y0];
        }
        return $placed;
    }

    /** SVG برای صفحه وب: نام شهرها و شهرستان‌ها فارسی، و شرح هر نقطه با نگه داشتن نشانگر. */
    public static function svg(array $collected, ?GeoPlaces $geo, int $w = 720, int $h = 480): string
    {
        $frame = self::frame($collected['events'], $w, $h);
        if ($frame === null) {
            return '';
        }
        [$project, $box] = $frame;
        $out = '<svg viewBox="0 0 ' . $w . ' ' . $h . '" width="100%" role="img" style="background:#F8FAFC;border-radius:10px">';
        foreach ($geo?->counties ?? [] as [$name, $rings]) {
            foreach ($rings as $ring) {
                $pts = implode(' ', array_map(static fn (array $p): string => vsprintf('%.1f,%.1f', $project($p[0], $p[1])), $ring));
                $out .= '<polygon points="' . $pts . '" fill="#EEF2F7" stroke="#94A3B8" stroke-width="1.2"><title>'
                    . htmlspecialchars($name) . '</title></polygon>';
            }
        }
        foreach ($geo?->citiesIn(...$box) ?? [] as [$name, $kind, $lat, $lon]) {
            [$x, $y] = $project($lat, $lon);
            $out .= sprintf('<rect x="%.1f" y="%.1f" width="6" height="6" fill="#64748B"/>', $x - 3, $y - 3)
                . sprintf('<text x="%.1f" y="%.1f" font-size="%d" fill="#475569" text-anchor="middle">%s</text>',
                    $x, $y - 7, $kind === GeoPlaces::KIND_CITY ? 13 : 10, htmlspecialchars($name));
        }
        foreach ($collected['events'] as $e) {
            if ($e['lat'] === null) {
                continue;
            }
            [$x, $y] = $project($e['lat'], $e['lon']);
            $out .= sprintf('<circle cx="%.1f" cy="%.1f" r="%d" fill="%s" fill-opacity="%s"><title>%s</title></circle>',
                $x, $y, $e['inspection'] ? 4 : 3, self::COLORS[$e['type']], $e['inspection'] ? '0.9' : '0.4',
                htmlspecialchars((CrewWork::TYPE_NAMES[$e['type']] ?? '') . ' ' . $e['plate'] . ' — ' . Jalali::format($e['at'])));
        }
        foreach (self::place($collected['visits'], $project, 22) as [$v, $x, $y, $x0, $y0]) {
            if ($x !== $x0 || $y !== $y0) {
                $out .= sprintf('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="#1E3A8A" stroke-width="1"/>', $x0, $y0, $x, $y);
            }
            $out .= sprintf('<g><title>%s</title><circle cx="%.1f" cy="%.1f" r="10" fill="%s" stroke="#fff" stroke-width="2"/>'
                . '<text x="%.1f" y="%.1f" font-size="10" fill="#fff" text-anchor="middle">%s</text></g>',
                htmlspecialchars($v['label'] . ' — ' . Jalali::format($v['first'])), $x, $y, self::VISIT, $x, $y + 3.5,
                Jalali::digits((string) $v['n']));
        }
        return $out . '</svg>';
    }

    /** JPEG برای Word؛ بی GD یا بی نقطه، تهی. */
    public static function jpeg(string $path, array $collected, ?GeoPlaces $geo, int $w = 960, int $h = 640): bool
    {
        $frame = self::frame($collected['events'], $w, $h);
        if ($frame === null || !function_exists('imagecreatetruecolor')) {
            return false;
        }
        [$project, $box, $metresPerPx] = $frame;
        $img = imagecreatetruecolor($w, $h);
        $rgb = static function (string $hex) use ($img): int {
            return imagecolorallocate($img, hexdec(substr($hex, 1, 2)), hexdec(substr($hex, 3, 2)), hexdec(substr($hex, 5, 2)));
        };
        imagefilledrectangle($img, 0, 0, $w, $h, $rgb('#F8FAFC'));
        imagesetthickness($img, 2);
        foreach ($geo?->counties ?? [] as [, $rings]) {
            foreach ($rings as $ring) {
                $flat = [];
                foreach ($ring as $p) {
                    array_push($flat, ...array_map('intval', $project($p[0], $p[1])));
                }
                if (count($flat) >= 6) {
                    imagefilledpolygon($img, $flat, $rgb('#EEF2F7'));
                    imagepolygon($img, $flat, $rgb('#94A3B8'));
                }
            }
        }
        foreach ($geo?->citiesIn(...$box) ?? [] as [, $kind, $lat, $lon]) {
            [$x, $y] = array_map('intval', $project($lat, $lon));
            $r = $kind === GeoPlaces::KIND_CITY ? 5 : 3;
            imagefilledrectangle($img, $x - $r, $y - $r, $x + $r, $y + $r, $rgb('#64748B'));
        }
        foreach ($collected['events'] as $e) {
            if ($e['lat'] !== null) {
                [$x, $y] = array_map('intval', $project($e['lat'], $e['lon']));
                imagefilledellipse($img, $x, $y, $e['inspection'] ? 11 : 7, $e['inspection'] ? 11 : 7, $rgb(self::COLORS[$e['type']]));
            }
        }
        $white = $rgb('#FFFFFF');
        imagesetthickness($img, 1);
        foreach (self::place($collected['visits'], $project, 30) as [$v, $fx, $fy, $x0, $y0]) {
            [$x, $y] = [(int) $fx, (int) $fy];
            if ($x !== (int) $x0 || $y !== (int) $y0) {
                imageline($img, (int) $x0, (int) $y0, $x, $y, $rgb(self::VISIT));
            }
            $label = (string) $v['n'];
            $r = 13 + 3 * max(0, strlen($label) - 2);
            imagefilledellipse($img, $x, $y, 2 * $r + 4, 2 * $r + 4, $white);
            imagefilledellipse($img, $x, $y, 2 * $r, 2 * $r, $rgb(self::VISIT));
            imagestring($img, 3, $x - intdiv(imagefontwidth(3) * strlen($label), 2), $y - 6, $label, $white);
        }
        self::scaleBar($img, $metresPerPx, $h, $rgb('#0F172A'));
        imagestring($img, 5, $w - 24, 10, 'N', $rgb('#0F172A'));
        imagerectangle($img, 0, 0, $w - 1, $h - 1, $rgb('#CBD5E1'));
        $ok = imagejpeg($img, $path, 88);
        imagedestroy($img);
        return $ok;
    }

    private static function scaleBar(GdImage $img, float $metresPerPx, int $h, int $color): void
    {
        $target = $metresPerPx * 160;
        $step = 1;
        foreach ([1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000] as $m) {
            if ($m <= $target) {
                $step = $m;
            }
        }
        $px = (int) round($step / $metresPerPx);
        imagefilledrectangle($img, 16, $h - 22, 16 + $px, $h - 18, $color);
        imagestring($img, 3, 16, $h - 38, $step >= 1000 ? ($step / 1000) . ' km' : $step . ' m', $color);
    }
}
