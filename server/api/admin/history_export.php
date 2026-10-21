<?php
declare(strict_types=1);
require __DIR__ . '/_boot.php';
require __DIR__ . '/_history_view.php';

// همان جستجوی سوابق بازرسی، به اکسل (خلاصه و همه خوانش‌ها) یا Word (نمودار و جدول هر پلاک).
// در پنل برای هر چهار نقش؛ از اپ فقط مدیر و مجری ستاد (HistoryController::export).
admin_require(CASE_ROLES);
$scope = admin_scope();
$filters = history_filters($_GET);
$keys = FieldHistorySearch::search($scope, $filters, 500);
$name = 'history-' . date('Ymd-His');
if (($_GET['format'] ?? '') === 'docx') {
    Docx::send($name, FieldHistoryExport::blocks($keys, FieldHistoryExport::filtersText($filters)));
} else {
    Xlsx::sendBook($name, FieldHistoryExport::book($keys, $scope));
}
