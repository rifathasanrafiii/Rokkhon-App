package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.FeatureFlags
import com.example.R
import com.example.ui.components.StoryArt
import com.example.ui.components.StoryBars
import com.example.ui.components.StoryHero
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import kotlinx.coroutines.delay

/**
 * Minimalist Luxury Splash Screen:
 * Pure smooth vertical crimson-burgundy-obsidian gradient with zero background shapes,
 * elevated optical positioning, and generous typography spacing.
 */
@Composable
fun RokkhonSplashScreen(
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(800L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onSplashFinished() }
            .testTag("rokkhon_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.rokkhon_wordmark_white),
                contentDescription = "রক্ষণ",
                modifier = Modifier.height(56.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "প্রতিটি পদক্ষেপ একটি জীবন বাঁচাতে পারে",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.4.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class TutorialHighlightPoint(
    val icon: ImageVector,
    val label: String
)

private data class TutorialSlide(
    val badge: String,
    val titleBn: String,
    val subtitleBn: String,
    val descriptionBn: String,
    val highlightPoints: List<TutorialHighlightPoint>,
    val icon: ImageVector,
    val art: StoryArt
)

/**
 * Clean 3-Step Onboarding Flow:
 * Pure gradient top section with zero decorative background shapes and generous spacing.
 */
@Composable
fun RokkhonTutorialScreen(
    onFinishTutorial: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    val slides = listOfNotNull(
        TutorialSlide(
            badge = "",
            titleBn = "কাছের ডোনার,\nএক নজরে",
            subtitleBn = "",
            descriptionBn = "আপনার এলাকায় কারা রক্ত দিতে পারবেন দেখুন, আর সরাসরি তাদের কল করুন।",
            highlightPoints = listOf(
                TutorialHighlightPoint(UIcons.Bloodtype, "রোগীর গ্রুপ মিলিয়ে ডোনার খোঁজা"),
                TutorialHighlightPoint(UIcons.Event, "শেষ রক্তদানের ৯০ দিন নিজে থেকেই হিসাব হয়"),
                TutorialHighlightPoint(UIcons.Call, "এক ট্যাপে কল বা মেসেজ")
            ),
            icon = UIcons.People,
            art = StoryArt.DONORS
        ),
        TutorialSlide(
            badge = "",
            titleBn = "রক্ত লাগলে\nআবেদন করুন",
            subtitleBn = "",
            descriptionBn = "রোগীর তথ্য দিয়ে আবেদন করলে, মিলে যাওয়া ডোনাররা সাথে সাথে জানতে পারেন।",
            highlightPoints = listOf(
                TutorialHighlightPoint(UIcons.LocalHospital, "হাসপাতাল, ওয়ার্ড আর কত ব্যাগ লাগবে"),
                TutorialHighlightPoint(UIcons.Share, "হোয়াটসঅ্যাপ বা ফেসবুকে শেয়ার করুন"),
                TutorialHighlightPoint(UIcons.CheckCircle, "রক্ত পেলে আবেদন বন্ধ করুন")
            ),
            icon = UIcons.Bloodtype,
            art = StoryArt.REQUEST
        ),
        TutorialSlide(
            badge = "",
            titleBn = "ম্যাপে দেখুন\nকে কত কাছে",
            subtitleBn = "",
            descriptionBn = "রাস্তার ম্যাপে আপনার এলাকার ডোনার আর রোগীর আবেদন একসাথে দেখুন।",
            highlightPoints = listOf(
                TutorialHighlightPoint(UIcons.Map, "সারা বাংলাদেশের ৬৪ জেলা"),
                TutorialHighlightPoint(UIcons.MyLocation, "জিপিএস দিয়ে নিজের এলাকা"),
                TutorialHighlightPoint(UIcons.Star, "প্রতিদিন চেক-ইন করে পয়েন্ট")
            ),
            icon = UIcons.Map,
            art = StoryArt.MAP
        ),
        if (FeatureFlags.SOS_ENABLED) TutorialSlide(
            badge = "",
            titleBn = "বিপদে\nএক ট্যাপে SOS",
            subtitleBn = "",
            descriptionBn = "৫ সেকেন্ডের অডিও প্রমাণ আর লাইভ লোকেশন সহ কাছের মানুষদের জানিয়ে দিন।",
            highlightPoints = listOf(
                TutorialHighlightPoint(UIcons.FlashOn, "এক ট্যাপে SOS"),
                TutorialHighlightPoint(UIcons.Call, "৯৯৯ কল ও পরিবারকে SMS"),
                TutorialHighlightPoint(UIcons.LocationOn, "নিরাপদ না হওয়া পর্যন্ত লাইভ লোকেশন")
            ),
            icon = UIcons.FlashOn,
            art = StoryArt.SOS
        ) else null
    )

    val slide = slides[currentStep]
    // Like a story: each page fills its bar and moves on by itself; tapping the picture skips.
    val storyProgress = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        storyProgress.snapTo(0f)
        storyProgress.animateTo(1f, tween(STORY_PAGE_MS, easing = LinearEasing))
        if (currentStep < slides.lastIndex) currentStep += 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag("rokkhon_tutorial_screen")
    ) {
        StoryBars(count = slides.size, index = currentStep, progress = storyProgress.value, modifier = Modifier.padding(bottom = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.rokkhon_wordmark_black),
                contentDescription = "রক্ষণ",
                modifier = Modifier.height(28.dp)
            )
            Text(
                text = "স্কিপ",
                color = RokkhonPalette.Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .springClick { onFinishTutorial() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("skip_tutorial_button")
            )
        }

        AnimatedContent(
            targetState = slide,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 6 }) togetherWith fadeOut()
            },
            label = "tutorial_slide"
        ) { s ->
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(20.dp))
                Box {
                    StoryHero(s.art)
                    // Tap the left half to go back, the right half to go on, like a story.
                    Row(Modifier.matchParentSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) { if (currentStep > 0) currentStep -= 1 })
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) { if (currentStep < slides.lastIndex) currentStep += 1 })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(s.titleBn, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink)
                Spacer(modifier = Modifier.height(12.dp))
                Text(s.descriptionBn, fontSize = 16.sp, lineHeight = 24.sp, color = RokkhonPalette.Muted)
                Spacer(modifier = Modifier.height(24.dp))
                s.highlightPoints.forEach { point ->
                    Row(modifier = Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(point.icon, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(point.label, fontSize = 15.5.sp, color = RokkhonPalette.Ink)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .springClick(pressedScale = 0.98f) {
                    if (currentStep < slides.lastIndex) currentStep += 1 else onFinishTutorial()
                }
                .testTag("tutorial_next_button"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentStep < slides.lastIndex) "পরবর্তী" else "শুরু করুন",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** How long one intro page shows before moving on by itself. */
internal const val STORY_PAGE_MS = 6_000
