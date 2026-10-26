<?php
declare(strict_types=1);

/**
 * کلیدهای جور کردن یک سطر آمار با پرونده یا با سطر دیگر: شماره اشتراک، شناسه قبض،
 * شماره پرونده و کد سامانه پیامکی، هر کدام یکسان‌شده (رقم لاتین، بی فاصله و خط تیره).
 * ترتیب کلیدها ترتیب اعتماد است: اشتراک از همه مطمئن‌تر.
 */
final class CenterMatch
{
    public const FIELDS = ['subscription' => 'sub', 'bill_id' => 'bill', 'file_number' => 'file', 'sms_code' => 'sms'];

    /** @return list<string> */
    public static function keys(array $values): array
    {
        $keys = [];
        foreach (self::FIELDS as $field => $prefix) {
            $value = self::clean($values[$field] ?? null);
            if ($value !== null) {
                $keys[] = "$prefix:$value";
            }
        }
        return $keys;
    }

    public static function clean(mixed $value): ?string
    {
        if ($value === null) {
            return null;
        }
        if (is_float($value) && floor($value) === $value) {
            $value = (string) (int) $value;
        }
        $text = strtoupper((string) preg_replace('/[^0-9A-Za-z]/', '', Jalali::latin((string) $value)));
        $text = ltrim($text, '0');
        return strlen($text) < 3 ? null : $text;
    }
}
