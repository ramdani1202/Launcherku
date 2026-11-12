package com.launcherku.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun SearchBar(onSearch: () -> Unit, onAssistant: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f)).clickable { onSearch() }
    ) {
        GoogleDots(Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(22.dp))
        Box(
            Modifier.align(Alignment.CenterEnd).width(104.dp).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF4F86E8), Color(0xFF3F74D9))))
                .clickable { onAssistant() },
            Alignment.Center,
        ) { Sparkle(Modifier.size(24.dp)) }
    }
}

private fun openChat(ctx: Context) {
    val n = WaContact.number
    if (n.isNotBlank()) safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n")))
    else launchApp(ctx, "com.whatsapp")
}

/**
 * Kontak WhatsApp asli: nama (dan foto) diambil dari kontak yang dipilih, pesan terbaru + jumlahnya
 * dibaca langsung dari notifikasi WhatsApp. Ketuk = buka chat, tahan = pengaturan.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactPill(modifier: Modifier, apps: List<AppEntry>, onPick: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val name = WaContact.name
    val photo = WaContact.photo
    val msg = WaInbox.latestFor(name)
    var listening by remember { mutableStateOf(listenerEnabled(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            listening = listenerEnabled(ctx)
            delay(2000)
        }
    }
    Row(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).combinedClickable(
            onClick = {
                val pi = msg?.intent
                when {
                    name.isBlank() -> onPick()
                    pi != null -> try { pi.send() } catch (_: Exception) { openChat(ctx) }
                    else -> openChat(ctx)
                }
            },
            onLongClick = onSettings,
        ).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp)) {
            if (photo != null) {
                Image(photo, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.BottomEnd)) { AppTile(Slots.whatsapp, apps, 16.dp, tappable = false) }
            } else {
                AppTile(Slots.whatsapp, apps, 36.dp, tappable = false)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            T(if (name.isBlank()) "Pilih kontak" else name, 14, Ink, FontWeight.SemiBold)
            val sub = when {
                msg != null -> msg.text
                name.isNotBlank() && !listening -> "Notif belum aktif"
                else -> null
            }
            if (sub != null) T(sub, 10, Color(0xFF5B5B70), FontWeight.Normal)
        }
        if (msg != null) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF25D366)), Alignment.Center) {
                T("${msg.count}", 11, Color.White, FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeatherCard(modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1E46C8), Color(0xFF0C2C94))))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("▲", 9, Color.White, modifier = Modifier.rotate(45f))
                Spacer(Modifier.width(4.dp))
                T(Config.CITY, 13, Color.White)
            }
            Row(verticalAlignment = Alignment.Top) {
                T(Config.TEMP, 46, Color.White, FontWeight.SemiBold)
                T("°", 26, Color.White, FontWeight.Medium, Modifier.padding(top = 4.dp))
            }
            T(Config.WEATHER_TEXT, 12, Color.White, FontWeight.SemiBold)
            T(Config.WEATHER_RANGE, 12, Color.White.copy(alpha = 0.85f))
        }
        CloudMoon(Modifier.align(Alignment.BottomEnd).size(70.dp).offset(x = 4.dp, y = 2.dp))
    }
}

@Composable
fun AlarmPill(modifier: Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var next by remember { mutableStateOf(am.nextAlarmClock) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { next = am.nextAlarmClock }
        }
        ContextCompat.registerReceiver(
            ctx, r, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { ctx.unregisterReceiver(r) }
    }
    val text = next?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.triggerTime)) } ?: "--:--"
    Row(
        modifier.clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF6A4FE0), Color(0xFF7B5CF0))))
            .clickable {
                try { next?.showIntent?.send() } catch (_: Exception) {
                    safeStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }
                if (next == null) safeStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF5237C4)), Alignment.Center) {
            AlarmGlyph(Modifier.size(28.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            T(text, 17, Color.White, FontWeight.Bold)
            T("Next Alarm", 11, Color.White, FontWeight.SemiBold)
        }
    }
}

@Composable
fun BatteryPill(modifier: Modifier) {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level = l * 100 / s
            }
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        sticky?.let { r.onReceive(ctx, it) }
        onDispose { ctx.unregisterReceiver(r) }
    }
    Box(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
            .clickable { safeStart(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth((0.2f + level / 100f * 0.8f).coerceIn(0.2f, 1f))
                .clip(CircleShape).background(Color(0xFF16C060))
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            T("$level%", 20, Ink, FontWeight.Bold, Modifier.padding(end = 28.dp))
        }
    }
}

@Composable
fun DndPill(modifier: Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier.clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF6C4FD9), Color(0xFF7D5BEA))))
            .clickable { safeStart(ctx, Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF4A31B5)), Alignment.Center) {
            DndGlyph(Modifier.size(30.dp))
        }
        Spacer(Modifier.weight(1f))
        T("DND", 14, Color.White, FontWeight.Bold)
        Spacer(Modifier.weight(1.2f))
    }
}

@Composable
fun MusicCard(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFF17171B))) {
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(84.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF6B6B70), Color(0xFF2A2A2E))))
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(Config.MUSIC_TITLE, 13, Color.White, FontWeight.Bold)
            T(Config.MUSIC_ARTIST, 10, Color(0xFFA0A0A8))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaIcon(0, Modifier.size(12.dp)); MediaIcon(1, Modifier.size(16.dp)); MediaIcon(2, Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun TimerWidget(modifier: Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.93f))
            .clickable {
                safeStart(
                    ctx,
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, Config.TIMER_MINUTES * 60)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                )
            }
            .padding(start = 22.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("%02d MIN".format(Config.TIMER_MINUTES), 44, Ink, FontWeight.Bold, family = CondensedBold)
        Spacer(Modifier.weight(1f))
        KufiLogo(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
    }
}

@Composable
fun PhotoCountdown(modifier: Modifier, photoVersion: Int, onPickPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val photo = remember(photoVersion) {
        val f = File(ctx.filesDir, MainActivity.PHOTO_FILE)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
    }
    val days = remember { daysLeft() }
    Box(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7C6A5A), Color(0xFF3B302A))))
            .longPress(onPickPhoto)
    ) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))
            )
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) {
            T(Config.EVENT_TITLE, 11, Color.White, FontWeight.SemiBold)
            T("${Config.EVENT_DAY} ${monthName(Config.EVENT_MONTH)}", 11, Color.White, FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            T("$days", 34, Color.White, FontWeight.Bold, family = CondensedBold)
            T("DAYS LEFT", 9, Color.White, FontWeight.SemiBold)
        }
    }
}

private fun daysLeft(): Int {
    val today = LocalDate.now()
    var target = LocalDate.of(today.year, Config.EVENT_MONTH, Config.EVENT_DAY)
    if (target.isBefore(today)) target = target.plusYears(1)
    return ChronoUnit.DAYS.between(today, target).toInt()
}

private fun monthName(m: Int) =
    listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")[m - 1]
