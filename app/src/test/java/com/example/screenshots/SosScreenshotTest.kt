package com.example.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.sos.GpsProblem
import com.example.data.sos.LiveFix
import com.example.data.sos.MicStatus
import com.example.data.sos.PushStatus
import com.example.data.sos.SmsStatus
import com.example.ui.sos.SmsContactsSheet
import com.example.ui.sos.SmsLine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.TrustedContact
import com.example.data.sos.SosRuntime
import com.example.ui.sos.PrivateSosScreen
import com.example.ui.sos.SosAccessScreen
import com.example.ui.sos.SosMonitorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PrivateSosViewModel
import com.example.ui.viewmodel.SosMonitorViewModel
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * The private SOS screens render without crashing, including without Firebase (they must say so
 * instead of pretending to work). Set ROKKHON_SCREENSHOTS to also write PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class SosScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File? = System.getenv("ROKKHON_SCREENSHOTS")?.let { File(it).apply { mkdirs() } }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() = SosRuntime.reset()

    private fun save(name: String) {
        composeRule.waitForIdle()
        val dir = outDir ?: return
        val root = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun sosHomeWithoutServerSaysSo() {
        val vm = PrivateSosViewModel(app)
        composeRule.setContent { MyApplicationTheme { PrivateSosScreen(vm, bn = true, onBack = {}, onOpenAccess = {}, onOpenSession = {}) } }
        composeRule.onNodeWithTag("sos_unavailable").assertExists()
        composeRule.onNodeWithTag("private_sos_hold_button").assertExists()
        save("sos_home")
    }

    @Test
    fun ownerDashboardShowsWhatIsShared() {
        SosRuntime.update {
            it.copy(
                sessionId = "0123456789abcdef0123456789abcdef",
                startedAtMs = System.currentTimeMillis() - 83_000,
                lastFix = LiveFix(23.7806, 90.4070, 9.0, System.currentTimeMillis() - 4_000, 0.4, false),
                lastPublishMs = System.currentTimeMillis() - 4_000,
                gpsProblem = GpsProblem.NONE,
                mic = MicStatus.LIVE,
                listening = setOf("a"),
                push = PushStatus.Unavailable("not_configured")
            )
        }
        val vm = PrivateSosViewModel(app)
        composeRule.setContent { MyApplicationTheme { PrivateSosScreen(vm, bn = true, onBack = {}, onOpenAccess = {}, onOpenSession = {}) } }
        composeRule.onNodeWithTag("sos_owner_header").assertExists()
        composeRule.onNodeWithTag("sos_gps_indicator").assertExists()
        composeRule.onNodeWithTag("sos_mic_indicator").assertExists()
        composeRule.onNodeWithTag("sos_push_status").assertExists()
        save("sos_owner_dashboard")
    }

    @Test
    fun accessScreenRenders() {
        val vm = PrivateSosViewModel(app)
        composeRule.setContent { MyApplicationTheme { SosAccessScreen(vm, bn = true, onBack = {}, onOpenHistory = {}) } }
        composeRule.onNodeWithTag("access_add_person").assertExists()
        composeRule.onNodeWithTag("access_empty").assertExists()
        save("sos_access")
    }

    @Test
    fun monitorWithoutServerIsBlockedNotFaked() {
        val vm = SosMonitorViewModel(app, "0123456789abcdef0123456789abcdef")
        composeRule.setContent { MyApplicationTheme { SosMonitorScreen(vm, bn = true, onBack = {}) } }
        composeRule.onNodeWithTag("monitor_blocked").assertExists()
        save("sos_monitor_blocked")
    }

    @Test
    fun smsStatusLineSaysWhatHappened() {
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.padding(16.dp)) {
                    listOf(SmsStatus.Sending, SmsStatus.Sent(2, 1), SmsStatus.Sent(0, 2), SmsStatus.NeedsPermission).forEach {
                        SmsLine(bn = true, sms = it, onSend = {}, onAllow = {}, onOpenSmsApp = {})
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        // Offered only when the phone could not send by itself: no permission, or every number failed.
        composeRule.onAllNodesWithTag("sos_sms_open_app", useUnmergedTree = true).assertCountEquals(2)
        save("sos_sms_status")
    }

    @Test
    fun smsContactsSheetHasThreeNumberFields() {
        val vm = PrivateSosViewModel(app)
        composeRule.setContent {
            MyApplicationTheme { SmsContactsSheet(vm, bn = true, initial = listOf(TrustedContact("মা", "01712345678")), onDismiss = {}) }
        }
        composeRule.waitForIdle()
        (0..2).forEach { composeRule.onNodeWithTag("sos_sms_phone_$it").assertExists() }
        composeRule.onNodeWithTag("sos_sms_save").assertExists()
    }
}
