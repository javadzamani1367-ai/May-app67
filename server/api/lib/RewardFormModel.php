<?php
declare(strict_types=1);

/**
 * فرم تک‌صفحه‌ای پاداش هر مرکز برای مکاتبه، به مدل OfficialXlsx و OfficialDocx: سربرگ شرکت،
 * شماره و تاریخ فرم ثبت‌شده، مشخصات مرکز، مستند پرداخت (دستورالعمل با شماره، شناسه و تاریخ نامه و
 * شرط‌هایش) و محاسبه همین مرکز، جدول افراد با سمت، اقدام و سهم، مبلغ به حروف و جای امضا.
 * فقط افراد تیک‌خورده در فرم می‌آیند.
 */
final class RewardFormModel
{
    public const LETTERHEAD = 'شرکت توزیع نیروی برق استان ایلام';
    public const SIGNATURES = ['مجری نظارت و ساماندهی رمزارز', 'معاون فروش و خدمات مشترکین', 'مدیر عامل'];
    private const TITLES = [
        RewardRules::FINDER => 'فرم پاداش عامل شناسایی (گزارش‌دهنده) — مرکز استخراج رمزارز',
        RewardRules::COLLECTION => 'فرم پاداش جمع‌آوری شرکت توزیع — مرکز استخراج رمزارز',
        RewardRules::ENFORCEMENT => 'فرم پاداش عوامل انتظامی — مرکز استخراج رمزارز',
        RewardRules::STAFF => 'فرم پاداش عوامل اداری — مرکز استخراج رمزارز',
    ];

    public static function build(array $row, int $kind): array
    {
        $state = $row['rewards'][$kind];
        $calc = $state['calc'];
        $v = $row['values'];
        $model = [
            'title' => self::TITLES[$kind], 'sheet' => 'فرم پاداش', 'portrait' => true, 'cell_size' => 20,
            'letterhead' => self::LETTERHEAD, 'signatures' => self::SIGNATURES,
            'meta' => $state['final_at'] === null ? [] : ['number' => $state['number'], 'date' => Jalali::format((int) $state['final_at'], false)],
            'subtitle' => self::subtitle($kind, $v, $calc),
            'paragraphs' => $calc === null ? ['برای تاریخ گزارش این مرکز دستورالعملی از این پاداش ثبت نشده است.']
                : ['مستند پرداخت: ' . RewardRules::cite($calc['rule']) . '. ' . Jalali::digits(RewardRules::terms($calc['rule'], false)) . '.',
                    self::calcText($kind, $calc)],
        ];
        $people = self::people($kind, $row, $calc);
        $model['tables'] = [self::info($row), $people];
        $amount = (float) ($calc['amount'] ?? 0);
        $model['notes'] = ['مبلغ به حروف: ' . NumberWords::rial($amount)];
        if ($kind === RewardRules::ENFORCEMENT && ($calc['payers'] ?? []) !== []) {
            $model['notes'][] = implode(' — ', array_map(static fn ($org, $sum) => 'سهم ' . $org . ': ' . OfficialDocx::show((float) $sum) . ' ریال',
                array_keys($calc['orgs']), $calc['orgs'])) . ' (سهم نیروی انتظامی را ' . RewardRules::TAVANIR . ' و سهم سپاه را '
                . RewardRules::COMPANY . ' می‌پردازد)';
        }
        if ($state['final_at'] === null) {
            $model['notes'][] = 'پیش‌نویس — این فرم هنوز ثبت نهایی نشده است.';
        }
        return $model;
    }

    /** فایل فرم یک مرکز: اکسل یا Word، A4 ایستاده. */
    public static function send(array $row, int $kind, string $format): void
    {
        $model = self::build($row, $kind);
        $name = 'reward-' . RewardRules::KEYS[$kind] . '-' . preg_replace('/[^A-Za-z0-9-]/', '', (string) ($row['values']['tracking_code'] ?? $row['id']));
        $format === 'docx' ? OfficialDocx::send($name, $model, false) : OfficialXlsx::send($name, $model);
    }

    /** مشخصات مرکز، برچسب و مقدار دوتا دوتا. */
    private static function info(array $row): array
    {
        $v = $row['values'];
        $t = static fn ($x) => $x === null || $x === '' ? '—' : Jalali::digits((string) $x);
        $date = static fn ($m) => $m === null ? '—' : Jalali::format((int) $m, false);
        $place = trim(($v['county'] ?? '') . ($row['area_code'] ? ' — ' . Areas::label($row['area_code']) : ''), ' —');
        $commission = empty($v['commission']) ? 'ندارد' : 'دارد' . (($v['commission_letter'] ?? '') !== '' ? ' — ' . $v['commission_letter'] : '');
        return ['caption' => 'مشخصات مرکز استخراج رمزارز', 'pairs' => true, 'widths' => [6, 9, 6, 9], 'rows' => [
            ['کد رهگیری مرکز', $t($v['tracking_code'] ?? null), 'تاریخ گزارش', $date($v['report_date'] ?? null)],
            ['تاریخ بازدید و جمع‌آوری', $date($v['visit_date'] ?? null), 'شهرستان / ناحیه', $t($place)],
            ['نام مشترک متخلف', $t($v['subscriber'] ?? null), 'شماره اشتراک', $t($v['subscription'] ?? null)],
            ['شناسه قبض', $t($v['bill_id'] ?? null), 'شماره پرونده', $t($v['file_number'] ?? null)],
            ['تعداد ماینر کشف‌شده', $t($v['miners'] ?? null) . ' دستگاه', 'ظرفیت برآوردی (KW)', $t($v['capacity_kw'] ?? null)],
            ['نشانی', $t($v['address'] ?? null), 'صورتجلسه کمیسیون', Jalali::digits($commission)],
        ]];
    }

    private static function people(int $kind, array $row, ?array $calc): array
    {
        $v = $row['values'];
        $people = $calc['people'] ?? [];
        $n = count($people);
        $share = static fn () => Jalali::digits('۱ از ' . $n);
        $lines = [];
        $amount = (float) ($calc['amount'] ?? 0);
        switch ($kind) {
            case RewardRules::FINDER:
                $organ = implode('، ', array_filter([!empty($v['org_sepah']) ? 'اطلاعات سپاه' : null, !empty($v['org_vaja']) ? 'اداره اطلاعات' : null,
                    !empty($v['org_faraja']) ? 'پلیس امنیت اقتصادی' : null, ($v['org_other'] ?? '') ?: null])) ?: '—';
                $lines[] = [1, ($v['finder_name'] ?? '') ?: '—', $organ, 'شناسایی و اعلام مرکز', ($v['sheba'] ?? '') ?: '—', '۱۰۰٪', $amount];
                return ['caption' => 'عامل شناسایی و سهم پاداش',
                    'header' => ['ردیف', 'نام و نام خانوادگی', 'ارگان / نوع گزارش', 'اقدام انجام‌شده', 'شماره شبا', 'سهم', 'مبلغ پاداش (ریال)'],
                    'rows' => $lines, 'total' => ['جمع', '', '', '', '', '', $amount], 'widths' => [4, 9, 9, 13, 12, 4, 8], 'right' => [1, 3]];
            case RewardRules::COLLECTION:
                foreach ($people as $i => $p) {
                    $lines[] = [$i + 1, $p['name'], $p['post'] ?: '—', $p['action'] ?? '', RewardPeople::SOURCES[$p['source']] ?? '', $share(), (float) $p['share']];
                }
                return ['caption' => 'پرسنل جمع‌آوری و سهم هر نفر',
                    'header' => ['ردیف', 'نام و نام خانوادگی', 'سمت', 'اقدام انجام‌شده', 'منبع', 'سهم', 'مبلغ پاداش (ریال)'],
                    'rows' => $lines, 'total' => ['جمع', '', '', '', '', Jalali::digits($n . ' نفر'), self::sum($people)],
                    'widths' => [4, 9, 8, 15, 7, 5, 8], 'right' => [1, 3]];
            case RewardRules::ENFORCEMENT:
                $count = [];
                foreach ($people as $i => $p) {
                    $count[$p['org']] = ($count[$p['org']] ?? 0) + 1;
                    $lines[] = [$i + 1, $p['name'], $p['post'] ?: '—', RewardRules::ENFORCERS[$p['org']], $p['action'] ?? '', $share(), (float) $p['share']];
                }
                $orgs = implode(' — ', array_map(static fn ($o, $c) => ($o === 'faraja' ? 'انتظامی ' : 'سپاه ') . $c . ' نفر',
                    array_keys($count), $count));
                return ['caption' => 'عوامل انتظامی حاضر و سهم هر نفر',
                    'header' => ['ردیف', 'نام و نام خانوادگی', 'درجه / سمت', 'سازمان', 'اقدام انجام‌شده', 'سهم', 'مبلغ پاداش (ریال)'],
                    'rows' => $lines, 'total' => ['جمع', '', '', Jalali::digits($orgs), '', Jalali::digits($n . ' نفر'), self::sum($people)],
                    'widths' => [4, 9, 9, 11, 13, 5, 8], 'right' => [1, 4]];
            default:
                $pct = 0.0;
                foreach ($people as $i => $p) {
                    $ceo = $p['key'] === 'ceo';
                    $pct += $ceo ? 0 : (float) $p['pct'];
                    $lines[] = [$i + 1, $p['name'], $p['post'] ?: '—', $p['action'] ?? '',
                        $ceo ? 'سهم مدیرعامل' : Jalali::digits(self::num((float) $p['pct']) . '٪'), (float) $p['share']];
                }
                return ['caption' => 'عوامل اداری، درصد و سهم هر نفر',
                    'header' => ['ردیف', 'نام و نام خانوادگی', 'پست سازمانی', 'اقدام انجام‌شده', 'درصد از سهم کارکنان', 'مبلغ پاداش (ریال)'],
                    'rows' => $lines, 'total' => ['جمع', '', '', '', Jalali::digits(self::num($pct) . '٪'), self::sum($people)],
                    'widths' => [4, 9, 12, 14, 7, 9], 'right' => [1, 2, 3]];
        }
    }

    private static function subtitle(int $kind, array $v, ?array $calc): string
    {
        $center = 'مرکز ' . (($v['tracking_code'] ?? '') ?: ($v['subscription'] ?? '') ?: '—');
        return Jalali::digits(match ($kind) {
            RewardRules::FINDER => $center . ' — پرداخت از محل اعتبار ' . RewardRules::TAVANIR,
            RewardRules::COLLECTION => $center . ' — پرداخت از محل اعتبار ' . RewardRules::COMPANY,
            RewardRules::ENFORCEMENT => $center . ' — حاضران: نیروی انتظامی ' . (self::has($calc, 'faraja') ? '☑' : '☐')
                . '  سپاه ' . (self::has($calc, 'sepah') ? '☑' : '☐'),
            default => $center . ' — مبنا: ' . RewardRules::BASES[$calc['rule']['spec']['base'] ?? 'energy'] . ' '
                . number_format((float) ($calc['base'] ?? 0)) . ' ریال',
        });
    }

    /** «محاسبه برای این مرکز: …» */
    private static function calcText(int $kind, array $calc): string
    {
        $m = static fn ($x) => number_format((float) $x);
        $spec = $calc['rule']['spec'];
        $capped = (float) ($spec['cap'] ?? 0) > 0 && (float) $calc['pot'] >= (float) $spec['cap'] ? ' (سقف هر مرکز)' : '';
        $count = static fn () => $calc['count'] . ' ' . RewardRules::COUNTS[$calc['count_from'] ?? 'found']
            . (($spec['count'] ?? 'found') === 'collected' && ($calc['count_from'] ?? '') === 'found' ? ' (تعداد جمع‌آوری‌شده ثبت نشده)' : '');
        $text = match ($kind) {
            RewardRules::FINDER => 'محاسبه برای این مرکز (' . $calc['count'] . ' دستگاه): ' . $m($calc['amount']) . ' ریال' . $capped . '.',
            RewardRules::STAFF => 'محاسبه برای این مرکز: کل ' . self::num((float) $spec['pct']) . '٪ = ' . $m($calc['amount']) . ' ریال' . $capped
                . '؛ سهم مدیرعامل ' . $m($calc['ceo']) . ' ریال؛ سهم کارکنان ' . $m($calc['staff']) . ' ریال به درصد هر نفر.',
            default => 'محاسبه برای این مرکز: ' . $count() . (isset($spec['per_device']) ? ' × ' . $m($spec['per_device']) : '') . ' = '
                . $m($calc['pot']) . ' ریال' . $capped . '، میان ' . count($calc['people']) . ' نفر: هر نفر ' . $m($calc['each']) . ' ریال.',
        };
        return Jalali::digits($text);
    }

    private static function has(?array $calc, string $org): bool
    {
        foreach ($calc['people'] ?? [] as $p) {
            if ($p['org'] === $org) {
                return true;
            }
        }
        return false;
    }

    private static function sum(array $people): float
    {
        return (float) array_sum(array_column($people, 'share'));
    }

    private static function num(float $x): string
    {
        return rtrim(rtrim(number_format($x, 2, '.', ''), '0'), '.');
    }
}
