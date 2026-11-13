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
import java.time.ZoneId
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

private const val YT_PKG = "com.google.android.youtube"

/** Pemutar YouTube: judul, channel, gambar, dan tombol kontrol dibaca langsung dari sesi media YouTube. */
@Composable
fun MusicCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(listenerEnabled(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            listening = listenerEnabled(ctx)
            delay(2000)
        }
    }
    val active = MediaState.active
    val art = MediaState.art
    val title = if (active && MediaState.title.isNotBlank()) MediaState.title else "YouTube"
    val subtitle = when {
        active -> MediaState.artist
        listening -> "Tidak ada yang diputar"
        else -> "Aktifkan akses notifikasi"
    }
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFF17171B)).clickable {
            val pi = MediaState.controller?.sessionActivity
            when {
                !listening -> safeStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                pi != null -> try { pi.send() } catch (_: Exception) { launchApp(ctx, YT_PKG) }
                else -> launchApp(ctx, YT_PKG)
            }
        }
    ) {
        if (art != null) {
            Image(
                art, null,
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(84.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(84.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6B6B70), Color(0xFF2A2A2E))))
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(title, 13, Color.White, FontWeight.Bold)
            T(subtitle, 10, Color(0xFFA0A0A8))
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(CircleShape).clickable { MediaState.controller?.transportControls?.skipToPrevious() }
                        .padding(10.dp)
                ) { MediaIcon(0, Modifier.size(12.dp)) }
                Box(
                    Modifier.clip(CircleShape).clickable {
                        val t = MediaState.controller?.transportControls
                        if (MediaState.playing) t?.pause() else t?.play()
                    }.padding(8.dp)
                ) { MediaIcon(if (MediaState.playing) 3 else 1, Modifier.size(16.dp)) }
                Box(
                    Modifier.clip(CircleShape).clickable { MediaState.controller?.transportControls?.skipToNext() }
                        .padding(10.dp)
                ) { MediaIcon(2, Modifier.size(12.dp)) }
            }
        }
    }
}

/** Email Gmail terbaru + akun + jumlah, dibaca dari notifikasi Gmail. */
@Composable
fun GmailWidget(modifier: Modifier) {
    val ctx = LocalContext.current
    var listening by remember { mutableStateOf(listenerEnabled(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            listening = listenerEnabled(ctx)
            delay(2000)
        }
    }
    val latest = GmailInbox.latest()
    val count = GmailInbox.count
    val account = GmailInbox.account
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.93f))
            .clickable {
                val pi = latest?.intent
                when {
                    !listening -> safeStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    pi != null -> try { pi.send() } catch (_: Exception) { launchApp(ctx, "com.google.android.gm") }
                    else -> launchApp(ctx, "com.google.android.gm")
                }
            }
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            T(if (account.isNotBlank()) account else "Gmail", 11, Color(0xFF5B5B70), FontWeight.SemiBold)
            if (latest != null) {
                T(latest.sender, 16, Ink, FontWeight.Bold)
                T(latest.subject, 12, Color(0xFF3A3A4A), FontWeight.Normal)
            } else if (!listening) {
                T("Aktifkan akses notifikasi", 14, Ink, FontWeight.Bold)
            } else {
                T("Tidak ada email baru", 15, Ink, FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEA4335)),
            contentAlignment = Alignment.Center,
        ) {
            T(if (count > 0) "$count" else "M", 30, Color.White, FontWeight.Bold, family = CondensedBold)
        }
    }
}

/** Hari, tanggal, dan tahun realtime. Tahan lama untuk mengganti foto latar. */
@Composable
fun DateCard(modifier: Modifier, photoVersion: Int, onPickPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val photo = remember(photoVersion) {
        val f = File(ctx.filesDir, MainActivity.PHOTO_FILE)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
    }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(AppLifecycle.resumeCount) { today = LocalDate.now() }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            val next = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            delay(maxOf(next - System.currentTimeMillis(), 1000L) + 500L)
        }
    }
    val id = Locale.forLanguageTag("id-ID")
    val dayName = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, id)
    val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, id)
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
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
            T(dayName, 14, Color.White, FontWeight.SemiBold)
            T("${today.dayOfMonth}", 56, Color.White, FontWeight.Bold, family = CondensedBold)
            T("$monthName ${today.year}", 13, Color.White, FontWeight.SemiBold)
        }
    }
}
