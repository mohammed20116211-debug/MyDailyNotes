package com.mydailynotes.notifications
import android.app.*
import android.content.*
import android.media.*
import androidx.core.app.NotificationCompat
import com.mydailynotes.data.Task
import com.mydailynotes.receivers.AlarmReceiver
import com.mydailynotes.ui.AlarmActivity
import com.mydailynotes.ui.MainActivity
import com.mydailynotes.ui.tm

object Notifier {
    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val ringAttr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel("start", "تنبيه البداية (احتياطي)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ringAttr); enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel("end", "تنبيه النهاية (احتياطي)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()); enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel("end_msg", "تنبيه نهاية المهمة", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 300, 200, 300) })
        nm.createNotificationChannel(NotificationChannel("start_s", "تنبيه بداية المهمة", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
        nm.createNotificationChannel(NotificationChannel("end_s", "تنبيه نهاية المهمة", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) })
    }
    private val F = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private fun open(c: Context, id: Long, ack: Boolean) = PendingIntent.getActivity(c, id.toInt() + (if (ack) 2_000_000 else 3_000_000),
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("ack", ack), F)
    private fun stopPi(c: Context, id: Long) = PendingIntent.getBroadcast(c, 1_000_000 + id.toInt(),
        Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("kind", "stop"), F)

    fun build(c: Context, t: Task, start: Boolean, silent: Boolean): Notification {
        val ch = if (start) (if (silent) "start_s" else "start") else (if (silent) "end_s" else "end_msg")
        val b = NotificationCompat.Builder(c, ch)
            .setSmallIcon(if (start) android.R.drawable.ic_menu_call else android.R.drawable.ic_dialog_email)
            .setContentTitle(if (start) t.title else "انتهى وقت المهمة")
            .setContentText(if (start) "حان وقت بدء المهمة • ${t.start.tm()} - ${t.end.tm()}" else "${t.title} • ${t.end.tm()}")
            .setContentIntent(open(c, t.id, true)).setAutoCancel(!start)
        if (start) {
            val ringPi = PendingIntent.getActivity(c, t.id.toInt() + 4_000_000, Intent(c, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("id", t.id).putExtra("title", t.title).putExtra("s", t.start).putExtra("e", t.end), F)
            val who = androidx.core.app.Person.Builder().setName(t.title).setImportant(true).build()
            b.setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(ringPi, true).setContentIntent(ringPi)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(who, stopPi(c, t.id), open(c, t.id, true)))
            if (!silent) b.setTimeoutAfter(60_000)
        } else b.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        val n = b.build()
        if (start && !silent) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }
    fun show(c: Context, id: Int, n: Notification) = c.getSystemService(NotificationManager::class.java).notify(id, n)
}
