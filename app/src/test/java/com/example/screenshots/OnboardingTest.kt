package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.profile.UserProfile
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Onboarding asks only what the account doesn't have yet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class OnboardingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun newGoogleAccountGoesNameThenBloodThenNumber() {
        composeRule.setContent { MyApplicationTheme { OnboardingScreen(UserProfile("", null, null), onDone = {}) } }
        composeRule.onNodeWithText("আপনার নাম").assertIsDisplayed()
        save("40_onboarding_name")
        composeRule.onNodeWithTag("onboarding_name").performTextInput("রাফি হাসান")
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText("রক্তের গ্রুপ").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_blood_O+").performClick()
        save("41_onboarding_blood")
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText("মোবাইল নম্বর").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_phone").performTextInput("01712345678")
        save("42_onboarding_phone")
    }

    @Test
    fun phoneSignInIsNotAskedForANumber() {
        var done = false
        composeRule.setContent {
            MyApplicationTheme { OnboardingScreen(UserProfile("রাফি", null, "+8801712345678"), onDone = { done = true }) }
        }
        composeRule.onNodeWithText("রক্তের গ্রুপ").assertIsDisplayed()
        composeRule.onNodeWithText("শুরু করুন").assertExists()
        composeRule.onNodeWithTag("onboarding_phone").assertDoesNotExist()
    }

    private fun save(name: String) {
        val dir = System.getenv("ROKKHON_SCREENSHOTS") ?: return
        composeRule.waitForIdle()
        val root = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir).mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
