<?php
declare(strict_types=1);

/**
 * افراد فرم پاداش هر مرکز. هر نفر: key (پایدار)، name، post، org، action، pct (فقط عوامل اداری)،
 * source و on (تیک: فقط تیک‌خورده‌ها سهم می‌برند).
 *
 * پیش‌فرض‌ها، تا مدیر نام تایپ نکند:
 *  - جمع‌آوری: کارشناس بازدید، حاضران شرکت توزیع، و پیشنهاد کارشناس؛ حاضران دیگر بی تیک
 *  - عوامل انتظامی: حاضران نیروی انتظامی و سپاه، و پیشنهاد کارشناس
 *  - عوامل اداری: مدیرعامل و پست‌های دستورالعمل با درصد پیش‌فرض، دارندگان از پیشنهاد کارشناس
 * فرمی که مدیر ذخیره کرده همان می‌ماند؛ فرد تازه‌ای که بعداً از بازدید یا کارشناس برسد بی تیک و
 * با نشان «تازه» به آخرش افزوده می‌شود تا مدیر ببیند.
 */
final class RewardPeople
{
    public const SOURCES = ['visit' => 'حاضر در بازدید', 'expert' => 'پیشنهاد کارشناس', 'manager' => 'افزوده مدیر',
        'rule' => 'پست دستورالعمل'];
    public const ORGS = ['company' => 'شرکت توزیع', 'faraja' => 'نیروی انتظامی', 'sepah' => 'سپاه', 'other' => 'سایر'];
    public const CEO = 'مدیرعامل';

    /**
     * افراد هر سه پاداش برای چند سطر آمار، با حالت فرم ذخیره‌شده.
     * @param list<array<string, mixed>> $rows سطرهای خام center_rows
     * @return array<string, array<int, array{people:list<array>, saved:bool, form:?array}>>
     */
    public static function forRows(array $rows): array
    {
        $reportIds = array_values(array_filter(array_column($rows, 'report_id')));
        $attendees = CenterSystem::attendees($reportIds);
        $proposals = RewardProposals::forReports($reportIds);
        $experts = self::experts($reportIds);
        $forms = RewardForms::forRows(array_column($rows, 'id'));
        $out = [];
        foreach ($rows as $row) {
            $rid = $row['report_id'];
            $ctx = ['attendees' => $attendees[$rid] ?? [], 'proposals' => $proposals[$rid] ?? [], 'expert' => $experts[$rid] ?? null,
                'date' => $row['row_date'] === null ? null : (int) $row['row_date']];
            foreach ([RewardRules::FINDER, RewardRules::COLLECTION, RewardRules::ENFORCEMENT, RewardRules::STAFF] as $kind) {
                $form = $forms[$row['id']][$kind] ?? null;
                $defaults = $kind === RewardRules::FINDER ? [] : self::defaults($kind, $ctx);
                $people = $form !== null && $form['people'] !== null ? self::merge($form['people'], $defaults) : $defaults;
                $out[$row['id']][$kind] = ['people' => $people, 'saved' => $form !== null && $form['people'] !== null, 'form' => $form];
            }
        }
        return $out;
    }

    /** @return list<array<string, mixed>> */
    public static function defaults(int $kind, array $ctx): array
    {
        $out = [];
        $mine = array_values(array_filter($ctx['proposals'], static fn ($p) => $p['kind'] === $kind));
        if ($kind === RewardRules::COLLECTION) {
            if ($ctx['expert'] !== null) {
                $out[] = self::person('x:expert', $ctx['expert'], 'کارشناس بازدید', 'company', 'visit', true);
            }
            foreach ($ctx['attendees'] as $a) {
                if (CenterRewards::enforcer($a) === null) {
                    $company = $a['org'] === 0;
                    $out[] = self::person('a:' . $a['id'], (string) $a['name'], (string) ($a['position'] ?? $a['org_name'] ?? ''),
                        $company ? 'company' : 'other', 'visit', $company);
                }
            }
        } elseif ($kind === RewardRules::ENFORCEMENT) {
            foreach ($ctx['attendees'] as $a) {
                $org = CenterRewards::enforcer($a);
                if ($org !== null) {
                    $out[] = self::person('a:' . $a['id'], (string) $a['name'], (string) ($a['position'] ?? ''), $org, 'visit', true);
                }
            }
        } else {
            return self::staffDefaults($mine, $ctx['date']);
        }
        foreach ($mine as $p) {
            $out[] = self::person('p:' . $p['id'], $p['name'], $p['post'], $p['org'] ?: ($kind === RewardRules::COLLECTION ? 'company' : 'faraja'),
                'expert', true, $p['action']);
        }
        return array_map(static fn ($p) => $p + ['action' => self::action($kind, $p['post'])], $out);
    }

    /** مدیرعامل، و هر پست دستورالعمل با درصدش میان دارندگان پیشنهادی (یا بی‌نام تا مدیر برگزیند). */
    private static function staffDefaults(array $mine, ?int $date): array
    {
        $rule = RewardRules::at(RewardRules::STAFF, $date);
        $ceo = array_values(array_filter($mine, static fn ($p) => self::isCeo($p['post'])))[0] ?? null;
        $out = [self::person('ceo', $ceo['name'] ?? '', self::CEO, '', $ceo === null ? 'rule' : 'expert', true, 'تأیید نهایی')];
        $used = $ceo === null ? [] : [$ceo['id'] => true];
        foreach ($rule['spec']['roles'] ?? [] as $role) {
            $holders = array_values(array_filter($mine, static fn ($p) => !isset($used[$p['id']]) && self::samePost($p['post'], $role['label'])));
            if ($holders === []) {
                $out[] = self::person('r:' . md5($role['label']), '', $role['label'], '', 'rule', true, null, (float) $role['weight']);
            }
            foreach ($holders as $p) {
                $used[$p['id']] = true;
                $out[] = self::person('p:' . $p['id'], $p['name'], $role['label'], '', 'expert', true, $p['action'],
                    round((float) $role['weight'] / count($holders), 4));
            }
        }
        foreach ($mine as $p) {
            if (!isset($used[$p['id']])) {
                $out[] = self::person('p:' . $p['id'], $p['name'], $p['post'], '', 'expert', true, $p['action'], 0.0);
            }
        }
        return array_map(static fn ($p) => $p + ['action' => self::action(RewardRules::STAFF, $p['post'])], $out);
    }

    /** فهرست ذخیره‌شده، به‌علاوه فرد تازه بازدید یا کارشناس بی تیک؛ پست‌های بی‌نام دستورالعمل نه. */
    public static function merge(array $saved, array $defaults): array
    {
        $have = array_flip(array_column($saved, 'key'));
        foreach ($defaults as $p) {
            if (!isset($have[$p['key']]) && $p['source'] !== 'rule' && $p['name'] !== '') {
                $saved[] = ['on' => false, 'new' => true] + $p;
            }
        }
        return $saved;
    }

    /** فهرستی که پنل یا اپ فرستاده، یکسان‌شده. @return list<array<string, mixed>> */
    public static function clean(int $kind, array $input): array
    {
        $out = [];
        foreach (array_slice(array_values($input), 0, 80) as $p) {
            if (!is_array($p)) {
                continue;
            }
            $key = (string) ($p['key'] ?? '');
            if (!preg_match('/^(ceo|x:expert|[apmr]:[A-Za-z0-9-]{1,40})$/', $key)) {
                $key = 'm:' . Db::uuid();
            }
            $org = (string) ($p['org'] ?? '');
            $name = self::text($p['name'] ?? '', 100);
            $post = self::text($p['post'] ?? '', 100);
            if ($name === '' && $post === '' && !str_starts_with($key, 'r:') && $key !== 'ceo') {
                continue;
            }
            $source = (string) ($p['source'] ?? 'manager');
            $out[] = [
                'key' => $key, 'name' => $name, 'post' => $post,
                'org' => isset(self::ORGS[$org]) ? $org : ($kind === RewardRules::ENFORCEMENT ? 'faraja' : ''),
                'action' => self::text($p['action'] ?? '', 255),
                'pct' => $kind === RewardRules::STAFF && $key !== 'ceo' ? (float) (CenterValues::number($p['pct'] ?? 0) ?? 0) : null,
                'source' => isset(self::SOURCES[$source]) ? $source : 'manager',
                'on' => filter_var($p['on'] ?? false, FILTER_VALIDATE_BOOLEAN),
            ];
        }
        return $out;
    }

    /** نام‌هایی که مدیر از میانشان برمی‌گزیند: اشخاص سامانه و پیشنهادهای کارشناسان. @return list<string> */
    public static function names(): array
    {
        $names = array_merge(
            array_column(Db::all('SELECT full_name FROM persons WHERE active = 1 ORDER BY full_name LIMIT 2000'), 'full_name'),
            array_column(Db::all('SELECT DISTINCT full_name FROM reward_proposals ORDER BY full_name LIMIT 2000'), 'full_name')
        );
        $names = array_values(array_unique(array_map('strval', $names)));
        sort($names);
        return $names;
    }

    /** اقدام پیش‌فرض هر پست؛ مدیر ویرایشش می‌کند. */
    public static function action(int $kind, string $post): string
    {
        $map = $kind === RewardRules::STAFF ? [
            'فروش' => 'پیگیری صدور و وصول صورتحساب', 'حقوقی' => 'تنظیم شکوائیه و پیگیری حقوقی', 'لوازم' => 'آزمایش و تعویض کنتور',
            'حراست' => 'هماهنگی و نظارت حراستی', 'کمیسیون' => 'تنظیم صورتجلسه کمیسیون', 'رمزارز' => 'پیگیری کامل پرونده',
            'استخراج لاگ' => 'استخراج لاگ ماینرها', 'تحلیل' => 'تحلیل لاگ و برآورد مصرف', 'مدیرعامل' => 'تأیید نهایی',
        ] : [
            'کارشناس' => 'بازدید، اندازه‌گیری و تنظیم صورت‌جلسه', 'وصول' => 'قطع انشعاب و همکاری در وصول', 'سیم' => 'قطع انشعاب و جمع‌آوری کابل و تجهیزات',
            'راننده' => 'حمل دستگاه‌ها به انبار', 'حراست' => 'هماهنگی با مراجع انتظامی',
        ];
        foreach ($map as $word => $text) {
            if (mb_strpos($post, $word) !== false) {
                return $text;
            }
        }
        return match ($kind) {
            RewardRules::ENFORCEMENT => 'حضور و تأمین امنیت عملیات و توقیف دستگاه‌ها',
            RewardRules::STAFF => 'پیگیری پرونده',
            default => 'حضور و همکاری در جمع‌آوری دستگاه‌ها',
        };
    }

    public static function isCeo(string $post): bool
    {
        return str_replace([' ', "\u{200C}"], '', $post) === 'مدیرعامل';
    }

    private static function samePost(string $a, string $b): bool
    {
        $flat = static fn (string $s) => str_replace([' ', "\u{200C}"], '', $s);
        return $a !== '' && $flat($a) === $flat($b);
    }

    private static function person(string $key, string $name, string $post, string $org, string $source, bool $on,
                                   ?string $action = null, ?float $pct = null): array
    {
        $p = ['key' => $key, 'name' => $name, 'post' => $post, 'org' => $org, 'pct' => $pct, 'source' => $source, 'on' => $on];
        return $action === null || $action === '' ? $p : $p + ['action' => $action];
    }

    /** نام کارشناس هر پرونده. @return array<string, string> */
    private static function experts(array $ids): array
    {
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $out = [];
        foreach (Db::all("SELECT r.id, u.full_name FROM reports r JOIN users u ON u.user_code = r.expert_code WHERE r.id IN ($in)", $ids) as $r) {
            $out[$r['id']] = (string) $r['full_name'];
        }
        return $out;
    }

    private static function text(mixed $value, int $max): string
    {
        return mb_substr(CenterValues::cleanText((string) ($value ?? '')), 0, $max);
    }
}
