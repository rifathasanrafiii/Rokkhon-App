package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * The app starts, shows Home with its tab bar, and the request form keeps its send button on
 * screen on every Android version we support: 7 (API 24) up to 16 (API 36).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [24, 26, 28, 30, 31, 33, 34, 35, 36], qualifiers = "w360dp-h740dp-xxhdpi")
class AndroidVersionsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipIntro() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("rokkhon_app_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("tutorial_done", true)
            .putBoolean("startup_all_permissions_asked_v3", true)
            .commit()
    }

    @Test
    fun homeAndRequestFormWork() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MyApplicationTheme { RokkhonAppRoot() } }
        repeat(40) {
            if (composeRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()) return@repeat
            if (composeRule.onAllNodesWithTag("skip_sign_in_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("skip_sign_in_button").performClick()
            }
            composeRule.mainClock.advanceTimeBy(250)
        }
        composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_sos").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_center_plus_button").performClick()
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.onNodeWithTag("submit_blood_request_button").assertIsDisplayed()
        composeRule.onNodeWithTag("blood_request_close").performClick()
        composeRule.mainClock.advanceTimeBy(900)

        composeRule.onNodeWithTag("nav_tab_profile").performClick()
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.onNodeWithTag("nav_tab_sos").performClick()
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.onNodeWithTag("private_sos_hold_button").assertIsDisplayed()
    }
}
