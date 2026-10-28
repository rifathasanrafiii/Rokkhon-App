package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.RewardClaim
import com.example.data.model.RewardOffer
import com.example.ui.screens.RewardCardsRow
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Reward cards from the admin panel: only an affordable, unclaimed card can be claimed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class RewardCardsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val offers = listOf(
        RewardOffer("checkup", "Free health check-up", "ফ্রি হেলথ চেকআপ", "", "", "Popular Diagnostic", 500, 0),
        RewardOffer("ticket", "Movie ticket", "সিনেমার টিকিট", "", "", "Star Cineplex", 1200, 1),
        RewardOffer("tshirt", "Rokkhon T-shirt", "রক্ষণ টি-শার্ট", "", "", "", 300, 2),
        RewardOffer("mug", "Rokkhon mug", "রক্ষণ মগ", "", "", "", 200, 3)
    )

    @Test
    fun onlyAffordableUnclaimedCardsCanBeClaimed() {
        val claimed = mutableListOf<String>()
        val claims = mapOf(
            "tshirt" to RewardClaim("tshirt", "approved", "অফিস থেকে নিয়ে যান", cost = 300),
            "mug" to RewardClaim("mug", "pending", null, cost = 200)
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize().background(Color.White)) {
                    RewardCardsRow(bn = true, offers = offers, claims = claims, points = 740, onClaim = { claimed += it.id })
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithText("আর 460 পয়েন্ট").assertExists()
        composeRule.onNodeWithTag("reward_claim_ticket").performClick()
        composeRule.onNodeWithTag("reward_claim_checkup").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals(listOf("checkup"), claimed)
        save("30_reward_cards")
    }

    @Test
    fun emptyStateWithoutCards() {
        composeRule.setContent {
            MyApplicationTheme { RewardCardsRow(bn = true, offers = emptyList(), claims = emptyMap(), points = 0, onClaim = {}) }
        }
        composeRule.onNodeWithTag("reward_cards_empty").assertExists()
    }

    private fun save(name: String) {
        val dir = System.getenv("ROKKHON_SCREENSHOTS") ?: return
        val root = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height / 3, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir).mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
