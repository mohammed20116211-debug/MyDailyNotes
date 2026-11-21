@file:OptIn(ExperimentalMaterial3Api::class)
package com.mydailynotes.ui
import android.Manifest
import android.app.*
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mydailynotes.data.*
import com.mydailynotes.notifications.Notifier
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Cats = listOf("العمل", "الدراسة", "مواعيد شخصية", "الصحة", "أخرى")
val Pris = listOf("منخفضة", "متوسطة", "عالية")
val Sts = listOf("لم تبدأ", "قيد التنفيذ", "مكتملة", "انتهى وقتها ولم تُنجز")

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); Notifier.channels(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF4FC3F7))
            else lightColorScheme(primary = Color(0xFF29A8E0), primaryContainer = Color(0xFFD6F0FC), background = Color.White, surface = Color.White)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }
}

@Composable fun App(vm: VM = viewModel()) {
    var tab by remember { mutableStateOf(0) }; var edit by remember { mutableStateOf<Task?>(null) }
    var form by remember { mutableStateOf(false) }; var sel by remember { mutableStateOf(LocalDate.now()) }
    val icons = listOf(Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.History)
    Scaffold(
        bottomBar = { NavigationBar { listOf("المهام", "الملاحظات", "السجل").forEachIndexed { i, s ->
            NavigationBarItem(tab == i, { tab = i }, { Icon(icons[i], null) }, label = { Text(s) }) } } },
        floatingActionButton = { if (tab == 0) FloatingActionButton({ edit = null; form = true }) { Icon(Icons.Default.Add, "إضافة مهمة") } }
    ) { p -> Box(Modifier.padding(p)) { when (tab) {
        0 -> TasksScreen(vm, sel, { sel = it }) { edit = it; form = true }
        1 -> NotesScreen(vm, sel); else -> HistScreen(vm) } } }
    if (form) TaskForm(edit, sel, { form = false }) { vm.save(it); form = false }
}

@Composable fun TasksScreen(vm: VM, sel: LocalDate, onSel: (LocalDate) -> Unit, onEdit: (Task?) -> Unit) {
    val all by vm.tasks.collectAsState(); var ym by remember { mutableStateOf(YearMonth.from(sel)) }
    var showDone by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Task?>(null) }
    val days = all.map { it.start.ld() }.toSet(); val day = all.filter { it.start.ld() == sel }
    val list = day.filter { (it.status == 2) == showDone }; val today = LocalDate.now()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("My Daily Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar")))) }
            TextButton({ ym = YearMonth.now(); onSel(today) }) { Text("اليوم") } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ ym = ym.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "السابق") }
            Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar"))), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton({ ym = ym.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "التالي") } } }
        item { Row { listOf("ح", "ن", "ث", "ر", "خ", "ج", "س").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray) } } }
        val off = ym.atDay(1).dayOfWeek.value % 7
        items((off + ym.lengthOfMonth() + 6) / 7) { r -> Row { for (c in 0..6) {
            val n = r * 7 + c - off + 1
            if (n < 1 || n > ym.lengthOfMonth()) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                val dt = ym.atDay(n); val s = dt == sel
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                    .background(if (s) MaterialTheme.colorScheme.primary else if (dt == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSel(dt) }, contentAlignment = Alignment.Center) {
                    Text("$n", color = if (s) Color.White else MaterialTheme.colorScheme.onSurface)
                    if (dt in days) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape).background(if (s) Color.White else MaterialTheme.colorScheme.primary)) } } } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المجدولة: ${day.size} • المنجزة: ${day.count { it.status == 2 }}", Modifier.weight(1f))
            FilterChip(showDone, { showDone = !showDone }, { Text("المكتملة") }) } }
        if (list.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventAvailable, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (showDone) "لا توجد مهام مكتملة" else "لا توجد مهام في هذا اليوم")
            if (!showDone) Button({ onEdit(null) }) { Text("إضافة مهمة") } } }
        items(list, key = { it.id }) { t -> TaskCard(t, { vm.toggleDone(t) }, { vm.postpone(t) }, { onEdit(t) }, { del = t }) }
    }
    del?.let { t -> AlertDialog({ del = null }, title = { Text("حذف المهمة") }, text = { Text("هل تريد حذف «${t.title}»؟") },
        confirmButton = { TextButton({ vm.delete(t); del = null }) { Text("حذف") } }, dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }) }
}

@Composable fun TaskCard(t: Task, onDone: () -> Unit, onPost: () -> Unit, onEdit: () -> Unit, onDel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.status == 2, { onDone() })
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Bold)
                if (t.description.isNotBlank()) Text(t.description, color = Color.Gray)
                Text("${t.start.tm()} → ${t.end.tm()}  (${dur(t.start, t.end)})")
                Text("${Cats[t.category]} • ${Pris[t.priority]} • ${Sts[t.status]}", color = MaterialTheme.colorScheme.primary)
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
        OutlinedButton({ DatePickerDialog(c, { _, y, m, d -> on(v.withYear(y).withMonth(m + 1).withDayOfMonth(d)) }, v.year, v.monthValue - 1, v.dayOfMonth).show() }) { Text("${v.toLocalDate()}") }
        OutlinedButton({ TimePickerDialog(c, { _, h, m -> on(v.withHour(h).withMinute(m)) }, v.hour, v.minute, false).show() }) { Text(v.ms().tm()) }
    }
}

@Composable fun TaskForm(t: Task?, day: LocalDate, onClose: () -> Unit, onSave: (Task) -> Unit) {
    val c = LocalContext.current; val base = LocalDateTime.of(day, LocalTime.of(9, 0))
    var title by remember { mutableStateOf(t?.title ?: "") }; var desc by remember { mutableStateOf(t?.description ?: "") }
    var s by remember { mutableStateOf(t?.start?.ldt() ?: base) }; var e by remember { mutableStateOf(t?.end?.ldt() ?: base.plusHours(1)) }
    var cat by remember { mutableStateOf(t?.category ?: 0) }; var pri by remember { mutableStateOf(t?.priority ?: 1) }
    var sa by remember { mutableStateOf(t?.startAlarm ?: true) }; var ea by remember { mutableStateOf(t?.endAlarm ?: true) }
    AlertDialog(onClose, title = { Text(if (t == null) "مهمة جديدة" else "تعديل المهمة") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان المهمة *") }, singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text("الوصف (اختياري)") })
            DT("البداية", s) { s = it; if (!e.isAfter(s)) e = s.plusHours(1) }; DT("النهاية", e) { e = it }
            if (e.isAfter(s)) Text("المدة: ${dur(s.ms(), e.ms())}", color = MaterialTheme.colorScheme.primary)
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

@Composable fun NotesScreen(vm: VM, day: LocalDate) {
    val notes by vm.notes.collectAsState(); var q by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }; var edit by remember { mutableStateOf<Note?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("بحث في الملاحظات") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), label = { Text("ملاحظة ليوم $day") })
            IconButton({ if (text.isNotBlank()) { vm.saveNote(Note(text = text, date = day.atStartOfDay(zone).toInstant().toEpochMilli())); text = "" } }) { Icon(Icons.Default.Save, "حفظ") } } }
        items(notes.filter { q.isBlank() || it.text.contains(q, true) }, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(n.date.ld().toString(), color = MaterialTheme.colorScheme.primary); Text(n.text) }
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
        items(h, key = { it.id }) { x -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("${x.op}: ${x.title}", fontWeight = FontWeight.Bold)
            Text("${x.time.ldt().toLocalDate()} ${x.time.tm()}", color = Color.Gray); if (x.info.isNotBlank()) Text(x.info) } } }
    }
}
