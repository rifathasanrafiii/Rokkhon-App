package com.example.ui.sos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.RoundIconButton
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.screens.bnDigits
import com.example.ui.theme.RokkhonPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun t(bn: Boolean, bangla: String, english: String) = if (bn) bangla else english

/** "এইমাত্র", "১২ সেকেন্ড আগে", "৩ মিনিট আগে". */
internal fun ago(bn: Boolean, thenMs: Long, nowMs: Long): String {
    val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> t(bn, "এইমাত্র", "just now")
        s < 60 -> t(bn, "${bnDigits(s.toInt())} সেকেন্ড আগে", "${s}s ago")
        s < 3600 -> t(bn, "${bnDigits((s / 60).toInt())} মিনিট আগে", "${s / 60} min ago")
        else -> t(bn, "${bnDigits((s / 3600).toInt())} ঘণ্টা আগে", "${s / 3600} h ago")
    }
}

/** 0:42, 12:05, 1:02:33 */
internal fun clock(ms: Long, bn: Boolean): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val text = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return if (bn) text.map { if (it.isDigit()) '০' + (it - '0') else it }.joinToString("") else text
}

/** A clock that ticks every second while shown. */
@Composable
internal fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

@Composable
internal fun SosTopBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(icon = UIcons.ArrowBack, onClick = onBack, contentDescription = "Back", testTag = "sos_back")
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Grouped grey card used for settings blocks. */
@Composable
internal fun SosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RokkhonPalette.Subtle)
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springClick(enabled = enabled, pressedScale = 0.98f) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) RokkhonPalette.Ink else RokkhonPalette.Faint)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, lineHeight = 17.sp, color = RokkhonPalette.Muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RokkhonPalette.Primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD5D5D5),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
internal fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, testTag: String = "", trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, lineHeight = 17.sp, color = RokkhonPalette.Muted)
        }
        if (trailing != null) Text(trailing, fontSize = 13.sp, color = RokkhonPalette.Muted, modifier = Modifier.padding(end = 4.dp))
        Icon(UIcons.KeyboardArrowRight, contentDescription = null, tint = RokkhonPalette.Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun IconCircle(icon: ImageVector, tint: Color = RokkhonPalette.Ink, background: Color = Color.White, size: Dp = 38.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small status pill: dot + text. */
@Composable
internal fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val alpha = if (pulse) {
        val tr = rememberInfiniteTransition(label = "pill")
        tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pill_a").value
    } else 1f
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, testTag: String = "") {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> RokkhonPalette.Chip
                    danger -> RokkhonPalette.Sos
                    else -> RokkhonPalette.Primary
                }
            )
            .springClick(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else RokkhonPalette.Faint)
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, testTag: String = "") {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RokkhonPalette.Subtle)
            .springClick(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, maxLines = 1)
    }
}

/**
 * The SOS button: press and keep holding for [holdMs]. A ring fills while held; letting go early
 * cancels, so a pocket tap or a quick touch never starts an SOS.
 */
@Composable
internal fun HoldToActivateButton(
    label: String,
    hint: String,
    onActivated: () -> Unit,
    enabled: Boolean = true,
    holdMs: Int = 3000,
    size: Dp = 220.dp,
    color: Color = RokkhonPalette.Sos,
    testTag: String = "private_sos_hold_button"
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val breathe = rememberInfiniteTransition(label = "sos_breathe")
    val halo by breathe.animateFloat(1f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "halo")
    val pressScale = 1f - progress.value * 0.04f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .testTag(testTag)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(holdMs, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onActivated()
                            progress.snapTo(0f)
                        }
                        waitForUpOrCancellation()
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(200)) }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(size * 0.92f)
                    .scale(if (enabled) halo else 1f)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (enabled) 0.10f else 0.05f))
            )
            Canvas(Modifier.size(size * 0.86f)) {
                val stroke = 7.dp.toPx()
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke)
                )
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Box(
                Modifier
                    .size(size * 0.70f)
                    .scale(pressScale)
                    .clip(CircleShape)
                    .background(if (enabled) color else RokkhonPalette.Faint)
                    .border(4.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    val secondsLeft = ((1f - progress.value) * holdMs / 1000f)
                    if (progress.value > 0f) {
                        Text("%.1f".format(secondsLeft), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(hint, fontSize = 13.sp, color = RokkhonPalette.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White)) { content() }
}
