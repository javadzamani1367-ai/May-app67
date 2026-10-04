# از اینجا شروع کنید — راهنمای ادامه کار

این فایل اولین چیزی است که یک برنامه‌نویس یا هوش مصنوعی دیگر باید بخواند. می‌گوید
پروژه از چه بخش‌هایی ساخته شده، هر بخش کجاست، چطور ساخته و تست می‌شود، تا کجا پیش
رفته، و قدم بعدی چیست.

> **For an AI assistant or a non-Persian-speaking developer:** this is TavanKav, a system
> for inspecting illegal crypto-mining sites in Ilam province, Iran. Read `CLAUDE.md`
> (the binding spec and rules), then this file, then `docs/HANDOVER.md` (architecture)
> and `docs/FIELD-APP.md` (the third app, in progress). Code comments are in English;
> all user-facing text is Persian and lives in resource files, never in code.
> Parts: `server/` (PHP 8, no framework, MariaDB, shared cPanel hosting), `windows/`
> (WPF, .NET Framework 4.8, must run on Windows 7), `android/` (Kotlin + Compose):
> `core` (shared library), `app` (two flavors: expert, manager), `field` (field app).
> Android is built only in GitHub Actions (`.github/workflows/android.yml`); the
> server has unit tests (`php server/tests/run.php`) and MySQL integration tests
> (`php server/tests/integration.php`). Continue with stage 3 in `docs/FIELD-APP.md`.

---

## ۱. نقشه سامانه

| بخش | پوشه | فناوری | کاربر |
|---|---|---|---|
| سرور (API) | `server/api/` | PHP 8 بدون فریم‌ورک، MariaDB/MySQL، هاست اشتراکی سی‌پنل | همه اپ‌ها |
| پرتال واحدها (وب) | `server/api/portal/` | صفحات PHP راست‌به‌چپ | واحدهای فروش، حراست، حقوقی، برق شهرستان |
| پنل مدیر (وب) | `server/api/admin/` | صفحات PHP، نشست جدا، CSRF | مدیر |
| نرم‌افزار ویندوز | `windows/` | WPF، .NET Framework 4.8 (ویندوز ۷) | آرشیو مرکزی آفلاین |
| ماژول مشترک اندروید | `android/core/` | Kotlin، Compose، Material 3 | پایه هر سه اپ |
| اپ کارشناس و اپ مدیر | `android/app/` | یک کد، دو flavor: `expert` و `manager` | کارشناس و مدیر |
| اپ بازرسی میدانی | `android/field/` | Kotlin، Compose | کاربران عملیاتی و همکاران |

سرور فعلی: `https://helth.ir/api/` — پنل مدیر: `https://helth.ir/api/admin/`
مخزن: `javadzamani1367-ai/May-app67`، شاخه توسعه `claude/crypto-inspection-system-42s3dm`

## ۲. اسنادی که باید خواند، به این ترتیب

1. `CLAUDE.md` — مشخصات کامل و **قواعد الزام‌آور** (اسکیما، کد رهگیری، طراحی، امنیت، قواعد کدنویسی)
2. همین فایل
3. `docs/HANDOVER.md` — معماری اپ کارشناس/مدیر، سرور و ویندوز، و «چیزهایی که وقتتان را می‌گیرند»
4. `docs/FIELD-APP.md` — اپ بازرسی میدانی: تصمیم‌های تأییدشده، مراحل، و وضعیت هر مرحله
5. `docs/COOKBOOK.md` — دستورنامه تغییرات رایج (فیلد تازه، نوع گزارش تازه، مسیر سرور تازه…)
6. `docs/DECISIONS.md` — چرا هر تصمیم گرفته شد و چه اشکال‌هایی رفع شده
7. `docs/SYNC.md` — پروتکل همگام‌سازی گوشی و ویندوز
8. `server/README.md` — نصب و به‌روزرسانی روی هاست، فهرست مسیرهای API، نکات امنیتی
9. `windows/README.md` و `windows/SCHEMA.md` — ساخت نرم‌افزار ویندوز و اسکیمای مشترک

## ۳. ساختن و تست کردن

### سرور
```bash
php server/tests/run.php                       # تست‌های بدون پایگاه داده
DB_HOST=127.0.0.1 DB_USER=root DB_PASS= DB_REQUIRED=1 php server/tests/integration.php
                                               # سرور واقعی روی HTTP واقعی روی MariaDB واقعی
```
تست یکپارچه پایگاه داده `inspection_test` را خودش می‌سازد و پاک می‌کند (با `DB_SOCKET`
هم کار می‌کند). نصب روی هاست: `server/README.md` بخش ۲. `api/config.php` (رمز پایگاه داده)
در مخزن نیست و نباید باشد؛ نمونه‌اش `config.sample.php` است.

**به‌روزرسانی هاست:** فقط فایل‌های تغییرکرده `server/api/` را داخل پوشه `api` هاست
کپی کنید. تغییر اسکیما خودکار است (`lib/Migrations.php`)؛ `schema.sql` فقط برای نصب اول.

### اندروید
روی محیط توسعه فعلی Android SDK نیست؛ **ساخت در GitHub Actions انجام می‌شود**
(`.github/workflows/android.yml`): بررسی‌های ایستا، تست‌های سرور، تست‌های واحد هر سه ماژول،
APKهای debug و release، و در صورت اجرای دستی با `release_tag` انتشار نسخه. با Android Studio
هم کار می‌کند — پوشه `android/` را باز کنید:
```bash
cd android
./gradlew :core:testDebugUnitTest testExpertDebugUnitTest :field:testDebugUnitTest
./gradlew assembleExpertDebug assembleManagerDebug :field:assembleDebug
```
فونت وزیرمتن در مخزن نیست؛ CI دانلودش می‌کند (README اصلی، بخش «فونت وزیرمتن»).
پیش از هر commit: `python3 tools/verify-resources.py` (رشته تکراری یا ناموجود، فایل بالای ۳۰۰ خط، …).

**امضا:** کلید رسمی هنوز در Secrets مخزن نیست؛ هر نسخه با کلید یک‌بارمصرف امضا می‌شود
و روی نسخه قبلی نصب نمی‌شود. راهنمای ساخت کلید: README اصلی، بخش «کلید امضای رسمی».
**کلید و رمزهایش هرگز در مخزن یا گفتگو قرار نمی‌گیرند.**

### ویندوز
Visual Studio 2019 یا بالاتر، `windows/CryptoInspection.sln`، پیکربندی Release. جزئیات و
حجم در `windows/README.md`. **در CI ساخته نمی‌شود** — آخرین تغییر (نام و رنگ توان‌کاو در
`MainWindow.xaml` و `Resources/Theme.xaml`) هنوز کامپایل نشده؛ اولین ساخت را بررسی کنید.

## ۴. وضعیت هر بخش

| بخش | وضعیت |
|---|---|
| اپ کارشناس و مدیر | کامل، نسخه ۱.۱.۰ در دست کاربر؛ بازطراحی کامل توان‌کاو انجام شده |
| سرور و پرتال واحدها | کامل؛ ۹۶ تست واحد و ۱۵۶ تست یکپارچه سبز |
| پنل وب مدیر | بخش کاربران (همه نقش‌ها، مجوز کاربر میدانی) و تنظیمات میدانی آماده؛ فهرست و نقشه موردها، ارجاع و آمار مانده (مرحله ۷) |
| اپ بازرسی میدانی | مراحل ۰ تا ۶ تمام: ورود، صفحه اصلی بر اساس مجوز، صف ارسال، بارگذاری قابل ادامه، گزارش رمزارز و برق غیرمجاز، آمپرگیری فیدر، ترموویژن با مسیر GPS، نقشه و آدرس آفلاین ایلام |
| ویندوز | کامل برای آرشیو پرونده‌های بازدید؛ به سرور وصل نیست (عمداً — ابزار مدیر اپ میدانی پنل وب است) |

آخرین نسخه منتشرشده: `v1.2.4` در Releases مخزن (سه APK)، و نقشه `map-ilam-20261004`.

## ۵. قدم بعد

ادامه از **مرحله ۷ در `docs/FIELD-APP.md`**: بقیه پنل وب مدیر:
- فهرست و نقشه موردهای میدانی، با فیلتر
- ادغام موارد تکراری
- ارجاع به اکیپ با QR
- زنجیره هر مورد
- آمار هر کاربر و هشدار موارد معوق
- خروجی PDF، Word و Excel

بعد مرحله ۸: «ارجاع‌های من» در اپ کارشناس.

نکته‌هایی که پیش از این پیدا شده:
- محیط توسعه فعلی Android SDK ندارد. کد Kotlin خالص (بدون اندروید) را می‌شود با کامپایلر
  Kotlin داخل Gradle (`/opt/gradle-*/lib/kotlin-compiler-embeddable-*.jar`) و JUnit همان‌جا
  کامپایل و تست کرد؛ تست‌های ترموویژن و آدرس‌یابی پیش از هر push این‌طور اجرا شدند
- اگر CI در تست واحد رد شود، خطای کامپایل یا تست شکست‌خورده به صورت یک annotation در صفحه
  خلاصه اجرا می‌آید (`android.yml`، گام Unit tests)
- Geofabrik و Overpass از محیط توسعه مسدودند؛ نقشه فقط در `map.yml` ساخته می‌شود

## ۶. قواعدی که نباید شکسته شوند (خلاصه؛ متن کامل در CLAUDE.md)

- هیچ رشته فارسی داخل کد نیست؛ اندروید در `strings*.xml`، ویندوز در `Resources/Strings.xaml`
- هر فایل حداکثر ۳۰۰ خط
- تاریخ در پایگاه داده یونیکس میلی‌ثانیه؛ شمسی فقط در نمایش
- مسیر فایل همیشه نسبی
- اسکیمای پرونده در اندروید، ویندوز و سرور یکسان است؛ هر تغییر در هر سه و در `windows/SCHEMA.md`
- صفحه‌ها رنگ خام نمی‌گیرند؛ `Tone` می‌خواهند. اجزای مشترک در `android/core/.../ui/common`
- آنچه بیش از یک اپ لازم دارد به `android/core` **منتقل** می‌شود، کپی نمی‌شود
- نام سازنده (کاراکو) فقط در «درباره برنامه»؛ هرگز در گزارش، خروجی یا پرتال
- هر تغییر رفتاری با تست؛ تست‌های امنیتی سرور یک بار با خاموش کردن محافظ شکست خورده‌اند تا ثابت شود چیزی می‌سنجند
- `server/api/config.php`، کلید امضا و هر رمزی هرگز در مخزن نیست

## ۷. بسته‌های تحویلی

اگر کد به صورت زیپ تحویل شده، این بسته‌ها هستند:

| زیپ | محتوا | کی لازم است |
|---|---|---|
| `tavankav-full-source.zip` | کل مخزن، با CI و ابزارها | **پیشنهادی** برای ادامه کار |
| `tavankav-server-web.zip` | سرور، پرتال واحدها، پنل مدیر، تست‌ها | فقط کار روی سرور یا وب |
| `tavankav-windows.zip` | نرم‌افزار ویندوز | فقط کار روی آرشیو ویندوز |
| `tavankav-android-expert-manager.zip` | `core` + `app` (کارشناس و مدیر) | فقط اپ کارشناس یا مدیر |
| `tavankav-android-field.zip` | `core` + `field` | فقط اپ بازرسی میدانی |

هر زیپ `CLAUDE.md` و پوشه `docs/` را هم دارد. در دو زیپ اندرویدی جدا، `settings.gradle.kts`
فقط ماژول‌های همان بسته را دارد و workflow کامل CI و `tools/verify-resources.py` (که هر سه
ماژول را می‌خواهد) در آن‌ها نیست؛ برای کار روی هر دو اپ یا با CI، زیپ کامل را بگیرید.
`core` بین دو بسته اندرویدی مشترک است: تغییری که در یکی به آن داده شود باید به دیگری هم برسد.
