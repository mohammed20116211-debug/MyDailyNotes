package com.mydailynotes.ui
import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.*
import androidx.room.withTransaction
import com.mydailynotes.CrashLog
import com.mydailynotes.data.*
import com.mydailynotes.scheduler.Scheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

val zone: ZoneId get() = ZoneId.systemDefault()
fun Long.ldt(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun Long.ld(): LocalDate = ldt().toLocalDate()
fun Long.tm(): String = ldt().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale("ar")))
fun LocalDateTime.ms(): Long = atZone(zone).toInstant().toEpochMilli()
fun dur(a: Long, b: Long): String { val m = (b - a) / 60000; return "${m / 60} ساعة و ${m % 60} دقيقة" }

class VM(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app); private val d = db.dao()
    val tasks = d.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = d.notes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subs = d.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hist = d.hist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private fun toast(s: String) = Toast.makeText(app, s, Toast.LENGTH_SHORT).show()
    private suspend fun say(s: String) = withContext(Dispatchers.Main) { toast(s) }
    private fun safe(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try { block() } catch (e: Throwable) { CrashLog.log(app, e); say("خطأ: ${e.message}") }
    }
    private suspend fun log(t: Task, op: String) =
        d.addHist(Hist(title = t.title, op = op, time = System.currentTimeMillis(), info = "${t.start.ld()} ${t.start.tm()} - ${t.end.tm()}"))

    fun save(t: Task) = safe {
        val isNew = t.id == 0L; val id = d.upsert(t); val s = if (isNew) t.copy(id = id) else t
        Scheduler.schedule(app, s); log(s, if (isNew) "إضافة" else "تعديل")
    }
    fun delete(t: Task) = safe { Scheduler.cancel(app, t.id); d.deleteTask(t.id); log(t, "حذف") }
    fun toggleDone(t: Task) = safe {
        if (t.status == 2) { val s = t.copy(status = 0); d.upsert(s); Scheduler.schedule(app, s); log(s, "إلغاء الإنجاز") }
        else { val s = t.copy(status = 2); d.upsert(s); Scheduler.cancel(app, t.id); log(s, "إنجاز") }
    }
    fun postpone(t: Task) = postponeTo(t, t.start.ldt().plusDays(1).ms(), "تأجيل إلى الغد")
    fun postponeTo(t: Task, newStart: Long, op: String = "تأجيل إلى وقت آخر") = safe {
        val s = t.copy(start = newStart, end = newStart + (t.end - t.start), status = 0)
        d.upsert(s); Scheduler.schedule(app, s); log(s, op)
        say("تم التأجيل إلى ${newStart.ld()} ${newStart.tm()}")
    }
    fun saveNote(n: Note) = safe { d.upsertNote(if (n.id == 0L) n.copy(pos = (notes.value.maxOfOrNull { it.pos } ?: -1) + 1) else n) }
    fun deleteNote(n: Note) = safe { d.deleteSubsOf(n.id); d.deleteNote(n) }
    fun saveSub(s: SubNote) = safe { d.upsertSub(if (s.id == 0L) s.copy(pos = (subs.value.filter { it.noteId == s.noteId }.maxOfOrNull { it.pos } ?: -1) + 1) else s) }
    fun deleteSub(s: SubNote) = safe { d.deleteSub(s) }
    fun moveNote(ordered: List<Note>, i: Int, dir: Int) = safe {
        val j = i + dir; if (j !in ordered.indices) return@safe
        val l = ordered.toMutableList(); l.add(j, l.removeAt(i))
        l.forEachIndexed { k, n -> if (n.pos != k) d.upsertNote(n.copy(pos = k)) }
    }
    fun moveSub(ordered: List<SubNote>, i: Int, dir: Int) = safe {
        val j = i + dir; if (j !in ordered.indices) return@safe
        val l = ordered.toMutableList(); l.add(j, l.removeAt(i))
        l.forEachIndexed { k, n -> if (n.pos != k) d.upsertSub(n.copy(pos = k)) }
    }

    // ---- نسخ احتياطي واستعادة
    fun export(uri: Uri) = safe {
        val o = JSONObject().put("app", "MyDailyNotes").put("version", 1).put("time", System.currentTimeMillis())
        o.put("tasks", JSONArray().also { a -> d.allTasks().forEach { t -> a.put(JSONObject().put("id", t.id).put("title", t.title).put("description", t.description)
            .put("start", t.start).put("end", t.end).put("category", t.category).put("priority", t.priority).put("startAlarm", t.startAlarm).put("endAlarm", t.endAlarm).put("status", t.status)) } })
        o.put("notes", JSONArray().also { a -> d.allNotes().forEach { n -> a.put(JSONObject().put("id", n.id).put("text", n.text).put("date", n.date).put("pos", n.pos)) } })
        o.put("subs", JSONArray().also { a -> d.allSubs().forEach { n -> a.put(JSONObject().put("id", n.id).put("noteId", n.noteId).put("text", n.text).put("pos", n.pos)) } })
        o.put("hist", JSONArray().also { a -> d.allHist().forEach { h -> a.put(JSONObject().put("id", h.id).put("title", h.title).put("op", h.op).put("time", h.time).put("info", h.info)) } })
        app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(o.toString(1).toByteArray()) }
        say("تم حفظ النسخة الاحتياطية")
    }
    fun import(uri: Uri) = safe {
        val txt = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        require(o.optString("app") == "MyDailyNotes") { "الملف ليس نسخة احتياطية صالحة" }
        fun <T> arr(k: String, f: (JSONObject) -> T): List<T> { val a = o.optJSONArray(k) ?: JSONArray(); return (0 until a.length()).map { f(a.getJSONObject(it)) } }
        val ts = arr("tasks") { Task(id = it.getLong("id"), title = it.getString("title"), description = it.optString("description"), start = it.getLong("start"), end = it.getLong("end"),
            category = it.optInt("category"), priority = it.optInt("priority", 1), startAlarm = it.optBoolean("startAlarm", true), endAlarm = it.optBoolean("endAlarm", true), status = it.optInt("status")) }
        val ns = arr("notes") { Note(id = it.getLong("id"), text = it.getString("text"), date = it.getLong("date"), pos = it.optInt("pos")) }
        val ss = arr("subs") { SubNote(id = it.getLong("id"), noteId = it.getLong("noteId"), text = it.getString("text"), pos = it.optInt("pos")) }
        val hs = arr("hist") { Hist(id = it.getLong("id"), title = it.getString("title"), op = it.getString("op"), time = it.getLong("time"), info = it.optString("info")) }
        d.allTasks().forEach { Scheduler.cancel(app, it.id) }
        db.withTransaction {
            d.clearTasks(); d.clearNotes(); d.clearSubs(); d.clearHist()
            ts.forEach { d.upsert(it) }; ns.forEach { d.upsertNote(it) }; ss.forEach { d.upsertSub(it) }; hs.forEach { d.addHist(it) }
        }
        ts.forEach { Scheduler.schedule(app, it) }
        say("تمت استعادة ${ts.size} مهمة و ${ns.size} ملاحظة")
    }
}
