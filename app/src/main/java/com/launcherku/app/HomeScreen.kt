package com.launcherku.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(apps: List<AppEntry>, homeSignal: Int, photoVersion: Int, pickPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { 2 }
    var drawer by remember { mutableStateOf(false) }

    LaunchedEffect(homeSignal) {
        drawer = false
        pager.scrollToPage(0)
    }
    BackHandler { if (drawer) drawer = false }

    val openSearch = {
        val g = "com.google.android.googlequicksearchbox"
        if (ctx.packageManager.getLaunchIntentForPackage(g) != null) launchApp(ctx, g) else drawer = true
    }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF6B7BE0), 0.55f to Color(0xFFA9A8EA), 1f to Color(0xFFEAD9D4)
                )
            )
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(900f, 900f), radius = 700f,
                )
            )
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, d -> total += d },
                    onDragEnd = { if (total < -140f) drawer = true },
                )
            }
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
            ) {
                if (page == 0) PageOne(apps, openSearch) else PageTwo(apps, photoVersion, pickPhoto)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), Alignment.Center) { PageDots(pager.currentPage, 2) }
                if (page == 0) Dock(listOf(Slots.messages, Slots.camera, Slots.gemini, Slots.whatsapp), apps)
                else Dock(listOf(Slots.soundcloud, Slots.snapseed, Slots.youtube, Slots.tiktok), apps)
            }
        }

        AnimatedVisibility(
            visible = drawer,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) { Drawer(apps) { drawer = false } }
    }
}

@Composable
private fun ColumnScope.PageOne(apps: List<AppEntry>, openSearch: () -> Unit) {
    BigClock(Modifier.fillMaxWidth().height(210.dp))
    SearchBar(onSearch = openSearch, onAssistant = openSearch)
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Folder(listOf(Slots.chrome, Slots.photos, Slots.keep, Slots.play), apps, 2, Modifier.fillMaxWidth().aspectRatio(1f))
            AlarmPill(Modifier.fillMaxWidth().height(62.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ContactPill(Modifier.fillMaxWidth().height(54.dp), apps)
            WeatherCard(Modifier.fillMaxWidth().aspectRatio(1f))
        }
    }
}

@Composable
private fun ColumnScope.PageTwo(apps: List<AppEntry>, photoVersion: Int, pickPhoto: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(128.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Folder(listOf(Slots.capcut, Slots.canva), apps, 1, Modifier.width(80.dp).fillMaxHeight())
        PhotoCountdown(Modifier.weight(1f).fillMaxHeight(), photoVersion, pickPhoto)
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Folder(listOf(Slots.axis, Slots.gopay, Slots.calculator, Slots.ovo), apps, 2, Modifier.fillMaxWidth().aspectRatio(1f))
            MusicCard(Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryPill(Modifier.fillMaxWidth().height(52.dp))
            Folder(listOf(Slots.boost, Slots.security, Slots.files, Slots.phone), apps, 2, Modifier.fillMaxWidth().aspectRatio(1f))
            DndPill(Modifier.fillMaxWidth().height(56.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    TimerWidget(Modifier.fillMaxWidth().height(66.dp))
}

@Composable
fun BigClock(modifier: Modifier = Modifier) {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalTime.now()
            delay((60 - time.second) * 1000L)
        }
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Row(Modifier.graphicsLayer { scaleY = 1.4f; transformOrigin = TransformOrigin(0f, 0.5f) }) {
            ClockDigits("%02d".format(time.hour), outlined = true)
            ClockDigits("%02d".format(time.minute), outlined = false)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(apps: List<AppEntry>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val list = remember(apps, query) { apps.filter { it.label.contains(query, ignoreCase = true) } }
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(Color(0xEE1E2146)).statusBarsPadding().padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (query.isEmpty()) T("Cari aplikasi", 16, Color.White.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.pkg }) { app ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .pointerInput(app.pkg) {
                            androidx.compose.foundation.gestures.detectTapGestures(
                                onTap = { launchApp(ctx, app.pkg); onClose() },
                                onLongPress = {
                                    safeStart(
                                        ctx,
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")),
                                    )
                                },
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(app.icon, app.label, Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)))
                    Spacer(Modifier.height(4.dp))
                    T(app.label, 11, Color.White, FontWeight.Normal)
                }
            }
        }
    }
}
