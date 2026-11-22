package com.mydailynotes
import android.app.Application
import android.content.Context
import android.util.Log
import com.mydailynotes.notifications.Notifier
import java.io.File

object CrashLog {
    fun log(c: Context, e: Throwable) {
        try { File(c.filesDir, "crash.txt").appendText(Log.getStackTraceString(e) + "\n----\n") } catch (_: Throwable) {}
    }
}
class DailyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> CrashLog.log(this, e); old?.uncaughtException(t, e) }
        try { Notifier.channels(this) } catch (e: Throwable) { CrashLog.log(this, e) }
    }
}
