<?php
declare(strict_types=1);

/**
 * فرم پاداش هر مرکز برای اپ مدیر: افراد با تیک، سازمان، اقدام و درصد؛ ذخیره، ثبت نهایی و
 * باز کردن؛ و فرم تک‌صفحه‌ای اکسل و Word. پنل وب همین کتابخانه‌ها را مستقیم صدا می‌زند. فقط مدیر.
 *
 * kind یکی از finder، collection، enforcement و staff است.
 */
final class RewardFormController
{
    public function form(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user, true);
        [$row, $kind] = self::load($access, $request);
        if ($request->method === 'POST') {
            $action = $request->str('action');
            $error = null;
            if ($action !== 'unlock' && array_key_exists('people', $request->body)) {
                $error = RewardForms::save($row, $kind, $request->arr('people'), $user);
                $row = CenterRows::one($access, $row['id']) ?? $row;
            }
            $error ??= match ($action) {
                'final' => RewardForms::finalize($row, $kind, $user),
                'unlock' => RewardForms::unlock($row, $kind, $user),
                default => null,
            };
            if ($error !== null) {
                Response::fail(400, 'invalid', $error);
            }
            $row = CenterRows::one($access, $row['id']) ?? $row;
        }
        Response::json(self::view($row, $kind));
    }

    public function formExport(Request $request): void
    {
        $user = Auth::require($request, null);
        $access = CenterSettings::require($user, true);
        [$row, $kind] = self::load($access, $request);
        RewardFormModel::send($row, $kind, $request->str('format'));
        exit;
    }

    /** آنچه اپ برای ویرایش فرم لازم دارد. */
    public static function view(array $row, int $kind): array
    {
        $state = $row['rewards'][$kind];
        $calc = $state['calc'];
        $shares = array_column($calc['people'] ?? [], 'share', 'key');
        $v = $row['values'];
        $rule = $calc['rule'] ?? RewardRules::at($kind, $row['row_date']);
        return [
            'row' => ['id' => $row['id'], 'year' => $row['year'], 'locked' => $row['locked'],
                'title' => ($v['subscriber'] ?? '') ?: ($v['subscription'] ?? '') ?: ($v['tracking_code'] ?? ''),
                'tracking_code' => $v['tracking_code'] ?? null, 'miners' => $v['miners'] ?? null, 'finder_name' => $v['finder_name'] ?? null],
            'kind' => RewardRules::KEYS[$kind], 'name' => RewardRules::NAMES[$kind],
            'final' => $state['final_at'] !== null, 'number' => $state['number'], 'final_at' => $state['final_at'], 'saved' => $state['saved'],
            'editable' => $kind !== RewardRules::FINDER && $state['final_at'] === null && !$row['locked'],
            'people' => array_map(static fn ($p) => $p + ['share' => $p['on'] ? (float) ($shares[$p['key']] ?? 0) : 0.0, 'new' => false],
                $kind === RewardRules::FINDER ? [] : $state['people']),
            'calc' => $calc === null ? null : array_diff_key($calc, ['rule' => 1, 'people' => 1]),
            'rule' => $rule === null ? null : RewardRules::describe($rule),
            'terms' => $rule === null ? null : Jalali::digits(RewardRules::terms($rule)),
            'model' => CenterRewardReport::forApp(RewardFormModel::build($row, $kind)),
            'names' => RewardPeople::names(),
            'posts' => self::posts($kind, $rule),
            'orgs' => RewardPeople::ORGS, 'sources' => RewardPeople::SOURCES,
        ];
    }

    /** پست‌هایی که مدیر برمی‌گزیند. @return list<string> */
    private static function posts(int $kind, ?array $rule): array
    {
        if ($kind !== RewardRules::STAFF) {
            return $kind === RewardRules::ENFORCEMENT ? ['فرمانده عملیات', 'افسر', 'درجه‌دار', 'کارشناس']
                : ['کارشناس بازدید', 'سرپرست اکیپ', 'سیم‌بان', 'مأمور وصول', 'راننده', 'کارشناس حراست'];
        }
        $labels = array_column($rule['spec']['roles'] ?? [], 'label');
        return array_values(array_unique(array_merge([RewardPeople::CEO], $labels, RewardSpec::STAFF_ROLES)));
    }

    /** @return array{0: array, 1: int} */
    private static function load(array $access, Request $request): array
    {
        $kind = array_search($request->str('kind'), RewardRules::KEYS, true);
        $row = CenterRows::one($access, $request->str('row'));
        if ($row === null || $kind === false) {
            Response::fail(404, 'not_found', 'این فرم پیدا نشد.');
        }
        return [$row, (int) $kind];
    }
}
