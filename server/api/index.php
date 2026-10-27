<?php
declare(strict_types=1);

/**
 * تنها ورودی سرویس. همه مسیرها از این فایل رد می‌شوند.
 *
 * بدون فریم‌ورک و بدون composer: روی هاست اشتراکی معمولاً نه دسترسی خط فرمان
 * هست و نه اجازه نصب بسته، و یک پوشه که کپی می‌شود و کار می‌کند ارزش بیشتری
 * از هر امکانات اضافه دارد.
 */

foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'LoginGuard', 'FieldSchema', 'Migrations', 'Storage',
          'Notifications', 'Jalali', 'Field', 'FieldCodes', 'FieldSettings', 'Users', 'FieldLabels',
          'FieldPresenter', 'FieldLine', 'FieldFeedback', 'FieldAdmin', 'FieldReferrals', 'FieldManage',
          'CaseLabels', 'CaseActions', 'PositionAudit', 'FieldRoster', 'FieldSubscribers', 'CrewCalendar', 'GeoPlaces', 'CrewWork', 'CrewDays', 'CrewReport', 'CrewMap', 'CrewSheets', 'CrewDocx', 'Docx', 'Xlsx',
          'Areas', 'Scope', 'UnitStages', 'UnitResponse', 'UserBatch', 'CaseCodes', 'CaseIntake', 'CaseFlow', 'CaseBoard',
          'FlowClock', 'FieldFlowClock', 'FieldHandOff', 'DeviceRegistry', 'DispatchMerge', 'Scores', 'ScoreBoard',
          'ScoreSheets', 'CaseKind', 'IllegalFlow', 'Persons', 'PersonAccounts', 'PersonDevices', 'PersonDirectory',
          'PersonWork', 'DeviceRequests', 'CrewAccounts', 'FieldReadings', 'FieldHistory', 'FieldHistorySearch',
          'FieldHistoryExport', 'HistoryReferral', 'CenterSchema', 'CenterColumns', 'CenterValues', 'CenterSystem', 'CenterMatch', 'CenterCheck', 'CenterLocks',
          'CenterSettings', 'RewardRules', 'RewardSpec', 'CenterRewards', 'CenterRows', 'CenterEdit', 'XlsxReader', 'CenterHeader',
          'CenterSupplement', 'CenterImport', 'CenterImportRun', 'CenterTemplate', 'CenterGrid', 'TemplateBook', 'OfficialXlsx', 'OfficialDocx',  'CenterSupplementSheet', 'CenterXlsx',
          'CenterDocx', 'CenterRewardReport', 'CenterCharts', 'RewardProposals', 'RewardPeople', 'RewardForms', 'NumberWords',
          'RewardFormModel'] as $class) {
    require_once __DIR__ . '/lib/' . $class . '.php';
}
foreach (glob(__DIR__ . '/lib/Controllers/*.php') ?: [] as $controller) {
    require_once $controller;
}

// خطاهای PHP هرگز به کلاینت نمی‌روند: یک پیام فارسی می‌رود و جزئیات در لاگ
// هاست می‌ماند، چون متن خطای پایگاه داده ساختار جدول‌ها را لو می‌دهد.
set_exception_handler(static function (Throwable $e): void {
    error_log('[inspection] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    Response::fail(500, 'server_error', 'خطای داخلی سرور. با پشتیبانی تماس بگیرید.');
});

$request = new Request();

// پایگاه داده نصب‌شده را، اگر از کد عقب است، پیش از هر کاری به‌روز می‌کند.
Migrations::ensure();
FlowClock::maybeTick();

// پیش‌پرواز CORS برای پرتال واحدها روی همان دامنه لازم نیست، ولی کلاینت
// اندروید ممکن است از دامنه دیگری صدا بزند.
if ($request->method === 'OPTIONS') {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Headers: Authorization, Content-Type');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    http_response_code(204);
    exit;
}
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type');

$routes = [
    'GET /ping'                  => [SystemController::class, 'ping'],
    'POST /auth/login'           => [AuthController::class, 'login'],
    'POST /auth/logout'          => [AuthController::class, 'logout'],
    'POST /auth/request-device'  => [AuthController::class, 'requestDevice'],
    'GET /auth/me'               => [AuthController::class, 'me'],

    'GET /users'                 => [UserController::class, 'index'],
    'POST /users/save'           => [UserController::class, 'save'],
    'POST /users/delete'         => [UserController::class, 'delete'],
    'GET /persons'               => [PersonController::class, 'index'],
    'GET /persons/one'           => [PersonController::class, 'one'],
    'POST /persons/save'         => [PersonController::class, 'save'],
    'POST /persons/accounts'     => [PersonController::class, 'accounts'],
    'POST /persons/password'     => [PersonController::class, 'password'],
    'POST /persons/device'       => [PersonController::class, 'device'],
    'POST /persons/attach'       => [PersonController::class, 'attach'],
    'GET /persons/requests'      => [PersonController::class, 'requests'],
    'POST /persons/requests/decide' => [PersonController::class, 'decide'],
    'GET /users/requests'        => [UserController::class, 'requests'],
    'POST /users/requests/decide' => [UserController::class, 'decideRequest'],

    'GET /sync/manifest'         => [SyncController::class, 'manifest'],
    'POST /sync/report'          => [SyncController::class, 'push'],
    'GET /sync/report'           => [SyncController::class, 'pull'],
    'POST /sync/file'            => [SyncFileController::class, 'upload'],
    'GET /sync/file'             => [SyncFileController::class, 'download'],

    'GET /field/me'              => [FieldController::class, 'me'],
    'POST /field/item'           => [FieldController::class, 'push'],
    'GET /field/items'           => [FieldController::class, 'items'],
    'POST /field/upload'         => [FieldUploadController::class, 'chunk'],

    'GET /referrals'             => [ReferralController::class, 'index'],
    'GET /referrals/item'        => [ReferralController::class, 'item'],
    'GET /referrals/file'        => [ReferralController::class, 'file'],
    'POST /referrals/accept'     => [ReferralController::class, 'accept'],
    'POST /referrals/result'     => [ReferralController::class, 'result'],

    'GET /manage/field/items'    => [FieldManageController::class, 'index'],
    'GET /manage/field/item'     => [FieldManageController::class, 'item'],
    'GET /manage/field/file'     => [FieldManageController::class, 'file'],
    'POST /manage/field/status'  => [FieldManageController::class, 'status'],
    'POST /manage/field/hand'    => [FieldManageController::class, 'hand'],
    'POST /manage/field/followup' => [FieldManageController::class, 'followUp'],
    'GET /field/history'          => [HistoryController::class, 'phone'],
    'GET /field/history/bundle'   => [HistoryController::class, 'bundle'],
    'GET /field/history/image'    => [HistoryController::class, 'phoneImage'],
    'GET /manage/history'         => [HistoryController::class, 'search'],
    'GET /manage/history/series'  => [HistoryController::class, 'series'],
    'GET /manage/history/item'    => [HistoryController::class, 'item'],
    'GET /manage/history/image'   => [HistoryController::class, 'image'],
    'GET /manage/history/export'  => [HistoryController::class, 'export'],
    'POST /manage/history/refer'  => [HistoryController::class, 'refer'],
    'GET /manage/devices'        => [RegistryController::class, 'search'],
    'GET /manage/devices/history' => [RegistryController::class, 'history'],
    'GET /manage/devices/alerts' => [RegistryController::class, 'alerts'],
    'GET /manage/scores'         => [StatsController::class, 'scores'],

    'POST /cases/intake'         => [CaseFlowController::class, 'intake'],
    'POST /cases/executor'       => [CaseFlowController::class, 'toExecutor'],
    'POST /cases/expert'         => [CaseFlowController::class, 'toExpert'],
    'POST /cases/followup'       => [CaseFlowController::class, 'followUp'],
    'GET /cases/flow'            => [CaseFlowController::class, 'flow'],
    'GET /cases/board'           => [CaseFlowController::class, 'board'],
    'GET /cases/people'          => [CaseFlowController::class, 'people'],
    'GET /cases/assigned'        => [CaseFlowController::class, 'assigned'],
    'GET /cases/mine'            => [CaseFlowController::class, 'mine'],
    'GET /manage/crew/report'    => [CrewController::class, 'report'],
    'POST /manage/crew/overtime' => [CrewController::class, 'overtime'],
    'GET /manage/crew/export'    => [CrewController::class, 'export'],

    'POST /approvals/submit'     => [ApprovalController::class, 'submit'],
    'POST /approvals/decide'     => [ApprovalController::class, 'decide'],
    'GET /approvals'             => [ApprovalController::class, 'index'],

    'POST /dispatches/create'    => [DispatchController::class, 'create'],
    'GET /dispatches'            => [DispatchController::class, 'index'],
    'GET /dispatches/inbox'      => [DispatchController::class, 'inbox'],
    'POST /dispatches/seen'      => [DispatchController::class, 'markSeen'],
    'POST /dispatches/respond'   => [DispatchController::class, 'respond'],

    'GET /notifications'         => [NotificationController::class, 'index'],
    'POST /notifications/read'   => [NotificationController::class, 'markRead'],

    'GET /stats/units'           => [StatsController::class, 'units'],

    'GET /centers'               => [CenterController::class, 'index'],
    'GET /centers/row'           => [CenterController::class, 'row'],
    'POST /centers/row'          => [CenterController::class, 'save'],
    'POST /centers/add'          => [CenterController::class, 'add'],
    'POST /centers/delete'       => [CenterController::class, 'delete'],
    'POST /centers/import'       => [CenterController::class, 'import'],
    'POST /centers/import/apply' => [CenterController::class, 'apply'],
    'GET /centers/export'        => [CenterController::class, 'export'],
    'GET /centers/rewards'       => [CenterController::class, 'rewards'],
    'GET /centers/rules'         => [CenterController::class, 'rules'],
    'POST /centers/rules'        => [CenterController::class, 'rules'],
    'GET /centers/form'          => [RewardFormController::class, 'form'],
    'POST /centers/form'         => [RewardFormController::class, 'form'],
    'GET /centers/form/export'   => [RewardFormController::class, 'formExport'],
    'GET /centers/settings'      => [CenterController::class, 'settings'],
    'POST /centers/settings'     => [CenterController::class, 'settings'],
];

$key = $request->method . ' ' . $request->path;
if (!isset($routes[$key])) {
    Response::fail(404, 'not_found', 'این مسیر روی سرور وجود ندارد.');
}

[$class, $method] = $routes[$key];
(new $class())->$method($request);
