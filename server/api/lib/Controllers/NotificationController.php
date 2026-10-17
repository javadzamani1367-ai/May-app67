<?php
declare(strict_types=1);

/** اعلان‌های یک کاربر: آنچه برای خودش، برای نقشش، یا برای واحدش ثبت شده. */
final class NotificationController
{
    public function index(Request $request): void
    {
        $user = Auth::require($request, null, true);
        $since = $request->int('since', 0) ?? 0;

        [$visible, $params] = Notifications::visibleTo($user);
        $rows = Db::all(
            "SELECT n.* FROM notifications n WHERE n.created_at > ? AND $visible ORDER BY n.created_at DESC LIMIT 200",
            array_merge([$since], $params)
        );

        Response::json([
            'notifications' => $rows,
            'unread' => count(array_filter($rows, static fn(array $r): bool => $r['read_at'] === null)),
            'server_time' => Db::now(),
        ]);
    }

    public function markRead(Request $request): void
    {
        $user = Auth::require($request, null, true);
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
