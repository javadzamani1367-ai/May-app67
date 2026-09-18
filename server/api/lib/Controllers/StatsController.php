<?php
declare(strict_types=1);

/**
 * عملکرد واحدها: چند مورد گرفتند، چند تا را پاسخ دادند، چقدر طول کشید و چند
 * مورد از مهلت گذشته است. مدیر از همین داده خروجی اکسل، ورد و PDF می‌گیرد.
 */
final class StatsController
{
    public function units(Request $request): void
    {
        Auth::require($request, Auth::ROLE_MANAGER);
        $from = $request->int('from', 0) ?? 0;
        $to = $request->int('to', Db::now()) ?? Db::now();
        $now = Db::now();

        $rows = Db::all(
            'SELECT unit,
                    COUNT(*)                                       AS sent,
                    SUM(status >= 1)                               AS seen,
                    SUM(status = 2)                                AS answered,
                    SUM(deadline_at IS NOT NULL AND status <> 2 AND deadline_at < ?) AS overdue,
                    SUM(deadline_at IS NOT NULL AND status = 2 AND answered_at <= deadline_at) AS on_time,
                    AVG(CASE WHEN answered_at IS NOT NULL
                             THEN answered_at - dispatched_at END) AS avg_answer_millis
             FROM dispatches
             WHERE dispatched_at BETWEEN ? AND ?
             GROUP BY unit
             ORDER BY unit',
            [$now, $from, $to]
        );

        foreach ($rows as &$row) {
            $sent = max(1, (int) $row['sent']);
            $row['answer_rate'] = round(((int) $row['answered'] / $sent) * 100, 1);
            $row['avg_answer_hours'] = $row['avg_answer_millis'] === null
                ? null
                : round(((float) $row['avg_answer_millis']) / 3600000, 1);
            unset($row['avg_answer_millis']);
        }

        Response::json([
            'units' => $rows,
            'from' => $from,
            'to' => $to,
            // پرونده‌هایی که هنوز منتظر تأیید مدیر مانده‌اند، چون در گزارش
            // عملکرد همان‌قدر مهم‌اند که کندی یک واحد.
            'pending_approvals' => (int) (Db::one(
                'SELECT COUNT(*) AS c FROM approvals WHERE decision = 0'
            )['c'] ?? 0),
        ]);
    }
}
