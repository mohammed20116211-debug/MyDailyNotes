package com.mydailynotes.notifications
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.media.*
import androidx.core.app.NotificationCompat
import com.mydailynotes.data.Task
import com.mydailynotes.receivers.AlarmReceiver
import com.mydailynotes.ui.MainActivity
import com.mydailynotes.ui.tm

object Notifier {
    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("start", "تنبيه بداية المهمة", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
        })
        nm.createNotificationChannel(NotificationChannel("end", "تنبيه نهاية المهمة", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        })
    }
    private fun open(c: Context, id: Long) = PendingIntent.getActivity(c, id.toInt(),
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    @SuppressLint("MissingPermission")
    fun start(c: Context, t: Task) {
        channels(c)
        val stop = PendingIntent.getBroadcast(c, 1_000_000 + t.id.toInt(),
            Intent(c, AlarmReceiver::class.java).putExtra("id", t.id).putExtra("kind", "stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, "start").setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(t.title).setContentText("حان وقت بدء المهمة • ${t.start.tm()} - ${t.end.tm()}")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(open(c, t.id), true).setContentIntent(open(c, t.id)).setTimeoutAfter(60_000)
            .addAction(0, "إيقاف الصوت", stop).addAction(0, "فتح التفاصيل", open(c, t.id)).build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // تكرار الرنين حتى الإيقاف أو انتهاء المهلة
        c.getSystemService(NotificationManager::class.java).notify(t.id.toInt() * 2, n)
    }
    @SuppressLint("MissingPermission")
    fun end(c: Context, t: Task) {
        channels(c)
        val n = NotificationCompat.Builder(c, "end").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("انتهى وقت المهمة").setContentText("${t.title} • ${t.end.tm()}")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setAutoCancel(true).setContentIntent(open(c, t.id)).build()
        c.getSystemService(NotificationManager::class.java).notify(t.id.toInt() * 2 + 1, n)
    }
}
