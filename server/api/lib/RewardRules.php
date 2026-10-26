<?php
declare(strict_types=1);

/**
 * دستورالعمل‌های پاداش، هر کدام با شماره، شناسه و تاریخ نامه و تاریخ اجرا. دستورالعمل
 * تازه از تاریخ اجرایش به بعد حساب می‌شود و پرونده‌های پیش از آن با دستورالعمل قبلی
 * می‌مانند؛ تاریخ هر مرکز تاریخ گزارش آن است.
 *
 * سه نوع:
 *  - عامل شناسایی (گزارش‌دهنده): پلکانی بر تعداد دستگاه، با سقف هر مرکز (ستون M فرم)
 *  - عوامل اجرایی: پلیس امنیت اقتصادی، اطلاعات سپاه، عوامل وصول شرکت توزیع که در
 *    بازدید حاضر بودند؛ همان پلکان و سقف، تقسیم میان حاضران
 *  - عوامل اداری: درصدی از مبلغ صورتحساب پرونده؛ سهم مدیرعامل و سهم کارکنان که میان
 *    نقش‌ها با وزن هر نقش و میان دارندگان هر نقش برابر تقسیم می‌شود
 *
 * دو نوع اول را توانیر می‌پردازد ولی باید حساب و گزارش شوند.
 */
final class RewardRules
{
    public const FINDER = 0;
    public const ENFORCEMENT = 1;
    public const STAFF = 2;
    public const NAMES = [self::FINDER => 'پاداش عامل شناسایی', self::ENFORCEMENT => 'پاداش عوامل اجرایی',
        self::STAFF => 'پاداش عوامل اداری'];

    /** پلکان: هر دستگاه با نرخ پله خودش / همه با نرخ پله‌ای که تعداد در آن است / مبلغ ثابت پله. */
    public const MODES = ['progressive' => 'پلکانی (هر دستگاه با نرخ پله خودش)',
        'bracket' => 'همه دستگاه‌ها با نرخ پله‌ای که تعداد در آن است', 'fixed' => 'مبلغ ثابت هر پله'];
    public const ENFORCERS = ['faraja' => 'پلیس امنیت اقتصادی (فراجا)', 'sepah' => 'اطلاعات سپاه',
        'collection' => 'عوامل وصول شرکت توزیع'];
    public const BASES = ['energy' => 'مبلغ انرژی', 'energy_damage' => 'مبلغ انرژی و خسارت', 'collected' => 'مبلغ وصول'];

    /** @var array<int, list<array<string, mixed>>>|null */
    private static ?array $cache = null;

    /** دستورالعمل‌های یک نوع، تازه‌ترین بالا. @return list<array<string, mixed>> */
    public static function list(int $kind): array
    {
        self::$cache ??= self::load();
        return self::$cache[$kind] ?? [];
    }

    /** دستورالعمل جاری در یک تاریخ؛ تهی اگر پیش از نخستین دستورالعمل است. */
    public static function at(int $kind, ?int $millis): ?array
    {
        foreach (self::list($kind) as $rule) {
            if ($millis === null || $rule['effective_from'] <= $millis) {
                return $rule;
            }
        }
        return null;
    }

    /** مبلغ پلکان برای یک تعداد، با سقف. */
    public static function amount(array $spec, int $count): float
    {
        if ($count <= 0) {
            return 0.0;
        }
        $mode = $spec['mode'] ?? 'progressive';
        $total = 0.0;
        foreach ($spec['tiers'] ?? [] as $tier) {
            $from = max(1, (int) ($tier['from'] ?? 1));
            $to = isset($tier['to']) && $tier['to'] !== null && $tier['to'] !== '' ? (int) $tier['to'] : PHP_INT_MAX;
            $rate = (float) ($tier['amount'] ?? 0);
            if ($mode === 'progressive') {
                $total += max(0, min($count, $to) - $from + 1) * $rate;
            } elseif ($count >= $from && $count <= $to) {
                $total = $mode === 'bracket' ? $count * $rate : $rate;
            }
        }
        $cap = (float) ($spec['cap'] ?? 0);
        return $cap > 0 ? min($total, $cap) : $total;
    }

    /** «نامه ۱۲۳/ص — شناسه ۴۵۶ — ۱۴۰۴/۰۲/۱۰، از ۱۴۰۴/۰۳/۰۱» */
    public static function describe(?array $rule): string
    {
        if ($rule === null) {
            return 'دستورالعملی ثبت نشده';
        }
        $parts = array_filter([
            $rule['letter_no'] ? 'نامه ' . $rule['letter_no'] : null,
            $rule['letter_id'] ? 'شناسه ' . $rule['letter_id'] : null,
            $rule['letter_date'] ? Jalali::format((int) $rule['letter_date'], false) : null,
        ]);
        return Jalali::digits(implode(' — ', $parts) . '، اجرا از ' . Jalali::format((int) $rule['effective_from'], false));
    }

    /**
     * ثبت یا ویرایش؛ خطا متن فارسی، موفق شناسه. ورودی: kind، letter_no، letter_id،
     * letter_date (شمسی)، effective_from (شمسی)، note و spec.
     */
    public static function save(array $input, array $user, ?string $id = null): string
    {
        $kind = (int) ($input['kind'] ?? -1);
        if (!isset(self::NAMES[$kind])) {
            return 'نوع پاداش معتبر نیست.';
        }
        $from = CenterValues::date($input['effective_from'] ?? '');
        if ($from === null) {
            return 'تاریخ اجرا را به شکل ۱۴۰۴/۰۱/۰۱ بنویسید.';
        }
        $letterDate = trim((string) ($input['letter_date'] ?? '')) === '' ? null : CenterValues::date($input['letter_date']);
        $spec = RewardSpec::clean($kind, is_array($input['spec'] ?? null) ? $input['spec'] : []);
        if (is_string($spec)) {
            return $spec;
        }
        $fields = [
            'kind' => $kind,
            'letter_no' => self::short($input['letter_no'] ?? null, 64),
            'letter_id' => self::short($input['letter_id'] ?? null, 64),
            'letter_date' => $letterDate,
            'effective_from' => self::dayStart($from),
            'spec_json' => json_encode($spec, JSON_UNESCAPED_UNICODE),
            'note' => self::short($input['note'] ?? null, 500),
        ];
        if ($id !== null && Db::one('SELECT id FROM reward_rules WHERE id = ? AND deleted_at IS NULL', [$id]) !== null) {
            $sets = implode(', ', array_map(static fn ($k) => Db::col($k) . ' = ?', array_keys($fields)));
            Db::run("UPDATE reward_rules SET $sets WHERE id = ?", [...array_values($fields), $id]);
        } else {
            $id = Db::uuid();
            Db::run(
                'INSERT INTO reward_rules (id, ' . Db::cols(array_keys($fields)) . ', created_by, created_at)
                 VALUES (?' . str_repeat(', ?', count($fields)) . ', ?, ?)',
                [$id, ...array_values($fields), $user['id'], Db::now()]
            );
        }
        self::$cache = null;
        return $id;
    }

    public static function delete(string $id): void
    {
        Db::run('UPDATE reward_rules SET deleted_at = ? WHERE id = ?', [Db::now(), $id]);
        self::$cache = null;
    }

    public static function find(string $id): ?array
    {
        foreach (self::NAMES as $kind => $name) {
            foreach (self::list($kind) as $rule) {
                if ($rule['id'] === $id) {
                    return $rule;
                }
            }
        }
        return null;
    }

    /** @return array<int, list<array<string, mixed>>> */
    private static function load(): array
    {
        $out = [];
        foreach (Db::all('SELECT * FROM reward_rules WHERE deleted_at IS NULL ORDER BY effective_from DESC, created_at DESC') as $row) {
            $row['kind'] = (int) $row['kind'];
            $row['effective_from'] = (int) $row['effective_from'];
            $row['letter_date'] = $row['letter_date'] === null ? null : (int) $row['letter_date'];
            $row['spec'] = CenterValues::decode($row['spec_json']);
            unset($row['spec_json']);
            $out[$row['kind']][] = $row;
        }
        return $out;
    }

    public static function forget(): void
    {
        self::$cache = null;
    }

    /** آغاز روز شمسی همان لحظه، تا دستورالعمل از اول روز اجرایش حساب شود. */
    public static function dayStart(int $millis): int
    {
        $parts = Jalali::parse(Jalali::format($millis, false));
        return $parts === null ? $millis : Jalali::startOfDay(...$parts);
    }

    private static function short(mixed $value, int $max): ?string
    {
        $text = CenterValues::cleanText((string) ($value ?? ''));
        return $text === '' ? null : mb_substr($text, 0, $max);
    }
}
