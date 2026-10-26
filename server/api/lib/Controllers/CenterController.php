<?php
declare(strict_types=1);

/**
 * آمار جامع مراکز استخراج و اقدامات، برای اپ مدیر (و مجری رمزارز اگر مدیر اجازه داده،
 * و واحدها در ستون‌هایی که مدیر برایشان تیک زده). پنل وب همین کتابخانه‌ها را مستقیم
 * صدا می‌زند. فقط پرونده‌های تأییدشده رمزارز؛ برق غیرمجاز نه.
 */
final class CenterController
{
    public function index(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user);
        [$from, $to] = self::range($request);
        $rows = CenterRows::query($access, $from, $to, $request->str('area') ?: null, $request->str('q'));
        Response::json([
            'from' => $from, 'to' => $to,
            'rows' => array_map(static fn ($row) => self::compact($row, $access), $rows),
            'totals' => array_intersect_key(CenterRows::totals($rows), array_flip($access['see'])),
            'columns' => array_values(array_filter(CenterColumns::spec(), static fn ($c) => in_array($c['key'], $access['see'], true))),
            'access' => ['fields' => CenterEdit::writable($access), 'manage' => $access['manage']],
            'years' => $access['manage'] ? CenterLocks::overview() : [],
            'charts' => $request->int('charts') === 1 ? CenterCharts::summary($rows) : null,
            'status_list' => CenterColumns::STATUS_LIST,
        ]);
    }

    public function row(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user);
        $row = CenterRows::one($access, $request->str('id'));
        if ($row === null) {
            Response::fail(404, 'not_found', 'این سطر پیدا نشد.');
        }
        Response::json(self::detail($row, $access));
    }

    public function save(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user);
        $id = $request->str('id');
        $error = CenterEdit::save($access, $user, $id, $request->arr('values'), $request->arr('choices'));
        if ($error !== null) {
            Response::fail(400, 'invalid', $error);
        }
        Response::json(self::detail(CenterRows::one($access, $id), $access));
    }

    public function add(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user);
        if ($access['role'] === Auth::ROLE_UNIT) {
            Response::fail(403, 'forbidden', 'واحد سطر تازه نمی‌سازد.');
        }
        $id = CenterEdit::add($access, $user, $request->str('date'), $request->str('area') ?: null, $request->str('copy_of') ?: null);
        if (str_starts_with($id, '!')) {
            Response::fail(400, 'invalid', substr($id, 1));
        }
        Response::json(['ok' => true, 'id' => $id]);
    }

    public function delete(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user, true);
        $error = CenterEdit::delete($access, $user, $request->str('id'));
        $error === null ? Response::json(['ok' => true]) : Response::fail(400, 'invalid', $error);
    }

    /** بارگذاری فایل و پیش‌نمایش؛ اجرا با همان نشانه در apply. */
    public function import(Request $request): void
    {
        $user = Auth::require($request, null);
        CenterSettings::require($user, true);
        // پنل با multipart می‌فرستد، اپ بایت‌های خام فایل را در بدنه.
        $file = $_FILES['file'] ?? null;
        $path = is_array($file) && ($file['error'] ?? 1) === UPLOAD_ERR_OK && is_uploaded_file((string) $file['tmp_name'])
            ? (string) $file['tmp_name'] : null;
        $raw = $path === null ? (string) file_get_contents('php://input', false, null, 0, 30 * 1024 * 1024) : '';
        if ($path === null && str_starts_with($raw, 'PK')) {
            $path = (string) tempnam(sys_get_temp_dir(), 'cimp');
            file_put_contents($path, $raw);
        }
        if ($path === null) {
            Response::fail(400, 'no_file', 'فایل اکسل نرسید.');
        }
        $year = $request->int('year') ?? FieldCodes::jalaliYear(Db::now());
        try {
            $token = CenterImportRun::stash($path, $user, $year);
            if ($raw !== '') {
                @unlink($path);
            }
            $plan = CenterImportRun::plan(CenterImportRun::open($token, $user)[0], $year, null);
        } catch (Throwable $e) {
            Response::fail(400, 'bad_file', 'این فایل اکسل (xlsx) خوانده نشد.');
        }
        Response::json(['token' => $token] + self::plan($plan));
    }

    public function apply(Request $request): void
    {
        $user = Auth::require($request, null);
        CenterSettings::require($user, true);
        $token = $request->str('token');
        $opened = CenterImportRun::open($token, $user);
        if ($opened === null) {
            Response::fail(404, 'expired', 'پیش‌نمایش منقضی شده؛ فایل را دوباره بارگذاری کنید.');
        }
        $sheets = $request->arr('sheets');
        $plan = CenterImportRun::plan($opened[0], $request->int('year') ?? $opened[1], $sheets === [] ? null : array_map('strval', $sheets));
        if ($request->int('preview') === 1) {
            Response::json(['token' => $token] + self::plan($plan));
        }
        $done = CenterImportRun::apply($plan, $user);
        CenterImportRun::discard($token);
        Response::json(['ok' => true] + $done);
    }

    public function export(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user);
        [$from, $to] = self::range($request);
        $rows = CenterRows::query($access, $from, $to, $request->str('area') ?: null, $request->str('q'));
        $format = $request->str('format') === 'docx' ? 'docx' : 'xlsx';
        $asked = array_filter(explode(',', $request->str('cols')));
        $keys = array_values(array_intersect($asked === [] ? CenterSettings::columns($format) : CenterColumns::pick($asked), $access['see']));
        $title = CenterCharts::title($from, $to);
        if ($format === 'docx') {
            CenterDocx::send('center-stats', $rows, $keys, 'آمار جامع مراکز استخراج و اقدامات — ' . $title,
                'از ' . Jalali::format($from, false) . ' تا ' . Jalali::format($to, false) . ' — ' . Jalali::digits((string) count($rows)) . ' مرکز');
        } else {
            CenterXlsx::send('center-stats', $rows, $keys, $title);
        }
        exit;
    }

    /** سه گزارش پاداش: kind = finder، enforcement یا staff؛ format = json، xlsx یا docx. */
    public function rewards(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user, true);
        [$from, $to] = self::range($request);
        $kind = $request->str('kind');
        $kind = isset(CenterRewardReport::KINDS[$kind]) ? $kind : 'finder';
        $model = CenterRewardReport::model($kind, CenterRows::query($access, $from, $to), $from, $to);
        match ($request->str('format')) {
            'xlsx' => OfficialXlsx::send($kind . '-rewards', $model),
            'docx' => OfficialDocx::send($kind . '-rewards', $model),
            default => Response::json(CenterRewardReport::forApp($model) + ['kind' => $kind, 'from' => $from, 'to' => $to]),
        };
        exit;
    }

    public function rules(Request $request): void
    {
        $user = Auth::require($request, null);
        CenterSettings::require($user, true);
        if ($request->method === 'POST') {
            if ($request->str('delete') !== '') {
                RewardRules::delete($request->str('delete'));
            } else {
                $result = RewardRules::save($request->body, $user, $request->str('id') ?: null);
                if (!Field::isUuid($result)) {
                    Response::fail(400, 'invalid', $result);
                }
            }
        }
        $out = [];
        foreach (RewardRules::NAMES as $kind => $name) {
            $out[] = ['kind' => $kind, 'name' => $name, 'rules' => array_map(static fn ($r) => $r + ['text' => RewardRules::describe($r)],
                RewardRules::list($kind))];
        }
        Response::json(['kinds' => $out, 'modes' => RewardRules::MODES, 'enforcers' => RewardRules::ENFORCERS,
            'bases' => RewardRules::BASES, 'staff_roles' => RewardSpec::STAFF_ROLES]);
    }

    public function settings(Request $request): void
    {
        $user = Auth::require($request, null);
        CenterSettings::require($user, true);
        if ($request->method === 'POST') {
            if ($request->int('year') !== null) {
                CenterLocks::set((int) $request->int('year'), $request->int('lock') === 1, $user);
                CenterEdit::log('year:' . $request->int('year'), '*', null, (string) $request->int('year'), $request->int('lock') === 1 ? 'lock' : 'unlock', $user);
            } else {
                CenterSettings::save($request->int('executor') === 1, $request->arr('units'), $request->arr('excel'), $request->arr('word'));
            }
        }
        Response::json(['executor' => CenterSettings::executorAllowed(), 'units' => CenterSettings::unitFields(),
            'unit_names' => UnitStages::NAMES, 'excel' => CenterSettings::columns('xlsx'), 'word' => CenterSettings::columns('docx'),
            'columns' => CenterColumns::spec(), 'years' => CenterLocks::overview()]);
    }

    /** بازه از from و to (میلی‌ثانیه) یا year شمسی؛ پیش‌فرض سال جاری. @return array{0:int, 1:int} */
    public static function range(Request $request): array
    {
        $year = $request->int('year');
        if ($request->int('from') === null && $request->int('to') === null) {
            return CenterCharts::year($year ?? FieldCodes::jalaliYear(Db::now()));
        }
        $from = $request->int('from') ?? 0;
        $to = $request->int('to') ?? Db::now();
        return [min($from, $to), max($from, $to)];
    }

    private static function compact(array $row, array $access): array
    {
        $see = array_flip($access['see']);
        return ['id' => $row['id'], 'report_id' => $row['report_id'], 'year' => $row['year'], 'area_code' => $row['area_code'],
            'source' => $row['source'], 'row_date' => $row['row_date'], 'locked' => $row['locked'], 'approved' => $row['approved'],
            'values' => array_intersect_key($row['values'], $see),
            'conflicts' => array_values(array_intersect($row['conflicts'], $access['see'])),
            'warnings' => $row['warnings'], 'hint' => $row['hint'], 'duplicate' => $row['duplicate'] ?? false];
    }

    private static function detail(array $row, array $access): array
    {
        $see = array_flip($access['see']);
        return self::compact($row, $access) + [
            'from' => array_intersect_key($row['from'], $see),
            'system' => array_intersect_key($row['system'], $see),
            'file' => array_intersect_key($row['file'], $see),
            'own' => array_intersect_key($row['own'], $see),
            'choices' => array_intersect_key($row['choices'], $see),
            'changes' => array_values(array_filter($row['changes'], static fn ($c) => $c['field'] === '*' || isset($see[$c['field']]))),
            'police' => $access['manage'] ? $row['police'] : null,
            'staff' => $access['manage'] ? $row['staff'] : null,
            'finder_rule' => RewardRules::describe($row['finder']['rule'] ?? null),
        ];
    }

    private static function plan(array $plan): array
    {
        return ['sheets' => $plan['sheets'], 'summary' => $plan['summary'],
            'rows' => array_map(static fn ($r) => ['sheet' => $r['sheet'], 'line' => $r['line'], 'year' => $r['year'],
                'status' => $r['status'], 'repeat_of' => $r['repeat_of'], 'conflicts' => $r['conflicts'],
                'subscription' => $r['values']['subscription'] ?? null, 'subscriber' => $r['values']['subscriber'] ?? null,
                'miners' => $r['values']['miners'] ?? null], array_slice($plan['rows'], 0, 400))];
    }
}
