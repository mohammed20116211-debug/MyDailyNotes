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

    fun schedule(c: Context, t: Task) {
        cancel(c, t.id)
        if (t.status == 2) return
        val am = c.getSystemService(AlarmManager::class.java); val now = System.currentTimeMillis()
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // setAlarmClock دقيق ومستثنى من قيود Doze ولا يحتاج إذن SCHEDULE_EXACT_ALARM
        if (t.startAlarm && t.start > now)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t.start, show), pi(c, t.id, "start", (t.id * 2).toInt()))
        if (t.end > now) // يُجدول دائماً لتسجيل «انتهى وقتها»، والإشعار حسب خيار المهمة
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t.end, show), pi(c, t.id, "end", (t.id * 2 + 1).toInt()))
    }
    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c, id, "start", (id * 2).toInt())); am.cancel(pi(c, id, "end", (id * 2 + 1).toInt()))
    }
}
