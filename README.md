# سامانه مدیریت بازدید مراکز رمزارز

اپ اندروید کارشناسان میدانی برای مدیریت چرخه بازدید از مراکز استخراج رمزارز،
از دریافت گزارش تا تولید فرم رسمی و ارسال مدارک به واحدهای سازمانی.
مستند فنی کامل: [`CLAUDE.md`](CLAUDE.md).

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
