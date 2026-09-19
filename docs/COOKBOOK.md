# دستورنامه تغییرات

«می‌خواهم فلان چیز را عوض کنم، کدام فایل‌ها؟» — هر دستور مستقل است و می‌توانید
یک‌راست بروید سر همان.

قبل از هر commit این سه را بزنید:

```bash
python3 tools/verify-resources.py
php server/tests/run.php
cd android && ./gradlew test
```

---

## ۱. اضافه کردن یک فیلد جدید به پرونده

پرخطرترین تغییر پروژه، چون در چهار جا اثر دارد. مثال: می‌خواهیم «نام شرکت
پیمانکار» اضافه شود.

**قدم ۱ — اندروید، ستون پایگاه داده**
`android/app/src/main/java/ir/ilam/inspection/data/db/ReportEntity.kt`:

```kotlin
@ColumnInfo(name = "contractor_name") val contractorName: String? = null,
```

**قدم ۲ — اندروید، مهاجرت**
`android/app/src/main/java/ir/ilam/inspection/data/db/AppDatabase.kt`:

```kotlin
const val DATABASE_VERSION = 6                     // یکی جلو

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reports ADD COLUMN contractor_name TEXT")
    }
}
```
و در `build()`: `.addMigrations(…, MIGRATION_5_6)`

> **هرگز** `fallbackToDestructiveMigration()` ننویسید. تا قبل از همگام‌سازی،
> گوشی کارشناس تنها نسخه آن پرونده را دارد.

**قدم ۳ — سرور**
- `server/schema.sql`، داخل `CREATE TABLE … reports`:
  `contractor_name VARCHAR(255) NULL,`
- `server/api/lib/Controllers/SyncController.php`، در `REPORT_COLUMNS` نام ستون
  را اضافه کنید. **اگر این را فراموش کنید، مقدار بی‌صدا دور ریخته می‌شود.**

**قدم ۴ — ویندوز**
- `Data/Schema.cs` → هم DDL جدول، هم یک سطر در `AddedColumns` تا آرشیوهای
  موجود هم ستون را بگیرند
- `Data/Models.cs` → فیلد مدل
- `Sync/ReportMapper.cs` → خواندن از JSON
- `Data/ReportRepository.cs` → فهرست ستون‌ها و `$`پارامترهای INSERT
- `Data/ReportQueries.cs` → **ستون تازه را به آخر `ReportColumns` اضافه کنید،
  نه وسطش.** `ReadReport` مقادیر را با **شماره ستون** می‌خواند، پس یک ستون
  اضافه‌شده در وسط، همه فیلدهای بعدی را یکی جابه‌جا می‌کند — آدرس به جای بخش
  خوانده می‌شود و هیچ خطایی هم نمی‌دهد
- `windows/SCHEMA.md` → مستند

**قدم ۵ — اگر باید بین دستگاه‌ها منتقل شود**
`SCHEMA_VERSION` را در هر چهار جا یکی جلو ببرید (فهرستشان در
[`HANDOVER.md` بخش ۷](HANDOVER.md#۷-تغییر-اسکیما--چهار-جا)) و
`php server/tests/run.php` را بزنید.

**قدم ۶ — ساختار انتقال**
`sync/SyncPayload.kt` (نوشتن) و `sync/SyncPayloadReader.kt` (خواندن). هر دو، وگرنه
فیلد به سرور می‌رود و برنمی‌گردد — یا برعکس. تست
`SyncPayloadTest.kt` یک پرونده کامل را از هر دو نیمه رد می‌کند؛ فیلد تازه را در
نمونه همان تست هم بگذارید تا اگر یک نیمه جا ماند، تست بگوید.

**قدم ۷ — نمایش**
- برچسب در `android/app/src/main/res/values/strings.xml`
- فیلد در همان مرحله بازدید (مثلاً `ui/visit/LocationStep.kt`)
- ردیف در خروجی: `export/WordExporter.kt`، `export/HtmlReportBuilder.kt` و
  اگر لازم است `export/ExcelExporter.kt` + آرایه `excel_headers`

---

## ۲. اضافه کردن یک نوع گزارش

مثال: نوع هفتم، «گزارش بازرسی».

1. `data/model/Enums.kt` → عضو جدید **در انتها**، با `code = 7` و یک حرف یکتا
   برای کد رهگیری. کدهای موجود را هرگز جابه‌جا نکنید — در پایگاه داده نوشته
   شده‌اند.
   ```kotlin
   INSPECTION(7, "B");
   ```
2. `strings.xml` → `report_type_inspection`
3. `ui/common/Labels.kt` → یک سطر در `reportTypeLabel`
4. `windows/.../Util/Labels.cs` و `Resources/Strings.xaml` → همان نام
5. `server/api/portal/` اگر نوع گزارش در پرتال نمایش داده می‌شود

`when` روی enum در کاتلین کامل بودنش را خودش بررسی می‌کند، پس بیلد به شما
می‌گوید کدام جاها مانده. ویندوز و سرور این را نمی‌گویند؛ دستی چک کنید.

---

## ۳. اضافه کردن یک دسته پیوست

1. `data/model/Enums.kt` → `AttachmentCategory` عضو جدید در انتها
2. `strings.xml` → یک ردیف **در انتهای** آرایه `attachment_categories`، دقیقاً
   در همان جایگاه عدد `code`

برچسب از روی جایگاه خوانده می‌شود:
```kotlin
stringArrayResource(R.array.attachment_categories).getOrElse(category.code) { "" }
```
یعنی **ترتیب آرایه = ترتیب enum**. اگر وسط آرایه چیزی اضافه کنید، همه برچسب‌ها
یکی جابه‌جا می‌شوند. همین برای `dispatch_units` و `tariff_types` هم هست.

3. `windows/.../Resources/Strings.xaml` → همان نام در همان جایگاه

---

## ۴. اضافه کردن یک واحد مقصد ارسال

1. `data/model/Enums.kt` → `DispatchUnit` عضو جدید در انتها
2. `strings.xml` → ردیف تازه در انتهای آرایه `dispatch_units`
3. `data/model/UnitPerformance.kt` → چیزی لازم نیست؛ `table()` خودش واحدهای
   بدون ارسال را با صفر پر می‌کند
4. سرور: `server/api/portal/` — اگر واحد تازه باید حساب پرتال داشته باشد، در
   `users` یک کاربر با نقش آن واحد ساخته می‌شود؛ کد نقش‌ها در
   `server/api/lib/Auth.php`

---

## ۵. عوض کردن یا اضافه کردن شهرستان و کد ناحیه

این کدها **رسمی شرکت توزیع** هستند و از داخل برنامه قابل ویرایش نیستند، تا دو
کارشناس هرگز کد رهگیری متفاوت تولید نکنند.

`android/app/src/main/res/values/strings.xml`:

```xml
<string-array name="county_names">  <item>ایلام</item> … </string-array>
<string-array name="county_codes">  <item>401</item>  … </string-array>
```

**دو آرایه باید هم‌طول و هم‌ترتیب باشند** — کد هر شهرستان از همان جایگاه خوانده
می‌شود (`data/CountyCatalog.kt`). اگر طولشان یکی نباشد، شهرستان‌های آخر کد
`401` می‌گیرند و کسی متوجه نمی‌شود — برای همین
`python3 tools/verify-resources.py` هم‌طول بودن، تکراری نبودن کدها و سه رقمی
بودنشان را بررسی می‌کند.

شهرستانی که چند ناحیه دارد (مثل ایلام: ۴۰۱ و ۴۰۲) یک ردیف به ازای هر ناحیه
می‌گیرد، با نام یکسان. فهرست انتخاب آن‌ها را با نام ناحیه از هم جدا می‌کند، ولی
آنچه ذخیره می‌شود نام شهرستان است؛ جاهایی که فهرست شهرستان‌ها را نشان می‌دهند
(صفحه آمار، فیلتر اکسل) باید `.distinct()` بزنند.

هیچ صفحه تنظیماتی برای ویرایش این‌ها وجود ندارد و نباید ساخته شود.

---

## ۶. اضافه کردن یک صفحه جدید

مثال: صفحه «گزارش ماهانه».

1. پوشه `ui/monthly/` بسازید با دو فایل: `MonthlyScreen.kt` و
   `MonthlyViewModel.kt` — همین تقسیم در همه صفحات رعایت شده.
2. ViewModel یک `AppContainer` می‌گیرد و از ریپازیتوری‌ها می‌خواند:
   ```kotlin
   class MonthlyViewModel(container: AppContainer) : ViewModel() {
       private val reports = container.reportRepository
       val rows = …  // StateFlow
   }
   ```
3. صفحه، ViewModel را از ظرف می‌گیرد:
   ```kotlin
   val appContainer = LocalContext.current.container
   val viewModel: MonthlyViewModel = viewModel(
       factory = remember { ContainerViewModelFactory(appContainer) { MonthlyViewModel(it) } }
   )
   ```
4. مسیر را در `ui/AppNavigation.kt` اضافه کنید: یک `const val` در `Routes` و یک
   `composable(…)` در `NavHost`.
5. راه رسیدن به آن را بگذارید — دکمه در `ui/pending/HomeScreen.kt` یا
   `ui/settings/SettingsScreen.kt`.
6. اگر فقط مدیر باید ببیند:
   ```kotlin
   if (UserRole.isManager) { /* دکمه */ }
   ```

**قاعده ۳۰۰ خط:** اگر صفحه بزرگ شد، کارت‌هایش را به فایل جدا ببرید — همان کاری
که `ui/visit/` و `ui/archive/` کرده‌اند.

---

## ۷. اضافه کردن فیلدی با «ستاره» متن‌های ذخیره‌شده

به‌جای `OutlinedTextField` از `SnippetField` استفاده کنید:

```kotlin
SnippetField(
    label = stringResource(R.string.field_description),
    value = state.description,
    onValueChange = viewModel::setDescription,
    fieldKey = "description",       // ← کلید یکتای همین فیلد
    multiline = true
)
```

`fieldKey` مهم است: متن‌های ذخیره‌شده **به ازای هر فیلد** جدا نگه داشته می‌شوند،
تا نام یک افسر در فهرست شرح بازدید ظاهر نشود. کلید را عوض نکنید، وگرنه کاربر
متن‌های ذخیره‌شده‌اش را از دست می‌دهد.

این جدول همگام‌سازی نمی‌شود و فقط روی همان گوشی است.

---

## ۸. اضافه کردن جایی که چیزی حذف می‌شود

هر حذفی باید بپرسد. تنها دکمه حذف برنامه همین است:

```kotlin
ConfirmDeleteButton(
    itemName = device.model,        // ← در سؤال نام برده می‌شود
    onConfirm = { viewModel.deleteDevice(device.id) }
)
```

`itemName` را خالی نگذارید؛ سؤال باید درباره همان ردیفی باشد که انگشت رویش
نشسته، نه «این مورد».

حذف خود پرونده استثنا است و دیالوگ مخصوص خودش را در `ui/pending/HomeScreen.kt`
دارد، چون تصاویر و مدارک را هم با خودش می‌برد و برای پرونده بایگانی‌شده فقط
وقتی مجاز است که همگام‌سازی شده باشد.

---

## ۹. تغییر فرم رسمی گزارش (PDF و Word)

هفت بخش فرم در دو جا ساخته می‌شوند و **هر دو باید عوض شوند**، وگرنه PDF و Word
یک پرونده با هم اختلاف پیدا می‌کنند:

| قالب | فایل |
|---|---|
| PDF | `export/HtmlReportBuilder.kt` (HTML+CSS، بعد WebView → PDF) |
| Word | `export/WordExporter.kt` (+ `WordDocumentXml.kt` برای XML) |

چیزهایی که مشترک‌اند و فقط یک‌جا عوض می‌شوند:

- برچسب‌ها → `export/ReportLabels.kt`
- ردیف‌های بخش فنی → `export/TechnicalRows.kt`

سمت ویندوز، همان فرم در `windows/.../Export/PdfReportBuilder.cs` و
`WordReportBuilder.cs` و `ReportFields.cs` است.

**راست‌به‌چپ در PDF:** فونت وزیرمتن به‌صورت base64 داخل خود HTML جاسازی می‌شود.
اگر فونت را عوض کردید، در `util/AppFonts.kt` و `HtmlReportBuilder.kt` هر دو.

---

## ۱۰. اضافه کردن ستون به خروجی اکسل

1. `strings.xml` → آرایه `excel_headers`، سرستون تازه
2. `export/ExcelExporter.kt` → در متد `row()` یک مقدار **در همان جایگاه** اضافه
   کنید

تعداد سرستون و تعداد مقادیر ردیف باید یکی باشد. اکسل خطا نمی‌دهد؛ فقط ستون‌ها
جابه‌جا می‌شوند و کسی نمی‌فهمد.

همین برای گزارش عملکرد در `performance_headers` و `export/PerformanceExporter.kt`.

---

## ۱۱. اضافه کردن یک مسیر به سرور

**قدم ۱** — متد را در یک کنترلر بنویسید، `server/api/lib/Controllers/`:

```php
public function monthly(Request $request): void
{
    $user = Auth::require($request, Auth::ROLE_MANAGER);   // نگهبان نقش
    $rows = Db::all('SELECT … WHERE county = ?', [$request->str('county')]);
    Response::json(['rows' => $rows]);
}
```

**قدم ۲** — یک سطر در جدول مسیرهای `server/api/index.php`:

```php
'GET /stats/monthly' => [StatsController::class, 'monthly'],
```

همین. هیچ فایل تنظیمات دیگری نیست، کنترلرها با `glob` بارگذاری می‌شوند.

قواعدی که نباید شکسته شوند:

- هر پرس‌وجو **پارامتری**: `Db::all($sql, [$value])` — هرگز رشته به هم چسبیده
- متن خطای داخلی به کلاینت نرود؛ `Response::fail()` پیام فارسی می‌دهد و جزئیات
  در لاگ هاست می‌ماند
- نقش را بررسی کنید: `Auth::require($request, Auth::ROLE_MANAGER)`

**سمت اندروید:** متد را در `sync/ServerApi.kt` (پرونده و ورود) یا
`sync/ServerAdminApi.kt` (کاربران، تأیید، آمار) اضافه کنید:

```kotlin
suspend fun monthly(token: String, county: String): ApiResult<List<Row>> =
    get("stats/monthly?county=$county", token) { data -> … }
```

---

## ۱۲. تغییر شرط خروج پرونده از صف «در دست اقدام»

همه‌اش در `data/model/Completion.kt` است، ۳۵ خط:

```kotlin
fun missing(detail: ReportDetail): List<Int> {
    val problems = mutableListOf<Int>()
    if (report.latitude == null || report.longitude == null) problems += R.string.missing_gps
    if (detail.photos.isEmpty()) problems += R.string.missing_photo
    if (!TechnicalInput.from(report).power().hasReading) problems += R.string.missing_measurement
    if (report.description.isNullOrBlank()) problems += R.string.missing_description
    return problems
}
```

شرط تازه = یک `if` تازه + یک رشته `missing_…`. برنامه فهرست موارد ناقص را با نام
به کارشناس نشان می‌دهد، پس رشته باید دقیق بگوید چه چیزی کم است.

تستش در `android/app/src/test/.../CompletionTest.kt` است؛ شرط تازه را همان‌جا هم
اضافه کنید.

---

## ۱۳. تغییر فرمول توان

`util/PowerCalc.kt`. فرمول امروز: توان هر فاز = ولتاژ همان فاز × جریان همان فاز،
و مجموع = جمع سه فاز.

عمداً از √۳ استفاده **نشده**. آن فرمول بار متعادل و اندازه‌گیری خط به خط را فرض
می‌کند و بار ماینرها متعادل نیست. اگر خواستید عوضش کنید، دلیل فنی‌اش را بدانید و
`PowerCalcTest.kt` را هم به‌روز کنید؛ توضیح کامل در
[`DECISIONS.md`](DECISIONS.md).

---

## ۱۴. اضافه کردن زبان یا تغییر متن‌ها

همه متن‌ها در یک فایل‌اند: `android/app/src/main/res/values/strings.xml`
(۵۴۹ خط). هیچ رشته فارسی‌ای داخل کد نیست و نباید باشد.

نام تکراری، بیلد را می‌شکند — ولی پنج دقیقه بعد، وسط merger منابع. این را
اول بزنید:

```bash
python3 tools/verify-resources.py
```

که نام تکراری، ارجاع به رشته‌ای که وجود ندارد، و فایل بالای ۳۰۰ خط را در یک
ثانیه می‌گوید.

سمت ویندوز: `windows/CryptoInspection.Archive/Resources/Strings.xaml`.

---

## ۱۵. ساختن نسخه نصبی برای کاربران

```bash
git tag v1.0.1 && git push origin v1.0.1
```

CI هر دو نسخه (کارشناس و مدیر) را می‌سازد، امضا می‌کند و به‌صورت GitHub Release
منتشر می‌کند.

کلید امضا در GitHub Secrets است: `RELEASE_KEYSTORE_BASE64`،
`RELEASE_KEYSTORE_PASSWORD`، `RELEASE_KEY_ALIAS`، `RELEASE_KEY_PASSWORD`.
طریقه ساختنش در [`../README.md`](../README.md).

> فایل `release.keystore` را گم نکنید. بدون آن هیچ نسخه بعدی نمی‌تواند اپ
> نصب‌شده روی گوشی‌ها را به‌روزرسانی کند و همه کاربران باید حذف و نصب کنند.
