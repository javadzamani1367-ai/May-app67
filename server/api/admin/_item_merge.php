<?php
declare(strict_types=1);

/*
 * بخش ادغام صفحه مورد: موردهای احتمالاً تکراری، و اگر خود مورد ادغام شده،
 * مقصدش با دکمه جدا کردن. فقط از item.php خوانده می‌شود.
 */

/** فاصله به متر، برای نمایش. */
function metres_text(?float $metres): string
{
    return $metres === null ? '' : fa_digits((string) (int) round($metres)) . ' متر';
}

function render_merge(array $item, ?array $target, array $candidates, array $merged): string
{
    $html = '';
    if ($target !== null) {
        $html .= '<div class="card"><p class="error">این مورد در <a href="item.php?id=' . e($target['id']) . '">'
            . e($target['tracking_code']) . '</a> ادغام شده است.</p>'
            . '<form method="post">' . csrf_field() . '<input type="hidden" name="action" value="unmerge">'
            . '<button type="submit">جدا کردن از ادغام</button></form></div>';
    }
    if ($merged !== []) {
        $links = array_map(static fn ($m) => '<a href="item.php?id=' . e($m['id']) . '">' . e($m['tracking_code']) . '</a>', $merged);
        $html .= '<div class="card"><h3>موردهای ادغام‌شده در این مورد</h3><p>' . implode('، ', $links) . '</p></div>';
    }
    if ($candidates !== []) {
        $html .= '<div class="card"><h3>موردهای احتمالاً تکراری</h3><p class="muted">ادغام، این مورد را می‌بندد و به مورد انتخاب‌شده'
            . ' می‌پیوندد؛ چیزی پاک نمی‌شود و برگشت‌پذیر است.</p><table>'
            . '<tr><th>کد</th><th>تاریخ</th><th>فاصله</th><th>وضعیت</th><th></th></tr>';
        foreach ($candidates as $c) {
            $html .= '<tr><td dir="ltr" style="text-align:right"><a href="item.php?id=' . e($c['id']) . '">'
                . e($c['tracking_code']) . '</a></td><td>' . e(Jalali::format((int) $c['created_at'])) . '</td><td>'
                . metres_text($c['metres']) . '</td><td>' . field_status_tag($c) . '</td><td>'
                . '<form method="post" style="margin:0">' . csrf_field()
                . '<input type="hidden" name="action" value="merge"><input type="hidden" name="target" value="' . e($c['id']) . '">'
                . '<button type="submit" style="margin:0;padding:6px 14px">ادغام این مورد در آن</button></form></td></tr>';
        }
        $html .= '</table></div>';
    }
    return $html;
}
