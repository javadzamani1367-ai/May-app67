<?php
declare(strict_types=1);

/**
 * پوسته مشترک صفحات پرتال. راست‌به‌چپ، فارسی، و بدون هیچ فایل بیرونی —
 * واحد مقصد ممکن است پشت شبکه‌ای باشد که CDN را نمی‌بیند. نشان توان‌کاو هم
 * به همین دلیل به صورت SVG درون خود صفحه است.
 *
 * رنگ‌ها همان خانواده‌های اپ هستند و هر کدام فقط یک معنا دارند: سرمه‌ای قاب،
 * سبزآبی اقدام، کهربایی دیده‌شده و منتظر، سبز پاسخ‌داده، قرمز مهلت‌گذشته.
 *
 * @param bool $signedIn پیوندهای صندوق و خروج فقط برای کاربر واردشده معنا دارند.
 */
function portal_header(string $title, bool $signedIn = true): void
{
    ?><!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= htmlspecialchars($title, ENT_QUOTES, 'UTF-8') ?> | توان‌کاو</title>
<style>
  :root { color-scheme: light;
          --navy:#0b1f3a; --navy-2:#12305a; --teal:#0f766e; --teal-2:#0d9488;
          --line:#e2e8f0; --ink:#0f172a; --muted:#475569; --bg:#f1f5f9; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--ink);
         font-family:"Vazirmatn","IRANSans","Segoe UI",Tahoma,sans-serif; line-height:1.8; }
  header { background:linear-gradient(135deg,var(--navy),var(--navy-2)); color:#fff;
           padding:12px 18px; display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
  header .brand { display:flex; align-items:center; gap:10px; margin-inline-end:auto; }
  header .brand svg { width:40px; height:40px; flex:none; }
  header .brand small { display:block; opacity:.75; font-size:.78em; line-height:1.4; }
  header strong { font-size:1.15em; line-height:1.4; }
  header nav a { color:#fff; text-decoration:none; padding:6px 12px; border-radius:8px;
                 border:1px solid rgba(255,255,255,.25); margin-inline-start:6px; }
  header nav a:hover { background:rgba(255,255,255,.12); }
  main { max-width:920px; margin:20px auto; padding:0 14px; }
  .card { background:#fff; border:1px solid var(--line); border-radius:14px;
          padding:18px; margin-bottom:14px; box-shadow:0 1px 2px rgba(15,23,42,.05); }
  h2, h3 { margin:0 0 8px; color:var(--navy); line-height:1.5; }
  h2 { font-size:1.25em; } h3 { font-size:1.05em; padding-inline-start:10px;
       border-inline-start:4px solid var(--teal-2); }
  a { color:var(--teal); }
  .muted { color:var(--muted); font-size:.9em; }
  .row { display:flex; gap:12px; flex-wrap:wrap; justify-content:space-between; }
  label { display:block; margin-top:12px; font-size:.95em; color:var(--muted); }
  input, textarea, select { width:100%; padding:11px; border:1px solid #cbd5e1;
          border-radius:10px; font:inherit; background:#fff; color:var(--ink); margin-top:4px; }
  input:focus, textarea:focus, select:focus { outline:2px solid var(--teal-2); border-color:transparent; }
  button { background:var(--teal); color:#fff; border:0; border-radius:10px;
           padding:11px 22px; font:inherit; font-weight:bold; cursor:pointer; margin-top:14px; }
  button:hover { background:var(--teal-2); }
  table { width:100%; border-collapse:collapse; }
  th { background:#f8fafc; color:var(--muted); font-weight:normal; font-size:.9em; }
  th, td { border-bottom:1px solid var(--line); padding:10px; text-align:right; }
  tr:hover td { background:#f8fafc; }
  .tag { display:inline-block; padding:2px 12px; border-radius:999px; font-size:.85em; font-weight:bold; white-space:nowrap; }
  .tag-sent { background:#dbeafe; color:#173172; }
  .tag-seen { background:#fef3c7; color:#6b3108; }
  .tag-answered { background:#dcfce7; color:#124a27; }
  .tag-overdue { background:#fee2e2; color:#6e1717; }
  .error { background:#fee2e2; color:#6e1717; padding:10px 12px; border-radius:10px;
           border-inline-start:4px solid #dc2626; }
  @media (max-width:600px){ main{margin:10px auto;} .card{padding:13px;}
    table { display:block; overflow-x:auto; } td { white-space:nowrap; } }
</style>
</head>
<body>
<header>
  <div class="brand">
    <?= portal_mark() ?>
    <div><strong>توان‌کاو</strong><small>پرتال واحدها</small></div>
  </div>
  <?php if ($signedIn): ?>
  <nav>
    <a href="index.php">صندوق ورودی</a>
    <a href="logout.php">خروج</a>
  </nav>
  <?php endif; ?>
</header>
<main>
<?php
}

/**
 * نشان توان‌کاو، همان نقشه‌ای که آیکون اپ است: قاب بازرسی سبزآبی، تراشه
 * ماینر، و صاعقه کهربایی در میانش.
 */
function portal_mark(): string
{
    return '<svg viewBox="22 22 64 64" aria-hidden="true">'
        . '<rect x="22" y="22" width="64" height="64" rx="14" fill="#12305a"/>'
        . '<path d="M27,38V32Q27,27 32,27H38M70,27H76Q81,27 81,32V38M81,70V76Q81,81 76,81H70M38,81H32Q27,81 27,76V70"'
        . ' fill="none" stroke="#2dd4bf" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>'
        . '<path d="M46,33.5V38M54,33.5V38M62,33.5V38M46,70V74.5M54,70V74.5M62,70V74.5M33.5,46H38M33.5,54H38M33.5,62H38M70,46H74.5M70,54H74.5M70,62H74.5"'
        . ' stroke="#e6edf7" stroke-width="2.6" stroke-linecap="round"/>'
        . '<path d="M43,38H65Q70,38 70,43V65Q70,70 65,70H43Q38,70 38,65V43Q38,38 43,38Z"'
        . ' fill="#0b1f3a" stroke="#e6edf7" stroke-width="3.2"/>'
        . '<path d="M57.5,41.5L46.5,56.2H53.2L50.8,66.5L61.8,51.3H55.1Z" fill="#fbbf24"'
        . ' stroke="#fbbf24" stroke-linejoin="round"/>'
        . '</svg>';
}

function portal_footer(): void
{
    ?></main>
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
