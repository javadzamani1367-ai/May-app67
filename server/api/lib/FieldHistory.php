<?php
declare(strict_types=1);

require_once __DIR__ . '/FieldReadings.php';

/**
 * سوابق و مقایسه خوانش‌های هر تابلو و تیر: هر کلید (ناحیه، تیر یا تابلو، پلاک، بخش)
 * یک رشته زمانی است. افزایش نامتعارف هر فاز نسبت به بار قبل و میانگین تا ۵ بار قبل
 * سنجیده می‌شود (history_rise_pct، با کف history_rise_min_a)، و هشدارش فقط به مدیر و
 * مجری رمزارز همان ناحیه می‌رود.
 */
final class FieldHistory
{
    /** خوانشی که حساب است: مورد ردشده یا ادغام‌شده در سوابق نمی‌آید. */
    public const VALID = 'i.status <> ' . Field::STATUS_REJECTED . ' AND i.merged_into IS NULL';
    public const PHASES = ['r', 's', 't'];
    private const COMPARE_WITH = 5;

    /** افزایش نامتعارف یک فاز: بیش از درصد مجاز و کف آمپر، نسبت به بار قبل یا میانگین. */
    public static function isRise(?float $value, ?float $previous, ?float $average, int $pct, int $minA): bool
    {
        if ($value === null) {
            return false;
        }
        foreach ([$previous, $average] as $base) {
            if ($base !== null && $value - $base >= $minA && $value > $base * (1 + $pct / 100)) {
                return true;
            }
        }
        return false;
    }

    /**
     * نشان نامتعارف خوانش‌های یک مورد، و اگر تازه نامتعارف شد، هشدار یک‌باره به مدیر و
     * مجری رمزارز ناحیه.
     *
     * @param array<string, mixed> $item سطر field_items
     */
    public static function mark(array $item, bool $notify): void
    {
        $settings = FieldSettings::all();
        $pct = (int) $settings['history_rise_pct'];
        $minA = (int) $settings['history_rise_min_a'];
        $found = [];
        foreach (Db::all('SELECT * FROM field_readings WHERE item_id = ? AND part <> ?', [$item['id'], FieldReadings::PART_THERMAL]) as $row) {
            $before = Db::all(
                'SELECT h.r, h.s, h.t FROM field_readings h JOIN field_items i ON i.id = h.item_id
                 WHERE (h.area_code <=> ?) AND h.asset = ? AND h.plate_key = ? AND h.part = ? AND h.measured_at < ?
                   AND h.item_id <> ? AND ' . self::VALID . ' ORDER BY h.measured_at DESC LIMIT ' . self::COMPARE_WITH,
                [$row['area_code'], $row['asset'], $row['plate_key'], $row['part'], $row['measured_at'], $item['id']]
            );
            $notes = [];
            foreach (self::PHASES as $phase) {
                $values = array_values(array_filter(array_map(static fn ($b) => $b[$phase] === null ? null : (float) $b[$phase], $before),
                    static fn ($v) => $v !== null));
                $previous = $before === [] || $before[0][$phase] === null ? null : (float) $before[0][$phase];
                $average = $values === [] ? null : array_sum($values) / count($values);
                $value = $row[$phase] === null ? null : (float) $row[$phase];
                if (self::isRise($value, $previous, $average, $pct, $minA)) {
                    $notes[] = strtoupper($phase) . ': ' . Jalali::digits(self::num($previous ?? $average)) . '→' . Jalali::digits(self::num($value));
                }
            }
            Db::run('UPDATE field_readings SET anomaly = ?, anomaly_note = ? WHERE id = ?',
                [$notes === [] ? 0 : 1, $notes === [] ? null : implode('، ', $notes), $row['id']]);
            if ($notes !== []) {
                $found[] = self::label((int) $row['asset'], (string) $row['plate'], (string) $row['part']) . ' — ' . implode('، ', $notes);
            }
        }
        if ($found === []) {
            Db::run('UPDATE field_items SET anomaly_at = NULL WHERE id = ?', [$item['id']]);
            return;
        }
        if ($item['anomaly_at'] !== null) {
            return;
        }
        Db::run('UPDATE field_items SET anomaly_at = ? WHERE id = ?', [Db::now(), $item['id']]);
        if ($notify) {
            $title = 'افزایش نامتعارف آمپر: ' . $item['tracking_code'];
            $text = implode("\n", array_slice($found, 0, 6));
            Notifications::toRole(Auth::ROLE_MANAGER, 'field_anomaly', $title, $text);
            Notifications::toExecutors($item['area_code'] ?? null, 'field_anomaly', $title, $text);
        }
    }

    /** «تابلو ۱۲۳ — کلید کل»، «تابلو ۱۲۳ — فیدر A»، «تیر ۴۵۶»، «تیر ۴۵۶ — ترموویژن». */
    public static function label(int $asset, string $plate, string $part): string
    {
        $what = ($asset === FieldReadings::ASSET_PANEL ? 'تابلو ' : 'تیر ') . Jalali::digits($plate);
        return $what . match ($part) {
            FieldReadings::PART_MAIN => ' — کلید کل',
            FieldReadings::PART_POLE => '',
            FieldReadings::PART_THERMAL => ' — ترموویژن',
            default => ' — فیدر ' . $part,
        };
    }

    /**
     * یک رشته: خوانش‌های یک کلید، تازه‌ترین اول، تا [$until] (همان بازدید به قبل).
     * [$names] نام ثبت‌کننده و کد مورد را هم می‌دهد — برای مدیر و مجری‌ها، نه کاربر میدانی.
     *
     * @return list<array<string, mixed>>
     */
    public static function series(?string $area, int $asset, string $plateKey, string $part, ?int $until = null,
                                  int $limit = 50, bool $names = false): array
    {
        $rows = Db::all(
            'SELECT h.*, i.tracking_code, u.full_name AS who FROM field_readings h JOIN field_items i ON i.id = h.item_id
             LEFT JOIN users u ON u.id = h.user_id
             WHERE (h.area_code <=> ?) AND h.asset = ? AND h.plate_key = ? AND h.part = ? AND ' . self::VALID
                . ($until === null ? '' : ' AND h.measured_at <= ' . $until) . '
             ORDER BY h.measured_at DESC, h.id DESC LIMIT ' . max(1, min(500, $limit)),
            [$area, $asset, $plateKey, $part]
        );
        return array_map(static fn (array $r) => self::present($r, $names), $rows);
    }

    /**
     * سوابق هر کلیدی که این مورد خوانده، از همین بازدید به قبل — برای صفحه مورد مدیر و مجری‌ها.
     *
     * @return list<array{label: string, asset: int, plate: string, part: string, rows: list<array<string, mixed>>}>
     */
    public static function forItem(string $itemId, bool $names = true): array
    {
        $keys = Db::all(
            'SELECT area_code, asset, plate, plate_key, part, MAX(measured_at) AS at FROM field_readings WHERE item_id = ?
             GROUP BY area_code, asset, plate, plate_key, part ORDER BY asset DESC, MIN(id)',
            [$itemId]
        );
        return array_map(static fn (array $k) => [
            'label' => self::label((int) $k['asset'], (string) $k['plate'], (string) $k['part']),
            'asset' => (int) $k['asset'], 'plate' => (string) $k['plate'], 'part' => (string) $k['part'],
            'rows' => self::series($k['area_code'], (int) $k['asset'], (string) $k['plate_key'], (string) $k['part'],
                (int) $k['at'], 50, $names),
        ], $keys);
    }

    /** @return array<string, mixed> */
    public static function present(array $r, bool $names): array
    {
        $num = static fn ($v) => $v === null ? null : (float) $v;
        $out = ['id' => (int) $r['id'], 'item_id' => (string) $r['item_id'], 'at' => (int) $r['measured_at'],
            'asset' => (int) $r['asset'], 'plate' => (string) $r['plate'], 'part' => (string) $r['part'],
            'r' => $num($r['r']), 's' => $num($r['s']), 't' => $num($r['t']), 'n' => $num($r['n']),
            'temp_max' => $num($r['temp_max']), 'temp_box_max' => $num($r['temp_box_max']), 'temp_box_avg' => $num($r['temp_box_avg']),
            'file_id' => $r['file_id'], 'anomaly' => (int) $r['anomaly'] === 1, 'anomaly_note' => $r['anomaly_note']];
        if ($names) {
            $out['tracking_code'] = $r['tracking_code'] ?? null;
            $out['who'] = $r['who'] ?? null;
        }
        return $out;
    }

    public static function num(?float $v): string
    {
        return $v === null ? '—' : rtrim(rtrim(number_format($v, 1, '.', ''), '0'), '.');
    }
}
