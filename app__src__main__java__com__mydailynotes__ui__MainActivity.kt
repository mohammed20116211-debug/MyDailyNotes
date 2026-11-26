@file:OptIn(ExperimentalMaterial3Api::class)
package com.mydailynotes.ui
import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mydailynotes.data.*
import com.mydailynotes.service.AlarmService
import kotlinx.coroutines.delay
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Cats = listOf("العمل", "الدراسة", "مواعيد شخصية", "الصحة", "أخرى")
val Pris = listOf("منخفضة", "متوسطة", "عالية")
val Sts = listOf("لم تبدأ", "قيد التنفيذ", "مكتملة", "انتهى وقتها ولم تُنجز")
val Sky = Color(0xFF1996D3); val Deep = Color(0xFF0B6A9E); val SkyTint = Color(0xFFE6F4FB)
val Yellow = Color(0xFFFFC83D); val YellowTint = Color(0xFFFFF3C9); val White = Color.White
private val scheme = lightColorScheme(
    primary = Sky, onPrimary = White, primaryContainer = SkyTint, onPrimaryContainer = Deep,
    secondary = Yellow, onSecondary = Deep, secondaryContainer = YellowTint, onSecondaryContainer = Deep,
    tertiary = Yellow, onTertiary = Deep, background = White, onBackground = Deep, surface = White, onSurface = Deep,
    surfaceVariant = SkyTint, onSurfaceVariant = Sky, outline = Sky, outlineVariant = SkyTint, surfaceTint = White,
    surfaceContainer = White, surfaceContainerLow = White, surfaceContainerHigh = White, surfaceContainerHighest = SkyTint
)
fun eff(t: Task, now: Long) = if (t.status == 2) 2 else if (now > t.end) 3 else if (now >= t.start) 1 else 0

class MainActivity : ComponentActivity() {
    private var warn by mutableStateOf<String?>(null)
    private var snooze by mutableStateOf(false)
    private var crash by mutableStateOf<String?>(null)
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        File(filesDir, "crash.txt").let { if (it.exists()) crash = it.readText() }
        ack(intent)
        setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                    val w = warn
                    if (w != null && !snooze) AlertDialog(
                        onDismissRequest = { snooze = true },
                        title = { Text("مطلوب لعمل التنبيهات") },
                        text = { Text(when (w) {
                            "exact" -> "لتعمل التنبيهات في وقتها بدقة فعّل «المنبهات والتذكيرات» للتطبيق."
                            "notif" -> "الإشعارات معطّلة للتطبيق، فلن تظهر التنبيهات. فعّلها من الإعدادات."
                            else -> "لتظهر شاشة تنبيه البداية فوق القفل فعّل «الإشعارات بملء الشاشة» لهذا التطبيق." }) },
                        confirmButton = { TextButton({
                            snooze = true
                            try {
                                startActivity(when (w) {
                                    "exact" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                                    "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    else -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")) })
                            } catch (e: Exception) { Toast.makeText(this@MainActivity, "افتح إعدادات التطبيق يدوياً", Toast.LENGTH_LONG).show() }
                        }) { Text("فتح الإعدادات") } },
                        dismissButton = { TextButton({ snooze = true }) { Text("لاحقاً") } })
                    crash?.let { log ->
                        AlertDialog(onDismissRequest = {}, title = { Text("سجل أخطاء سابق") },
                            text = { Text(log.take(1800), Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp) },
                            confirmButton = { TextButton({
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", log))
                                Toast.makeText(this@MainActivity, "تم النسخ", Toast.LENGTH_SHORT).show()
                            }) { Text("نسخ") } },
                            dismissButton = { TextButton({ File(filesDir, "crash.txt").delete(); crash = null }) { Text("حذف السجل") } })
                    }
                }
            }
        }
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); ack(i) }
    private fun ack(i: Intent?) { if (i?.getBooleanExtra("ack", false) == true) stopService(Intent(this, AlarmService::class.java)) }
    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NotificationManager::class.java)
        warn = when {
            Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms() -> "exact"
            !nm.areNotificationsEnabled() -> "notif"
            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() -> "fsi"
            else -> null
        }
    }
}

class AlarmActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getLongExtra("id", -1); val title = intent.getStringExtra("title") ?: ""
        val s = intent.getLongExtra("s", 0); val e = intent.getLongExtra("e", 0)
        fun stopAll() {
            stopService(Intent(this, AlarmService::class.java))
            getSystemService(NotificationManager::class.java).cancel(id.toInt() * 2)
        }
        setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlarmScreen(title, s, e,
                        onAccept = { stopAll(); startActivity(Intent(this@AlarmActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() },
                        onStop = { stopAll(); finish() })
                }
            }
        }
    }
}

@Composable fun AlarmScreen(title: String, s: Long, e: Long, onAccept: () -> Unit, onStop: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "pulse")
    val sc by tr.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Sky, Deep))).padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("My Daily Notes", color = Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(110.dp).scale(sc).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Alarm, null, Modifier.size(56.dp), tint = Deep) }
            Spacer(Modifier.height(40.dp))
            Text(title, color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("حان وقت بدء المهمة", color = Yellow, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("${s.tm()} – ${e.tm()} • ${dur(s, e)}", color = White, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = onStop, modifier = Modifier.size(76.dp), shape = CircleShape, color = White) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "إيقاف", Modifier.size(36.dp), tint = Deep) } }
                Spacer(Modifier.height(8.dp)); Text("إيقاف", color = White, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = onAccept, modifier = Modifier.size(76.dp), shape = CircleShape, color = Yellow) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, "موافق", Modifier.size(40.dp), tint = Deep) } }
                Spacer(Modifier.height(8.dp)); Text("موافق", color = Yellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
