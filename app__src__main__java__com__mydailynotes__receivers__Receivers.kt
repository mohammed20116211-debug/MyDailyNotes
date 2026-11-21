package com.mydailynotes.receivers
import android.app.NotificationManager
import android.content.*
import com.mydailynotes.data.*
import com.mydailynotes.notifications.Notifier
import com.mydailynotes.scheduler.Scheduler
import kotlinx.coroutines.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1); val kind = i.getStringExtra("kind") ?: return
        if (kind == "stop") { c.getSystemService(NotificationManager::class.java).cancel(id.toInt() * 2); return }
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val d = AppDb.get(c).dao(); val t = d.task(id) ?: return@launch
                if (t.status == 2) return@launch
                if (kind == "start" && t.startAlarm) { d.upsert(t.copy(status = 1)); Notifier.start(c, t) }
                if (kind == "end") {
                    d.upsert(t.copy(status = 3))
                    d.addHist(Hist(title = t.title, op = "انتهى وقتها ولم تُنجز", time = System.currentTimeMillis(), info = ""))
                    if (t.endAlarm) Notifier.end(c, t)
                }
            } finally { pr.finish() }
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { AppDb.get(c).dao().upcoming(System.currentTimeMillis()).forEach { Scheduler.schedule(c, it) } }
            finally { pr.finish() }
        }
    }
}
