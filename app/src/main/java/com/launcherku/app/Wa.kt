package com.launcherku.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import java.io.File

/** Kontak WhatsApp yang dipilih dari daftar kontak HP. */
object WaContact {
    var name by mutableStateOf("")
    var number by mutableStateOf("")
    var photo by mutableStateOf<ImageBitmap?>(null)

    private const val PHOTO_FILE = "contact_photo.jpg"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("contact", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        name = p.getString("name", "") ?: ""
        number = p.getString("number", "") ?: ""
        val f = File(ctx.filesDir, PHOTO_FILE)
        photo = if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
    }

    fun save(ctx: Context, newName: String, rawNumber: String, bmp: Bitmap?) {
        val n = normalize(rawNumber)
        prefs(ctx).edit().putString("name", newName).putString("number", n).apply()
        val f = File(ctx.filesDir, PHOTO_FILE)
        if (bmp != null) {
            try {
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            } catch (_: Exception) {
            }
        } else f.delete()
        load(ctx)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        File(ctx.filesDir, PHOTO_FILE).delete()
        WaInbox.items = emptyList()
        load(ctx)
    }

    /** Nomor lokal berawalan 0 dianggap Indonesia (62). Selain itu dipakai apa adanya. */
    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.startsWith("0")) "62" + digits.substring(1) else digits
    }
}

class WaMsg(
    val key: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val count: Int,
)

/** Notifikasi WhatsApp yang sedang aktif (diisi oleh WaNotificationService). */
object WaInbox {
    var items by mutableStateOf<List<WaMsg>>(emptyList())

    fun put(m: WaMsg) {
        items = items.filter { it.key != m.key } + m
    }

    fun drop(key: String) {
        items = items.filter { it.key != key }
    }

    fun latestFor(name: String): WaMsg? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return items
            .filter { it.title.trim().equals(n, ignoreCase = true) || it.title.startsWith("$n (", ignoreCase = true) }
            .maxByOrNull { it.time }
    }
}

fun listenerEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

class WaNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        val active = try { activeNotifications } catch (_: Exception) { null }
        WaInbox.items = active?.mapNotNull { toMsg(it) } ?: emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        toMsg(sbn)?.let { WaInbox.put(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        WaInbox.drop(sbn.key)
    }

    private fun toMsg(sbn: StatusBarNotification): WaMsg? {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return null
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras ?: return null
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val inTitle = Regex("\\((\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        @Suppress("DEPRECATION")
        val inExtras = try { ex.getParcelableArray(Notification.EXTRA_MESSAGES)?.size } catch (_: Exception) { null }
        return WaMsg(sbn.key, title, text, sbn.postTime, n.contentIntent, inTitle ?: inExtras ?: 1)
    }
}
