package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FeatureFlags
import com.example.data.model.AppLanguage
import com.example.data.model.GUARDIAN_REWARD_TIERS
import com.example.ui.components.SectionHeader
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.RokkhonViewModel
import kotlinx.coroutines.launch

/** Points, streak, daily check-in, how to earn more, and the badges to unlock. */
@Composable
fun RewardsScreen(viewModel: RokkhonViewModel) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val points by viewModel.guardianPoints.collectAsStateWithLifecycle()
    val streak by viewModel.dailyStreakCount.collectAsStateWithLifecycle()
    val checkedInToday by viewModel.checkedInToday.collectAsStateWithLifecycle()
    val checkInPoints by viewModel.rewardCheckInPoints.collectAsStateWithLifecycle()
    val pledgePoints by viewModel.rewardBloodPledgePoints.collectAsStateWithLifecycle()
    val sosPoints by viewModel.rewardSosRespondPoints.collectAsStateWithLifecycle()
    val bn = language == AppLanguage.BENGALI

    val tiers = GUARDIAN_REWARD_TIERS
    val current = tiers.lastOrNull { points >= it.requiredPoints }
    val next = tiers.firstOrNull { points < it.requiredPoints }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RokkhonPalette.Background)
            .testTag("rewards_screen")
    ) {
        item(key = "rewards_title") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            ) {
                Text(if (bn) "রিওয়ার্ড" else "Rewards", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                Text(
                    text = if (bn) "প্রতিদিনের ছোট সাহায্যেও পয়েন্ট জমে" else "Every small bit of help earns points",
                    fontSize = 15.sp,
                    color = RokkhonPalette.Muted
                )
            }
        }

        // Points card: counts up when the page opens
        item(key = "points_card") {
            val shown = remember { Animatable(0f) }
            LaunchedEffect(points) { shown.animateTo(points.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
            val progress by animateFloatAsState(
                targetValue = when {
                    next == null -> 1f
                    else -> {
                        val from = current?.requiredPoints ?: 0
                        ((points - from).toFloat() / (next.requiredPoints - from)).coerceIn(0f, 1f)
                    }
                },
                animationSpec = tween(1000, delayMillis = 200),
                label = "tier_progress"
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .padding(20.dp)
                    .enterOnce(0)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (bn) "আপনার পয়েন্ট" else "Your points", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = current?.let { if (bn) it.titleBn else it.titleEn } ?: (if (bn) "নতুন সদস্য" else "New member"),
                        color = Color.Black,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Text(
                    text = if (bn) bnDigits(shown.value.toInt()) else shown.value.toInt().toString(),
                    color = Color.White,
                    fontSize = 52.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (next == null) {
                        if (bn) "সব ব্যাজ অর্জিত — আপনি অসাধারণ!" else "Every badge unlocked — you're amazing!"
                    } else {
                        val left = next.requiredPoints - points
                        if (bn) "${next.titleBn} হতে আর ${bnDigits(left)} পয়েন্ট" else "${left} points to ${next.titleEn}"
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.5.sp
                )
            }
        }

        item(key = "streak_card") {
            StreakCard(
                bn = bn,
                streak = streak,
                checkedInToday = checkedInToday,
                checkInPoints = checkInPoints,
                onCheckIn = { viewModel.performDailySafeCheckIn() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).enterOnce(1)
            )
        }

        item(key = "earn_header") {
            SectionHeader(
                title = if (bn) "যেভাবে পয়েন্ট পাবেন" else "Ways to earn",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 6.dp)
            )
        }
        item(key = "earn_checkin") {
            EarnRow(UIcons.CheckCircle, if (bn) "প্রতিদিন চেক-ইন" else "Daily check-in", if (bn) "দিনে একবার" else "Once a day", checkInPoints, bn)
        }
        item(key = "earn_pledge") {
            EarnRow(UIcons.Bloodtype, if (bn) "রক্ত দেওয়ার প্রতিশ্রুতি" else "Pledge to donate", if (bn) "কোনো আবেদনে \"রক্ত দেব\" চাপলে" else "Tap \"I'll donate\" on a request", pledgePoints, bn)
        }
        if (FeatureFlags.SOS_ENABLED) {
            item(key = "earn_sos") {
                EarnRow(UIcons.Shield, if (bn) "SOS-এ সাড়া" else "Respond to an SOS", if (bn) "কাছের কারো বিপদে" else "When someone nearby needs help", sosPoints, bn)
            }
        }

        item(key = "badges_header") {
            SectionHeader(
                title = if (bn) "ব্যাজ" else "Badges",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 6.dp)
            )
        }
        tiers.forEachIndexed { index, tier ->
            item(key = "badge_${tier.id}") {
                BadgeRow(
                    title = if (bn) tier.titleBn else tier.titleEn,
                    subtitle = if (bn) tier.subtitleBn else tier.subtitleEn,
                    required = tier.requiredPoints,
                    points = points,
                    bn = bn,
                    modifier = Modifier.enterOnce(index + 2)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun StreakCard(
    bn: Boolean,
    streak: Int,
    checkedInToday: Boolean,
    checkInPoints: Int,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val floatUp = remember { Animatable(0f) }
    var burstKey by remember { mutableIntStateOf(0) }
    // The flame breathes until today's check-in is done
    val breathe = rememberInfiniteTransition(label = "flame")
    val flameScale = breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "flame_scale"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RokkhonPalette.Subtle)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    UIcons.LocalFireDepartment,
                    contentDescription = null,
                    tint = RokkhonPalette.Ink,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val s = if (checkedInToday) 1f else flameScale.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bn) "${bnDigits(streak)} দিনের স্ট্রিক" else "$streak-day streak",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = RokkhonPalette.Ink
                )
                Text(
                    text = if (bn) "টানা প্রতিদিন চেক-ইন করলে স্ট্রিক বাড়ে" else "Check in every day to keep it going",
                    fontSize = 13.sp,
                    color = RokkhonPalette.Muted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Last seven days, today on the right
        val labels = if (bn) listOf("শ", "র", "সো", "ম", "বু", "বৃ", "শু") else listOf("S", "S", "M", "T", "W", "T", "F")
        val todayIndex = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) % 7) // Sat=0 … Fri=6
        val doneDays = if (checkedInToday) streak else streak.coerceAtMost(6)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { i ->
                val daysAgo = 6 - i
                val done = if (checkedInToday) daysAgo < doneDays else daysAgo in 1..doneDays
                val pop = remember { Animatable(0f) }
                LaunchedEffect(done) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                scaleX = 0.6f + 0.4f * pop.value
                                scaleY = 0.6f + 0.4f * pop.value
                            }
                            .clip(CircleShape)
                            .background(if (done) Color.Black else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) Icon(UIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = labels[(todayIndex - daysAgo + 7) % 7],
                        fontSize = 11.5.sp,
                        color = if (daysAgo == 0) RokkhonPalette.Ink else RokkhonPalette.Muted,
                        fontWeight = if (daysAgo == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = checkedInToday,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "checkin_state"
            ) { done ->
                if (done) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(UIcons.CheckCircle, contentDescription = null, tint = RokkhonPalette.Positive, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (bn) "আজকের চেক-ইন হয়ে গেছে" else "Checked in today", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .springClick(pressedScale = 0.96f) {
                                burstKey++
                                scope.launch {
                                    floatUp.snapTo(0f)
                                    floatUp.animateTo(1f, tween(900))
                                }
                                onCheckIn()
                            }
                            .testTag("rewards_checkin_button"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (bn) "আজ চেক-ইন করুন  +${bnDigits(checkInPoints)}" else "Check in today  +$checkInPoints",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            // "+25" floats up and fades after tapping
            if (burstKey > 0 && floatUp.value in 0.001f..0.999f) {
                Text(
                    text = "+$checkInPoints",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RokkhonPalette.Ink,
                    modifier = Modifier
                        .offset(y = (-40 * floatUp.value).dp)
                        .graphicsLayer { alpha = 1f - floatUp.value }
                )
            }
        }
    }
}

@Composable
private fun EarnRow(icon: ImageVector, title: String, subtitle: String, points: Int, bn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(RokkhonPalette.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink)
            Text(subtitle, fontSize = 13.5.sp, color = RokkhonPalette.Muted)
        }
        Text(
            text = "+" + if (bn) bnDigits(points) else points.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = RokkhonPalette.Ink
        )
    }
}

@Composable
private fun BadgeRow(title: String, subtitle: String, required: Int, points: Int, bn: Boolean, modifier: Modifier = Modifier) {
    val unlocked = points >= required
    val progress by animateFloatAsState((points.toFloat() / required).coerceIn(0f, 1f), tween(900, delayMillis = 250), label = "badge_progress")
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(if (unlocked) Color.Black else RokkhonPalette.Subtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) UIcons.Verified else UIcons.CardGiftcard,
                contentDescription = null,
                tint = if (unlocked) Color.White else RokkhonPalette.Ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, modifier = Modifier.weight(1f))
                Text(
                    text = if (unlocked) (if (bn) "অর্জিত" else "Unlocked") else (if (bn) "${bnDigits(required)} পয়েন্ট" else "$required pts"),
                    fontSize = 13.sp,
                    fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                    color = if (unlocked) RokkhonPalette.Positive else RokkhonPalette.Muted
                )
            }
            Text(subtitle, fontSize = 13.sp, color = RokkhonPalette.Muted)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Color.Black,
                trackColor = RokkhonPalette.Chip,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
