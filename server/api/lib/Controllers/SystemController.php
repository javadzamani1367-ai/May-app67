<?php
declare(strict_types=1);

/** بررسی سلامت سرویس — اولین چیزی که بعد از نصب باید جواب بدهد. */
final class SystemController
{
    public const SCHEMA_VERSION = 2;

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
