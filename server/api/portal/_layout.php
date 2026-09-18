<?php
declare(strict_types=1);

/**
 * پوسته مشترک صفحات پرتال. راست‌به‌چپ، فارسی، و بدون هیچ فایل بیرونی —
 * واحد مقصد ممکن است پشت شبکه‌ای باشد که CDN را نمی‌بیند.
 */
function portal_header(string $title): void
{
    ?><!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= htmlspecialchars($title, ENT_QUOTES, 'UTF-8') ?></title>
<style>
  :root { color-scheme: light; --line:#d8dee9; --ink:#12212f; --muted:#5b6b7c; --brand:#0b5d8a; }
  * { box-sizing: border-box; }
  body { margin:0; background:#f4f6f8; color:var(--ink);
         font-family:"Vazirmatn","IRANSans","Segoe UI",Tahoma,sans-serif; line-height:1.8; }
  header { background:var(--brand); color:#fff; padding:14px 18px; }
  header a { color:#fff; text-decoration:none; margin-inline-start:14px; }
  main { max-width:900px; margin:18px auto; padding:0 14px; }
  .card { background:#fff; border:1px solid var(--line); border-radius:10px;
          padding:16px; margin-bottom:14px; }
  .muted { color:var(--muted); font-size:.9em; }
  .row { display:flex; gap:12px; flex-wrap:wrap; justify-content:space-between; }
  label { display:block; margin-top:10px; font-size:.95em; }
  input, textarea, select { width:100%; padding:10px; border:1px solid var(--line);
          border-radius:8px; font:inherit; background:#fff; }
  button { background:var(--brand); color:#fff; border:0; border-radius:8px;
           padding:11px 18px; font:inherit; cursor:pointer; margin-top:12px; }
  table { width:100%; border-collapse:collapse; }
  th, td { border-bottom:1px solid var(--line); padding:9px; text-align:right; }
  .tag { display:inline-block; padding:2px 10px; border-radius:999px; font-size:.85em; }
  .tag-sent { background:#e8eef4; color:#12384f; }
  .tag-seen { background:#fff3d6; color:#6b4b00; }
  .tag-answered { background:#e3f5e6; color:#14532d; }
  .tag-overdue { background:#fde4e4; color:#8a1616; }
  .error { background:#fde4e4; color:#8a1616; padding:10px; border-radius:8px; }
  @media (max-width:600px){ main{margin:10px auto;} .card{padding:12px;} }
</style>
</head>
<body>
<header>
  <strong>سامانه بازدید مراکز رمزارز</strong>
  <a href="index.php">صندوق ورودی</a>
  <a href="logout.php">خروج</a>
</header>
<main>
<?php
}

function portal_footer(): void
{
    ?></main>
<p class="muted" style="text-align:center">قدرت گرفته از کاراکو</p>
</body></html><?php
}

/** عدد فارسی برای نمایش؛ ذخیره‌سازی همیشه لاتین می‌ماند. */
function fa_digits(?string $value): string
{
    if ($value === null) {
        return '';
    }
    return strtr($value, ['0'=>'۰','1'=>'۱','2'=>'۲','3'=>'۳','4'=>'۴',
                          '5'=>'۵','6'=>'۶','7'=>'۷','8'=>'۸','9'=>'۹']);
}

function e(?string $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES, 'UTF-8');
}
