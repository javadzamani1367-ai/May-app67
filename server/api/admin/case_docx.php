<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

// فرم رسمی به فایل فقط با مدیر و مجری ستاد (CaseActions::EXPORT_ROLES).
admin_require(CaseActions::EXPORT_ROLES);

// همان فرم رسمی، به Word: هفت بخش، جدول دستگاه‌ها و حاضرین، و پیوست تصاویر.
// مثل فرم چاپی، نام نرم‌افزار در آن نیست.
$id = (string) ($_GET['id'] ?? '');
$case = admin_case($id);
$code = CaseLabels::code($case['report']);
$blocks = CaseReport::wordBlocks($case);
Docx::send('case-' . preg_replace('/[^A-Za-z0-9-]/', '', $code ?: $id), $blocks);
