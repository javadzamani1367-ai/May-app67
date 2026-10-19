<?php
declare(strict_types=1);

/**
 * عملکرد واحدها: چند مورد گرفتند، چند تا را پاسخ دادند، چقدر طول کشید و چند
 * مورد از مهلت گذشته است. مدیر از همین داده خروجی اکسل، ورد و PDF می‌گیرد.
 */
final class StatsController
{
    /** امتیاز ۱۰۰ ناحیه‌ها، مجریان، واحدها و کارشناسان؛ مجری فقط ناحیه‌های خودش. */
    public function scores(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, Auth::CASE_MANAGERS));
        $to = $request->int('to') ?? Db::now();
        $from = $request->int('from') ?? $to - 30 * 86_400_000;
        // مدیر هر دو حوزه را می‌بیند (kind=1 برای برق غیرمجاز)؛ مجریان حوزه خودشان را.
        $board = (new ScoreBoard(min($from, $to), max($from, $to), $scope, Db::now(), $request->int('kind')))->all();
        if ($request->str('format') === 'xlsx') {
            Xlsx::sendBook('scores', ScoreSheets::book($board));
            exit;
        }
        Response::json($board + ['from' => $from, 'to' => $to, 'spec' => ScoreSheets::spec()]);
    }

    public function units(Request $request): void
    {
        $scope = new Scope(Auth::requireAny($request, Auth::CASE_MANAGERS));
        $from = $request->int('from', 0) ?? 0;
        $to = $request->int('to', Db::now()) ?? Db::now();
        $kind = $scope->kind() ?? $request->int('kind');
        $rows = CaseActions::unitStats($from, $to, $scope, $kind);
        [$where, $params] = CaseActions::dispatchWhere($scope, $kind);

        Response::json([
            'units' => $rows,
            'from' => $from,
            'to' => $to,
            // پرونده‌هایی که هنوز منتظر تأیید مدیر مانده‌اند، چون در گزارش
            // عملکرد همان‌قدر مهم‌اند که کندی یک واحد.
            'pending_approvals' => (int) (Db::one(
                "SELECT COUNT(*) AS c FROM approvals a JOIN reports r ON r.id = a.report_id WHERE a.decision = 0 AND $where",
                $params
            )['c'] ?? 0),
        ]);
    }
}
