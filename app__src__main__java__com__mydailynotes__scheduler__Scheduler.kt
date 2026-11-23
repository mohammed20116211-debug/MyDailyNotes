package com.mydailynotes.scheduler
import android.app.*
import android.content.*
import com.mydailynotes.data.Task
import com.mydailynotes.receivers.AlarmReceiver
import com.mydailynotes.ui.MainActivity

object Scheduler {
    // لكل مهمة معرّفان مستقلان: البداية = id*2، النهاية = id*2+1
    private fun pi(c: Context, id: Long, kind: String, code: Int) = PendingIntent.getBroadcast(
        c, code, Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("kind", kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun set(am: AlarmManager, at: Long, show: PendingIntent, p: PendingIntent) {
        try { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p) }
        catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) } // بديل إذا لم يُمنح إذن الدقة
    }
    fun schedule(c: Context, t: Task) {
        cancel(c, t.id)
        if (t.status == 2) return
        val am = c.getSystemService(AlarmManager::class.java); val now = System.currentTimeMillis()
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        if (t.startAlarm && t.start > now) set(am, t.start, show, pi(c, t.id, "start", (t.id * 2).toInt()))
        if (t.end > now) set(am, t.end, show, pi(c, t.id, "end", (t.id * 2 + 1).toInt()))
    }
    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c, id, "start", (id * 2).toInt())); am.cancel(pi(c, id, "end", (id * 2 + 1).toInt()))
    }
}
