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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction

fun dayLabel(d: LocalDate, today: LocalDate) = when (d) {
    today -> "اليوم"; today.plusDays(1) -> "غداً"; today.minusDays(1) -> "أمس"
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar")))
}


@Composable fun Header(title: String, sub: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = Sky, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = White) } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) { Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Yellow) { Text(sub, color = Deep, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) } }
            }
            actions()
        }
    }
}

@Composable fun App(vm: VM = viewModel()) {
    var tab by remember { mutableStateOf(0) }; var edit by remember { mutableStateOf<Task?>(null) }; var form by remember { mutableStateOf(false) }
    val icons = listOf(Icons.Default.DateRange, Icons.Default.Edit, Icons.Default.History, Icons.Default.Backup)
    Scaffold(containerColor = White,
        bottomBar = { NavigationBar(containerColor = White) { listOf("المهام", "الملاحظات", "السجل", "النسخ").forEachIndexed { i, s ->
            NavigationBarItem(tab == i, { tab = i }, { Icon(icons[i], null) }, label = { Text(s) }) } } },
        floatingActionButton = { if (tab == 0) FloatingActionButton({ edit = null; form = true }, containerColor = Yellow, contentColor = Deep) { Icon(Icons.Default.Add, "إضافة مهمة") } }
    ) { p -> Box(Modifier.padding(p)) { when (tab) {
        0 -> TasksScreen(vm) { edit = it; form = true }
        1 -> NotesScreen(vm); 2 -> HistScreen(vm); else -> BackupScreen(vm) } } }
    if (form) TaskForm(edit, { form = false }) { vm.save(it); form = false }
}

@Composable fun TasksScreen(vm: VM, onEdit: (Task?) -> Unit) {
    val all by vm.tasks.collectAsState()
    var showDone by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Task?>(null) }; var pp by remember { mutableStateOf<Task?>(null) }
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
                items(ts, key = { it.id }) { t -> TaskCard(t, eff(t, now), { vm.toggleDone(t) }, { vm.postpone(t) }, { pp = t }, { onEdit(t) }, { del = t }) }
            }
        }
    }
    del?.let { t -> AlertDialog({ del = null }, title = { Text("حذف المهمة") }, text = { Text("هل تريد حذف «${t.title}»؟") },
        confirmButton = { TextButton({ vm.delete(t); del = null }) { Text("حذف") } }, dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }) }
    pp?.let { t -> PostponeDlg(t, { vm.postponeTo(t, it); pp = null }, { pp = null }) }
}

@Composable fun PostponeDlg(t: Task, onPick: (Long) -> Unit, onClose: () -> Unit) {
    var custom by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0).plusHours(1)) }
    val opts = listOf("15 دقيقة" to 15, "30 دقيقة" to 30, "ساعة" to 60, "ساعتان" to 120, "3 ساعات" to 180)
    AlertDialog(onClose, title = { Text("تأجيل المهمة") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تأجيل «${t.title}» بعد:", maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                opts.forEach { (l, m) -> AssistChip({ onPick(System.currentTimeMillis() + m * 60_000L) }, { Text(l) }) } }
            Text("أو اختر موعداً محدداً:")
            DT("الموعد", custom) { custom = it }
        } }, confirmButton = { TextButton({ onPick(custom.ms()) }) { Text("تأجيل للموعد المحدد") } }, dismissButton = { TextButton(onClose) { Text("إلغاء") } })
}

@Composable fun TaskCard(t: Task, st: Int, onDone: () -> Unit, onPost: () -> Unit, onPostLater: () -> Unit, onEdit: () -> Unit, onDel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }; var open by remember { mutableStateOf(false) }
    val prog = st == 1; val lines = if (open) Int.MAX_VALUE else 1
    Card(Modifier.fillMaxWidth().alpha(if (st == 2) 0.65f else 1f), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (prog) Sky else if (st == 3) YellowTint else White, contentColor = if (prog) White else Deep),
        border = if (prog) BorderStroke(2.dp, Yellow) else if (st == 3) BorderStroke(1.dp, Yellow) else BorderStroke(1.dp, Sky.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(if (prog) 12.dp else 1.dp)) {
        Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(t.status == 2, { onDone() }, colors = CheckboxDefaults.colors(
                checkedColor = if (prog) Yellow else Sky, uncheckedColor = if (prog) White else Sky, checkmarkColor = if (prog) Deep else White))
            Column(Modifier.weight(1f).clickable { open = !open }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (prog) { Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = Yellow) { Text("قيد التنفيذ", color = Deep, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)) } }
                }
                Text("${t.start.tm()} – ${t.end.tm()} • ${dur(t.start, t.end)}", fontSize = 12.sp)
                Text("${Cats[t.category]} • ${Pris[t.priority]} • ${Sts[st]}", fontSize = 11.sp, modifier = Modifier.alpha(0.85f))
                if (t.description.isNotBlank()) Text(t.description, fontSize = 11.sp, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.85f))
            }
            Box { IconButton({ menu = true }, Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("تعديل") }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text("تأجيل إلى الغد") }, { menu = false; onPost() })
                    DropdownMenuItem({ Text("تأجيل إلى وقت آخر…") }, { menu = false; onPostLater() })
                    DropdownMenuItem({ Text("حذف") }, { menu = false; onDel() }) } }
        }
    }
}

@Composable fun SubRow(s: SubNote, i: Int, reorder: Boolean, canUp: Boolean, canDown: Boolean, onSave: (String) -> Unit, onDelete: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    var text by remember(s.id) { mutableStateOf(s.text) }
    LaunchedEffect(text) { if (text.isNotBlank() && text != s.text) { delay(600); onSave(text) } }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(SkyTint), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Yellow))
        Text("${i + 1}", color = Sky, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        BasicTextField(text, { text = it }, Modifier.weight(1f).padding(vertical = 10.dp), textStyle = TextStyle(color = Deep, fontSize = 14.sp), cursorBrush = SolidColor(Sky))
        if (reorder) {
            IconButton(onUp, Modifier.size(32.dp), enabled = canUp) { Icon(Icons.Default.KeyboardArrowUp, "أعلى", Modifier.size(20.dp)) }
            IconButton(onDown, Modifier.size(32.dp), enabled = canDown) { Icon(Icons.Default.KeyboardArrowDown, "أسفل", Modifier.size(20.dp)) }
        } else IconButton(onDelete, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "حذف", Modifier.size(16.dp)) }
    }
}

@Composable fun DraftRow(n: Int, onAdd: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    fun commit() { if (t.isNotBlank()) { onAdd(t.trim()); t = "" } }
    LaunchedEffect(t) { if (t.isNotBlank()) { delay(900); commit() } }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(SkyTint.copy(alpha = 0.6f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Yellow.copy(alpha = 0.5f)))
        Text("$n", color = Sky.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp))
        BasicTextField(t, { t = it }, Modifier.weight(1f).padding(vertical = 10.dp).onFocusChanged { if (!it.isFocused) commit() },
            textStyle = TextStyle(color = Deep, fontSize = 14.sp), cursorBrush = SolidColor(Sky),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commit() }),
            decorationBox = { inner -> if (t.isEmpty()) Text("اكتب ملاحظة فرعية هنا…", color = Sky.copy(alpha = 0.6f), fontSize = 14.sp); inner() })
    }
}

@Composable fun NotesScreen(vm: VM) {
    val notes by vm.notes.collectAsState(); val subs by vm.subs.collectAsState()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var q by remember { mutableStateOf("") }; var reorder by remember { mutableStateOf(false) }
    var addNote by remember { mutableStateOf(false) }; var editNote by remember { mutableStateOf<Note?>(null) }; var delNote by remember { mutableStateOf<Note?>(null) }
    val cur = notes.firstOrNull { it.id == openId }
    BackHandler(enabled = cur != null) { openId = null; reorder = false }
    val cardColors = CardDefaults.cardColors(containerColor = White, contentColor = Deep)
    val border = BorderStroke(1.dp, Sky.copy(alpha = 0.3f))
    val sortBtn: @Composable RowScope.() -> Unit = { IconButton({ reorder = !reorder }) { Icon(Icons.Default.SwapVert, "ترتيب", tint = if (reorder) Yellow else White) } }
    Box(Modifier.fillMaxSize()) {
        if (cur == null) {
            val rmode = reorder && q.isBlank()
            val shown = notes.filter { n -> q.isBlank() || n.text.contains(q, true) || subs.any { it.noteId == n.id && it.text.contains(q, true) } }
            Column(Modifier.fillMaxSize()) {
                Header("الملاحظات", if (rmode) "وضع الترتيب: حرّك بالأسهم" else "${notes.size} ملاحظة", actions = sortBtn)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("بحث") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
                    if (shown.isEmpty()) item { Text(if (notes.isEmpty()) "لا توجد ملاحظات، اضغط + للإضافة" else "لا توجد نتائج", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
                    items(shown, key = { it.id }) { n ->
                        val idx = notes.indexOfFirst { it.id == n.id }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = cardColors, border = border) {
                            Row(Modifier.clickable(enabled = !rmode) { openId = n.id }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val mine = subs.filter { it.noteId == n.id }
                                    Text(n.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    mine.take(3).forEach { sn ->
                                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(5.dp).clip(CircleShape).background(Yellow)); Spacer(Modifier.width(6.dp))
                                            Text(sn.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.85f)) } }
                                    Text(if (mine.size > 3) "+${mine.size - 3} أخرى • ${n.date.ld()}" else if (mine.isEmpty()) "بدون ملاحظات فرعية • ${n.date.ld()}" else "${n.date.ld()}",
                                        fontSize = 11.sp, color = Sky, modifier = Modifier.padding(top = 3.dp)) }
                                if (rmode) {
                                    IconButton({ vm.moveNote(notes, idx, -1) }, Modifier.size(34.dp), enabled = idx > 0) { Icon(Icons.Default.KeyboardArrowUp, "أعلى") }
                                    IconButton({ vm.moveNote(notes, idx, 1) }, Modifier.size(34.dp), enabled = idx < notes.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "أسفل") }
                                } else {
                                    IconButton({ editNote = n }, Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "تعديل", Modifier.size(18.dp)) }
                                    IconButton({ delNote = n }, Modifier.size(34.dp)) { Icon(Icons.Default.Delete, "حذف", Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
            FloatingActionButton({ addNote = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Yellow, contentColor = Deep) { Icon(Icons.Default.Add, "إضافة") }
        } else {
            val mine = subs.filter { it.noteId == cur.id }
            Column(Modifier.fillMaxSize()) {
                Header(cur.text, if (reorder) "وضع الترتيب: حرّك بالأسهم" else "${mine.size} ملاحظة فرعية", onBack = { openId = null; reorder = false }, actions = sortBtn)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(mine, key = { _, x -> x.id }) { i, s ->
                        SubRow(s, i, reorder, i > 0, i < mine.lastIndex, { vm.saveSub(s.copy(text = it)) }, { vm.deleteSub(s) }, { vm.moveSub(mine, i, -1) }, { vm.moveSub(mine, i, 1) })
                    }
                    item(key = "draft") { DraftRow(mine.size + 1) { vm.saveSub(SubNote(noteId = cur.id, text = it)) } }
                }
            }
        }
    }
    if (addNote) TextDlg("ملاحظة جديدة", "", { vm.saveNote(Note(text = it, date = System.currentTimeMillis())); addNote = false }, { addNote = false })
    editNote?.let { n -> TextDlg("تعديل الملاحظة", n.text, { vm.saveNote(n.copy(text = it)); editNote = null }, { editNote = null }) }
    delNote?.let { n -> AlertDialog({ delNote = null }, title = { Text("حذف الملاحظة") }, text = { Text("سيتم حذف «${n.text}» مع كل ملاحظاتها الفرعية.") },
        confirmButton = { TextButton({ vm.deleteNote(n); delNote = null }) { Text("حذف") } }, dismissButton = { TextButton({ delNote = null }) { Text("إلغاء") } }) }
}

@Composable fun BackupScreen(vm: VM) {
    var pending by remember { mutableStateOf<Uri?>(null) }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? -> if (u != null) vm.export(u) }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> if (u != null) pending = u }
    Column(Modifier.fillMaxSize()) {
        Header("النسخ الاحتياطي")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("احفظ نسخة من كل مهامك وملاحظاتك وسجلك في ملف تستطيع نقله أو رفعه على التخزين السحابي، واستعدها متى شئت.")
            Button({ exp.launch("MyDailyNotes-${LocalDate.now()}.json") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Sky, contentColor = White)) {
                Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("إنشاء نسخة احتياطية") }
            OutlinedButton({ imp.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("استعادة من نسخة") }
        }
    }
    pending?.let { u -> AlertDialog({ pending = null }, title = { Text("استعادة نسخة") }, text = { Text("ستُستبدل المهام والملاحظات الحالية بما في النسخة. هل تريد المتابعة؟") },
        confirmButton = { TextButton({ vm.import(u); pending = null }) { Text("استعادة") } }, dismissButton = { TextButton({ pending = null }) { Text("إلغاء") } }) }
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

