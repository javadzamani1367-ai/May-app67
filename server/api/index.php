<?php
declare(strict_types=1);

/**
 * تنها ورودی سرویس. همه مسیرها از این فایل رد می‌شوند.
 *
 * بدون فریم‌ورک و بدون composer: روی هاست اشتراکی معمولاً نه دسترسی خط فرمان
 * هست و نه اجازه نصب بسته، و یک پوشه که کپی می‌شود و کار می‌کند ارزش بیشتری
 * از هر امکانات اضافه دارد.
 */

foreach (['Response', 'Config', 'Db', 'Request', 'Auth', 'Storage', 'Notifications'] as $class) {
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
    'GET /users/requests'        => [UserController::class, 'requests'],
    'POST /users/requests/decide' => [UserController::class, 'decideRequest'],

    'GET /sync/manifest'         => [SyncController::class, 'manifest'],
    'POST /sync/report'          => [SyncController::class, 'push'],
    'GET /sync/report'           => [SyncController::class, 'pull'],
    'POST /sync/file'            => [SyncController::class, 'upload'],
    'GET /sync/file'             => [SyncController::class, 'download'],

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
];

$key = $request->method . ' ' . $request->path;
if (!isset($routes[$key])) {
    Response::fail(404, 'not_found', 'این مسیر روی سرور وجود ندارد.');
}

[$class, $method] = $routes[$key];
(new $class())->$method($request);
