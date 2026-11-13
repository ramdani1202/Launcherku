package com.launcherku.app

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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

/** Sesi media YouTube yang sedang aktif (judul, channel, gambar, tombol kontrol). */
object MediaState {
    var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
    var controller: MediaController? = null

    fun clear() {
        active = false
        title = ""
        artist = ""
        art = null
        playing = false
        controller = null
    }
}

class GmailMsg(val key: String, val sender: String, val subject: String, val time: Long, val intent: PendingIntent?)

/** Email Gmail yang sedang ada di notifikasi. */
object GmailInbox {
    var items by mutableStateOf<List<GmailMsg>>(emptyList())
    var summaryCount by mutableStateOf(0)
    var account by mutableStateOf("")
    var summaryKey: String? = null

    val count: Int get() = maxOf(items.size, summaryCount)

    fun latest(): GmailMsg? = items.maxByOrNull { it.time }

    fun load(ctx: Context) {
        account = ctx.getSharedPreferences("gmail", Context.MODE_PRIVATE).getString("account", "") ?: ""
    }

    fun setAccount(ctx: Context, a: String) {
        if (a == account) return
        account = a
        ctx.getSharedPreferences("gmail", Context.MODE_PRIVATE).edit().putString("account", a).apply()
    }

    fun reset() {
        items = emptyList()
        summaryCount = 0
        summaryKey = null
    }

    fun drop(key: String) {
        items = items.filter { it.key != key }
        if (key == summaryKey) {
            summaryCount = 0
            summaryKey = null
        }
    }
}

private val YOUTUBE_PKGS = setOf(
    "com.google.android.youtube",
    "com.google.android.apps.youtube.music",
    "app.revanced.android.youtube",
)

class WaNotificationService : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private var tracked: List<MediaController> = emptyList()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateMedia()
        override fun onSessionDestroyed() = updateMedia()
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        bindSessions(list.orEmpty())
    }

    override fun onListenerConnected() {
        refreshNotifications()
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            sessions = msm
            val cn = ComponentName(this, WaNotificationService::class.java)
            msm.addOnActiveSessionsChangedListener(sessionListener, cn)
            bindSessions(msm.getActiveSessions(cn))
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() = unbind()

    override fun onDestroy() {
        unbind()
        super.onDestroy()
    }

    private fun unbind() {
        for (c in tracked) {
            try { c.unregisterCallback(callback) } catch (_: Exception) { }
        }
        tracked = emptyList()
        try { sessions?.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) { }
        MediaState.clear()
    }

    private fun bindSessions(list: List<MediaController>) {
        for (c in tracked) {
            try { c.unregisterCallback(callback) } catch (_: Exception) { }
        }
        tracked = list.filter { it.packageName in YOUTUBE_PKGS }
        for (c in tracked) c.registerCallback(callback)
        updateMedia()
    }

    private fun updateMedia() {
        val c = tracked.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: tracked.firstOrNull()
        if (c == null) {
            MediaState.clear()
            return
        }
        val md = c.metadata
        MediaState.controller = c
        MediaState.title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        MediaState.artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        MediaState.art = bmp?.asImageBitmap()
        MediaState.playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        MediaState.active = true
    }

    private fun refreshNotifications() {
        val active = try { activeNotifications } catch (_: Exception) { null }
        WaInbox.items = active?.mapNotNull { toWa(it) } ?: emptyList()
        GmailInbox.reset()
        active?.forEach { handleGmail(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        toWa(sbn)?.let { WaInbox.put(it) }
        handleGmail(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        WaInbox.drop(sbn.key)
        GmailInbox.drop(sbn.key)
    }

    private fun handleGmail(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.google.android.gm") return
        val n = sbn.notification
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        if (sub.contains("@")) GmailInbox.setAccount(applicationContext, sub)
        else if (text.contains("@") && !text.contains(" ")) GmailInbox.setAccount(applicationContext, text)
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            GmailInbox.summaryKey = sbn.key
            GmailInbox.summaryCount = Regex("\\d+").find(title)?.value?.toIntOrNull()
                ?: Regex("\\d+").find(text)?.value?.toIntOrNull() ?: 0
        } else if (title.isNotBlank()) {
            val m = GmailMsg(sbn.key, title, text, sbn.postTime, n.contentIntent)
            GmailInbox.items = GmailInbox.items.filter { it.key != m.key } + m
        }
    }

    private fun toWa(sbn: StatusBarNotification): WaMsg? {
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
