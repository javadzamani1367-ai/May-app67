<?php
declare(strict_types=1);

/**
 * برچسب فارسی کلیدهای چک‌لیست گزارش‌های میدانی، همان که گوشی نشان می‌دهد.
 * ساخته‌شده با tools/field_labels.py از ReportLabels.kt و رشته‌های اپ؛
 * دستی ویرایش نکنید — CI ناهمخوانی را رد می‌کند.
 */
final class FieldLabels
{
    public const SIGNS = [
        'fan_noise' => 'صدای مداوم فن یا وزوز، به‌خصوص شب',
        'heat_exhaust' => 'گرمای غیرعادی یا خروجی هوای گرم',
        'covered_windows' => 'پنجره پوشانده یا فن صنعتی روی دیوار',
        'heavy_cabling' => 'کابل‌کشی غیرعادی یا کابل قطور',
        'new_transformer' => 'انشعاب یا ترانس تازه نزدیک محل',
        'network_gear' => 'مودم یا آنتن اینترنت غیرمعمول',
        'night_activity' => 'رفت‌وآمد شبانه یا محل بسته با برق روشن',
        'neighbour_report' => 'گزارش همسایه یا شاکی',
        'thermal_hot_pole' => 'دمای بالای تیر یا تابلو در ترموویژن',
        'voltage_drops' => 'افت ولتاژ یا قطعی مکرر در منطقه',
    ];

    public const CONSUMERS = [
        'air_conditioner' => 'کولر گازی',
        'heater' => 'بخاری یا هیتر برقی',
        'water_pump' => 'پمپ آب',
        'industrial_motor' => 'موتور صنعتی',
        'welder' => 'دستگاه جوش',
        'furnace' => 'کوره یا المنت',
    ];

    public const USAGES = [
        'residential' => 'مسکونی',
        'commercial' => 'تجاری',
        'industrial' => 'صنعتی',
        'agri_well' => 'کشاورزی — چاه',
        'agri_greenhouse' => 'کشاورزی — گلخانه',
        'agri_poultry' => 'کشاورزی — مرغداری',
        'agri_livestock' => 'کشاورزی — دامداری',
        'construction' => 'ساختمان در حال ساخت',
        'other' => 'سایر',
    ];

    public const VIOLATIONS = [
        'no_meter' => 'اتصال مستقیم بدون کنتور',
        'meter_bypass' => 'دور زدن کنتور',
        'meter_tamper' => 'دستکاری کنتور یا شکستن پلمب',
        'borrowed_supply' => 'انشعاب دیگران یا انشعاب موقت',
        'wrong_usage' => 'کاربری غیرمجاز',
    ];

    public const TEAM_QUESTIONS = [
        'vehicle_access' => 'خودرو تا محل می‌رسد؟',
        'needs_ladder' => 'نردبان، بالابر یا جرثقیل لازم است؟',
        'needs_police' => 'همراهی نیروی انتظامی یا مأمور قضایی لازم است؟',
        'guarded' => 'سگ نگهبان، نگهبان، دوربین یا درب امن دیده شد؟',
        'active_now' => 'محل الان فعال و روشن است؟',
        'safety_hazard' => 'خطر ایمنی هست؟ (سیم لخت، آب، سازه ناایمن)',
        'people_seen' => 'ساکن یا فردی در محل دیده شد؟',
    ];

    public const BEST_TIMES = [
        'morning' => 'صبح',
        'afternoon' => 'عصر',
        'night' => 'شب',
    ];
}
