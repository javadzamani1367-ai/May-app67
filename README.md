# سامانه مدیریت بازدید مراکز رمزارز

اپ اندروید کارشناسان میدانی برای مدیریت چرخه بازدید از مراکز استخراج رمزارز،
از دریافت گزارش تا تولید فرم رسمی و ارسال مدارک به واحدهای سازمانی.
مستند فنی کامل: [`CLAUDE.md`](CLAUDE.md).

## دریافت فایل نصبی

هر تغییری روی شاخه توسعه، در GitHub Actions ساخته می‌شود و فایل APK به همان
اجرا پیوست می‌شود: بخش **Actions** مخزن ← آخرین اجرای «Android APK» ←
`crypto-inspection-debug-apk`.

برای دریافت مستقیم روی گوشی، یک تگ بزنید تا APK به صورت Release منتشر شود:

```bash
git tag v1.0.0-test1 && git push origin v1.0.0-test1
```

فایل با کلید debug امضا می‌شود؛ برای نصب باید «نصب از منابع ناشناس» فعال باشد.
اگر نسخه تازه‌تری نصب می‌کنید و اندروید خطای امضا داد، اول نسخه قبلی را حذف کنید.

### حجم

| نسخه | حجم | توضیح |
|---|---|---|
| debug | حدود ۲۹ مگابایت | بدون R8، همراه ابزارهای اشکال‌زدایی Compose — همین است که نصب می‌کنید |
| release (امضاشده) | حدود ۱۲ مگابایت | با R8 و حذف منابع بلااستفاده — هدف مستند زیر ۲۰ مگابایت ✅ |

اندازه‌ای که GitHub برای artifact نشان می‌دهد کوچک‌تر است (حدود ۷ مگابایت برای
release)، چون artifact یک فایل zip است؛ عدد بالا اندازه خود فایل APK است.

نسخه release در هر بیلد ساخته، امضا و پیوست می‌شود:

- اگر کلید امضای واقعی تنظیم شده باشد → `crypto-inspection-release-signed-apk`
- در غیر این صورت → `crypto-inspection-release-testkey-apk`، امضاشده با یک کلید
  یک‌بارمصرف که هر بیلد عوض می‌شود. قابل نصب و تست است، اما برای توزیع رسمی نیست
  و با آن نمی‌توان نسخه قبلی را به‌روزرسانی کرد.

## کلید امضای رسمی

کلید هرگز داخل مخزن نگهداری نمی‌شود. یک بار روی سیستم خودتان بسازید:

```bash
# لینوکس یا مک
tools/make-release-keystore.sh
```
```powershell
# ویندوز
powershell -ExecutionPolicy Bypass -File tools\make-release-keystore.ps1
```

سپس در **Settings ← Secrets and variables ← Actions** این چهار مورد را اضافه کنید:

| نام Secret | مقدار |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | محتوای فایل `release.keystore.base64.txt` |
| `RELEASE_KEYSTORE_PASSWORD` | رمزی که انتخاب کردید |
| `RELEASE_KEY_ALIAS` | `inspection` |
| `RELEASE_KEY_PASSWORD` | همان رمز |

از بیلد بعد، خروجی با کلید شما امضا می‌شود و تگ زدن (`v*`) آن را به صورت
Release منتشر می‌کند. صحت امضا در هر بیلد با `apksigner verify` بررسی می‌شود.

> **فایل `release.keystore` را جای امن نگه دارید و پشتیبان بگیرید.** اگر گم شود،
> هیچ نسخه بعدی نمی‌تواند اپ نصب‌شده روی گوشی‌ها را به‌روزرسانی کند و کاربران
> باید اپ را حذف و دوباره نصب کنند.

## ساخت

```bash
cd android
./gradlew assembleDebug        # یا: gradle assembleDebug
```

نیازمندی‌ها: JDK 17، Android SDK با API 34، AGP 8.5.

### فونت وزیرمتن

فایل‌های فونت به دلیل مجوز و حجم داخل مخزن نیستند. قبل از ساخت نسخه نهایی
این دو فایل را از [github.com/rastikerdar/vazirmatn](https://github.com/rastikerdar/vazirmatn)
(مجوز SIL OFL) بگیرید و اینجا بگذارید:

```
android/app/src/main/assets/fonts/Vazirmatn-Regular.ttf
android/app/src/main/assets/fonts/Vazirmatn-Bold.ttf
```

بدون این فایل‌ها پروژه ساخته و اجرا می‌شود اما رابط کاربری و PDF از فونت
پیش‌فرض دستگاه استفاده می‌کنند.

## آنچه ساخته شده

| فاز | وضعیت |
|---|---|
| پایه: تم راست‌به‌چپ، Room + SQLCipher، تاریخ شمسی، تنظیمات | ✅ |
| چرخه اصلی: ثبت سریع، کد رهگیری، صف در دست اقدام، بازدید پنج‌مرحله‌ای، GPS، دوربین، بارکد | ✅ |
| خروجی: PDF، Word، اکسل، اشتراک‌گذاری | ✅ |
| مدارک: پیوست پس از بازدید، ارسال گزینشی به واحدها، جستجو و آمار | ✅ |
| همگام‌سازی: وب‌سرور روی گوشی، بسته `.cvz` | ✅ |
| نرم‌افزار ویندوز (WPF): دریافت‌کننده، آرشیو مرکزی، گزارش‌گیری تجمیعی | ✅ |

## نقشه کد

```
android/app/src/main/java/ir/ilam/inspection/
├── data/db      موجودیت‌ها، DAOها، پایگاه داده رمزنگاری‌شده
├── data/model   enumها، مدل دامنه، قواعد تکمیل پرونده
├── data/repo    ریپازیتوری پرونده، محتوا و تنظیمات
├── ui/…         صفحات Compose (pending, intake, visit, archive, dispatch, settings, stats, lock)
├── export       HTML → PDF، سازنده OOXML برای Word و Excel، اشتراک‌گذاری
├── sync         NanoHTTPD، ساختار JSON، بسته‌ساز آفلاین
└── util         تاریخ شمسی، ارقام، کد رهگیری، مهر تصویر، رمزنگاری بسته
```
`android/app/src/main/java/android/print/PdfPrint.kt` عمداً در پکیج `android.print`
است؛ سازنده کلاس‌های callback در آن پکیج package-private است و تنها راه تولید PDF
بدون باز شدن پنجره چاپ سیستم، همین است.

## تست

```bash
cd android && ./gradlew test
```
تست‌های واحد روی منطق خالص: تبدیل تاریخ شمسی (رفت و برگشت کامل ۱۳۵۰ تا ۱۴۵۰)،
ساخت کد رهگیری و کد موقت، اعتبارسنجی کد ملی و شکل‌دهی ارقام.

## نرم‌افزار ویندوز

```cmd
cd windows
nuget restore CryptoInspection.sln
msbuild CryptoInspection.sln /p:Configuration=Release
```
جزئیات نیازمندی‌ها، بسته‌ها و نکته حجم نصاب: [`windows/README.md`](windows/README.md).

## اسناد

- [`CLAUDE.md`](CLAUDE.md) — مستند فنی و مرجع توسعه
- [`windows/README.md`](windows/README.md) — ساخت و اجرای نرم‌افزار ویندوز
- [`windows/SCHEMA.md`](windows/SCHEMA.md) — آینه اسکیما، مشترک بین دو نرم‌افزار
- [`docs/SYNC.md`](docs/SYNC.md) — پروتکل همگام‌سازی برای پیاده‌ساز سمت ویندوز
