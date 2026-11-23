@file:OptIn(ExperimentalMaterial3Api::class)
package com.mydailynotes.ui
import android.Manifest
import android.app.AlarmManager
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
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
val Sky = Color(0xFF1996D3); val Deep = Color(0xFF0B6A9E); val SkyTint = Color(0xFFE6F4FB)
val Yellow = Color(0xFFFFC83D); val YellowTint = Color(0xFFFFF3C9); val White = Color.White
private val scheme = lightColorScheme(
    primary = Sky, onPrimary = White, primaryContainer = SkyTint, onPrimaryContainer = Deep,
    secondary = Yellow, onSecondary = Deep, secondaryContainer = YellowTint, onSecondaryContainer = Deep,
    tertiary = Yellow, onTertiary = Deep, background = White, onBackground = Deep, surface = White, onSurface = Deep,
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
                        text = { Text(when (w) {
                            "exact" -> "لتعمل التنبيهات في وقتها بدقة فعّل «المنبهات والتذكيرات» للتطبيق."
                            "notif" -> "الإشعارات معطّلة للتطبيق، فلن تظهر التنبيهات. فعّلها من الإعدادات."
                            else -> "لتظهر شاشة تنبيه البداية فوق القفل فعّل «الإشعارات بملء الشاشة» لهذا التطبيق." }) },
                        confirmButton = { TextButton({
                            snooze = true
                            try {
                                startActivity(when (w) {
                                    "exact" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                                    "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    else -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")) })
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
            Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms() -> "exact"
            !nm.areNotificationsEnabled() -> "notif"
            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() -> "fsi"
            else -> null
        }
    }
}

@Composable fun Header(title: String, sub: String? = null, onBack: (() -> Unit)? = null) {
    Surface(color = Sky, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = White) } else Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) { Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Yellow) { Text(sub, color = Deep, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) } }
            }
        }
    }
}

@Composable fun App(vm: VM = viewModel()) {
    var tab by remember { mutableStateOf(0) }; var edit by remember { mutableStateOf<Task?>(null) }; var form by remember { mutableStateOf(false) }
    val icons = listOf(Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.History)
    Scaffold(containerColor = White,
        bottomBar = { NavigationBar(containerColor = White) { listOf("المهام", "الملاحظات", "السجل").forEachIndexed { i, s ->
            NavigationBarItem(tab == i, { tab = i }, { Icon(icons[i], null) }, label = { Text(s) }) } } },
        floatingActionButton = { if (tab == 0) FloatingActionButton({ edit = null; form = true }, containerColor = Yellow, contentColor = Deep) { Icon(Icons.Default.Add, "إضافة مهمة") } }
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
    val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = Yellow, selectedLabelColor = Deep)
    Column {
        Header("My Daily Notes", today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar"))))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showDone, { showDone = false }, { Text("غير المكتملة (${all.count { it.status != 2 }})") }, colors = chip)
            FilterChip(showDone, { showDone = true }, { Text("المكتملة (${all.count { it.status == 2 }})") }, colors = chip)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (grouped.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EventAvailable, null, Modifier.size(48.dp), tint = Yellow)
                Text(if (showDone) "لا توجد مهام مكتملة" else "لا توجد مهام غير مكتملة")
                if (!showDone) Button({ onEdit(null) }, colors = ButtonDefaults.buttonColors(containerColor = Sky, contentColor = White)) { Text("إضافة مهمة") } } }
            grouped.forEach { (day, ts) ->
                item(key = "h$day") { Text(dayLabel(day, today), fontWeight = FontWeight.Bold, color = Sky, modifier = Modifier.padding(top = 8.dp)) }
                items(ts, key = { it.id }) { t -> TaskCard(t, eff(t, now), { vm.toggleDone(t) }, { vm.postpone(t) }, { onEdit(t) }, { del = t }) }
            }
        }
    }
    del?.let { t -> AlertDialog({ del = null }, title = { Text("حذف المهمة") }, text = { Text("هل تريد حذف «${t.title}»؟") },
        confirmButton = { TextButton({ vm.delete(t); del = null }) { Text("حذف") } }, dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }) }
}

@Composable fun TaskCard(t: Task, st: Int, onDone: () -> Unit, onPost: () -> Unit, onEdit: () -> Unit, onDel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val prog = st == 1
    Card(Modifier.fillMaxWidth().alpha(if (st == 2) 0.65f else 1f), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (prog) Sky else if (st == 3) YellowTint else White, contentColor = if (prog) White else Deep),
        border = if (prog) BorderStroke(2.dp, Yellow) else if (st == 3) BorderStroke(1.dp, Yellow) else BorderStroke(1.dp, Sky.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(if (prog) 10.dp else 1.dp)) {
        Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.status == 2, { onDone() }, colors = CheckboxDefaults.colors(
                checkedColor = if (prog) Yellow else Sky, uncheckedColor = if (prog) White else Sky, checkmarkColor = if (prog) Deep else White))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (prog) { Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = Yellow) { Text("قيد التنفيذ", color = Deep, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)) } }
                }
                Text("${t.start.tm()} – ${t.end.tm()} • ${dur(t.start, t.end)}", fontSize = 12.sp)
                Text("${Cats[t.category]} • ${Pris[t.priority]} • ${Sts[st]}", fontSize = 11.sp, modifier = Modifier.alpha(0.85f))
                if (t.description.isNotBlank()) Text(t.description, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.85f))
            }
            Box { IconButton({ menu = true }, Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(20.dp)) }
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
    val chip = FilterChipDefaults.filterChipColors(selectedContainerColor = Yellow, selectedLabelColor = Deep)
    AlertDialog(onClose, title = { Text(if (t == null) "مهمة جديدة" else "تعديل المهمة") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان المهمة *") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text("الوصف (اختياري)") })
            DT("البداية", s) { s = it; if (!e.isAfter(s)) e = s.plusHours(1) }; DT("النهاية", e) { e = it }
            if (e.isAfter(s)) Text("المدة: ${dur(s.ms(), e.ms())}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Cats.forEachIndexed { i, n -> FilterChip(cat == i, { cat = i }, { Text(n) }, colors = chip) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pris.forEachIndexed { i, n -> FilterChip(pri == i, { pri = i }, { Text(n) }, colors = chip) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تنبيه البداية", Modifier.weight(1f)); Switch(sa, { sa = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تنبيه النهاية", Modifier.weight(1f)); Switch(ea, { ea = it }) }
        } }, confirmButton = { TextButton({
            if (title.isBlank()) Toast.makeText(c, "العنوان مطلوب", Toast.LENGTH_SHORT).show()
            else if (!e.isAfter(s)) Toast.makeText(c, "وقت النهاية يجب أن يكون بعد البداية", Toast.LENGTH_SHORT).show()
            else onSave((t ?: Task(title = "", start = 0, end = 0)).copy(title = title.trim(), description = desc, start = s.ms(), end = e.ms(),
                category = cat, priority = pri, startAlarm = sa, endAlarm = ea, status = if (t?.status == 2) 2 else 0))
        }) { Text("حفظ") } }, dismissButton = { TextButton(onClose) { Text("إلغاء") } })
}

@Composable fun TextDlg(title: String, init: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var t by remember { mutableStateOf(init) }
    AlertDialog(onClose, title = { Text(title) }, text = { OutlinedTextField(t, { t = it }) },
        confirmButton = { TextButton({ if (t.isNotBlank()) onSave(t.trim()) }) { Text("حفظ") } }, dismissButton = { TextButton(onClose) { Text("إلغاء") } })
}

@Composable fun NotesScreen(vm: VM) {
    val notes by vm.notes.collectAsState(); val subs by vm.subs.collectAsState()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var q by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf(false) }; var addSub by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf<Note?>(null) }; var editSub by remember { mutableStateOf<SubNote?>(null) }; var delNote by remember { mutableStateOf<Note?>(null) }
    val cur = notes.firstOrNull { it.id == openId }
    BackHandler(enabled = cur != null) { openId = null }
    val cardColors = CardDefaults.cardColors(containerColor = White, contentColor = Deep)
    val border = BorderStroke(1.dp, Sky.copy(alpha = 0.3f))
    Box(Modifier.fillMaxSize()) {
        if (cur == null) {
            val shown = notes.filter { n -> q.isBlank() || n.text.contains(q, true) || subs.any { it.noteId == n.id && it.text.contains(q, true) } }
            Column(Modifier.fillMaxSize()) {
                Header("الملاحظات", "${notes.size} ملاحظة")
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("بحث") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
                    if (shown.isEmpty()) item { Text(if (notes.isEmpty()) "لا توجد ملاحظات، اضغط + للإضافة" else "لا توجد نتائج", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
                    items(shown, key = { it.id }) { n ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = cardColors, border = border) {
                            Row(Modifier.clickable { openId = n.id }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(n.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${subs.count { it.noteId == n.id }} ملاحظة فرعية • ${n.date.ld()}", fontSize = 11.sp, modifier = Modifier.alpha(0.8f)) }
                                IconButton({ editNote = n }, Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "تعديل", Modifier.size(18.dp)) }
                                IconButton({ delNote = n }, Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "حذف", Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        } else {
            val mine = subs.filter { it.noteId == cur.id }
            Column(Modifier.fillMaxSize()) {
                Header(cur.text, "${mine.size} ملاحظة فرعية", onBack = { openId = null })
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (mine.isEmpty()) item { Text("لا توجد ملاحظات فرعية، اضغط + للإضافة", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
                    items(mine, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = cardColors, border = border) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.text, Modifier.weight(1f).padding(vertical = 6.dp), fontSize = 14.sp)
                                IconButton({ editSub = s }, Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "تعديل", Modifier.size(18.dp)) }
                                IconButton({ vm.deleteSub(s) }, Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "حذف", Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton({ if (cur == null) addNote = true else addSub = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Yellow, contentColor = Deep) { Icon(Icons.Default.Add, "إضافة") }
    }
    if (addNote) TextDlg("ملاحظة جديدة", "", { vm.saveNote(Note(text = it, date = System.currentTimeMillis())); addNote = false }, { addNote = false })
    if (addSub && cur != null) TextDlg("ملاحظة فرعية جديدة", "", { vm.saveSub(SubNote(noteId = cur.id, text = it)); addSub = false }, { addSub = false })
    editNote?.let { n -> TextDlg("تعديل الملاحظة", n.text, { vm.saveNote(n.copy(text = it)); editNote = null }, { editNote = null }) }
    editSub?.let { s -> TextDlg("تعديل الملاحظة الفرعية", s.text, { vm.saveSub(s.copy(text = it)); editSub = null }, { editSub = null }) }
    delNote?.let { n -> AlertDialog({ delNote = null }, title = { Text("حذف الملاحظة") }, text = { Text("سيتم حذف «${n.text}» مع كل ملاحظاتها الفرعية.") },
        confirmButton = { TextButton({ vm.deleteNote(n); delNote = null }) { Text("حذف") } }, dismissButton = { TextButton({ delNote = null }) { Text("إلغاء") } }) }
}

@Composable fun HistScreen(vm: VM) {
    val h by vm.hist.collectAsState()
    Column {
        Header("السجل", "آخر 100 عملية")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(h, key = { it.id }) { x -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = White, contentColor = Deep), border = BorderStroke(1.dp, Sky.copy(alpha = 0.3f))) {
                Column(Modifier.padding(10.dp)) { Text("${x.op}: ${x.title}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${x.time.ldt().toLocalDate()} ${x.time.tm()}", fontSize = 12.sp); if (x.info.isNotBlank()) Text(x.info, fontSize = 11.sp) } } }
        }
    }
}

class AlarmActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getLongExtra("id", -1); val title = intent.getStringExtra("title") ?: ""
        val s = intent.getLongExtra("s", 0); val e = intent.getLongExtra("e", 0)
        fun stopAll() {
            stopService(Intent(this, AlarmService::class.java))
            getSystemService(NotificationManager::class.java).cancel(id.toInt() * 2)
        }
        setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlarmScreen(title, s, e,
                        onAccept = { stopAll(); startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() },
                        onStop = { stopAll(); finish() })
                }
            }
        }
    }
}

@Composable fun AlarmScreen(title: String, s: Long, e: Long, onAccept: () -> Unit, onStop: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "pulse")
    val sc by tr.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Sky, Deep))).padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("My Daily Notes", color = Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(110.dp).scale(sc).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Alarm, null, Modifier.size(56.dp), tint = Deep) }
            Spacer(Modifier.height(40.dp))
            Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("حان وقت بدء المهمة", color = Yellow, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("${s.tm()} – ${e.tm()} • ${dur(s, e)}", color = White, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = onStop, modifier = Modifier.size(76.dp), shape = CircleShape, color = White) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "إيقاف", Modifier.size(36.dp), tint = Deep) } }
                Spacer(Modifier.height(8.dp)); Text("إيقاف", color = White, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = onAccept, modifier = Modifier.size(76.dp), shape = CircleShape, color = Yellow) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, "موافق", Modifier.size(40.dp), tint = Deep) } }
                Spacer(Modifier.height(8.dp)); Text("موافق", color = Yellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
