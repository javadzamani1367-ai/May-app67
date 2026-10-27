<?php
declare(strict_types=1);

/**
 * دستورالعمل‌های پاداش. هر دستورالعمل یک نامه است — شماره، شناسه و تاریخ نامه و تاریخ اجرا —
 * که هر چهار پاداش را با هم تعیین می‌کند و از تاریخ اجرایش تا اجرای دستورالعمل بعدی برای
 * تاریخ گزارش هر مرکز حساب می‌شود:
 *  - عامل شناسایی (گزارش‌دهنده): پلکانی بر تعداد دستگاه، با سقف هر مرکز؛ پرداخت توانیر
 *  - جمع‌آوری شرکت توزیع: مبلغ هر دستگاه با سقف، میان پرسنل جمع‌آوری برابر؛ پرداخت شرکت توزیع
 *  - عوامل انتظامی: مبلغ هر دستگاه با سقف، میان همه پرسنل حاضر نیروی انتظامی و سپاه برابر
 *    (نه میان دو سازمان)؛ سهم نیروی انتظامی را توانیر و سهم سپاه را شرکت توزیع می‌پردازد
 *  - عوامل اداری: درصدی از مبلغ صورتحساب؛ سهم مدیرعامل و سهم کارکنان که درصد پیش‌فرض هر پست
 *    را دارد و مدیر پیش از ثبت نهایی فرم هر مرکز تغییرش می‌دهد؛ پرداخت شرکت توزیع
 *
 * دستورالعمل‌های نسخه پیش هر کدام یک نوع داشتند (kind ۰ تا ۲)؛ همچنان خوانده می‌شوند. بخشی که
 * در دستورالعملی خالی مانده از دستورالعمل پیش از آن می‌آید.
 */
final class RewardRules
{
    public const FINDER = 0;
    public const ENFORCEMENT = 1;
    public const STAFF = 2;
    public const COLLECTION = 3;
    /** دستورالعمل یک‌جای هر چهار پاداش. */
    public const COMBINED = 9;
    public const NAMES = [self::FINDER => 'پاداش عامل شناسایی', self::COLLECTION => 'پاداش جمع‌آوری شرکت توزیع',
        self::ENFORCEMENT => 'پاداش عوامل انتظامی', self::STAFF => 'پاداش عوامل اداری'];
    public const KEYS = [self::FINDER => 'finder', self::COLLECTION => 'collection', self::ENFORCEMENT => 'enforcement',
        self::STAFF => 'staff'];

    public const TAVANIR = 'شرکت توانیر';
    public const COMPANY = 'شرکت توزیع';
    /** نیروی انتظامی و سپاه؛ مأموران شرکت توزیع (وصول و…) از پاداش جمع‌آوری سهم می‌برند، نه از این. */
    public const ENFORCERS = ['faraja' => 'نیروی انتظامی (پلیس امنیت اقتصادی)', 'sepah' => 'اطلاعات سپاه'];
    public const ORG_PAYER = ['faraja' => self::TAVANIR, 'sepah' => self::COMPANY];

    /** پلکان عامل شناسایی: هر دستگاه با نرخ پله خودش / همه با نرخ پله‌ای که تعداد در آن است / مبلغ ثابت پله. */
    public const MODES = ['progressive' => 'پلکانی (هر دستگاه با نرخ پله خودش)',
        'bracket' => 'همه دستگاه‌ها با نرخ پله‌ای که تعداد در آن است', 'fixed' => 'مبلغ ثابت هر پله'];
    public const COUNTS = ['found' => 'دستگاه کشف‌شده', 'collected' => 'دستگاه جمع‌آوری‌شده'];
    public const BASES = ['energy' => 'مبلغ انرژی', 'energy_damage' => 'مبلغ انرژی و خسارت', 'collected' => 'مبلغ وصول'];

    /** @var list<array<string, mixed>>|null */
    private static ?array $cache = null;

    /** پرداخت‌کننده هر پاداش؛ عوامل انتظامی به سازمان هر نفر بسته است. */
    public static function payer(int $kind, ?string $org = null): string
    {
        return match ($kind) {
            self::FINDER => self::TAVANIR,
            self::ENFORCEMENT => self::ORG_PAYER[$org ?? ''] ?? self::TAVANIR,
            default => self::COMPANY,
        };
    }

    /** دستورالعمل‌ها، تازه‌ترین بالا؛ با kind فقط آن‌هایی که این پاداش را دارند. @return list<array<string, mixed>> */
    public static function list(?int $kind = null): array
    {
        self::$cache ??= self::load();
        return $kind === null ? self::$cache
            : array_values(array_filter(self::$cache, static fn (array $r) => isset($r['sections'][$kind])));
    }

    /** دستورالعمل جاری یک پاداش در یک تاریخ، با kind و spec همان بخش؛ تهی اگر پیش از نخستین است. */
    public static function at(int $kind, ?int $millis): ?array
    {
        foreach (self::list($kind) as $rule) {
            if ($millis === null || $rule['effective_from'] <= $millis) {
                return ['kind' => $kind, 'spec' => $rule['sections'][$kind]] + $rule;
            }
        }
        return null;
    }

    /** مبلغ مرکز برای یک تعداد دستگاه: مبلغ هر دستگاه یا پلکان، با سقف. */
    public static function amount(array $spec, int $count): float
    {
        if ($count <= 0) {
            return 0.0;
        }
        $total = 0.0;
        if (isset($spec['per_device'])) {
            $total = $count * (float) $spec['per_device'];
        }
        $mode = $spec['mode'] ?? 'progressive';
        foreach (isset($spec['per_device']) ? [] : $spec['tiers'] ?? [] as $tier) {
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

    /** «نامه ۱۲۳/ص — شناسه ۴۵۶ — ۱۴۰۴/۰۲/۱۰، اجرا از ۱۴۰۴/۰۳/۰۱» */
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
        return Jalali::digits(($parts === [] ? 'دستورالعمل' : implode(' — ', $parts)) . '، اجرا از '
            . Jalali::format((int) $rule['effective_from'], false));
    }

    /** «دستورالعمل … به شماره نامه … و شناسه … مورخ …، اجرا از …» برای استناد فرم. */
    public static function cite(array $rule): string
    {
        $text = 'دستورالعمل ' . self::NAMES[$rule['kind']] . ($rule['letter_no'] ? ' به شماره نامه ' . $rule['letter_no'] : '')
            . ($rule['letter_id'] ? ' و شناسه ' . $rule['letter_id'] : '')
            . ($rule['letter_date'] ? ' مورخ ' . Jalali::format((int) $rule['letter_date'], false) : '')
            . '، اجرا از ' . Jalali::format((int) $rule['effective_from'], false);
        return Jalali::digits($text);
    }

    /** شرح یک بخش دستورالعمل (rule از at): مبلغ، پلکان یا درصدها، سقف و پرداخت‌کننده. */
    public static function explain(array $rule): string
    {
        return Jalali::digits('بر پایه ' . self::describe($rule) . ' — ' . self::terms($rule) . '.');
    }

    /** متن شرط‌های یک بخش، بی نامه؛ فرم هر مرکز درصد پست‌ها را در جدول خودش دارد (withRoles نه). */
    public static function terms(array $rule, bool $withRoles = true): string
    {
        $spec = $rule['spec'];
        $money = static fn ($n) => number_format((float) $n) . ' ریال';
        $cap = (float) ($spec['cap'] ?? 0) > 0 ? '؛ سقف هر مرکز ' . $money($spec['cap']) : '';
        $rate = static function (array $spec) use ($money): string {
            if (isset($spec['per_device'])) {
                return 'هر ' . self::COUNTS[$spec['count'] ?? 'found'] . ' ' . $money($spec['per_device']);
            }
            return self::MODES[$spec['mode'] ?? 'progressive'] . ': ' . implode('؛ ', array_map(static fn ($t) => 'از ' . $t['from']
                . ($t['to'] === null ? ' به بالا' : ' تا ' . $t['to']) . ' دستگاه ' . $money($t['amount']), $spec['tiers'] ?? []));
        };
        return match ($rule['kind']) {
            self::STAFF => $spec['pct'] . ' درصد ' . self::BASES[$spec['base'] ?? 'energy'] . ' هر مرکز: ' . $spec['ceo_pct']
                . ' درصد سهم مدیرعامل و ' . $spec['staff_pct'] . ' درصد سهم کارکنان' . $cap
                . (($spec['roles'] ?? []) === [] || !$withRoles ? '' : '؛ درصد پیش‌فرض هر پست از سهم کارکنان: '
                    . implode('، ', array_map(static fn ($r) => $r['label'] . ' ' . $r['weight'] . '٪', $spec['roles'])))
                . '؛ پرداخت ' . self::COMPANY,
            self::ENFORCEMENT => $rate($spec) . $cap . '؛ مبلغ مرکز به‌طور مساوی میان همه پرسنل حاضر '
                . implode(' و ', array_map(static fn ($o) => self::ENFORCERS[$o], $spec['orgs'] ?? array_keys(self::ENFORCERS)))
                . ' تقسیم می‌شود (نه میان سازمان‌ها)؛ سهم نیروی انتظامی را ' . self::TAVANIR . ' و سهم سپاه را ' . self::COMPANY . ' می‌پردازد',
            self::COLLECTION => $rate($spec) . $cap . '؛ مبلغ مرکز به‌طور مساوی میان پرسنل حاضر در جمع‌آوری و افرادی که مدیر افزوده'
                . ' تقسیم می‌شود؛ پرداخت ' . self::COMPANY,
            default => $rate($spec) . $cap . '؛ پرداخت ' . self::TAVANIR,
        };
    }

    /**
     * ثبت یا ویرایش؛ خطا متن فارسی، موفق شناسه. ورودی: letter_no، letter_id، letter_date و
     * effective_from (شمسی)، note و sections {finder, collection, enforcement, staff}؛ بخش خالی یعنی
     * این دستورالعمل آن پاداش را عوض نمی‌کند. ورودی قدیمی kind و spec هم پذیرفته است.
     */
    public static function save(array $input, array $user, ?string $id = null): string
    {
        $raw = is_array($input['sections'] ?? null) ? $input['sections'] : [];
        if ($raw === [] && isset($input['kind'], self::KEYS[(int) $input['kind']])) {
            $raw = [self::KEYS[(int) $input['kind']] => is_array($input['spec'] ?? null) ? $input['spec'] : []];
        }
        $sections = [];
        foreach (self::KEYS as $kind => $key) {
            if (!isset($raw[$key]) || !is_array($raw[$key])) {
                continue;
            }
            $spec = RewardSpec::clean($kind, $raw[$key]);
            if (is_string($spec)) {
                return self::NAMES[$kind] . ': ' . $spec;
            }
            if ($spec !== null) {
                $sections[$key] = $spec;
            }
        }
        if ($sections === []) {
            return 'دست‌کم یکی از چهار پاداش را با مبلغ یا درصد بنویسید.';
        }
        $from = CenterValues::date($input['effective_from'] ?? '');
        if ($from === null) {
            return 'تاریخ اجرا را به شکل ۱۴۰۴/۰۱/۰۱ بنویسید.';
        }
        $letterDate = trim((string) ($input['letter_date'] ?? '')) === '' ? null : CenterValues::date($input['letter_date']);
        $fields = [
            'kind' => self::COMBINED,
            'letter_no' => self::short($input['letter_no'] ?? null, 64),
            'letter_id' => self::short($input['letter_id'] ?? null, 64),
            'letter_date' => $letterDate,
            'effective_from' => self::dayStart($from),
            'spec_json' => json_encode($sections, JSON_UNESCAPED_UNICODE),
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
        foreach (self::list() as $rule) {
            if ($rule['id'] === $id) {
                return $rule;
            }
        }
        return null;
    }

    /** هر سطر جدول با sections [نوع => بخش]؛ دستورالعمل تک‌نوع نسخه پیش یک بخش دارد. @return list<array<string, mixed>> */
    private static function load(): array
    {
        $out = [];
        foreach (Db::all('SELECT * FROM reward_rules WHERE deleted_at IS NULL ORDER BY effective_from DESC, created_at DESC') as $row) {
            $spec = CenterValues::decode($row['spec_json']);
            $sections = [];
            if ((int) $row['kind'] === self::COMBINED) {
                foreach (self::KEYS as $kind => $key) {
                    if (is_array($spec[$key] ?? null)) {
                        $sections[$kind] = $spec[$key];
                    }
                }
            } elseif (isset(self::NAMES[(int) $row['kind']])) {
                $sections[(int) $row['kind']] = $spec;
            }
            unset($row['spec_json'], $row['kind']);
            $row['effective_from'] = (int) $row['effective_from'];
            $row['letter_date'] = $row['letter_date'] === null ? null : (int) $row['letter_date'];
            $row['sections'] = $sections;
            $out[] = $row;
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
