package com.launcherku.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
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

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            savePhoto(uri)
            photoVersion++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val ctx = LocalContext.current
            var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
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
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
                androidx.core.content.ContextCompat.registerReceiver(
                    ctx, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
                )
                onDispose { ctx.unregisterReceiver(receiver) }
            }

            LaunchedEffect(reload) {
                apps = withContext(Dispatchers.IO) { loadApps(ctx) }
            }

            HomeScreen(
                apps = apps,
                homeSignal = homeSignal,
                photoVersion = photoVersion,
                pickPhoto = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal++
    }

    private fun savePhoto(uri: Uri) {
        try {
            val src = ImageDecoder.createSource(contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val sample = max(1, max(info.size.width, info.size.height) / 900)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            File(filesDir, PHOTO_FILE).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PHOTO_FILE = "widget_photo.jpg"
    }
}
