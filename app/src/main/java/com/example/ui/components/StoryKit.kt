package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Instagram-style segments: done ones full, the current one filled to [progress]. */
@Composable
fun StoryBars(count: Int, index: Int, progress: Float, modifier: Modifier = Modifier, dark: Boolean = false) {
    val track = if (dark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.10f)
    val fill = if (dark) Color.White else Color.Black
    Row(modifier.fillMaxWidth().testTag("story_bars"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val f = when {
                i < index -> 1f
                i == index -> progress.coerceIn(0f, 1f)
                else -> 0f
            }
            Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(fill))
            }
        }
    }
}

/** The looping picture at the top of each intro and onboarding page; each page has its own. */
enum class StoryArt { DONORS, REQUEST, MAP, SOS, NAME, BLOOD, PHONE, CODE }

@Composable
fun StoryHero(art: StoryArt, modifier: Modifier = Modifier, height: Dp = 250.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(RokkhonPalette.Subtle)
            .testTag("story_hero_${art.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        when (art) {
            StoryArt.DONORS -> DonorsRadar()
            StoryArt.REQUEST -> FillingDrop()
            StoryArt.MAP -> DroppingPin()
            StoryArt.SOS -> SosPulse()
            StoryArt.NAME -> TypingCard()
            StoryArt.BLOOD -> HeartbeatDrop()
            StoryArt.PHONE -> PhoneText()
            StoryArt.CODE -> CodeBoxes()
        }
    }
}

@Composable
private fun loop(ms: Int, label: String): Float {
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = label)
    return v
}

/** Ripples from the centre; donors orbit and light up as a ripple passes them. */
@Composable
private fun DonorsRadar() {
    val t = loop(2_400, "radar")
    val spin = loop(16_000, "orbit")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            repeat(3) { k ->
                val p = (t + k / 3f) % 1f
                drawCircle(Color.Black.copy(alpha = 0.16f * (1f - p)), radius = 40.dp.toPx() + p * 90.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
            }
            val r = 88.dp.toPx()
            repeat(6) { i ->
                val a = (spin * 2 * PI + i * PI / 3).toFloat()
                val pos = Offset(c.x + r * cos(a), c.y + r * 0.62f * sin(a))
                val near = ((t * 3f) % 1f).let { 1f - kotlin.math.abs(it - (i % 3) / 3f) * 3f }.coerceIn(0f, 1f)
                drawCircle(Color.White, radius = 13.dp.toPx(), center = pos)
                drawCircle(if (near > 0.5f) Color.Black else Color(0xFFBDBDBD), radius = (7 + 2 * near).dp.toPx(), center = pos)
            }
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            Icon(UIcons.People, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

private fun dropPath(size: Size): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w / 2f, 0f)
    cubicTo(w * 0.15f, h * 0.38f, 0f, h * 0.55f, 0f, h * 0.68f)
    cubicTo(0f, h * 0.88f, w * 0.22f, h, w / 2f, h)
    cubicTo(w * 0.78f, h, w, h * 0.88f, w, h * 0.68f)
    cubicTo(w, h * 0.55f, w * 0.85f, h * 0.38f, w / 2f, 0f)
    close()
}

/** A drop fills up with a moving wave while little "+" bubbles rise. */
@Composable
private fun FillingDrop() {
    val fill = loop(3_200, "fill")
    val wave = loop(1_400, "wave")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(110.dp, 140.dp)) {
            val drop = dropPath(size)
            clipPath(drop) {
                drawRect(Color.White)
                val level = size.height * (1f - (0.15f + 0.7f * fill))
                val path = Path().apply {
                    moveTo(0f, level)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, level + sin((x / size.width * 2 * PI + wave * 2 * PI)).toFloat() * 5.dp.toPx())
                        x += 4f
                    }
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(path, RokkhonPalette.Sos)
            }
            drawPath(drop, Color.Black, style = Stroke(3.dp.toPx()))
        }
        repeat(3) { i ->
            val p = (fill + i / 3f) % 1f
            Text(
                "+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                modifier = Modifier
                    .offset(x = (-70 + i * 70).dp, y = (60 - p * 120).dp)
                    .graphicsLayer { alpha = 1f - p }
            )
        }
    }
}

/** Streets in the background; a pin drops, squashes and sends a ripple. */
@Composable
private fun DroppingPin() {
    val t = loop(2_400, "pin")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.Black.copy(alpha = 0.07f)
            val step = 34.dp.toPx()
            var x = -step / 2
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x + size.height * 0.3f, size.height), 6.dp.toPx()); x += step * 2 }
            var y = step / 2
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y - 20f), 6.dp.toPx()); y += step * 2 }
            val land = 0.45f
            if (t > land) {
                val p = (t - land) / (1 - land)
                drawCircle(Color.Black.copy(alpha = 0.25f * (1 - p)), radius = 10.dp.toPx() + p * 70.dp.toPx(), center = Offset(center.x, center.y + 34.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            val shadow = if (t < land) 0.4f + 0.6f * (t / land) else 1f
            drawOval(Color.Black.copy(alpha = 0.15f), topLeft = Offset(center.x - 16.dp.toPx() * shadow, center.y + 30.dp.toPx()), size = Size(32.dp.toPx() * shadow, 8.dp.toPx()))
        }
        val fall = if (t < 0.45f) { val p = t / 0.45f; -120f * (1 - p * p) } else 0f
        val squash = if (t in 0.45f..0.6f) 1f - 0.18f * sin(((t - 0.45f) / 0.15f) * PI).toFloat() else 1f
        Icon(
            UIcons.LocationOn, null, tint = Color.Black,
            modifier = Modifier.size(56.dp).offset(y = fall.dp).graphicsLayer { scaleY = squash; scaleX = 2f - squash; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
        )
    }
}

/** The SOS button breathing, with red rings going out. */
@Composable
private fun SosPulse() {
    val t = loop(1_800, "sos")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(2) { k ->
                val p = (t + k / 2f) % 1f
                drawCircle(RokkhonPalette.Sos.copy(alpha = 0.35f * (1 - p)), radius = 46.dp.toPx() + p * 70.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
            }
        }
        val s = 1f + 0.05f * sin(t * 2 * PI).toFloat()
        Box(Modifier.size(92.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            Text("SOS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A profile card whose name line types itself out, with a blinking cursor. */
@Composable
private fun TypingCard() {
    val t = loop(3_000, "typing")
    Row(
        Modifier.width(240.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            Icon(UIcons.Person, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val typed = (t / 0.7f).coerceAtMost(1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(110.dp * typed).height(12.dp).clip(CircleShape).background(Color.Black))
                Box(Modifier.padding(start = 3.dp).width(2.dp).height(18.dp).graphicsLayer { alpha = if ((t * 6).toInt() % 2 == 0) 1f else 0f }.background(Color.Black))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(70.dp).height(9.dp).clip(CircleShape).background(Color(0xFFE2E2E2)))
        }
    }
}

/** A heartbeat line runs across; the drop beats with each spike. */
@Composable
private fun HeartbeatDrop() {
    val t = loop(1_600, "beat")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2
            val period = size.width / 2
            val shift = t * period
            val path = Path()
            var x = -period
            var first = true
            while (x <= size.width + period) {
                // One beat per period: flat, a small dip, a tall spike, back to flat.
                val local = ((x + shift) % period + period) % period / period
                val y = when {
                    local in 0.40f..0.45f -> mid + (local - 0.40f) / 0.05f * 12.dp.toPx()
                    local in 0.45f..0.52f -> mid + 12.dp.toPx() - (local - 0.45f) / 0.07f * 70.dp.toPx()
                    local in 0.52f..0.58f -> mid - 58.dp.toPx() + (local - 0.52f) / 0.06f * 70.dp.toPx()
                    local in 0.58f..0.62f -> mid + 12.dp.toPx() - (local - 0.58f) / 0.04f * 12.dp.toPx()
                    else -> mid
                }
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 3f
            }
            drawPath(path, Color.Black.copy(alpha = 0.18f), style = Stroke(3.dp.toPx()))
        }
        val beat = 1f + 0.12f * kotlin.math.max(0f, sin(((t * 2f) % 1f) * PI).toFloat().let { if ((t * 2f) % 1f < 0.3f) it else 0f })
        Box(Modifier.size(84.dp).graphicsLayer { scaleX = beat; scaleY = beat }.clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            Icon(UIcons.Bloodtype, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
    }
}

/** A phone; a message slides in and gets a tick. */
@Composable
private fun PhoneText() {
    val t = loop(3_000, "phone")
    Box(Modifier.size(120.dp, 196.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black).padding(7.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.White)) {
            val inP = (t / 0.3f).coerceIn(0f, 1f)
            val out = if (t > 0.85f) (t - 0.85f) / 0.15f else 0f
            Row(
                Modifier
                    .padding(10.dp)
                    .offset(y = (-40 * (1 - inP)).dp + (90 * out).dp)
                    .graphicsLayer { alpha = inP * (1 - out) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(RokkhonPalette.Subtle)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("123 456", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.width(6.dp))
                val tick = ((t - 0.4f) / 0.15f).coerceIn(0f, 1f)
                Icon(UIcons.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = tick; scaleY = tick })
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(34.dp, 4.dp).clip(CircleShape).background(Color(0xFFDDDDDD)))
        }
    }
}

/** Six boxes fill one by one, then turn black together. */
@Composable
private fun CodeBoxes() {
    val t = loop(3_200, "code")
    val filled = (t / 0.75f * 6).toInt().coerceAtMost(6)
    val done = t > 0.8f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            Box(
                Modifier.size(38.dp, 50.dp).clip(RoundedCornerShape(12.dp)).background(if (done) Color.Black else Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (i < filled) Box(Modifier.size(10.dp).clip(CircleShape).background(if (done) Color.White else Color.Black))
                else if (i == filled) Box(Modifier.width(2.dp).height(20.dp).graphicsLayer { alpha = if ((t * 8).toInt() % 2 == 0) 1f else 0f }.background(Color.Black))
            }
        }
    }
}
