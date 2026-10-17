<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_crew.php';

admin_require(CASE_ROLES);

// امتیاز ۱۰۰ ناحیه‌ها، مجریان، واحدهای مقصد و کارشناسان در یک بازه، با فرمول هر
// جدول زیر همان جدول. مجری فقط ناحیه‌های خودش و آدم‌های همان‌جا را می‌بیند.
$period = crew_period();
$board = (new ScoreBoard($period['from'], $period['to'], admin_scope(), Db::now()))->all();
if (($_GET['format'] ?? '') === 'xlsx') {
    Xlsx::sendBook('scores-' . str_replace('-', '', $period['from_day']) . '-' . str_replace('-', '', $period['to_day']),
        ScoreSheets::book($board));
    exit;
}
$spec = ScoreSheets::spec();

/** یک جدول رتبه‌بندی با نمره هر جزء. */
function score_table(array $rows, array $parts, bool $unit = false): string
{
    if ($rows === []) {
        return '<p class="muted">کسی در این دسته نیست.</p>';
    }
    $html = '<table><tr><th>رتبه</th><th>نام</th>' . ($unit ? '' : '<th>ناحیه</th>') . '<th>امتیاز</th>';
    foreach ($parts as $p) {
        $html .= '<th title="' . e($p['label']) . '">' . e($p['label']) . '<br><span class="muted">وزن ' . fa_digits((string) $p['weight']) . '</span></th>';
    }
    $html .= '</tr>';
    foreach ($rows as $row) {
        $score = $row['score'];
        $html .= '<tr><td>' . ($row['rank'] === null ? '—' : fa_digits((string) $row['rank'])) . '</td><td>' . e($row['name'])
            . (isset($row['unit']) && !$unit ? '' : '') . '</td>' . ($unit ? '' : '<td>' . e($row['area'] ?? '') . '</td>')
            . '<td><span class="score ' . Scores::grade($score) . '">' . ($score === null ? '—' : fa_digits((string) $score)) . '</span></td>';
        foreach ($parts as $p) {
            $v = $row['parts'][$p['key']] ?? null;
            $html .= '<td>' . ($v === null ? '<span class="muted">—</span>'
                : '<div class="bar"><span style="width:' . max(0, min(100, (int) round((float) $v))) . '%"></span></div>'
                . '<span class="muted">' . fa_digits((string) (int) round((float) $v)) . '</span>') . '</td>';
        }
        $html .= '</tr>';
    }
    return $html . '</table>';
}

admin_header('امتیازها');
?>
<div class="card">
  <div class="row">
    <h2>امتیاز عملکرد — از ۱۰۰</h2>
    <a href="scores.php?<?= e(crew_query($period)) ?>&amp;format=xlsx"><button type="button">اکسل</button></a>
  </div>
  <form method="get" class="row" style="justify-content:flex-start;gap:12px">
    <label style="margin:0">از<input name="from" value="<?= e($period['from_text']) ?>" style="width:120px"></label>
    <label style="margin:0">تا<input name="to" value="<?= e($period['to_text']) ?>" style="width:120px"></label>
    <button type="submit" style="margin:0">نمایش</button>
  </form>
  <p class="muted">هر امتیاز میانگین وزنی چند جزء است و هر جزء از ۰ تا ۱۰۰. جزئی که در این بازه داده ندارد کنار می‌رود و وزنش
    میان بقیه پخش می‌شود؛ «—» یعنی کاری نرسیده که بشود سنجید، نه کار بد. سبز ۸۰ به بالا، کهربایی ۶۰ تا ۷۹، قرمز کمتر.</p>
</div>
<?php foreach (['areas' => false, 'executors' => false, 'units' => false, 'experts' => false] as $key => $isUnit): ?>
  <div class="card">
    <h3><?= e($spec[$key]['title']) ?></h3>
    <?= score_table($key === 'units' ? array_map(static fn ($r) => ['name' => $r['name']] + $r, $board[$key]) : $board[$key], $spec[$key]['parts']) ?>
  </div>
<?php endforeach; ?>
<?php portal_footer();
