<?php
declare(strict_types=1);

/**
 * ارجاع‌های میدانی برای اپ کارشناس. فقط نقش کارشناس، و هر کارشناس فقط
 * ارجاع‌های خودش: قاعده دسترسی در FieldReferrals است، یک‌جا.
 */
final class ReferralController
{
    public function index(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        Response::json(['referrals' => FieldReferrals::forExpert($user), 'server_time' => Db::now()]);
    }

    /** یک ارجاع با شناسه — همان که QR برگه ارجاع دارد. */
    public function item(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $item = FieldReferrals::oneForExpert($user, $request->str('id'));
        if ($item === null) {
            Response::fail(404, 'not_yours', 'این ارجاع به شما نیست یا دیگر وجود ندارد.');
        }
        Response::json(['referral' => $item]);
    }

    /** عکس گزارش‌دهنده، فقط برای کارشناسی که ارجاع به او رسیده. */
    public function file(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $id = $request->str('id');
        $row = Field::isUuid($id) ? Db::one(
            'SELECT f.path FROM field_files f JOIN field_items i ON i.id = f.item_id
             WHERE f.id = ? AND f.complete = 1 AND f.role IN (?, ?) AND i.assigned_to = ? AND i.merged_into IS NULL',
            [$id, Field::ROLE_PHOTO, Field::ROLE_PHOTO_STAMPED, $user['id']]
        ) : null;
        $path = $row === null || $row['path'] === null ? null : Storage::resolve((string) $row['path']);
        if ($path === null || !is_file($path)) {
            Response::fail(404, 'no_file', 'این فایل در دسترس شما نیست.');
        }
        header('Content-Type: application/octet-stream');
        header('Content-Length: ' . filesize($path));
        header('X-Content-Type-Options: nosniff');
        readfile($path);
        exit;
    }

    public function accept(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $error = FieldReferrals::accept($user, $request->str('id'), $request->str('report_id'));
        if ($error !== null) {
            Response::fail(409, 'refused', $error);
        }
        Response::json(['accepted' => true]);
    }

    public function result(Request $request): void
    {
        $user = Auth::require($request, Auth::ROLE_EXPERT);
        $error = FieldReferrals::result($user, $request->str('id'), $request->str('outcome'), $request->str('note'));
        if ($error !== null) {
            Response::fail(409, 'refused', $error);
        }
        Response::json(['saved' => true]);
    }
}
