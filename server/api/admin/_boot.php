<?php
declare(strict_types=1);

/**
 * پنل وب مدیر: راه‌اندازی، نشست، و محافظت فرم‌ها.
 *
 * نشست جدا از پرتال واحدها (نام کوکی دیگر)، تا ورود به یکی هرگز معنای ورود به
 * دیگری را ندهد. فقط نقش مدیر وارد می‌شود. هر فرمی که چیزی را عوض می‌کند یک
 * توکن CSRF دارد: SameSite=Lax جلوی بیشتر حمله‌ها را می‌گیرد ولی این صفحه‌ها
 * کاربر می‌سازند و رمز عوض می‌کنند، و یک لایه کافی نیست.
 */
foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'LoginGuard', 'FieldSchema', 'Migrations', 'Storage',
          'Notifications', 'Jalali', 'Field', 'FieldSettings', 'Users', 'FieldAdmin', 'FieldLabels', 'Xlsx', 'FieldDuplicates', 'Qr', 'Docx', 'FieldPresenter', 'FieldLine', 'FieldReferrals', 'FieldManage',
          'CaseLabels', 'CaseReport', 'CaseArchive', 'CaseActions', 'PositionAudit', 'FieldRoster', 'FieldSubscribers', 'CrewCalendar', 'GeoPlaces', 'CrewWork', 'CrewDays', 'CrewReport', 'CrewMap', 'CrewSheets', 'CrewDocx',
          'Areas', 'Scope', 'UnitStages', 'UnitResponse', 'UserBatch', 'CaseCodes', 'CaseIntake', 'CaseFlow', 'CaseBoard',
          'FlowClock', 'FieldFlowClock', 'FieldHandOff', 'DeviceRegistry', 'DispatchMerge', 'Scores', 'ScoreBoard', 'ScoreSheets',
          'FieldCodes', 'CaseKind', 'IllegalFlow', 'Persons', 'PersonAccounts', 'PersonDevices', 'PersonDirectory', 'PersonWork',
          'DeviceRequests', 'CrewAccounts', 'FieldReadings', 'FieldHistory', 'FieldHistorySearch', 'FieldHistoryExport',
          'HistoryReferral', 'CenterSchema', 'CenterColumns', 'CenterValues', 'CenterSystem', 'CenterMatch', 'CenterCheck', 'CenterLocks',
          'CenterSettings', 'RewardRules', 'RewardSpec', 'CenterRewards', 'CenterRows', 'CenterEdit', 'XlsxReader', 'CenterHeader',
          'CenterSupplement', 'CenterImport', 'CenterImportRun', 'CenterTemplate', 'CenterGrid', 'TemplateBook', 'OfficialXlsx', 'OfficialDocx',  'CenterSupplementSheet', 'CenterXlsx',
          'CenterDocx', 'CenterRewardReport', 'CenterCharts'] as $class) {
    require_once dirname(__DIR__) . '/lib/' . $class . '.php';
}
require_once dirname(__DIR__) . '/portal/_layout.php';

Migrations::ensure();
FlowClock::maybeTick();

if (session_status() === PHP_SESSION_NONE) {
    session_name('tavankav_admin');
    session_set_cookie_params([
        'httponly' => true,
        'samesite' => 'Strict',
        'secure' => (($_SERVER['HTTPS'] ?? '') !== ''),
    ]);
    session_start();
}

/** نقش‌هایی که به پنل وارد می‌شوند: مدیر، و مجریان که هر کدام فقط محدوده و حوزه خودشان را می‌بینند. */
const ADMIN_ROLES = Auth::CASE_MANAGERS;
const CASE_ROLES = Auth::CASE_MANAGERS;
/** عملکرد اکیپ: مدیر و مجری رمزارز ناحیه. */
const CREW_ROLES = [Auth::ROLE_MANAGER, Auth::ROLE_EXECUTOR];

/** کاربر واردشده، یا تهی. حساب غیرفعال‌شده همان لحظه بیرون می‌افتد. */
function admin_user(): ?array
{
    static $user = false;
    if ($user !== false) {
        return $user;
    }
    $id = $_SESSION['admin_id'] ?? null;
    if (!is_string($id)) {
        return $user = null;
    }
    $row = Db::one('SELECT * FROM users WHERE id = ? AND active = 1', [$id]);
    return $user = ($row !== null && in_array((int) $row['role'], ADMIN_ROLES, true) ? $row : null);
}

/**
 * کاربر واردشده با یکی از این نقش‌ها. صفحه‌ای که مال نقش دیگری است، مجری را به
 * صفحه اول خودش می‌برد: هیچ صفحه‌ای بیرون از محدوده‌اش برایش باز نمی‌شود.
 *
 * @param list<int> $roles
 */
function admin_require(array $roles = [Auth::ROLE_MANAGER]): array
{
    $user = admin_user();
    if ($user === null) {
        header('Location: login.php');
        exit;
    }
    if (!in_array((int) $user['role'], $roles, true)) {
        header('Location: ' . admin_home($user));
        exit;
    }
    return $user;
}

/** پرونده‌ای که کاربر واردشده می‌بیند (مدیر همه، مجری ناحیه‌اش)، یا ۴۰۴. */
function admin_case(string $id): array
{
    $case = Field::isUuid($id) ? CaseReport::load($id) : null;
    if ($case === null || !admin_scope()->canSeeReport($case['report'])) {
        http_response_code(404);
        exit('پرونده پیدا نشد.');
    }
    return $case;
}

function admin_home(array $user): string
{
    return match ((int) $user['role']) {
        Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA => 'board.php',
        default => 'index.php',
    };
}

/** محدوده کاربر واردشده؛ همه پرس‌وجوهای فهرست از همین می‌گذرند. */
function admin_scope(): Scope
{
    return new Scope(admin_user() ?? ['role' => -1, 'areas' => null, 'id' => '']);
}

function csrf_token(): string
{
    if (!isset($_SESSION['csrf']) || !is_string($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="csrf" value="' . e(csrf_token()) . '">';
}

/** درخواست POST بدون توکن درست، پیش از هر تغییری متوقف می‌شود. */
function csrf_check(): void
{
    $sent = (string) ($_POST['csrf'] ?? '');
    if ($sent === '' || !hash_equals(csrf_token(), $sent)) {
        http_response_code(400);
        exit('درخواست نامعتبر است. صفحه را دوباره باز کنید.');
    }
}

function admin_header(string $title): void
{
    $user = admin_user();
    $role = (int) ($user['role'] ?? Auth::ROLE_MANAGER);
    $nav = match ($role) {
        Auth::ROLE_EXECUTOR => [['board.php', 'میز پیگیری'], ['intake.php', 'ثبت گزارش'], ['cases.php', 'پرونده‌ها'],
            ['approvals.php', 'تأییدها'], ['performance.php', 'عملکرد واحدها'], ['scores.php', 'امتیازها'],
            ['crew.php', 'عملکرد اکیپ'], ['items.php', 'موردهای میدانی'], ['history.php', 'سوابق بازرسی']],
        Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA => [['board.php', 'میز پیگیری'], ['intake.php', 'ثبت گزارش'],
            ['cases.php', 'پرونده‌ها'], ['approvals.php', 'تأییدها'], ['performance.php', 'عملکرد واحدها'],
            ['scores.php', 'امتیازها'], ['items.php', 'موردهای میدانی'], ['history.php', 'سوابق بازرسی']],
        default => [['index.php', 'داشبورد'], ['board.php', 'میز پیگیری'], ['intake.php', 'ثبت گزارش'],
            ['cases.php', 'آرشیو پرونده‌ها'], ['approvals.php', 'تأییدها'], ['performance.php', 'عملکرد واحدها'],
            ['scores.php', 'امتیازها'], ['centers.php', 'آمار جامع مراکز'], ['devices.php', 'بانک دستگاه‌ها'], ['crew.php', 'عملکرد اکیپ'],
            ['items.php', 'موردهای میدانی'], ['history.php', 'سوابق بازرسی'], ['map.php', 'نقشه'], ['persons.php', 'اشخاص و کاربری‌ها'], ['roster.php', 'اکیپ و فیدرها'],
            ['settings.php', 'تنظیمات']],
    };
    if ($role === Auth::ROLE_EXECUTOR && CenterSettings::executorAllowed()) {
        $nav[] = ['centers.php', 'آمار جامع مراکز'];
    }
    $nav[] = ['logout.php', 'خروج'];
    $subtitle = match ($role) {
        Auth::ROLE_EXECUTOR => 'پنل مجری رمزارز — ' . implode('، ', array_map([Areas::class, 'label'], Areas::parse($user['areas'] ?? ''))),
        Auth::ROLE_ILLEGAL => 'پنل مجری برق غیرمجاز ستاد',
        Auth::ROLE_ILLEGAL_AREA => 'پنل مجری برق غیرمجاز — ' . implode('، ', array_map([Areas::class, 'label'], Areas::parse($user['areas'] ?? ''))),
        default => 'پنل مدیر',
    };
    portal_header($title, true, $subtitle, $nav);
}

const ROLE_NAMES = Users::ROLE_NAMES;

/** حوزه‌ای که صفحه نشان می‌دهد: مجریان حوزه خودشان، مدیر با ?kind=1 برق غیرمجاز. */
function admin_kind(): int
{
    return admin_scope()->kind() ?? (((int) ($_GET['kind'] ?? 0)) === CaseKind::ILLEGAL ? CaseKind::ILLEGAL : CaseKind::CRYPTO);
}

/** دو زبانه رمزارز و برق غیرمجاز، فقط برای مدیر که هر دو را می‌بیند. */
function kind_tabs(): string
{
    if (!admin_scope()->isManager()) {
        return '';
    }
    $html = '<p class="row" style="justify-content:flex-start;gap:8px">';
    foreach (CaseKind::NAMES as $kind => $name) {
        $html .= '<a href="' . e(query_with($_GET, ['kind' => $kind === CaseKind::CRYPTO ? null : $kind])) . '"><button type="button"'
            . (admin_kind() === $kind ? '' : ' style="background:#64748b"') . '>' . e($name) . '</button></a>';
    }
    return $html . '</p>';
}

/** مجوزهای کاربر میدانی، به زبان آدم. */
function permission_label(int $bits): string
{
    $parts = [];
    if (($bits & Auth::PERM_INSPECT) !== 0) {
        $parts[] = 'بازرسی';
    }
    if (($bits & Auth::PERM_REPORT) !== 0) {
        $parts[] = 'گزارش';
    }
    return $parts === [] ? 'بدون مجوز' : implode(' و ', $parts);
}

/** برچسب رنگی وضعیت یک مورد، با «مهلت گذشته» اگر ارجاعش دیر شده. */
function field_status_tag(array $item): string
{
    $status = (int) $item['status'];
    $html = '<span class="tag ' . (FieldAdmin::STATUS_TAGS[$status] ?? 'tag-sent') . '">'
        . e(FieldAdmin::STATUS_NAMES[$status] ?? '—') . '</span>';
    if (FieldAdmin::isOverdue($item)) {
        $html .= ' <span class="tag tag-overdue">مهلت گذشته</span>';
    }
    return $html;
}

/** پارامترهای فعلی صفحه، با تغییر چند کلید — برای پیوند صفحه‌بندی و خروجی. */
function query_with(array $values, array $changes = []): string
{
    $merged = array_filter(array_merge($values, $changes), static fn ($v) => $v !== null && $v !== '');
    return $merged === [] ? '' : '?' . http_build_query($merged);
}

/** متن لاتین (کد کاربر، کد رهگیری) میان متن فارسی، جدا از جهت اطرافش تا جابه‌جا نشود. */
function ltr(string $text): string
{
    return "\u{2066}" . $text . "\u{2069}";
}
