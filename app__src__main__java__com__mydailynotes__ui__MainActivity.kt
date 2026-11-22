@file:OptIn(ExperimentalMaterial3Api::class)
package com.mydailynotes.ui
import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mydailynotes.data.*
import com.mydailynotes.service.AlarmService
import kotlinx.coroutines.delay
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Cats = listOf("العمل", "الدراسة", "مواعيد شخصية", "الصحة", "أخرى")
val Pris = listOf("منخفضة", "متوسطة", "عالية")
val Sts = listOf("لم تبدأ", "قيد التنفيذ", "مكتملة", "انتهى وقتها ولم تُنجز")
val Sky = Color(0xFF1996D3)
val SkyTint = Color(0xFFE6F4FB)
val White = Color.White
private val scheme = lightColorScheme(
    primary = Sky, onPrimary = White, primaryContainer = SkyTint, onPrimaryContainer = Sky,
    secondary = Sky, onSecondary = White, secondaryContainer = SkyTint, onSecondaryContainer = Sky,
    background = White, onBackground = Sky, surface = White, onSurface = Sky,
    surfaceVariant = SkyTint, onSurfaceVariant = Sky, outline = Sky, outlineVariant = SkyTint, surfaceTint = White,
    surfaceContainer = White, surfaceContainerLow = White, surfaceContainerHigh = White, surfaceContainerHighest = SkyTint
)
fun eff(t: Task, now: Long) = if (t.status == 2) 2 else if (now > t.end) 3 else if (now >= t.start) 1 else 0

class MainActivity : ComponentActivity() {
    private var warn by mutableStateOf<String?>(null)
    private var snooze by mutableStateOf(false)
    private var crash by mutableStateOf<String?>(null)
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        File(filesDir, "crash.txt").let { if (it.exists()) crash = it.readText() }
        ack(intent)
        setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                    val w = warn
                    if (w != null && !snooze) AlertDialog(
                        onDismissRequest = { snooze = true },
                        title = { Text("مطلوب لعمل التنبيهات") },
                        text = { Text(if (w == "notif") "الإشعارات معطّلة للتطبيق، فلن تظهر التنبيهات. فعّلها من الإعدادات." else "لتظهر شاشة تنبيه البداية فوق القفل فعّل «الإشعارات بملء الشاشة» لهذا التطبيق.") },
                        confirmButton = { TextButton({
                            snooze = true
                            try {
                                startActivity(if (w == "notif") Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                else Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                            } catch (e: Exception) { Toast.makeText(this@MainActivity, "افتح إعدادات التطبيق يدوياً", Toast.LENGTH_LONG).show() }
                        }) { Text("فتح الإعدادات") } },
                        dismissButton = { TextButton({ snooze = true }) { Text("لاحقاً") } })
                    crash?.let { log ->
                        AlertDialog(onDismissRequest = {}, title = { Text("سجل أخطاء سابق") },
                            text = { Text(log.take(1800), Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp) },
                            confirmButton = { TextButton({
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", log))
                                Toast.makeText(this@MainActivity, "تم النسخ", Toast.LENGTH_SHORT).show()
                            }) { Text("نسخ") } },
                            dismissButton = { TextButton({ File(filesDir, "crash.txt").delete(); crash = null }) { Text("حذف السجل") } })
                    }
                }
            }
        }
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); ack(i) }
    private fun ack(i: Intent?) { if (i?.getBooleanExtra("ack", false) == true) stopService(Intent(this, AlarmService::class.java)) }
    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        warn = when {
            !nm.areNotificationsEnabled() -> "notif"
            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() -> "fsi"
            else -> null
        }
    }
}

@Composable fun App(vm: VM = viewModel()) {
    var tab by remember { mutableStateOf(0) }; var edit by remember { mutableStateOf<Task?>(null) }; var form by remember { mutableStateOf(false) }
    val icons = listOf(Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.History)
    Scaffold(containerColor = White,
        bottomBar = { NavigationBar(containerColor = White) { listOf("المهام", "الملاحظات", "السجل").forEachIndexed { i, s ->
            NavigationBarItem(tab == i, { tab = i }, { Icon(icons[i], null) }, label = { Text(s) }) } } },
        floatingActionButton = { if (tab == 0) FloatingActionButton({ edit = null; form = true }, containerColor = Sky, contentColor = White) { Icon(Icons.Default.Add, "إضافة مهمة") } }
    ) { p -> Box(Modifier.padding(p)) { when (tab) {
        0 -> TasksScreen(vm) { edit = it; form = true }
        1 -> NotesScreen(vm); else -> HistScreen(vm) } } }
    if (form) TaskForm(edit, { form = false }) { vm.save(it); form = false }
}

fun dayLabel(d: LocalDate, today: LocalDate) = when (d) {
    today -> "اليوم"; today.plusDays(1) -> "غداً"; today.minusDays(1) -> "أمس"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar")))
}

@Composable fun TasksScreen(vm: VM, onEdit: (Task?) -> Unit) {
    val all by vm.tasks.collectAsState()
    var showDone by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Task?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val today = LocalDate.now()
    val grouped = all.filter { (it.status == 2) == showDone }.sortedBy { it.start }.groupBy { it.start.ld() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Column(Modifier.fillMaxWidth()) {
            Text("My Daily Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar")))) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("النشطة: ${all.count { it.status != 2 }} • المنجزة: ${all.count { it.status == 2 }}", Modifier.weight(1f))
            FilterChip(showDone, { showDone = !showDone }, { Text("المكتملة") }) } }
        if (grouped.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventAvailable, null, Modifier.size(56.dp))
            Text(if (showDone) "لا توجد مهام مكتملة" else "لا توجد مهام")
            if (!showDone) Button({ onEdit(null) }, colors = ButtonDefaults.buttonColors(containerColor = Sky, contentColor = White)) { Text("إضافة مهمة") } } }
        grouped.forEach { (day, ts) ->
            item(key = "h$day") { Text(dayLabel(day, today), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
            items(ts, key = { it.id }) { t -> TaskCard(t, eff(t, now), { vm.toggleDone(t) }, { vm.postpone(t) }, { onEdit(t) }, { del = t }) }
        }
    }
    del?.let { t -> AlertDialog({ del = null }, title = { Text("حذف المهمة") }, text = { Text("هل تريد حذف «${t.title}»؟") },
        confirmButton = { TextButton({ vm.delete(t); del = null }) { Text("حذف") } }, dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }) }
}

@Composable fun TaskCard(t: Task, st: Int, onDone: () -> Unit, onPost: () -> Unit, onEdit: () -> Unit, onDel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val prog = st == 1
    Card(shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (prog) Sky else White, contentColor = if (prog) White else Sky),
        border = if (prog) null else BorderStroke(1.dp, Sky.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(if (prog) 12.dp else 1.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.status == 2, { onDone() }, colors = CheckboxDefaults.colors(
                checkedColor = if (prog) White else Sky, uncheckedColor = if (prog) White else Sky, checkmarkColor = if (prog) Sky else White))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (prog) { Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = White) { Text("قيد التنفيذ", color = Sky, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } }
                }
                if (t.description.isNotBlank()) Text(t.description)
                Text("${t.start.tm()} → ${t.end.tm()}  (${dur(t.start, t.end)})")
                Text("${Cats[t.category]} • ${Pris[t.priority]} • ${Sts[st]}")
            }
            Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("تعديل") }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text("تأجيل إلى الغد") }, { menu = false; onPost() })
                    DropdownMenuItem({ Text("حذف") }, { menu = false; onDel() }) } }
        }
    }
}

@Composable fun DT(label: String, v: LocalDateTime, on: (LocalDateTime) -> Unit) {
    val c = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.width(52.dp))
        OutlinedButton({ DatePickerDialog(c, { _, y, m, d -> on(v.withDayOfMonth(1).withYear(y).withMonth(m + 1).withDayOfMonth(d)) }, v.year, v.monthValue - 1, v.dayOfMonth).show() }) { Text("${v.toLocalDate()}") }
        OutlinedButton({ TimePickerDialog(c, { _, h, m -> on(v.withHour(h).withMinute(m)) }, v.hour, v.minute, false).show() }) { Text(v.ms().tm()) }
    }
}

@Composable fun TaskForm(t: Task?, onClose: () -> Unit, onSave: (Task) -> Unit) {
    val c = LocalContext.current; val base = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(5)
    var title by remember { mutableStateOf(t?.title ?: "") }; var desc by remember { mutableStateOf(t?.description ?: "") }
    var s by remember { mutableStateOf(t?.start?.ldt() ?: base) }; var e by remember { mutableStateOf(t?.end?.ldt() ?: base.plusHours(1)) }
    var cat by remember { mutableStateOf(t?.category ?: 0) }; var pri by remember { mutableStateOf(t?.priority ?: 1) }
    var sa by remember { mutableStateOf(t?.startAlarm ?: true) }; var ea by remember { mutableStateOf(t?.endAlarm ?: true) }
    AlertDialog(onClose, title = { Text(if (t == null) "مهمة جديدة" else "تعديل المهمة") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان المهمة *") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text("الوصف (اختياري)") })
            DT("البداية", s) { s = it; if (!e.isAfter(s)) e = s.plusHours(1) }; DT("النهاية", e) { e = it }
            if (e.isAfter(s)) Text("المدة: ${dur(s.ms(), e.ms())}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Cats.forEachIndexed { i, n -> FilterChip(cat == i, { cat = i }, { Text(n) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pris.forEachIndexed { i, n -> FilterChip(pri == i, { pri = i }, { Text(n) }) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تنبيه البداية", Modifier.weight(1f)); Switch(sa, { sa = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تنبيه النهاية", Modifier.weight(1f)); Switch(ea, { ea = it }) }
        } }, confirmButton = { TextButton({
            if (title.isBlank()) Toast.makeText(c, "العنوان مطلوب", Toast.LENGTH_SHORT).show()
            else if (!e.isAfter(s)) Toast.makeText(c, "وقت النهاية يجب أن يكون بعد البداية", Toast.LENGTH_SHORT).show()
            else onSave((t ?: Task(title = "", start = 0, end = 0)).copy(title = title.trim(), description = desc, start = s.ms(), end = e.ms(),
                category = cat, priority = pri, startAlarm = sa, endAlarm = ea, status = if (t?.status == 2) 2 else 0))
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClose) { Text("إلغاء") } })
}

@Composable fun NotesScreen(vm: VM) {
    val notes by vm.notes.collectAsState(); var q by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }; var edit by remember { mutableStateOf<Note?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("بحث في الملاحظات") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("ملاحظة جديدة") })
            IconButton({ if (text.isNotBlank()) { vm.saveNote(Note(text = text, date = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli())); text = "" } }) { Icon(Icons.Default.Save, "حفظ") } } }
        items(notes.filter { q.isBlank() || it.text.contains(q, true) }, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = White, contentColor = Sky), border = BorderStroke(1.dp, Sky.copy(alpha = 0.35f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(n.date.ld().toString(), fontWeight = FontWeight.Bold); Text(n.text) }
                    IconButton({ edit = n }) { Icon(Icons.Default.Edit, "تعديل") }; IconButton({ vm.deleteNote(n) }) { Icon(Icons.Default.Delete, "حذف") } } } }
    }
    edit?.let { n -> var t by remember(n) { mutableStateOf(n.text) }
        AlertDialog({ edit = null }, title = { Text("تعديل الملاحظة") }, text = { OutlinedTextField(t, { t = it }) },
            confirmButton = { TextButton({ vm.saveNote(n.copy(text = t)); edit = null }) { Text("حفظ") } }, dismissButton = { TextButton({ edit = null }) { Text("إلغاء") } }) }
}

@Composable fun HistScreen(vm: VM) {
    val h by vm.hist.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("سجل العمليات (آخر 100)", fontWeight = FontWeight.Bold) }
        items(h, key = { it.id }) { x -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = White, contentColor = Sky), border = BorderStroke(1.dp, Sky.copy(alpha = 0.35f))) {
            Column(Modifier.padding(12.dp)) { Text("${x.op}: ${x.title}", fontWeight = FontWeight.Bold)
                Text("${x.time.ldt().toLocalDate()} ${x.time.tm()}"); if (x.info.isNotBlank()) Text(x.info) } } }
    }
}
