package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.PinCrimsonRed
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RadarObsidianBg
import kotlinx.coroutines.delay

/**
 * Animated Luxury Splash Screen inspired by Uber & Lifeline:
 * Deep obsidian-burgundy canvas with concentric radar rings and the "rokkhon [+]" emblem.
 */
@Composable
fun RokkhonSplashScreen(
    onSplashFinished: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_radar")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_scale"
    )

    LaunchedEffect(Unit) {
        delay(1200L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4B0D18),
                        RadarDarkMaroon,
                        RadarObsidianBg
                    )
                )
            )
            .testTag("rokkhon_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(300.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = PinCrimsonRed.copy(alpha = (1.35f - ringScale).coerceIn(0f, 0.35f)),
                radius = (size.minDimension / 2f) * ringScale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = PinCrimsonRed.copy(alpha = 0.18f),
                radius = size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "rokkhon",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp
                )
                Spacer(modifier = Modifier.width(10.dp))
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "রক্ষণ • Every drop & every second matters",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.76f)
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
    val titleEn: String,
    val titleBn: String,
    val descriptionBn: String,
    val descriptionEn: String,
    val highlightPoints: List<TutorialHighlightPoint>,
    val icon: ImageVector
)

/**
 * Interactive 3-Step Tutorial / Onboarding Flow with Uber-Style Split Bottom Sheet:
 * Uses 100% clean Flaticon-style vector interface icons with zero emojis.
 */
@Composable
fun RokkhonTutorialScreen(
    onFinishTutorial: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    val slides = listOf(
        TutorialSlide(
            badge = "STEP 1 OF 3 • AREA-BASED DONOR & PATIENT MATCH",
            titleEn = "Every drop can\nsave a life.",
            titleBn = "এলাকাভিত্তিক উপলব্ধ রক্তদাতা ও রোগীর আলাদা ফিল্টার",
            descriptionBn = "উপলব্ধ রক্তদাতা এবং রোগীর রক্তের আবেদন আলাদা ট্যাবে দেখুন। রোগীর নাম, হিমোগ্লোবিন (Hb), সমস্যা ও কেবিন নম্বর দিয়ে পোস্ট করলে সেই এলাকার ডোনারদের কাছে রিয়েল-টাইম নোটিফিকেশন যাবে।",
            descriptionEn = "Switch cleanly between Available Donors and Patient Blood Requests on the real Bangladesh map with area-based matching.",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.Bloodtype,
                    label = "উপলব্ধ ডোনার ও রোগীর পোস্টের জন্য আলাদা ফিল্টার ও ১-ট্যাপ রক্তদান লক"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.LocalHospital,
                    label = "আলাদা ফিল্ড: রোগীর নাম, রক্তের ব্যাগ, হিমোগ্লোবিন (Hb), সমস্যা ও কেবিন নম্বর"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.Whatshot,
                    label = "ডেইলি চেক-ইন স্ট্রিক ও গার্ডিয়ান পয়েন্ট ব্যাজ"
                )
            ),
            icon = Icons.Default.Bloodtype
        ),
        TutorialSlide(
            badge = "STEP 2 OF 3 • REAL BANGLADESH MAP & OFFLINE BLE",
            titleEn = "Real street map &\noffline BLE mesh.",
            titleBn = "রিয়েল বাংলাদেশ ম্যাপ ও অফলাইন ব্লুটুথ সিগন্যাল",
            descriptionBn = "রিয়েল স্ট্রিট ম্যাপে আপনার এলাকার সক্রিয় ডোনার খুঁজুন। ইন্টারনেট না থাকলেও Offline Bluetooth Low Energy (BLE) সিগন্যালের মাধ্যমে কাছের ফোনে বিপদ সংকেত পৌঁছে যাবে।",
            descriptionEn = "Interactive OpenStreetMap Bangladesh street layer paired with peer-to-peer Bluetooth emergency beacons.",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.Map,
                    label = "বাংলাদেশ ম্যাপে এলাকাভিত্তিক ডোনার, রোগী ও SOS পিন"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.BluetoothConnected,
                    label = "ইন্টারনেট ছাড়াই Offline Bluetooth Low Energy (BLE) মেশ সিগন্যাল"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.NotificationsActive,
                    label = "রক্তের গ্রুপ কম্প্যাটিবিলিটি ও ৯০ দিনের রক্তদান বিরতি অটো চেক"
                )
            ),
            icon = Icons.Default.BluetoothConnected
        ),
        TutorialSlide(
            badge = "STEP 3 OF 3 • 3X POWER SOS • TRUSTED CONTACTS",
            titleEn = "3x Power tap SOS.\n5 seconds to cancel.",
            titleBn = "৩-বার পাওয়ার বাটনে SOS • ভুল হলে ৫ সেকেন্ডে বাতিল",
            descriptionBn = "অ্যাপ খোলা থাকলে পাওয়ার বা ভলিউম বাটনে পরপর ৩ বার চাপ দিন। ৫ সেকেন্ডের মধ্যে বাতিল না করলে মাইক্রোফোনের অডিও প্রমাণসহ SOS চলে যাবে, ১ কি.মি.-এর ইউজাররা জানবে এবং নিরাপদ মার্ক না করা পর্যন্ত লাইভ লোকেশন শেয়ার হবে।",
            descriptionEn = "With the app open, press Power or Volume 3x. You get 5 seconds to cancel, then the SOS goes out with microphone evidence and live GPS until you mark yourself safe.",
            highlightPoints = listOf(
                TutorialHighlightPoint(
                    icon = Icons.Default.FlashOn,
                    label = "৩-বার পাওয়ার/ভলিউম চাপে SOS • ভুল চাপ হলে ৫ সেকেন্ডে বাতিল"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.Call,
                    label = "পরিবারের ৩ জন বিশ্বস্ত কন্টাক্টকে লোকেশন লিংকসহ SMS ও এক ট্যাপে ৯৯৯ কল"
                ),
                TutorialHighlightPoint(
                    icon = Icons.Default.LocationOn,
                    label = "নিরাপদ মার্ক না করা পর্যন্ত আসল জিপিএস লাইভ লোকেশন শেয়ার"
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
        // Top Dark Atmospheric Hero Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF470C17),
                            RadarDarkMaroon,
                            RadarObsidianBg
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp)
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
                    color = Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable { onFinishTutorial() }
                        .testTag("skip_tutorial_button")
                ) {
                    Text(
                        text = "Skip • স্কিপ",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = slide,
                modifier = Modifier.align(Alignment.BottomStart),
                label = "tutorial_hero"
            ) { targetSlide ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PinCrimsonRed.copy(alpha = 0.24f))
                            .border(1.dp, PinCrimsonRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = targetSlide.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = targetSlide.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB3C0),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = targetSlide.titleEn,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 35.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetSlide.titleBn,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
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
                .weight(0.52f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
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
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = slide.descriptionBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1A0F12),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slide.highlightPoints.forEach { point ->
                            Surface(
                                color = LifelineBlush.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(IosSystemRed.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = point.icon,
                                            contentDescription = null,
                                            tint = IosSystemRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = point.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF4A0E17),
                                        fontWeight = FontWeight.SemiBold
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
                            text = if (currentStep < slides.lastIndex) "Next • পরবর্তী" else "Get Started • শুরু করুন",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
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
}
