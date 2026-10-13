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
        $rows = CaseActions::unitStats($from, $to);

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
