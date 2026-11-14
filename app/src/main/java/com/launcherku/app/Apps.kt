package com.launcherku.app

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

/**
 * Satu "slot" ikon di home. Dicari lewat daftar nama paket, lalu lewat intent
 * (kamera, telepon, kalkulator). Kalau tidak ketemu, tampil kotak warna pengganti.
 */
data class Slot(
    val pkgs: List<String> = emptyList(),
    val fallbackColor: Color = Color(0xFF8E8EA8),
    val fallbackText: String = "?",
    val action: String? = null,
    val category: String? = null,
)

/** Daftar aplikasi disimpan di memori supaya home langsung penuh saat dibuka lagi. */
object AppCache {
    var apps by mutableStateOf(emptyList<AppEntry>())
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                icon = it.loadIcon(pm).toBitmap(120, 120).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun resolve(ctx: Context, apps: List<AppEntry>, slot: Slot): AppEntry? {
    for (p in slot.pkgs) apps.firstOrNull { it.pkg == p }?.let { return it }
    if (slot.action != null) {
        val i = Intent(slot.action)
        slot.category?.let { i.addCategory(it) }
        val pkg = ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName
        if (pkg != null) apps.firstOrNull { it.pkg == pkg }?.let { return it }
    }
    return null
}

fun launchApp(ctx: Context, pkg: String) {
    try {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it)
        }
    } catch (_: Exception) {
    }
}

/**
 * Buka aplikasi dengan animasi zoom dari posisi ikon (seperti iPhone).
 * [onLaunched] dipanggil kalau aplikasi benar-benar berhasil dijalankan.
 */
fun launchAppAnimated(ctx: Context, pkg: String, view: View?, from: Rect?, onLaunched: () -> Unit = {}) {
    try {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = if (view != null && from != null) {
            ActivityOptions.makeScaleUpAnimation(
                view, from.left.toInt(), from.top.toInt(), from.width.toInt(), from.height.toInt(),
            ).toBundle()
        } else null
        ctx.startActivity(intent, options)
        onLaunched()
    } catch (_: Exception) {
    }
}

fun safeStart(ctx: Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (_: Exception) {
    }
}

object Slots {
    val chrome = Slot(listOf("com.android.chrome"), Color(0xFFEA4335), "C")
    val photos = Slot(listOf("com.google.android.apps.photos"), Color(0xFFFBBC05), "P")
    val keep = Slot(listOf("com.google.android.keep"), Color(0xFFFFBB00), "K")
    val play = Slot(listOf("com.android.vending"), Color(0xFF01875F), "▶")

    val messages = Slot(listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"), Color(0xFF3B82F6), "M")
    val camera = Slot(
        listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.oplus.camera", "com.oppo.camera", "com.vivo.camera", "com.mi.android.camera"),
        Color(0xFF334155), "◉", action = "android.media.action.IMAGE_CAPTURE",
    )
    val gemini = Slot(listOf("com.google.android.apps.bard"), Color(0xFF4285F4), "✦")
    val whatsapp = Slot(listOf("com.whatsapp", "com.whatsapp.w4b"), Color(0xFF25D366), "W")

    val capcut = Slot(listOf("com.lemon.lvoverseas", "com.lemon.lvoverseas.lite"), Color(0xFF111111), "C")
    val canva = Slot(listOf("com.canva.editor"), Color(0xFF7D2AE8), "Canva")
    val axis = Slot(listOf("com.axis.net", "com.axis.axisnet"), Color(0xFF6A1B9A), "AXIS")
    val gopay = Slot(listOf("com.gojek.app", "com.gojek.gopay"), Color(0xFF00AED6), "gopay")
    val calculator = Slot(
        listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator", "com.miui.calculator"),
        Color(0xFF5F6B7A), "±", action = Intent.ACTION_MAIN, category = "android.intent.category.APP_CALCULATOR",
    )
    val ovo = Slot(listOf("ovo.id"), Color(0xFF4C2A86), "OVO")

    val boost = Slot(listOf("com.miui.powerkeeper", "com.coloros.oppoguardelf", "com.samsung.android.lool", "com.vivo.abe"), Color(0xFF34C28A), "⚡")
    val security = Slot(listOf("com.miui.securitycenter", "com.coloros.safecenter", "com.iqoo.secure", "com.google.android.apps.security.securityhub"), Color(0xFF1FB36B), "✓")
    val files = Slot(
        listOf("com.google.android.apps.nbu.files", "com.mi.android.globalFileexplorer", "com.sec.android.app.myfiles", "com.google.android.documentsui", "com.android.documentsui"),
        Color(0xFFF5A623), "▤",
    )
    val phone = Slot(
        listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
        Color(0xFF22C55E), "✆", action = Intent.ACTION_DIAL,
    )

    val soundcloud = Slot(listOf("com.soundcloud.android"), Color(0xFF222222), "☁")
    val snapseed = Slot(listOf("com.niksoftware.snapseed"), Color(0xFF2E9E4F), "S")
    val youtube = Slot(listOf("com.google.android.youtube"), Color(0xFFFF0000), "▶")
    val tiktok = Slot(listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), Color(0xFF111111), "♪")
}
