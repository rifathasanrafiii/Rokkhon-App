package com.example.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import com.example.RokkhonAppRoot
import com.example.data.model.AppLanguage
import com.example.ui.components.HardwareSosCountdownOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.RokkhonTutorialScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PendingHardwareSos
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

/**
 * Renders every main page of the app with Robolectric's native graphics and writes PNGs.
 * Opt-in: set ROKKHON_SCREENSHOTS=/path/to/output/dir, e.g.
 *   ROKKHON_SCREENSHOTS=$PWD/screenshots ./gradlew testDebugUnitTest --tests '*ScreenshotTourTest*'
 * Without Firebase config the app runs in demo mode, so the shots show the sample community.
 * The street map is a WebView and cannot render here; the built-in vector map underneath is shown instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain JDK TLS so map tile downloads use the JVM trust store and proxy settings.
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotTourTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File? = System.getenv("ROKKHON_SCREENSHOTS")?.let { File(it) }

    @Before
    fun setUp() {
        assumeTrue("Set ROKKHON_SCREENSHOTS to render screenshots", outDir != null)
        outDir!!.mkdirs()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("rokkhon_app_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("tutorial_done", true)
            .putBoolean("startup_permissions_asked", true)
            .putString("app_language", System.getenv("ROKKHON_SCREENSHOT_LANG") ?: "bn")
            .commit()
    }

    private fun save(name: String) {
        composeRule.waitForIdle()
        val root = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        // Compose dialogs live in their own window: draw the latest one on top of a dimmed screen.
        val dialog = ShadowDialog.getLatestDialog()
        if (dialog != null && dialog.isShowing) {
            canvas.drawColor(android.graphics.Color.argb(150, 0, 0, 0))
            val dialogView = dialog.window!!.decorView
            canvas.save()
            canvas.translate(
                ((root.width - dialogView.width) / 2f).coerceAtLeast(0f),
                ((root.height - dialogView.height) / 2f).coerceAtLeast(0f)
            )
            dialogView.draw(canvas)
            canvas.restore()
        }
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        writeTagBounds(name, root.width, root.height)
    }

    /**
     * Writes where every tagged element sits on this screenshot ($name.tags.json), so a
     * click-through preview can put its tap areas exactly on the real buttons.
     */
    private fun writeTagBounds(name: String, rootWidth: Int, rootHeight: Int) {
        val hasTag = SemanticsMatcher("has test tag") { it.config.contains(SemanticsProperties.TestTag) }
        val nodes = composeRule.onAllNodes(hasTag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val entries = nodes.mapNotNull { node ->
            var top = node
            while (top.parent != null) top = top.parent!!
            val rootSize = top.size
            // Dialog windows are drawn centred over the screen (see save()).
            val dx = if (rootSize.width < rootWidth) (rootWidth - rootSize.width) / 2f else 0f
            val dy = if (rootSize.height < rootHeight) (rootHeight - rootSize.height) / 2f else 0f
            val b = node.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) return@mapNotNull null
            val tag = node.config[SemanticsProperties.TestTag].replace("\"", "")
            "{\"tag\":\"$tag\",\"x\":${(b.left + dx).toInt()},\"y\":${(b.top + dy).toInt()},\"w\":${b.width.toInt()},\"h\":${b.height.toInt()},\"dialog\":${dx != 0f || dy != 0f}}"
        }
        File(outDir, "$name.tags.json").writeText("[" + entries.joinToString(",") + "]")
    }


    private fun startApp() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MyApplicationTheme { RokkhonAppRoot() } }
        repeat(40) {
            if (composeRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.mainClock.advanceTimeBy(600)
                return
            }
            // Another test may have initialised Firebase in this JVM: continue as a guest.
            if (composeRule.onAllNodesWithTag("skip_sign_in_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("skip_sign_in_button").performClick()
            }
            composeRule.mainClock.advanceTimeBy(250)
            composeRule.waitForIdle()
        }
        save("debug_start_failed")
        error("Home screen did not appear")
    }

    private fun showOnly(content: @Composable () -> Unit) {
        composeRule.setContent { MyApplicationTheme { content() } }
        composeRule.waitForIdle()
    }

    /** Scrolls the screen content up by about half a screen. */
    private fun swipeUp() {
        composeRule.onAllNodes(androidx.compose.ui.test.isRoot())[0].performTouchInput {
            swipe(start = Offset(centerX, height * 0.70f), end = Offset(centerX, height * 0.25f), durationMillis = 300)
        }
        composeRule.mainClock.advanceTimeBy(1_200)
    }

    private fun tap(tag: String): Boolean {
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) return false
        composeRule.onAllNodesWithTag(tag)[0].performClick()
        composeRule.mainClock.advanceTimeBy(900)
        return true
    }

    /** Lets osmdroid fetch and draw map tiles on its background threads. */
    private fun waitForMapTiles() {
        val root = composeRule.activity.window.decorView
        val scratch = Canvas(Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888))
        repeat(16) {
            // osmdroid requests tiles while drawing; nothing draws in a test unless we do.
            root.draw(scratch)
            Thread.sleep(500)
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun home() {
        startApp()
        save("01_home")
        swipeUp()
        save("02_home_scrolled")
        swipeUp()
        save("03_home_scrolled_more")
    }

    @Test
    fun find() {
        startApp()
        tap("nav_tab_map")
        waitForMapTiles()
        save("04_find_donors")
        tap("map_filter_tab_patient_requests")
        waitForMapTiles()
        save("05_find_patients")
        tap("map_filter_tab_sos_alerts")
        waitForMapTiles()
        save("06_find_sos")
    }

    @Test
    fun sos() {
        startApp()
        tap("nav_tab_sos")
        save("07_sos")
        swipeUp()
        save("08_sos_scrolled")
        tap("nav_tab_sos")
        tap("sos_guardian_main_button")
        composeRule.mainClock.advanceTimeBy(800)
        save("09_sos_send_dialog")
    }

    @Test
    fun profile() {
        startApp()
        tap("nav_tab_profile")
        save("10_profile")
        swipeUp()
        save("11_profile_scrolled")
        swipeUp()
        save("12_profile_scrolled_more")
    }

    @Test
    fun bloodRequestForm() {
        startApp()
        tap("nav_center_plus_button")
        save("13_blood_request_form")
        // Semantic click: the submit button is below the fold of the form.
        composeRule.onNodeWithTag("submit_blood_request_button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeBy(600)
        save("13b_blood_request_validation")
    }

    @Test
    fun notificationsAndArea() {
        startApp()
        tap("notification_bell_button")
        save("14_notifications")
    }

    @Test
    fun areaPicker() {
        startApp()
        tap("area_dropdown_selector")
        save("15_area_picker")
    }

    @Test
    fun hardwareCountdown() {
        showOnly {
            HardwareSosCountdownOverlay(
                language = AppLanguage.BENGALI,
                pending = PendingHardwareSos("3X_POWER_BUTTON", 4),
                liveDecibels = 63,
                onCancel = {},
                onSendNow = {}
            )
        }
        save("16_hardware_sos_countdown")
    }

    @Test
    fun tutorial() {
        showOnly { RokkhonTutorialScreen(onFinishTutorial = {}) }
        save("17_tutorial")
    }

    @Test
    fun signIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:0:android:0")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-screenshots")
                    .build()
            )
        }
        showOnly { AuthScreen(onAuthSuccess = {}, onSkipSignIn = {}) }
        save("18_sign_in")
    }
}
