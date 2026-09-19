<?php
declare(strict_types=1);

/** بررسی سلامت سرویس — اولین چیزی که بعد از نصب باید جواب بدهد. */
final class SystemController
{
    /**
     * باید با `SCHEMA_VERSION` در `AppDatabase.kt` و `Schema.Version` ویندوز
     * یکی باشد. تست `server/tests/run.php` این سه را با هم مقایسه می‌کند،
     * چون یک عدد جامانده یعنی سرور ادعا می‌کند اسکیمایی دارد که ندارد.
     */
    public const SCHEMA_VERSION = 4;

    public function ping(Request $request): void
    {
        $tables = Db::all('SHOW TABLES');
        Response::json([
            'service' => 'crypto-inspection',
            'schema_version' => self::SCHEMA_VERSION,
            'php' => PHP_VERSION,
            'tables' => count($tables),
            'storage_writable' => is_writable(Storage::root()),
            'time' => Db::now(),
        ]);
    }
}
