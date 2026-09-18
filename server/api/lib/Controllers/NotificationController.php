<?php
declare(strict_types=1);

/** اعلان‌های یک کاربر: آنچه برای خودش، برای نقشش، یا برای واحدش ثبت شده. */
final class NotificationController
{
    public function index(Request $request): void
    {
        $user = Auth::require($request);
        $since = $request->int('since', 0) ?? 0;

        $rows = Db::all(
            'SELECT * FROM notifications
             WHERE created_at > ?
               AND (user_id = ?
                    OR (user_id IS NULL AND role = ? AND (unit IS NULL OR unit = ?)))
             ORDER BY created_at DESC LIMIT 200',
            [$since, $user['id'], (int) $user['role'], $user['unit']]
        );

        Response::json([
            'notifications' => $rows,
            'unread' => count(array_filter($rows, static fn(array $r): bool => $r['read_at'] === null)),
            'server_time' => Db::now(),
        ]);
    }

    public function markRead(Request $request): void
    {
        $user = Auth::require($request);
        $ids = $request->arr('ids');
        if ($ids === []) {
            Response::json(['done' => true]);
        }
        $placeholders = implode(', ', array_fill(0, count($ids), '?'));
        Db::run(
            "UPDATE notifications SET read_at = ?
             WHERE id IN ($placeholders) AND (user_id = ? OR user_id IS NULL)",
            array_merge([Db::now()], array_values($ids), [$user['id']])
        );
        Response::json(['done' => true]);
    }
}
