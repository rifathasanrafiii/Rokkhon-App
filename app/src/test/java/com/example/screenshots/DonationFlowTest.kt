package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ui.screens.DonationCycleCard
import com.example.ui.screens.LogDonationSheet
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** Recording a donation needs a chosen day and a 1.5 s hold; a tap records nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class DonationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapDoesNothingHoldRecords() {
        var recorded: String? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.padding(16.dp)) {
                    DonationCycleCard(bn = true, lastDonationDate = "2016-10-10", onLogDonation = {}, canUndo = false, onUndo = {})
                }
                LogDonationSheet(bn = true, lastDonationDate = "2016-10-10", onDismiss = {}, onConfirm = { recorded = it })
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        // Nothing picked yet: holding does nothing.
        hold(1_800)
        assertNull(recorded)

        composeRule.onNodeWithTag("donation_day_0").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        // A quick tap is not a confirmation.
        composeRule.onNodeWithTag("confirm_donated_today").performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        assertNull(recorded)

        save("23_record_donation_sheet")
        hold(1_800)
        assertEquals(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()), recorded)
    }

    private fun hold(ms: Long) {
        composeRule.onNodeWithTag("confirm_donated_today").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.onNodeWithTag("confirm_donated_today").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(300)
    }

    private fun save(name: String) {
        val dir = System.getenv("ROKKHON_SCREENSHOTS") ?: return
        composeRule.waitForIdle()
        val root = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        root.draw(canvas)
        // The sheet is its own window: draw it at the bottom of the screen.
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?.let { v ->
            canvas.drawColor(android.graphics.Color.argb(110, 0, 0, 0))
            canvas.save()
            canvas.translate(0f, (root.height - v.height).toFloat().coerceAtLeast(0f))
            v.draw(canvas)
            canvas.restore()
        }
        File(dir).mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
