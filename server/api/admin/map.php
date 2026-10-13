<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';

admin_require();

// نقشه موردها با همان فیلترهای فهرست. کتابخانه نقشه‌ای در کار نیست (هاست اشتراکی،
// بدون CDN): چند ده خط اسکریپت که کاشی‌های OpenStreetMap را می‌چیند. بدون اینترنت
// کاشی‌ها نمی‌آیند ولی نقطه‌ها روی زمینه خالی همچنان سر جایشان هستند.
$filter = FieldAdmin::filters($_GET);
$rows = Db::all(
    "SELECT i.id, i.tracking_code, i.kind, i.status, i.due_at, i.latitude, i.longitude FROM field_items i
     WHERE {$filter['where']} AND i.latitude IS NOT NULL AND i.longitude IS NOT NULL
     ORDER BY i.created_at DESC LIMIT 3000",
    $filter['params']
);
$now = Db::now();
$points = array_map(static fn (array $r): array => [
    'id' => $r['id'],
    'c' => (string) $r['tracking_code'],
    'k' => FieldAdmin::KIND_NAMES[(int) $r['kind']] ?? '',
    's' => FieldAdmin::isOverdue($r, $now) ? 'overdue' : (string) (int) $r['status'],
    'la' => round((float) $r['latitude'], 6),
    'lo' => round((float) $r['longitude'], 6),
], $rows);
$colors = [
    (string) Field::STATUS_REGISTERED => '#2563eb', (string) Field::STATUS_REVIEWING => '#d97706',
    (string) Field::STATUS_REFERRED => '#7c3aed', (string) Field::STATUS_RESULT => '#16a34a',
    (string) Field::STATUS_CLOSED => '#64748b', (string) Field::STATUS_REJECTED => '#94a3b8',
    (string) Field::STATUS_REVISIT => '#ea580c', 'overdue' => '#dc2626',
];
$json = static fn ($v): string => json_encode($v, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_UNESCAPED_UNICODE);

admin_header('نقشه موردها');
?>
<div class="card">
  <div class="row">
    <div>
      <h2>نقشه موردها</h2>
      <p class="muted"><?= fa_digits((string) count($points)) ?> مورد دارای موقعیت
        <?= $filter['values'] === [] ? '' : '— با فیلترهای فهرست' ?></p>
    </div>
    <div><a href="items.php<?= e(query_with($filter['values'])) ?>">فهرست و فیلترها</a></div>
  </div>
  <p class="muted">
    <?php foreach ($colors as $status => $color): ?>
      <span style="white-space:nowrap"><span style="display:inline-block;width:11px;height:11px;border-radius:50%;background:<?= $color ?>"></span>
        <?= e($status === 'overdue' ? 'مهلت گذشته' : FieldAdmin::STATUS_NAMES[(int) $status]) ?></span>&nbsp;
    <?php endforeach; ?>
  </p>
  <div id="map" style="position:relative;height:540px;overflow:hidden;border-radius:12px;background:#e2e8f0;
       touch-action:none;cursor:grab;direction:ltr">
    <div id="tiles" style="position:absolute;inset:0"></div>
    <div id="marks" style="position:absolute;inset:0"></div>
    <div style="position:absolute;top:10px;right:10px;display:flex;flex-direction:column;gap:6px;z-index:2">
      <button type="button" id="zin" style="margin:0;padding:4px 12px">+</button>
      <button type="button" id="zout" style="margin:0;padding:4px 12px">−</button>
    </div>
    <div style="position:absolute;bottom:0;left:0;background:rgba(255,255,255,.85);font-size:11px;padding:1px 6px;z-index:2">
      © OpenStreetMap contributors</div>
  </div>
</div>
<script>
(function () {
  var points = <?= $json($points) ?>, colors = <?= $json($colors) ?>;
  var map = document.getElementById('map'), tiles = document.getElementById('tiles'), marks = document.getElementById('marks');
  var T = 256, z = 9, cx = 0, cy = 0;
  function px(lon, lat, zoom) {
    var n = T * Math.pow(2, zoom), s = Math.sin(lat * Math.PI / 180);
    return [(lon + 180) / 360 * n, (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * n];
  }
  function fit() {
    if (!points.length) { var c = px(46.4227, 33.6374, 9); z = 9; cx = c[0]; cy = c[1]; return; }
    var w = map.clientWidth - 60, h = map.clientHeight - 60;
    for (z = 17; z > 5; z--) {
      var a = px(Math.min.apply(null, points.map(function (p) { return p.lo; })), Math.max.apply(null, points.map(function (p) { return p.la; })), z);
      var b = px(Math.max.apply(null, points.map(function (p) { return p.lo; })), Math.min.apply(null, points.map(function (p) { return p.la; })), z);
      if (b[0] - a[0] <= w && b[1] - a[1] <= h) { cx = (a[0] + b[0]) / 2; cy = (a[1] + b[1]) / 2; return; }
    }
    var m = px(points[0].lo, points[0].la, z); cx = m[0]; cy = m[1];
  }
  function draw() {
    var w = map.clientWidth, h = map.clientHeight, left = cx - w / 2, top = cy - h / 2, n = Math.pow(2, z), html = '';
    for (var x = Math.floor(left / T); x <= Math.floor((left + w) / T); x++) {
      for (var y = Math.floor(top / T); y <= Math.floor((top + h) / T); y++) {
        if (y < 0 || y >= n) continue;
        var tx = ((x % n) + n) % n;
        html += '<img alt="" draggable="false" onerror="this.style.visibility=\'hidden\'" src="https://tile.openstreetmap.org/' + z + '/' + tx + '/' + y + '.png" style="position:absolute;width:256px;height:256px;left:'
          + Math.round(x * T - left) + 'px;top:' + Math.round(y * T - top) + 'px">';
      }
    }
    tiles.innerHTML = html;
    marks.innerHTML = '';
    points.forEach(function (p) {
      var q = px(p.lo, p.la, z), a = document.createElement('a');
      a.href = 'item.php?id=' + encodeURIComponent(p.id);
      a.title = p.c + ' — ' + p.k;
      a.style.cssText = 'position:absolute;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;border:2px solid #fff;'
        + 'box-shadow:0 0 2px rgba(0,0,0,.6);background:' + (colors[p.s] || '#000') + ';left:' + Math.round(q[0] - left) + 'px;top:' + Math.round(q[1] - top) + 'px';
      marks.appendChild(a);
    });
  }
  function zoom(delta, ox, oy) {
    var nz = Math.max(5, Math.min(18, z + delta));
    if (nz === z) return;
    var f = Math.pow(2, nz - z), w = map.clientWidth, h = map.clientHeight;
    ox = ox === undefined ? w / 2 : ox; oy = oy === undefined ? h / 2 : oy;
    var wx = cx - w / 2 + ox, wy = cy - h / 2 + oy;
    cx = wx * f - ox + w / 2; cy = wy * f - oy + h / 2; z = nz; draw();
  }
  var drag = null;
  map.addEventListener('pointerdown', function (e) {
    if (e.target.tagName === 'A' || e.target.tagName === 'BUTTON') return;
    drag = [e.clientX, e.clientY, cx, cy]; map.setPointerCapture(e.pointerId); map.style.cursor = 'grabbing';
  });
  map.addEventListener('pointermove', function (e) {
    if (!drag) return;
    cx = drag[2] - (e.clientX - drag[0]); cy = drag[3] - (e.clientY - drag[1]); draw();
  });
  map.addEventListener('pointerup', function () { drag = null; map.style.cursor = 'grab'; });
  map.addEventListener('wheel', function (e) {
    e.preventDefault(); var r = map.getBoundingClientRect(); zoom(e.deltaY < 0 ? 1 : -1, e.clientX - r.left, e.clientY - r.top);
  }, { passive: false });
  document.getElementById('zin').onclick = function () { zoom(1); };
  document.getElementById('zout').onclick = function () { zoom(-1); };
  window.addEventListener('resize', draw);
  fit(); draw();
})();
</script>
<?php portal_footer();
