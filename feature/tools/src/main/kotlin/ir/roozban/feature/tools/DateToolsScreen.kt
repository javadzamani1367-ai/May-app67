package ir.roozban.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.CalendarDates
import ir.roozban.core.calendar.CalendarSystem
import ir.roozban.core.calendar.DateSpan
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.Span
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.domain.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DateToolsViewModel @Inject constructor(settings: SettingsRepository, private val clock: Clock) : ViewModel() {
    val hijriOffset: StateFlow<Int> = settings.settings.map { it.hijriOffset }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun today(): LocalDate = LocalDate.now(clock)
}

/** Date converter (Jalali ⇄ Gregorian ⇄ Hijri) and the time between two dates. */
@Composable
internal fun DateToolsScreen(startOnSpan: Boolean, onBack: () -> Unit, viewModel: DateToolsViewModel = hiltViewModel()) {
    val offset by viewModel.hijriOffset.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(if (startOnSpan) 1 else 0) }
    val today = viewModel.today()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("ابزار تاریخ") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("تبدیل تاریخ") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("فاصلهٔ دو تاریخ") })
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) Converter(today, offset) else SpanTool(today, offset)
            }
        }
    }
}

@Composable
private fun Converter(today: LocalDate, offset: Int) {
    var date by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    SectionTitle("تاریخ را در هر تقویمی وارد کن")
    DateInput(initial = today, offset = offset, onDate = { date = it?.toEpochDay() })
    val d = date?.let(LocalDate::ofEpochDay)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d == null) {
                Text("این تاریخ وجود ندارد.", color = MaterialTheme.colorScheme.error)
            } else {
                Text(PersianNames.weekday(d.dayOfWeek), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row { Label("شمسی"); Text(PersianDateFormatter.fullDate(d.toJalali()), fontWeight = FontWeight.SemiBold) }
                Row { Label("میلادی"); Text(PersianDateFormatter.gregorian(d), fontWeight = FontWeight.SemiBold) }
                Row { Label("قمری"); Text(PersianDateFormatter.hijri(d, offset) ?: "—", fontWeight = FontWeight.SemiBold) }
                val diff = d.toEpochDay() - today.toEpochDay()
                Text(
                    when {
                        diff == 0L -> "امروز"
                        diff > 0 -> "${PersianDigits.format(diff)} روز دیگر"
                        else -> "${PersianDigits.format(-diff)} روز پیش"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SpanTool(today: LocalDate, offset: Int) {
    var from by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    var to by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    var includeEnd by rememberSaveable { mutableStateOf(false) }
    SectionTitle("از تاریخ")
    DateInput(initial = today, offset = offset, onDate = { from = it?.toEpochDay() })
    SectionTitle("تا تاریخ")
    DateInput(initial = today, offset = offset, onDate = { to = it?.toEpochDay() })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("روز آخر هم شمرده شود", modifier = Modifier.weight(1f))
        Switch(checked = includeEnd, onCheckedChange = { includeEnd = it })
    }
    val a = from?.let(LocalDate::ofEpochDay)
    val b = to?.let(LocalDate::ofEpochDay)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a == null || b == null) {
                Text("یکی از تاریخ‌ها وجود ندارد.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val s = DateSpan.between(a, b, includeEnd, offset)
            if (s.totalDays < 0) Text("تاریخ دوم پیش از تاریخ اول است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${PersianDigits.format(kotlin.math.abs(s.totalDays))} روز",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (s.weeks > 0) Text("${PersianDigits.format(s.weeks)} هفته" + if (s.weekDays > 0) " و ${PersianDigits.format(s.weekDays)} روز" else "")
            Spacer(Modifier.height(4.dp))
            Row { Label("به شمسی"); Text(spanText(s.jalali), fontWeight = FontWeight.SemiBold) }
            Row { Label("به میلادی"); Text(spanText(s.gregorian), fontWeight = FontWeight.SemiBold) }
            s.hijri?.let { Row { Label("به قمری"); Text(spanText(it), fontWeight = FontWeight.SemiBold) } }
        }
    }
}

private fun spanText(s: Span): String {
    val parts = buildList {
        if (s.years > 0) add("${PersianDigits.format(s.years)} سال")
        if (s.months > 0) add("${PersianDigits.format(s.months)} ماه")
        if (s.days > 0 || isEmpty()) add("${PersianDigits.format(s.days)} روز")
    }
    return parts.joinToString(" و ")
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).then(Modifier))
}

/** Calendar choice plus day / month / year; reports the date, or null when it does not exist. */
@Composable
private fun DateInput(initial: LocalDate, offset: Int, onDate: (LocalDate?) -> Unit) {
    var calendar by rememberSaveable { mutableStateOf(CalendarSystem.JALALI) }
    val start = CalendarDates.parts(initial, calendar, offset) ?: Triple(1405, 1, 1)
    var year by rememberSaveable(calendar) { mutableStateOf(PersianDigits.format(start.first)) }
    var month by rememberSaveable(calendar) { mutableStateOf(PersianDigits.format(start.second)) }
    var day by rememberSaveable(calendar) { mutableStateOf(PersianDigits.format(start.third)) }
    fun num(s: String) = PersianDigits.toAscii(s).trim().toIntOrNull()
    fun report(y: String = year, m: String = month, d: String = day, c: CalendarSystem = calendar) {
        val yy = num(y)
        val mm = num(m)
        val dd = num(d)
        onDate(if (yy == null || mm == null || dd == null) null else CalendarDates.of(c, yy, mm, dd, offset))
    }
    val calendars = listOf(CalendarSystem.JALALI to "شمسی", CalendarSystem.GREGORIAN to "میلادی", CalendarSystem.HIJRI to "قمری")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        calendars.forEachIndexed { i, (cal, label) ->
            SegmentedButton(
                selected = calendar == cal,
                onClick = {
                    // Keep the same day, shown in the other calendar.
                    val current = num(year)?.let { y -> num(month)?.let { m -> num(day)?.let { d -> CalendarDates.of(calendar, y, m, d, offset) } } }
                    val p = CalendarDates.parts(current ?: initial, cal, offset) ?: start
                    calendar = cal
                    year = PersianDigits.format(p.first)
                    month = PersianDigits.format(p.second)
                    day = PersianDigits.format(p.third)
                    report(year, month, day, cal)
                },
                shape = SegmentedButtonDefaults.itemShape(i, calendars.size),
            ) { Text(label) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("روز", day, Modifier.weight(1f)) { day = it; report(d = it) }
        NumberField("ماه", month, Modifier.weight(1f)) { month = it; report(m = it) }
        NumberField("سال", year, Modifier.weight(1.4f)) { year = it; report(y = it) }
    }
    if (calendar == CalendarSystem.JALALI) {
        num(month)?.takeIf { it in 1..12 }?.let { Text(PersianNames.jalaliMonth(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline) }
    }
    TextButton(onClick = {
        val p = CalendarDates.parts(initial, calendar, offset) ?: return@TextButton
        year = PersianDigits.format(p.first)
        month = PersianDigits.format(p.second)
        day = PersianDigits.format(p.third)
        report()
    }) { Text("امروز") }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(PersianDigits.toPersian(PersianDigits.toAscii(it).filter(Char::isDigit).take(4))) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
