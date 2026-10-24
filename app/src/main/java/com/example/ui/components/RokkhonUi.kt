package com.example.ui.components

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.BloodGroup
import com.example.ui.theme.Rk

/** Plain screen title bar used by every tab (no heavy gradients outside emergency screens). */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // Same crimson gradient as the Home hero, ending in a rounded "sheet" lip.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFFA3132B), Color(0xFF7A0C1E), Color(0xFF5A0915))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = if (onBack != null) 6.dp else Rk.Gutter, end = 14.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("screen_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                if (subtitle != null) {
                    Text(text = subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Rk.Bg)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, color = Rk.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = Rk.Crimson,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onAction() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** White rounded card with a hairline border. */
@Composable
fun RkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Rk.Surface,
    border: Color = Rk.Line,
    contentPadding: PaddingValues = PaddingValues(Rk.CardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Rk.CardRadius)
    Surface(
        color = color,
        shape = shape,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Rk.Crimson,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = Rk.SurfaceMuted,
            disabledContentColor = Rk.Muted
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Rk.Ink
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Rk.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Rk.Surface, contentColor = color),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Single-select chip. */
@Composable
fun FilterChipRk(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    Surface(
        color = if (selected) Rk.Ink else Rk.Surface,
        contentColor = if (selected) Color.White else Rk.InkSoft,
        shape = CircleShape,
        border = if (selected) null else BorderStroke(1.dp, Rk.Line),
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.RadioButton) { onClick() }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}

/** "All" + 8 blood groups in one scrollable row. Selecting the active group clears it. */
@Composable
fun BloodGroupChipRow(
    language: AppLanguage,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String? = null
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.width(Rk.Gutter - 8.dp))
        FilterChipRk(
            text = allLabel ?: if (language == AppLanguage.BENGALI) "সব গ্রুপ" else "All groups",
            selected = selected == null,
            onClick = { onSelect(null) },
            tag = "blood_filter_all"
        )
        BloodGroup.ALL_LABELS.forEach { group ->
            FilterChipRk(
                text = group,
                selected = selected == group,
                onClick = { onSelect(if (selected == group) null else group) },
                tag = "blood_filter_$group"
            )
        }
        Spacer(modifier = Modifier.width(Rk.Gutter - 8.dp))
    }
}

/** Compact area selector shown in headers. */
@Composable
fun AreaChip(areaName: String, onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    Surface(
        color = if (dark) Color.White.copy(alpha = 0.14f) else Rk.Surface,
        contentColor = if (dark) Color.White else Rk.Ink,
        shape = CircleShape,
        border = if (dark) null else BorderStroke(1.dp, Rk.Line),
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button) { onClick() }
            .testTag("area_dropdown_selector")
    ) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (dark) Color.White else Rk.Crimson, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(areaName, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Round icon tile used in lists and settings. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Two-option or three-option segmented control. */
@Composable
fun SegmentedControl(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Rk.SurfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Rk.Surface else Color.Transparent)
                    .then(if (selected) Modifier.border(1.dp, Rk.Line, RoundedCornerShape(11.dp)) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(key) }
                    .testTag("segment_$key"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Rk.Ink else Rk.Muted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}
