package com.launcherku.app

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Isi bawaan setiap slot ikon. Id slot = prefix grup + nomor urut, misal "g1_0". */
object Defaults {
    private val groups: Map<String, List<Slot>> = mapOf(
        "g1_" to listOf(Slots.chrome, Slots.photos, Slots.keep, Slots.play),
        "g2_" to listOf(Slots.capcut, Slots.canva),
        "g3_" to listOf(Slots.axis, Slots.gopay, Slots.calculator, Slots.ovo),
        "g4_" to listOf(Slots.boost, Slots.security, Slots.files, Slots.phone),
        "d0_" to listOf(Slots.messages, Slots.camera, Slots.gemini, Slots.whatsapp),
        "d1_" to listOf(Slots.soundcloud, Slots.snapseed, Slots.youtube, Slots.tiktok),
    )

    val slots: Map<String, Slot> = groups.flatMap { (prefix, list) ->
        list.mapIndexed { i, s -> "$prefix$i" to s }
    }.toMap()

    fun ids(prefix: String): List<String> = List(groups.getValue(prefix).size) { "$prefix$it" }
}

/** Isi sebuah slot setelah dicocokkan dengan aplikasi yang terpasang. */
sealed interface Tile {
    object Empty : Tile
    class App(val entry: AppEntry) : Tile
    class Missing(val slot: Slot) : Tile
}

/**
 * Menyimpan perubahan tata letak (pindah / ganti / hapus) di SharedPreferences.
 * Nilai tersimpan per slot: "" = kosong, "pkg:xxx" = aplikasi pilihan, "def:id" = isi bawaan slot lain.
 * Slot yang tidak ada di sini memakai isi bawaannya.
 */
class LayoutStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val overrides = mutableStateMapOf<String, String>()

    init {
        for ((k, v) in prefs.all) if (v is String) overrides[k] = v
    }

    private fun fromSlot(ctx: Context, apps: List<AppEntry>, slot: Slot): Tile =
        resolve(ctx, apps, slot)?.let { Tile.App(it) } ?: Tile.Missing(slot)

    fun tileFor(ctx: Context, apps: List<AppEntry>, id: String): Tile {
        val token = overrides[id] ?: "def:$id"
        if (token.isEmpty()) return Tile.Empty
        if (token.startsWith("pkg:")) {
            // daftar aplikasi belum selesai dimuat -> jangan tampilkan slot kosong dulu
            if (apps.isEmpty()) return Tile.Missing(Defaults.slots[id] ?: Slot())
            val pkg = token.substring(4)
            val entry = apps.firstOrNull { it.pkg == pkg }
            return if (entry != null) Tile.App(entry) else Tile.Empty
        }
        val slot = Defaults.slots[token.removePrefix("def:")] ?: return Tile.Empty
        return fromSlot(ctx, apps, slot)
    }

    private fun tokenOf(id: String) = overrides[id] ?: "def:$id"

    private fun set(id: String, token: String) {
        if (token == "def:$id") {
            overrides.remove(id)
            prefs.edit().remove(id).apply()
        } else {
            overrides[id] = token
            prefs.edit().putString(id, token).apply()
        }
    }

    fun swap(a: String, b: String) {
        val ta = tokenOf(a)
        val tb = tokenOf(b)
        set(a, tb)
        set(b, ta)
    }

    fun remove(id: String) = set(id, "")

    fun replace(id: String, pkg: String) = set(id, "pkg:$pkg")

    fun reset() {
        overrides.clear()
        prefs.edit().clear().apply()
    }
}

/** Status sementara saat ikon sedang diseret / menu terbuka. */
class EditState {
    val bounds = HashMap<String, Rect>()
    var trashBounds: Rect = Rect.Zero

    var dragId by mutableStateOf<String?>(null)
    var dragTile by mutableStateOf<Tile?>(null)
    var dragPos by mutableStateOf(Offset.Zero)
    var overId by mutableStateOf<String?>(null)
    var overTrash by mutableStateOf(false)

    /** true selama aplikasi sedang dibuka: layar home membesar & memudar, lalu kembali saat pulang. */
    var opening by mutableStateOf(false)

    var menuId by mutableStateOf<String?>(null)
    var pickId by mutableStateOf<String?>(null)

    var suppressClick = false
    private var moved = 0f

    fun begin(id: String, tile: Tile, pos: Offset) {
        moved = 0f
        dragTile = tile
        dragPos = pos
        overId = null
        overTrash = false
        dragId = id
    }

    fun drag(delta: Offset) {
        dragPos += delta
        moved += delta.getDistance()
        val p = dragPos
        overTrash = trashBounds != Rect.Zero && trashBounds.contains(p)
        overId = if (overTrash) null else {
            bounds.entries.firstOrNull { it.key != dragId && it.value.inflate(20f).contains(p) }?.key
        }
    }

    fun finish(store: LayoutStore) {
        val src = dragId ?: return
        val target = overId
        val trash = overTrash
        val wasMoved = moved > 24f
        cancel()
        when {
            trash -> store.remove(src)
            target != null -> store.swap(src, target)
            !wasMoved -> menuId = src
        }
    }

    fun cancel() {
        dragId = null
        dragTile = null
        overId = null
        overTrash = false
    }
}

val LocalLayout = compositionLocalOf<LayoutStore> { error("LayoutStore belum disediakan") }
val LocalEdit = compositionLocalOf<EditState> { error("EditState belum disediakan") }

/** Status siklus hidup activity, diisi dari MainActivity. */
object AppLifecycle {
    var resumed by mutableStateOf(true)
    var resumeCount by mutableIntStateOf(0)
}
