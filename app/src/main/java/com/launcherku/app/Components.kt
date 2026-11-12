package com.launcherku.app

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Glass = Color.White.copy(alpha = 0.55f)
val Ink = Color(0xFF14142B)
val CondensedBold = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

@Composable
fun T(
    text: String,
    size: Int,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
    family: FontFamily? = null,
    maxLines: Int = 1,
) = BasicText(
    text = text,
    modifier = modifier,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = family),
)

@Composable
fun AppTile(slot: Slot, apps: List<AppEntry>, size: Dp = 52.dp, tappable: Boolean = true) {
    val ctx = LocalContext.current
    val entry = remember(apps, slot) { resolve(ctx, apps, slot) }
    val shape = RoundedCornerShape(size * 0.27f)
    if (entry != null) {
        Image(
            bitmap = entry.icon,
            contentDescription = entry.label,
            modifier = Modifier.size(size).clip(shape).let {
                if (tappable) it.clickable { launchApp(ctx, entry.pkg) } else it
            },
        )
    } else {
        Box(Modifier.size(size).clip(shape).background(slot.fallbackColor), Alignment.Center) {
            T(slot.fallbackText, if (slot.fallbackText.length > 2) 11 else 20, weight = FontWeight.Bold)
        }
    }
}

@Composable
fun Folder(prefix: String, apps: List<AppEntry>, columns: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Glass).padding(10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Defaults.ids(prefix).chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { row.forEach { EditableTile(it, apps) } }
        }
    }
}

@Composable
fun Dock(prefix: String, apps: List<AppEntry>) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(30.dp)).background(Glass),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) { Defaults.ids(prefix).forEach { EditableTile(it, apps, 54.dp) } }
}

@Composable
fun PageDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) {
            Box(
                Modifier.size(if (it == current) 8.dp else 6.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (it == current) 0.95f else 0.5f))
            )
        }
    }
}

// ---------- jam besar ----------
@Composable
fun ClockDigits(text: String, outlined: Boolean) {
    val base = TextStyle(
        fontFamily = CondensedBold, fontSize = 150.sp, letterSpacing = (-5).sp, lineHeight = 130.sp,
    )
    if (outlined) {
        Box {
            BasicText(text, style = base.copy(color = Color.White.copy(alpha = 0.14f)))
            BasicText(
                text,
                style = base.copy(
                    color = Color.White,
                    drawStyle = Stroke(width = 5f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                ),
            )
        }
    } else {
        BasicText(text, style = base.copy(color = Color.White))
    }
}

// ---------- ikon kecil yang digambar sendiri ----------
@Composable
fun Sparkle(modifier: Modifier, color: Color = Color.White) = Canvas(modifier) {
    val w = size.width; val h = size.height; val cx = w / 2; val cy = h / 2
    val p = Path().apply {
        moveTo(cx, 0f); quadraticBezierTo(cx, cy, w, cy)
        quadraticBezierTo(cx, cy, cx, h); quadraticBezierTo(cx, cy, 0f, cy)
        quadraticBezierTo(cx, cy, cx, 0f); close()
    }
    drawPath(p, color)
}

@Composable
fun GoogleDots(modifier: Modifier) = Canvas(modifier) {
    val r = size.width * 0.17f; val c = size.width / 2; val d = size.width * 0.27f
    drawCircle(Color(0xFF4285F4), r, Offset(c, c - d))
    drawCircle(Color(0xFFEA4335), r, Offset(c - d, c))
    drawCircle(Color(0xFFFBBC05), r, Offset(c + d, c))
    drawCircle(Color(0xFF34A853), r, Offset(c, c + d))
}

@Composable
fun AlarmGlyph(modifier: Modifier) = Canvas(modifier) {
    val c = Offset(size.width / 2, size.height / 2); val r = size.width * 0.28f
    drawCircle(Color.White, r, c, style = Stroke(width = size.width * 0.07f))
    drawLine(Color.White, c, Offset(c.x, c.y - r * 0.6f), strokeWidth = size.width * 0.07f)
    drawLine(Color.White, c, Offset(c.x + r * 0.45f, c.y + r * 0.2f), strokeWidth = size.width * 0.07f)
}

@Composable
fun DndGlyph(modifier: Modifier) = Canvas(modifier) {
    val c = Offset(size.width / 2, size.height / 2); val r = size.width * 0.3f
    drawCircle(Color.White, r, c, style = Stroke(width = size.width * 0.07f))
    drawLine(Color.White, Offset(c.x - r * 0.5f, c.y), Offset(c.x + r * 0.5f, c.y), strokeWidth = size.width * 0.07f)
}

@Composable
fun MediaIcon(kind: Int, modifier: Modifier = Modifier.size(15.dp)) = Canvas(modifier) {
    val w = size.width; val h = size.height
    when (kind) {
        1 -> drawPath(Path().apply { moveTo(w * .15f, 0f); lineTo(w, h / 2); lineTo(w * .15f, h); close() }, Color.White)
        0 -> {
            drawRect(Color.White, Offset(0f, 0f), Size(w * .15f, h))
            drawPath(Path().apply { moveTo(w, 0f); lineTo(w * .25f, h / 2); lineTo(w, h); close() }, Color.White)
        }
        else -> {
            drawRect(Color.White, Offset(w * .85f, 0f), Size(w * .15f, h))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * .75f, h / 2); lineTo(0f, h); close() }, Color.White)
        }
    }
}

@Composable
fun CloudMoon(modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val moon = Path().apply { addOval(Rect(Offset(w * .42f, 0f), Size(w * .46f, w * .46f))) }
    val cut = Path().apply { addOval(Rect(Offset(w * .58f, -w * .04f), Size(w * .42f, w * .42f))) }
    drawPath(Path.combine(PathOperation.Difference, moon, cut), Color(0xFFFFC83D))
    val cloud = Brush.verticalGradient(listOf(Color(0xFFF2F7FF), Color(0xFFB9D0FF)))
    drawCircle(cloud, w * .20f, Offset(w * .34f, h * .66f))
    drawCircle(cloud, w * .27f, Offset(w * .60f, h * .58f))
    drawRoundRect(cloud, Offset(w * .08f, h * .64f), Size(w * .84f, h * .32f), CornerRadius(h * .16f))
}

@Composable
fun KufiLogo(modifier: Modifier) = Canvas(modifier) {
    val n = 6; val cell = size.width / n
    val pattern = listOf(
        "110111", "101001", "111011", "100101", "110111", "011010",
    )
    drawRect(Color.White)
    pattern.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch ->
            if (ch == '1') drawRect(Color(0xFF1B2A6B), Offset(x * cell, y * cell), Size(cell * .86f, cell * .86f))
        }
    }
}

fun Modifier.longPress(onLong: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures(onLongPress = { onLong() }) }
