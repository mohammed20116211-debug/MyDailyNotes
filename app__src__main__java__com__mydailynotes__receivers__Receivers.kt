package com.mydailynotes.receivers
import android.app.NotificationManager
import android.content.*
import androidx.core.content.ContextCompat
import com.mydailynotes.CrashLog
import com.mydailynotes.data.*
import com.mydailynotes.notifications.Notifier
import com.mydailynotes.scheduler.Scheduler
import com.mydailynotes.service.AlarmService
import kotlinx.coroutines.*

private fun ring(c: Context, t: Task, start: Boolean) {
    Notifier.channels(c)
    if (!start) { Notifier.show(c, t.id.toInt() * 2 + 1, Notifier.build(c, t, false, false)); return }
    try {
        ContextCompat.startForegroundService(c, Intent(c, AlarmService::class.java).putExtra("id", t.id)
            .putExtra("kind", if (start) "start" else "end").putExtra("title", t.title).putExtra("s", t.start).putExtra("e", t.end))
    } catch (e: Throwable) {
        CrashLog.log(c, e)
        Notifier.show(c, t.id.toInt() * 2 + (if (start) 0 else 1), Notifier.build(c, t, start, false))
    }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1); val kind = i.getStringExtra("kind") ?: return
        if (kind == "stop") { c.stopService(Intent(c, AlarmService::class.java)); c.getSystemService(NotificationManager::class.java).cancel(id.toInt() * 2); return }
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val d = AppDb.get(c).dao(); val t = d.task(id) ?: return@launch
                if (t.status == 2) return@launch
                if (kind == "start") { d.upsert(t.copy(status = 1)); if (t.startAlarm) ring(c, t, true) }
                else {
                    d.upsert(t.copy(status = 3))
                    d.addHist(Hist(title = t.title, op = "انتهى وقتها ولم تُنجز", time = System.currentTimeMillis(), info = ""))
                    if (t.endAlarm) ring(c, t, false)
                }
            } catch (e: Throwable) { CrashLog.log(c, e) } finally { pr.finish() }
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { AppDb.get(c).dao().upcoming(System.currentTimeMillis()).forEach { Scheduler.schedule(c, it) } }
            catch (e: Throwable) { CrashLog.log(c, e) } finally { pr.finish() }
        }
    }
}
