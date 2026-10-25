package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
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
        delay(1300L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8B1124),
                        Color(0xFF4B0A15),
                        RadarObsidianBg
                    )
                )
            )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "rokkhon",
                    fontSize = 44.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.0).sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PinCrimsonRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "প্রতিটি পদক্ষেপ একটি জীবন বাঁচাতে পারে",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.90f),
                fontWeight = FontWeight.Medium,
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
    val icon: ImageVector
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

    val slides = listOf(
        TutorialSlide(
            badge = "STEP 01 / 03",
            titleBn = "প্রতিটি পদক্ষেপ\nএকটি জীবন বাঁচাতে পারে",
            subtitleBn = "এলাকাভিত্তিক রক্তদাতা ও জরুরি রোগীর আবেদন",
            descriptionBn = "রোগীর নাম, হিমোগ্লোবিন (Hb), সমস্যা ও কেবিন নম্বর দিয়ে পোস্ট করলে সেই এলাকার ম্যাচিং ডোনারদের কাছে তাৎক্ষণিক নোটিফিকেশন পৌঁছে যাবে।",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.Bloodtype,
                    label = "উপলব্ধ ডোনার ও রোগীর পোস্টের জন্য আলাদা ফিল্টার"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.LocalHospital,
                    label = "রোগীর নাম, রক্তের ব্যাগ, হিমোগ্লোবিন ও কেবিন তথ্য"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.Whatshot,
                    label = "ডেইলি চেক-ইন স্ট্রিক ও গার্ডিয়ান পয়েন্ট ব্যাজ"
                )
            ),
            icon = Icons.Default.Bloodtype
        ),
        TutorialSlide(
            badge = "STEP 02 / 03",
            titleBn = "রিয়েল বাংলাদেশ ম্যাপ ও\nঅফলাইন ব্লুটুথ সিগন্যাল",
            subtitleBn = "ইন্টারনেট ছাড়াই কাছের মানুষদের কাছে বিপদ সংকেত",
            descriptionBn = "রিয়েল স্ট্রিট ম্যাপে আপনার এলাকার সক্রিয় ডোনার খুঁজুন। ইন্টারনেট না থাকলেও ১ কি.মি. এলাকার ফোনে ব্লুটুথ SOS পৌঁছে যাবে।",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.Map,
                    label = "বাংলাদেশ ম্যাপে এলাকাভিত্তিক ডোনার ও SOS এরিয়া"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.BluetoothConnected,
                    label = "ইন্টারনেট ছাড়াই ১ কি.মি. ব্লুটুথ (BLE) মেশ সিগন্যাল"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.NotificationsActive,
                    label = "রক্তের গ্রুপ কম্প্যাটিবিলিটি ও ৯০ দিনের বিরতি চেক"
                )
            ),
            icon = Icons.Default.BluetoothConnected
        ),
        TutorialSlide(
            badge = "STEP 03 / 03",
            titleBn = "হোম SOS বাটন ও\n৩-বার পাওয়ার চাপে সুরক্ষা",
            subtitleBn = "ভুল চাপ হলে ৫ সেকেন্ডের মধ্যে বাতিল করার সুযোগ",
            descriptionBn = "হোম স্ক্রিনের SOS বাটনে ট্যাপ করলে বা পাওয়ার বাটনে ৩ বার চাপ দিলে অডিও প্রমাণ ও লাইভ জিপিএস লোকেশনসহ জরুরি অ্যালার্ট চলে যাবে।",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.FlashOn,
                    label = "হোম SOS বাটন ও ৩-বার পাওয়ার চাপে দ্রুত অ্যালার্ট"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.Call,
                    label = "পরিবারের ৩ জন বিশ্বস্ত কন্টাক্টকে SMS ও ৯৯৯ কল"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.LocationOn,
                    label = "নিরাপদ মার্ক না করা পর্যন্ত লাইভ জিপিএস লোকেশন"
                )
            ),
            icon = Icons.Default.FlashOn
        )
    )

    val slide = slides[currentStep]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarObsidianBg)
            .testTag("rokkhon_tutorial_screen")
    ) {
        // Top Pure Gradient Hero Section (No background shapes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8B1124),
                            Color(0xFF4B0A15),
                            RadarObsidianBg
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "rokkhon",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PinCrimsonRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable { onFinishTutorial() }
                        .testTag("skip_tutorial_button")
                ) {
                    Text(
                        text = "স্কিপ",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = slide,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 24.dp),
                label = "tutorial_hero"
            ) { targetSlide ->
                Column {
                    Text(
                        text = targetSlide.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = targetSlide.titleBn,
                        fontSize = 28.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 38.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = targetSlide.subtitleBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Bottom Rounded Pure White Sheet
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2D5D7))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = slide.descriptionBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2B1B1E),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 23.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        slide.highlightPoints.forEach { point ->
                            Surface(
                                color = Color(0xFFFAF5F6),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(LifelineBlush),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = point.icon,
                                            contentDescription = null,
                                            tint = IosSystemRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = point.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF3B131A),
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slides.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (idx == currentStep) 26.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (idx == currentStep) IosSystemRed else Color(0xFFE2D5D7)
                                    )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep < slides.lastIndex) {
                                currentStep += 1
                            } else {
                                onFinishTutorial()
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosSystemRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("tutorial_next_button")
                    ) {
                        Text(
                            text = if (currentStep < slides.lastIndex) "পরবর্তী" else "শুরু করুন",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
