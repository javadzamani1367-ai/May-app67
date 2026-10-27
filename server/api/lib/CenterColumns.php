<?php
declare(strict_types=1);

/**
 * ستون‌های «آمار جامع مراکز استخراج و اقدامات»، به همان ترتیب و همان سرستون‌های
 * فرم توانیر (برگه «کلی»، ستون A تا AU)، و ستون‌های اضافه سامانه که مدیر می‌تواند
 * تیک بزند.
 *
 * هر ستون:
 *  - cols: حرف‌های فرم؛ چند حرف یعنی خانه‌های ادغام‌شده (نام عامل E:F). اضافه‌ها null
 *  - head: سرستون از بالا به پایین، هر سطح [متن، سطر شروع، سطر پایان] در چهار سطر
 *    سرستون؛ گروه‌های هم‌متن کنار هم ادغام می‌شوند، پس حذف یک ستون گروه را نمی‌شکند
 *  - type: text، int، money، kw، date، tick، choice، status، sheba
 *  - src: sys از پرونده، man فقط دستی، calc محاسبه از دستورالعمل پاداش
 *  - total: جمع (یا شمار تیک) در سطر جمع پایین جدول
 */
final class CenterColumns
{
    public const STATUS_LIST = ['قطع', 'وصل', 'پرداخت', 'قسط‌بندی', 'دادگاه', 'لاگ', 'جمع‌آوری', 'تکمیل مدارک',
        'ابلاغ ۲ هفته', 'صادر نشده'];
    public const BRANCH_TYPES = ['خانگی', 'صنعتی', 'سایر مصارف', 'عمومی', 'کشاورزی'];
    public const LICENSE = ['دارد', 'ندارد'];
    public const ADJUST = ['دارای انشعاب', 'فاقد انشعاب'];
    /** راهنمای ستون «سایر موارد» در پایین فرم. */
    public const OTHER_LEGEND = [1 => 'گزارش مردمی یا سراغ', 2 => 'گزارش همکار', 3 => 'گزارش آتش نشانی', 4 => 'غیره'];

    /** بخش‌های صفحه ویرایش سطر (پنل و اپ)، به ترتیب کار. */
    public const SECTIONS = [
        'پرونده و مکان' => ['tracking_code', 'county', 'area', 'report_date', 'visit_date', 'address', 'coordinates', 'expert'],
        'عامل شناسایی' => ['finder_name', 'org_sepah', 'org_vaja', 'org_faraja', 'org_other', 'sheba', 'miners', 'finder_reward'],
        'مرکز و مشترک' => ['sms_code', 'capacity_kw', 'bill_id', 'subscriber', 'subscription', 'file_number', 'owner_name',
            'owner_national_id', 'measured_amperage', 'branch_type', 'meter_type', 'install_place'],
        'کمیسیون و نامه‌ها' => ['commission', 'commission_letter', 'sales_letter', 'sales_letter_fix', 'license', 'adjust_branch',
            'account_reason'],
        'انشعاب غیرمجاز' => ['with_found', 'with_collected', 'with_kw', 'with_bill', 'without_found', 'without_collected',
            'without_kw', 'without_bill'],
        'مبالغ و پاداش‌ها' => ['energy_amount', 'damage_amount', 'reward_amount', 'collected_amount', 'collection_reward', 'police_reward',
            'staff_reward'],
        'وضعیت' => ['status'],
    ];

    private const FINDER = ['مشخصات عامل شناسایی', 1, 1];
    private const ORGAN = ['ارگان شناسایی کننده', 2, 2];
    private const CENTER = ['مشخصات مرکز کشف شده و اطلاعات ثبت شده در حراست شرکت توزیع نیروی برق', 1, 2];
    private const WITH = ['وضعیت انشعاب غیرمجاز با داشتن انشعاب قانونی', 1, 3];
    private const WITHOUT = ['وضعیت انشعاب غیرمجاز بدون داشتن انشعاب قانونی', 1, 3];

    /** @var array<string, array<string, mixed>>|null */
    private static ?array $all = null;

    /** @return array<string, array<string, mixed>> کلید → تعریف، به ترتیب فرم و بعد اضافه‌ها */
    public static function all(): array
    {
        if (self::$all !== null) {
            return self::$all;
        }
        $list = [
            self::c('row', ['A'], [['ردیف', 1, 4]], 'int', 'calc'),
            self::c('county', ['B'], [['شهرستان / بخش', 1, 4]], 'text', 'sys'),
            self::c('report_date', ['C'], [['تاریخ گزارش', 1, 4]], 'date', 'sys'),
            self::c('visit_date', ['D'], [['تاریخ بازدید', 1, 4]], 'date', 'sys'),
            self::c('finder_name', ['E', 'F'], [self::FINDER, ['نام و نام خانوادگی', 2, 4]], 'text', 'man'),
            self::c('org_sepah', ['G'], [self::FINDER, self::ORGAN, ['اطلاعات سپاه', 3, 4]], 'tick', 'man', true),
            self::c('org_vaja', ['H'], [self::FINDER, self::ORGAN, ['اداره اطلاعات (واجا)', 3, 4]], 'tick', 'man', true),
            self::c('org_faraja', ['I'], [self::FINDER, self::ORGAN, ['پلیس امنیت اقتصادی (فراجا)', 3, 4]], 'tick', 'man', true),
            self::c('org_other', ['J'], [self::FINDER, self::ORGAN, ['* سایر موارد', 3, 4]], 'text', 'sys', true),
            self::c('sheba', ['K'], [self::FINDER, ['شماره شبا بانکی', 2, 4]], 'sheba', 'man'),
            self::c('miners', ['L'], [self::FINDER, ['تعداد ماینر کشف شده (دستگاه)', 2, 4]], 'int', 'sys', true),
            self::c('finder_reward', ['M'], [self::FINDER, ['مبلغ پاداش (ریال)', 2, 4]], 'money', 'calc', true),
            self::c('sms_code', ['N', 'O'], [self::CENTER, ['کد پیگیری سامانه پیامکی', 3, 4]], 'text', 'sys'),
            self::c('capacity_kw', ['P', 'Q', 'R'], [self::CENTER, ['مجموع ظرفیت برآوردی دستگاه‌ها (KW)', 3, 4]], 'kw', 'sys', true),
            self::c('bill_id', ['S', 'T'], [self::CENTER, ['شناسه قبض مشترک متخلف', 3, 4]], 'text', 'sys'),
            self::c('subscriber', ['U', 'V'], [self::CENTER, ['نام مشترک متخلف', 3, 4]], 'text', 'sys'),
            self::c('subscription', ['W'], [['شماره اشتراک', 1, 4]], 'text', 'sys'),
            self::c('file_number', ['X'], [['شماره پرونده', 1, 4]], 'text', 'sys'),
            self::c('commission', ['Y'], [['کمیسیون رمزارز', 1, 4]], 'tick', 'sys', true),
            self::c('commission_letter', ['Z'], [['شناسه نامه کمیسیون', 1, 4]], 'text', 'man'),
            self::c('sales_letter', ['AA'], [['شناسه نامه فروش انرژی', 1, 4]], 'text', 'man'),
            self::c('sales_letter_fix', ['AB'], [['شناسه نامه فروش انرژی (اصلاحیه)', 1, 4]], 'text', 'man'),
            self::c('branch_type', ['AC'], [['نوع انشعاب - خانگی - صنعتی - سایر مصارف - عمومی - کشاورزی', 1, 4]], 'choice', 'sys'),
            self::c('with_found', ['AD'], [self::WITH, ['تعداد شناسایی شده', 4, 4]], 'int', 'sys', true),
            self::c('with_collected', ['AE'], [self::WITH, ['تعداد جمع آوری شده', 4, 4]], 'int', 'man', true),
            self::c('with_kw', ['AF'], [self::WITH, ['کیلو وات شناسایی شده', 4, 4]], 'kw', 'man', true),
            self::c('with_bill', ['AG'], [self::WITH, ['بهاء برق مصرفی (ریال)', 4, 4]], 'money', 'man', true),
            self::c('without_found', ['AH'], [self::WITHOUT, ['تعداد شناسایی شده', 4, 4]], 'int', 'sys', true),
            self::c('without_collected', ['AI'], [self::WITHOUT, ['تعداد جمع آوری شده', 4, 4]], 'int', 'man', true),
            self::c('without_kw', ['AJ'], [self::WITHOUT, ['کیلو وات شناسایی شده', 4, 4]], 'kw', 'man', true),
            self::c('without_bill', ['AK'], [self::WITHOUT, ['بهاء برق مصرفی', 4, 4]], 'money', 'man', true),
            self::c('license', ['AL'], [['وضعیت مجوز قانونی رمزارز (پروانه بهره‌برداری)', 1, 3], ['دارد / ندارد', 4, 4]], 'choice', 'man'),
            self::c('adjust_branch', ['AM'], [['نوع انشعاب برای صدور تعدیل', 1, 4]], 'choice', 'sys'),
            self::c('account_reason', ['AN'], [['علل حساب', 1, 4]], 'text', 'man'),
            self::c('energy_amount', ['AO'], [['مبلغ انرژی (ریال)', 1, 4]], 'money', 'man', true),
            self::c('damage_amount', ['AP'], [['مبلغ خسارت (ریال)', 1, 4]], 'money', 'man', true),
            self::c('reward_amount', ['AQ'], [['مبلغ پاداش (ریال)', 1, 4]], 'money', 'man', true),
            self::c('collected_amount', ['AR'], [['مبلغ وصول (ریال)', 1, 4]], 'money', 'man', true),
            self::c('meter_type', ['AS'], [['نوع کنتور', 1, 4]], 'text', 'sys'),
            self::c('install_place', ['AT'], [['محل نصب', 1, 4]], 'text', 'man'),
            self::c('status', ['AU'], [['قطع/وصل/قسطی/دادگاه/لاگ', 1, 4]], 'status', 'man'),
            // ستون‌های اضافه سامانه؛ در فرم توانیر نیستند و فقط با تیک مدیر می‌آیند.
            self::c('tracking_code', null, [['کد رهگیری', 1, 4]], 'text', 'sys'),
            self::c('area', null, [['ناحیه', 1, 4]], 'text', 'sys'),
            self::c('address', null, [['آدرس', 1, 4]], 'text', 'sys'),
            self::c('coordinates', null, [['مختصات', 1, 4]], 'text', 'sys'),
            self::c('expert', null, [['کارشناس بازدید', 1, 4]], 'text', 'sys'),
            self::c('owner_name', null, [['نام مالک یا متصرف', 1, 4]], 'text', 'sys'),
            self::c('owner_national_id', null, [['کد ملی مالک', 1, 4]], 'text', 'sys'),
            self::c('measured_amperage', null, [['آمپر اندازه‌گیری‌شده', 1, 4]], 'kw', 'sys'),
            self::c('collection_reward', null, [['پاداش جمع‌آوری شرکت توزیع (ریال)', 1, 4]], 'money', 'calc', true),
            self::c('police_reward', null, [['پاداش عوامل انتظامی (ریال)', 1, 4]], 'money', 'calc', true),
            self::c('staff_reward', null, [['پاداش عوامل اداری (ریال)', 1, 4]], 'money', 'calc', true),
        ];
        self::$all = [];
        foreach ($list as $column) {
            self::$all[$column['key']] = $column;
        }
        return self::$all;
    }

    /** ستون‌های فرم توانیر: پیش‌فرض اکسل. */
    public static function templateKeys(): array
    {
        return array_keys(array_filter(self::all(), static fn (array $c) => $c['cols'] !== null));
    }

    public static function extraKeys(): array
    {
        return array_keys(array_filter(self::all(), static fn (array $c) => $c['cols'] === null));
    }

    /** ستون‌هایی که کاربر می‌نویسد (دستی یا اصلاح مقدار سامانه)؛ محاسبه‌ای‌ها نه. */
    public static function editableKeys(): array
    {
        return array_keys(array_filter(self::all(), static fn (array $c) => $c['src'] !== 'calc'));
    }

    public static function get(string $key): ?array
    {
        return self::all()[$key] ?? null;
    }

    /** نام کوتاه ستون: پایین‌ترین سطح سرستون، با گروهش وقتی تنها گنگ است. */
    public static function label(string $key): string
    {
        $column = self::get($key);
        if ($column === null) {
            return $key;
        }
        $leaf = end($column['head'])[0];
        $group = count($column['head']) > 1 ? $column['head'][0][0] : null;
        return match ($key) {
            'with_found', 'with_collected', 'with_kw', 'with_bill' => $leaf . ' (دارای انشعاب)',
            'without_found', 'without_collected', 'without_kw', 'without_bill' => $leaf . ' (فاقد انشعاب)',
            'finder_reward' => 'پاداش عامل شناسایی (ریال)',
            'license' => 'مجوز رمزارز (دارد / ندارد)',
            'status' => 'وضعیت (قطع/وصل/قسطی/دادگاه/لاگ)',
            'branch_type' => 'نوع انشعاب',
            default => $leaf . ($group !== null && $key === 'finder_name' ? ' عامل شناسایی' : ''),
        };
    }

    /** گزینه‌های ستون انتخابی. @return list<string> */
    public static function choices(string $key): array
    {
        return match ($key) {
            'branch_type' => self::BRANCH_TYPES,
            'license' => self::LICENSE,
            'adjust_branch' => self::ADJUST,
            'status' => self::STATUS_LIST,
            default => [],
        };
    }

    /** فهرست تیک‌خورده معتبر، به ترتیب فرم؛ کلید ناشناخته کنار می‌رود. @return list<string> */
    public static function pick(array $keys, ?array $fallback = null): array
    {
        $wanted = array_flip(array_map('strval', $keys));
        $picked = array_values(array_filter(array_keys(self::all()), static fn (string $k) => isset($wanted[$k])));
        return $picked === [] ? ($fallback ?? self::templateKeys()) : $picked;
    }

    /** برای اپ و پنل: کلید، نام، نوع، منبع، گزینه‌ها و اینکه در فرم توانیر هست. */
    public static function spec(): array
    {
        $spec = [];
        foreach (self::all() as $key => $c) {
            $spec[] = ['key' => $key, 'label' => self::label($key), 'type' => $c['type'], 'src' => $c['src'],
                'template' => $c['cols'] !== null, 'total' => $c['total'], 'choices' => self::choices($key),
                'section' => self::section($key)];
        }
        return $spec;
    }

    public static function section(string $key): string
    {
        foreach (self::SECTIONS as $title => $keys) {
            if (in_array($key, $keys, true)) {
                return $title;
            }
        }
        return '';
    }

    private static function c(string $key, ?array $cols, array $head, string $type, string $src, bool $total = false): array
    {
        return ['key' => $key, 'cols' => $cols, 'head' => $head, 'type' => $type, 'src' => $src, 'total' => $total];
    }
}
