package com.launcherku.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private var homeSignal by mutableIntStateOf(0)
    private var photoVersion by mutableIntStateOf(0)

    private var pickTarget = PHOTO_FILE

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (savePhoto(uri, pickTarget)) photoVersion++
            else Toast.makeText(this, "Foto gagal dimuat", Toast.LENGTH_SHORT).show()
        }
    }

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == RESULT_OK && uri != null) saveContact(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WaContact.load(this)
        GmailInbox.load(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current
            val apps = AppCache.apps
            var reload by remember { mutableIntStateOf(0) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        reload++
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    ctx, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
                )
                onDispose { ctx.unregisterReceiver(receiver) }
            }

            LaunchedEffect(reload) {
                // muat hanya kalau belum ada di memori atau ada aplikasi baru/dihapus
                if (reload > 0 || AppCache.apps.isEmpty()) {
                    AppCache.apps = withContext(Dispatchers.IO) { loadApps(ctx) }
                }
            }

            HomeScreen(
                apps = apps,
                homeSignal = homeSignal,
                photoVersion = photoVersion,
                pickPhoto = { target ->
                    pickTarget = target
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                pickContact = {
                    try {
                        contactPicker.launch(
                            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                        )
                    } catch (_: Exception) {
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AppLifecycle.resumed = true
        AppLifecycle.resumeCount++
    }

    override fun onPause() {
        super.onPause()
        AppLifecycle.resumed = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal++
    }

    private fun saveContact(uri: Uri) {
        try {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                "photo_uri",
            )
            contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0).orEmpty()
                    val number = c.getString(1).orEmpty()
                    val photoUri = c.getString(2)
                    var bmp: Bitmap? = null
                    if (photoUri != null) {
                        try {
                            bmp = contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        } catch (_: Exception) {
                        }
                    }
                    val b = bmp
                    val small = if (b != null && maxOf(b.width, b.height) > 256) {
                        val k = 256f / maxOf(b.width, b.height)
                        Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
                    } else b
                    WaContact.save(this, name, number, small)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun savePhoto(uri: Uri, name: String): Boolean {
        var bmp: Bitmap? = try {
            val src = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val sample = max(1, max(info.size.width, info.size.height) / 900)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }
        if (bmp == null) {
            bmp = try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (_: Exception) {
                null
            }
        }
        val out = bmp ?: return false
        return try {
            File(filesDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val PHOTO_FILE = "widget_photo.jpg"
        const val MUSIC_FILE = "music_photo.jpg"
    }
}
