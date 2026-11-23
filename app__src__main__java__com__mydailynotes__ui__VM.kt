package com.mydailynotes.ui
import android.app.Application
import android.widget.Toast
import androidx.lifecycle.*
import com.mydailynotes.CrashLog
import com.mydailynotes.data.*
import com.mydailynotes.scheduler.Scheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

val zone: ZoneId get() = ZoneId.systemDefault()
fun Long.ldt(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun Long.ld(): LocalDate = ldt().toLocalDate()
fun Long.tm(): String = ldt().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale("ar")))
fun LocalDateTime.ms(): Long = atZone(zone).toInstant().toEpochMilli()
fun dur(a: Long, b: Long): String { val m = (b - a) / 60000; return "${m / 60} ساعة و ${m % 60} دقيقة" }

class VM(private val app: Application) : AndroidViewModel(app) {
    private val d = AppDb.get(app).dao()
    val tasks = d.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = d.notes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subs = d.subs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hist = d.hist().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private fun toast(s: String) = Toast.makeText(app, s, Toast.LENGTH_SHORT).show()
    private fun safe(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try { block() } catch (e: Throwable) { CrashLog.log(app, e); withContext(Dispatchers.Main) { toast("خطأ: ${e.message}") } }
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
    fun postpone(t: Task) = safe {
        val s = t.copy(start = t.start.ldt().plusDays(1).ms(), end = t.end.ldt().plusDays(1).ms(), status = 0)
        d.upsert(s); Scheduler.schedule(app, s); log(s, "تأجيل إلى الغد")
        withContext(Dispatchers.Main) { toast("تم التأجيل إلى الغد") }
    }
    fun saveNote(n: Note) = safe { d.upsertNote(n) }
    fun deleteNote(n: Note) = safe { d.deleteSubsOf(n.id); d.deleteNote(n) }
    fun saveSub(s: SubNote) = safe { d.upsertSub(s) }
    fun deleteSub(s: SubNote) = safe { d.deleteSub(s) }
}
