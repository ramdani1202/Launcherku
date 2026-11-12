package com.launcherku.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
private fun TileFace(tile: Tile) {
    when (tile) {
        is Tile.App -> Image(tile.entry.icon, tile.entry.label, Modifier.fillMaxSize())
        is Tile.Missing -> Box(Modifier.fillMaxSize().background(tile.slot.fallbackColor), Alignment.Center) {
            T(tile.slot.fallbackText, if (tile.slot.fallbackText.length > 2) 11 else 20, weight = FontWeight.Bold)
        }
        Tile.Empty -> Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)), Alignment.Center) {
            T("+", 22, Color.White.copy(alpha = 0.75f), FontWeight.Light)
        }
    }
}

/**
 * Ikon yang bisa diedit:
 * - ketuk = buka aplikasi (slot kosong = pilih aplikasi)
 * - tahan lalu geser ke ikon lain = tukar posisi, geser ke "Hapus" = hapus
 * - tahan lalu lepas tanpa geser = menu Ganti / Hapus
 */
@Composable
fun EditableTile(id: String, apps: List<AppEntry>, size: Dp = 52.dp) {
    val ctx = LocalContext.current
    val store = LocalLayout.current
    val edit = LocalEdit.current
    val currentApps by rememberUpdatedState(apps)
    val tile = store.tileFor(ctx, apps, id)
    val shape = RoundedCornerShape(size * 0.27f)
    val hovered = edit.dragId != null && edit.overId == id
    val view = LocalView.current
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && edit.dragId == null) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val hoverScale by animateFloatAsState(
        targetValue = if (hovered) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hover",
    )

    DisposableEffect(id) { onDispose { edit.bounds.remove(id) } }

    Box(
        Modifier
            .size(size)
            .onGloballyPositioned { edit.bounds[id] = it.boundsInRoot() }
            .graphicsLayer {
                alpha = if (edit.dragId == id) 0.25f else 1f
                val s = pressScale * hoverScale
                scaleX = s
                scaleY = s
            }
            // reset penanda "jangan buka aplikasi" setiap ada sentuhan baru
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    edit.suppressClick = false
                }
            }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        val t = store.tileFor(ctx, currentApps, id)
                        if (t !is Tile.Empty) {
                            edit.suppressClick = true
                            edit.begin(id, t, (edit.bounds[id]?.topLeft ?: Offset.Zero) + local)
                        }
                    },
                    onDrag = { change, amount ->
                        if (edit.dragId == id) {
                            change.consume()
                            edit.drag(amount)
                        }
                    },
                    onDragEnd = { if (edit.dragId == id) edit.finish(store) },
                    onDragCancel = { if (edit.dragId == id) edit.cancel() },
                )
            }
            .clip(shape)
            .clickable(interactionSource = pressSource, indication = null) {
                if (edit.suppressClick) {
                    edit.suppressClick = false
                } else {
                    val t = store.tileFor(ctx, currentApps, id)
                    if (t is Tile.App) {
                        launchAppAnimated(ctx, t.entry.pkg, view, edit.bounds[id]) { edit.opening = true }
                    } else {
                        edit.pickId = id
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TileFace(tile)
    }
}

/** Zona "Hapus" yang muncul di atas layar saat ikon diseret. */
@Composable
fun BoxScope.TrashZone() {
    val edit = LocalEdit.current
    if (edit.dragId == null) return
    Box(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp)
            .onGloballyPositioned { edit.trashBounds = it.boundsInRoot() }
            .clip(CircleShape)
            .background(if (edit.overTrash) Color(0xFFE5484D) else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        T("✕  Hapus", 15, Color.White, FontWeight.Bold)
    }
}

/** Salinan ikon yang mengikuti jari saat diseret. */
@Composable
fun DragGhost() {
    val edit = LocalEdit.current
    val tile = edit.dragTile ?: return
    if (edit.dragId == null) return
    val half = with(LocalDensity.current) { 30.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((edit.dragPos.x - half).roundToInt(), (edit.dragPos.y - half).roundToInt()) }
            .size(60.dp)
            .graphicsLayer { scaleX = 1.1f; scaleY = 1.1f }
            .clip(RoundedCornerShape(16.dp))
    ) {
        TileFace(tile)
    }
}

/** Kartu menu sederhana di tengah layar. */
@Composable
fun ActionMenu(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    BackHandler(onBack = onDismiss)
    val noRipple = remember { MutableInteractionSource() }
    val noRipple2 = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFFF4F3FB))
                .clickable(interactionSource = noRipple2, indication = null) { }
                .padding(18.dp)
        ) {
            T(title, 17, Ink, FontWeight.Bold, maxLines = 2)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                T(hint, 12, Color(0xFF5B5B70), FontWeight.Normal, maxLines = 3)
            }
            Spacer(Modifier.height(10.dp))
            actions.forEach { (label, run) ->
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xFFE4E1F7))
                        .clickable { run() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    T(label, 15, Ink, FontWeight.SemiBold)
                }
            }
        }
    }
}
