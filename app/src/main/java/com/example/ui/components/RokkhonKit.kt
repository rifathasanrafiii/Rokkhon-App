package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RokkhonPalette
import kotlinx.coroutines.delay

/*
 * Building blocks for the icon-first layouts: springy press feedback, numbers that roll when they
 * change, round quick actions, stat tiles, a sliding segmented control and the app's bottom sheet.
 * Everything uses RokkhonPalette, Google Sans and the existing card shapes.
 */

/** Tap with a light haptic tick and a spring "press in" instead of a ripple. */
fun Modifier.springClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * Slides and fades a block in the first time it appears; [order] staggers neighbours.
 * Played once per item, so scrolling back does not replay it.
 */
fun Modifier.enterOnce(order: Int = 0): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val shift = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(order.coerceAtMost(6) * 40L)
            progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            played = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift
    }
}

/** A number that rolls up or down when it changes, like a balance. */
@Composable
fun RollingNumber(
    value: Int,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    suffix: String = ""
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically { h -> if (up) h else -h } + fadeIn()) togetherWith
                (slideOutVertically { h -> if (up) -h else h } + fadeOut())
        },
        label = "rolling_number",
        modifier = modifier
    ) { n ->
        Text(
            text = "$n$suffix",
            fontSize = fontSize,
            lineHeight = fontSize,
            color = color,
            fontWeight = fontWeight
        )
    }
}

/** Round icon button with a short label under it (the row under the hero number). */
@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onDark: Boolean = true,
    testTag: String = ""
) {
    Column(
        modifier = modifier
            .springClick(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .then(if (emphasized) Modifier.shadow(10.dp, CircleShape, ambientColor = RokkhonPalette.CrimsonBright, spotColor = RokkhonPalette.CrimsonBright) else Modifier)
                .clip(CircleShape)
                .background(
                    when {
                        emphasized -> RokkhonPalette.CrimsonBright
                        onDark -> Color.White.copy(alpha = 0.14f)
                        else -> RokkhonPalette.Blush
                    }
                )
                .border(
                    1.dp,
                    if (onDark) Color.White.copy(alpha = if (emphasized) 0.38f else 0.18f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (onDark || emphasized) Color.White else RokkhonPalette.Crimson,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (onDark) Color.White.copy(alpha = 0.92f) else RokkhonPalette.Ink,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Icon tile for a 2-column grid: tinted icon, big rolling value, one short label. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: Int,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    valueSuffix: String = "",
    valueText: String? = null,
    testTag: String = "",
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        color = if (selected) RokkhonPalette.BlushSoft else Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .border(
                if (selected) 1.4.dp else 1.dp,
                if (selected) accent.copy(alpha = 0.45f) else RokkhonPalette.Border,
                RoundedCornerShape(22.dp)
            )
            .springClick(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (valueText != null) {
                Text(
                    text = valueText,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    color = RokkhonPalette.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                RollingNumber(value = value, fontSize = 26.sp, color = RokkhonPalette.Ink, suffix = valueSuffix)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = RokkhonPalette.Muted,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One segment of [SegmentedControl]. */
class Segment(val label: String, val icon: ImageVector? = null, val testTag: String = "")

/** Pill control with a white thumb that slides to the selected segment. */
@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(RokkhonPalette.Track)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / segments.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "segment_thumb"
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            segments.forEachIndexed { index, seg ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .springClick(pressedScale = 0.97f, role = Role.Tab) { onSelect(index) }
                        .then(if (seg.testTag.isNotEmpty()) Modifier.testTag(seg.testTag) else Modifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (seg.icon != null) {
                        Icon(
                            imageVector = seg.icon,
                            contentDescription = null,
                            tint = if (selected) RokkhonPalette.Crimson else RokkhonPalette.Muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = seg.label,
                        fontSize = 13.sp,
                        color = if (selected) RokkhonPalette.Ink else RokkhonPalette.Muted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Section title with an optional trailing control. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 17.sp, color = RokkhonPalette.Ink, fontWeight = FontWeight.ExtraBold)
        trailing?.invoke()
    }
}

/** Small round icon button used in section headers. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    contentDescription: String? = null,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (filled) RokkhonPalette.Crimson else RokkhonPalette.BlushSoft)
            .springClick(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) Color.White else RokkhonPalette.Crimson,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Grid cell for bottom-sheet menus: icon tile + label (+ optional state line). */
@Composable
fun SheetGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: String? = null,
    active: Boolean = false,
    testTag: String = ""
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .springClick(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (active) RokkhonPalette.Crimson else RokkhonPalette.BlushSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color.White else RokkhonPalette.Crimson,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = RokkhonPalette.Ink,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 15.sp
        )
        if (state != null) {
            Text(
                text = state,
                fontSize = 10.5.sp,
                color = if (active) RokkhonPalette.Crimson else RokkhonPalette.Muted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/** The app's bottom sheet: white, 28dp top corners, small grey handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RokkhonBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    testTag: String = "rokkhon_bottom_sheet",
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDAD2D4))
            )
        },
        modifier = Modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        ) {
            if (title != null) {
                Text(text = title, fontSize = 19.sp, color = RokkhonPalette.Ink, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(14.dp))
            }
            content()
        }
    }
}
