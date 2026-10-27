<?php
declare(strict_types=1);

/**
 * افرادی که کارشناس در اپ برای پاداش یک پرونده پیشنهاد کرده: پرسنل جمع‌آوری، عوامل انتظامی
 * و سپاه، و دارندگان پست‌های اداری. همراه پرونده بالا می‌آیند و هر بار جای نسخه قبل را
 * می‌گیرند (مالکشان کارشناس است)؛ اپ قدیمی که این فهرست را نمی‌فرستد آن را پاک نمی‌کند.
 * در فرم پاداش مرکز پیش‌فرض‌اند و مدیر تأییدشان می‌کند، کم یا زیاد می‌کند (RewardPeople).
 */
final class RewardProposals
{
    /** نوع‌های پیشنهادی از گوشی؛ عامل شناسایی از ستون آمار است. */
    public const KINDS = ['collection' => RewardRules::COLLECTION, 'enforcement' => RewardRules::ENFORCEMENT,
        'staff' => RewardRules::STAFF];
    public const ORGS = ['company', 'faraja', 'sepah', 'other'];
    private const MAX = 60;

    /** @param mixed $list فهرست گوشی، یا تهی وقتی اپ آن را نفرستاده */
    public static function replace(mixed $list, string $reportId, array $user): void
    {
        if (!is_array($list)) {
            return;
        }
        Db::run('DELETE FROM reward_proposals WHERE report_id = ?', [$reportId]);
        $now = Db::now();
        foreach (array_slice(array_values($list), 0, self::MAX) as $p) {
            if (!is_array($p)) {
                continue;
            }
            $kind = self::KINDS[(string) ($p['kind'] ?? '')] ?? null;
            $name = self::text($p['full_name'] ?? $p['name'] ?? null, 100);
            if ($kind === null || $name === null) {
                continue;
            }
            $org = (string) ($p['org'] ?? '');
            $id = Field::isUuid((string) ($p['id'] ?? '')) ? (string) $p['id'] : Db::uuid();
            Db::run(
                'INSERT INTO reward_proposals (id, report_id, kind, full_name, post, org, action, user_id, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE report_id = VALUES(report_id), kind = VALUES(kind),
                 full_name = VALUES(full_name), post = VALUES(post), org = VALUES(org), action = VALUES(action), updated_at = VALUES(updated_at)',
                [$id, $reportId, $kind, $name, self::text($p['post'] ?? null, 100), in_array($org, self::ORGS, true) ? $org : null,
                    self::text($p['action'] ?? null, 255), $user['id'] ?? null, $now]
            );
        }
    }

    /** @return array<string, list<array<string, mixed>>> پیشنهادهای هر پرونده */
    public static function forReports(array $ids): array
    {
        $ids = array_values(array_unique(array_filter($ids)));
        if ($ids === []) {
            return [];
        }
        $in = implode(',', array_fill(0, count($ids), '?'));
        $out = [];
        foreach (Db::all("SELECT * FROM reward_proposals WHERE report_id IN ($in) ORDER BY full_name", $ids) as $p) {
            $out[$p['report_id']][] = ['id' => $p['id'], 'kind' => (int) $p['kind'], 'name' => $p['full_name'],
                'post' => (string) ($p['post'] ?? ''), 'org' => (string) ($p['org'] ?? ''), 'action' => (string) ($p['action'] ?? '')];
        }
        return $out;
    }

    /** برای GET /sync/report: همان شکلی که گوشی می‌فرستد. @return list<array<string, string>> */
    public static function forSync(string $reportId): array
    {
        $kinds = array_flip(self::KINDS);
        return array_map(static fn ($p) => ['id' => $p['id'], 'kind' => $kinds[$p['kind']] ?? 'collection', 'full_name' => $p['name'],
            'post' => $p['post'], 'org' => $p['org'], 'action' => $p['action']], self::forReports([$reportId])[$reportId] ?? []);
    }

    private static function text(mixed $value, int $max): ?string
    {
        $text = CenterValues::cleanText((string) ($value ?? ''));
        return $text === '' ? null : mb_substr($text, 0, $max);
    }
}
