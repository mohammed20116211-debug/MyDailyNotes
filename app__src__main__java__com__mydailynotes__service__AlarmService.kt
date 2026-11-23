package com.mydailynotes.service
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.ServiceCompat
import com.mydailynotes.CrashLog
import com.mydailynotes.data.Task
import com.mydailynotes.notifications.Notifier

// يشغّل الصوت على قناة المنبّه فيعمل حتى لو كان الهاتف على الصامت
class AlarmService : Service() {
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val id = i?.getLongExtra("id", -1) ?: -1L
        val ring = i?.getStringExtra("kind") == "start"
        val t = Task(id = id, title = i?.getStringExtra("title") ?: "", start = i?.getLongExtra("s", 0) ?: 0L, end = i?.getLongExtra("e", 0) ?: 0L)
        val nid = id.toInt() * 2 + (if (ring) 0 else 1)
        try {
            ServiceCompat.startForeground(this, nid, Notifier.build(this, t, ring, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: Throwable) {
            CrashLog.log(this, e); Notifier.show(this, nid, Notifier.build(this, t, ring, false)); stopSelf(); return START_NOT_STICKY
        }
        h.removeCallbacksAndMessages(null); play(ring)
        h.postDelayed({ stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }, if (ring) 60_000L else 4_000L)
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun play(ring: Boolean) {
        try {
            mp?.release(); mp = null
            val vib = getSystemService(Vibrator::class.java)
            val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val uri = RingtoneManager.getDefaultUri(if (ring) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION)
                mp = MediaPlayer().apply { setAudioAttributes(attr); setDataSource(this@AlarmService, uri); isLooping = ring; prepare(); start() }
                if (ring) vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attr)
            } else { // وضع الهزاز أو الصامت: اهتزاز فقط
                vib.vibrate(if (ring) VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0)
                            else VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1), attr)
            }
        } catch (e: Throwable) { CrashLog.log(this, e) }
    }
    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { mp?.release(); getSystemService(Vibrator::class.java).cancel() } catch (_: Throwable) {}
        mp = null; super.onDestroy()
    }
}
