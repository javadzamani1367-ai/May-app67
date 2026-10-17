<?php
declare(strict_types=1);

/**
 * آنچه هر کاربر می‌بیند. یک قاعده برای API، پنل وب و پرتال، تا هیچ صفحه‌ای
 * محدوده را خودش حدس نزند.
 *
 * - مدیر: همه چیز.
 * - مجری رمزارز: پرونده‌های بازدید و موردهای میدانی ناحیه‌های خودش؛ گزارش رمزارز
 *   اپ میدانی را نمی‌بیند (آن فقط مال مدیر است).
 * - مجری برق غیرمجاز: فقط موردهای «برق غیرمجاز»، در ناحیه‌هایش یا اگر ناحیه‌ای
 *   ندارد در همه استان؛ پرونده بازدید رمزارز را نمی‌بیند.
 * - بقیه: هیچ‌کدام از فهرست‌های مدیریتی.
 *
 * محدوده همیشه روی سرور و در هر درخواست سنجیده می‌شود؛ پنهان کردن یک دکمه
 * در اپ چیزی را محافظت نمی‌کند.
 */
final class Scope
{
    public readonly int $role;
    /** @var list<string> */
    public readonly array $areas;

    public function __construct(public readonly array $user)
    {
        $this->role = (int) $user['role'];
        $this->areas = Areas::parse($user['areas'] ?? '');
    }

    public function isManager(): bool
    {
        return $this->role === Auth::ROLE_MANAGER;
    }

    public function isExecutor(): bool
    {
        return $this->role === Auth::ROLE_EXECUTOR;
    }

    public function isIllegal(): bool
    {
        return $this->role === Auth::ROLE_ILLEGAL;
    }

    /** مدیر یا مجری رمزارز: کسی که پرونده بازدید را می‌گرداند. */
    public function managesCases(): bool
    {
        return $this->isManager() || $this->isExecutor();
    }

    /** مدیر، یا مجری غیرمجازی که به ناحیه‌ای بسته نشده. */
    public function wholeProvince(): bool
    {
        return $this->isManager() || ($this->isIllegal() && $this->areas === []);
    }

    public function coversArea(?string $area): bool
    {
        if ($this->wholeProvince()) {
            return true;
        }
        $area = Areas::code($area);
        return $area !== null && in_array($area, $this->areas, true);
    }

    /**
     * شرط SQL پرونده‌های بازدید برای این کاربر.
     *
     * @return array{0:string,1:list<mixed>}
     */
    public function reportWhere(string $alias = 'r'): array
    {
        if ($this->isManager()) {
            return ['1=1', []];
        }
        if (!$this->isExecutor() || $this->areas === []) {
            return ['1=0', []];
        }
        return [self::in("$alias.area_code", $this->areas), $this->areas];
    }

    public function canSeeReport(array $report): bool
    {
        return $this->isManager() || ($this->isExecutor() && $this->coversArea($report['area_code'] ?? null));
    }

    /**
     * شرط SQL موردهای میدانی برای این کاربر.
     *
     * @return array{0:string,1:list<mixed>}
     */
    public function fieldWhere(string $alias = 'i'): array
    {
        if ($this->isManager()) {
            return ['1=1', []];
        }
        if ($this->isExecutor()) {
            if ($this->areas === []) {
                return ['1=0', []];
            }
            return ["$alias.kind <> ? AND " . self::in("$alias.area_code", $this->areas),
                array_merge([Field::KIND_CRYPTO], $this->areas)];
        }
        if ($this->isIllegal()) {
            if ($this->areas === []) {
                return ["$alias.kind = ?", [Field::KIND_ILLEGAL]];
            }
            return ["$alias.kind = ? AND " . self::in("$alias.area_code", $this->areas),
                array_merge([Field::KIND_ILLEGAL], $this->areas)];
        }
        return ['1=0', []];
    }

    public function canSeeField(array $item): bool
    {
        $kind = (int) $item['kind'];
        if ($this->isManager()) {
            return true;
        }
        if ($this->isExecutor()) {
            return $kind !== Field::KIND_CRYPTO && $this->coversArea($item['area_code'] ?? null);
        }
        if ($this->isIllegal()) {
            return $kind === Field::KIND_ILLEGAL && $this->coversArea($item['area_code'] ?? null);
        }
        return false;
    }

    /**
     * کارشناسانی که این کاربر می‌تواند به آن‌ها ارجاع دهد: مدیر و مجری غیرمجاز استانی
     * همه، مجری فقط ناحیه‌های خودش. ناحیه داده‌شده فهرست را باز هم تنگ‌تر می‌کند.
     */
    public function experts(?string $area = null): array
    {
        return self::usersIn(Auth::ROLE_EXPERT, $this->targetAreas($area));
    }

    /** مجریان رمزارزی که می‌شود مورد را به آن‌ها سپرد. */
    public function executors(?string $area = null): array
    {
        return self::usersIn(Auth::ROLE_EXECUTOR, $this->targetAreas($area));
    }

    /** @return list<string>|null تهی یعنی همه استان */
    private function targetAreas(?string $area): ?array
    {
        $area = Areas::code($area);
        if ($area !== null) {
            return $this->coversArea($area) ? [$area] : ['---'];
        }
        return $this->wholeProvince() ? null : $this->areas;
    }

    /**
     * کاربران فعال یک نقش که دست‌کم یکی از این ناحیه‌ها را دارند (تهی: همه).
     *
     * @param list<string>|null $areas
     */
    public static function usersIn(int $role, ?array $areas): array
    {
        $rows = Db::all('SELECT id, user_code, full_name, areas FROM users WHERE role = ? AND active = 1 ORDER BY full_name',
            [$role]);
        $out = [];
        foreach ($rows as $row) {
            $own = Areas::parse($row['areas'] ?? '');
            if ($areas === null || array_intersect($own, $areas) !== []) {
                $out[] = ['id' => $row['id'], 'user_code' => $row['user_code'], 'name' => $row['full_name'],
                    'areas' => $own];
            }
        }
        return $out;
    }

    /** مجری رمزارز یک ناحیه؛ اگر چند نفرند، اولی به ترتیب نام. */
    public static function executorOf(?string $area): ?array
    {
        $area = Areas::code($area);
        return $area === null ? null : (self::usersIn(Auth::ROLE_EXECUTOR, [$area])[0] ?? null);
    }

    /** کاربر واحد این ارسال را می‌بیند: همان واحد، و ناحیه‌اش یا واحد استانی. */
    public static function unitSees(array $user, array $dispatch): bool
    {
        if ((int) $user['role'] !== Auth::ROLE_UNIT || (int) $user['unit'] !== (int) $dispatch['unit']) {
            return false;
        }
        $areas = Areas::parse($user['areas'] ?? '');
        return $areas === [] || in_array((string) ($dispatch['area_code'] ?? ''), $areas, true);
    }

    /**
     * شرط SQL صندوق یک کاربر واحد. @return array{0:string,1:list<mixed>}
     */
    public static function unitWhere(array $user, string $alias = 'd'): array
    {
        $areas = Areas::parse($user['areas'] ?? '');
        if ($areas === []) {
            return ["$alias.unit = ?", [(int) $user['unit']]];
        }
        return ["$alias.unit = ? AND " . self::in("$alias.area_code", $areas), array_merge([(int) $user['unit']], $areas)];
    }

    /** @param list<string> $values */
    private static function in(string $column, array $values): string
    {
        return "$column IN (" . implode(', ', array_fill(0, count($values), '?')) . ')';
    }
}
