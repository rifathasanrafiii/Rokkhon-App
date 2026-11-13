package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.ui.components.ClinicalBloodRequestCard
import com.example.ui.screens.DonorDetail
import com.example.ui.screens.DonorRow
import com.example.ui.screens.UrgentRequestCard
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Donor rows and request cards with realistic content, to check how real data reads. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ListItemsGalleryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val donors = listOf(
        DonorNode("u1", "u1", "রাফি হাসান", "O+", true, "Mirpur 10, Dhaka", "01711000001", lastDonationDate = "2026-05-01"),
        DonorNode("u2", "u2", "Nusrat Jahan Chowdhury Mim", "AB-", true, "Bauphal, Patuakhali", "01811000002", lastDonationDate = "2026-10-20"),
        DonorNode("u3", "u3", "তানভীর", "B+", false, "Chattogram", "01911000003", lastDonationDate = "")
    )
    private val req = BloodRequest(
        "r1", "u2", "Nusrat", "করিম উদ্দিন", "O-", 2, "7.2", "অপারেশন", "ঢাকা মেডিকেল কলেজ হাসপাতাল", "ওয়ার্ড ৩, বেড ১২",
        "এখনই", "Mirpur 10, Dhaka", "01811000002", "CRITICAL", respondersCount = 3, createdAt = Timestamp(Date(System.currentTimeMillis() - 40 * 60_000))
    )

    @Test
    fun gallery() {
        val dir = System.getenv("ROKKHON_SCREENSHOTS") ?: return
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    donors.forEach { DonorRow(it, AppLanguage.BENGALI, onClick = {}, onCall = {}) }
                    Spacer(Modifier.height(12.dp))
                    Row { UrgentRequestCard(req, AppLanguage.BENGALI) {}; Spacer(Modifier.width(8.dp)); UrgentRequestCard(req.copy(urgencyLevel = "STANDARD", bloodGroup = "AB+", hospitalName = "Square"), AppLanguage.BENGALI) {} }
                    Spacer(Modifier.height(12.dp))
                    ClinicalBloodRequestCard(req, AppLanguage.BENGALI, isOwner = false, onDonateClick = {}, onCallClick = {}, onFulfillClick = {})
                    Spacer(Modifier.height(12.dp))
                    DonorDetail(donors[0], AppLanguage.BENGALI, onCall = {}, onSms = {})
                }
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir).mkdirs()
        FileOutputStream(File(dir, "60_list_items.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
