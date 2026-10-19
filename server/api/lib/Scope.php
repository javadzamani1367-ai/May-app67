<?php
declare(strict_types=1);

require_once __DIR__ . '/CaseKind.php';

/**
 * آنچه هر کاربر می‌بیند و می‌گرداند. یک قاعده برای API، پنل وب و پرتال، تا هیچ
 * صفحه‌ای محدوده را خودش حدس نزند.
 *
 * - مدیر: همه پرونده‌ها و موردها را می‌بیند؛ پرونده رمزارز را می‌گرداند، برق غیرمجاز
 *   را فقط نظارت می‌کند.
 * - مجری رمزارز: پرونده‌های رمزارز ناحیه‌های خودش؛ از موردهای میدانی ناحیه، همه جز
 *   گزارش رمزارز (فقط مدیر) و برق غیرمجاز (حوزه مجریان غیرمجاز).
 * - مجری غیرمجاز ستاد: همه پرونده‌ها و گزارش‌های برق غیرمجاز استان.
 * - مجری غیرمجاز شهرستان: همان، فقط ناحیه خودش.
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

    /** مجری غیرمجاز، ستاد یا شهرستان. */
    public function isIllegal(): bool
    {
        return in_array($this->role, [Auth::ROLE_ILLEGAL, Auth::ROLE_ILLEGAL_AREA], true);
    }

    public function isIllegalHq(): bool
    {
        return $this->role === Auth::ROLE_ILLEGAL;
    }

    /** گرداننده شهرستان در یکی از دو حوزه: مجری رمزارز یا مجری غیرمجاز شهرستان. */
    public function isAreaExecutor(): bool
    {
        return in_array($this->role, [Auth::ROLE_EXECUTOR, Auth::ROLE_ILLEGAL_AREA], true);
    }

    /** کسی که فهرست پرونده‌ها را دارد. */
    public function managesCases(): bool
    {
        return in_array($this->role, Auth::CASE_MANAGERS, true);
    }

    /** حوزه‌ای که این کاربر در آن کار می‌کند؛ تهی برای مدیر. */
    public function kind(): ?int
    {
        return CaseKind::ofRole($this->role);
    }

    public function wholeProvince(): bool
    {
        return $this->isManager() || $this->isIllegalHq();
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
        $kind = $this->kind();
        if ($kind === null) {
            return ['1=0', []];
        }
        if ($this->wholeProvince()) {
            return ["$alias.case_kind = ?", [$kind]];
        }
        if ($this->areas === []) {
            return ['1=0', []];
        }
        return ["$alias.case_kind = ? AND " . self::in("$alias.area_code", $this->areas), array_merge([$kind], $this->areas)];
    }

    public function canSeeReport(array $report): bool
    {
        if ($this->isManager()) {
            return true;
        }
        $kind = $this->kind();
        return $kind !== null && CaseKind::of($report) === $kind && $this->coversArea($report['area_code'] ?? null);
    }

    /**
     * این کاربر این پرونده را می‌گرداند (ارجاع، پیگیری، تأیید، ارسال)؟ مدیر در برق
     * غیرمجاز فقط ناظر است. قاعده جایگزینی ستاد به جای شهرستان در IllegalFlow است.
     */
    public function canActOn(array $report): bool
    {
        if ($this->isManager()) {
            return CaseKind::of($report) === CaseKind::CRYPTO;
        }
        return $this->canSeeReport($report);
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
            return ["$alias.kind NOT IN (?, ?) AND " . self::in("$alias.area_code", $this->areas),
                array_merge([Field::KIND_CRYPTO, Field::KIND_ILLEGAL], $this->areas)];
        }
        if ($this->isIllegalHq()) {
            return ["$alias.kind = ?", [Field::KIND_ILLEGAL]];
        }
        if ($this->isIllegal() && $this->areas !== []) {
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
            return !in_array($kind, [Field::KIND_CRYPTO, Field::KIND_ILLEGAL], true) && $this->coversArea($item['area_code'] ?? null);
        }
        if ($this->isIllegal()) {
            return $kind === Field::KIND_ILLEGAL && $this->coversArea($item['area_code'] ?? null);
        }
        return false;
    }

    /**
     * کارشناسانی که این کاربر می‌تواند به آن‌ها ارجاع دهد: مدیر و ستاد همه، گرداننده
     * شهرستان فقط ناحیه‌های خودش. ناحیه داده‌شده فهرست را باز هم تنگ‌تر می‌کند.
     */
    public function experts(?string $area = null): array
    {
        return self::usersIn(Auth::ROLE_EXPERT, $this->targetAreas($area));
    }

    /** گردانندگان شهرستان در حوزه این کاربر (مدیر: مجریان رمزارز). */
    public function executors(?string $area = null): array
    {
        return self::usersIn(CaseKind::executorRole($this->kind() ?? CaseKind::CRYPTO), $this->targetAreas($area));
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
     * کاربری‌های فعال یک نقش که دست‌کم یکی از این ناحیه‌ها را دارند (تهی: همه).
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

    /** گرداننده شهرستان یک ناحیه در یک حوزه؛ اگر چند نفرند، اولی به ترتیب نام. */
    public static function executorOf(?string $area, int $kind = CaseKind::CRYPTO): ?array
    {
        $area = Areas::code($area);
        return $area === null ? null : (self::usersIn(CaseKind::executorRole($kind), [$area])[0] ?? null);
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
