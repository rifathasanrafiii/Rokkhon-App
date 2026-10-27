package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.model.AppLanguage
import com.example.ui.components.PostBloodRequestDialog
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * A small phone with a large display size and the biggest font setting: the request form must
 * still keep its send button on screen and its rows on one line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w340dp-h700dp-xxhdpi")
class LargeFontLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun bigFont() {
        RuntimeEnvironment.setFontScale(1.3f)
    }

    @Test
    fun requestFormFitsWithLargeFont() {
        composeRule.setContent {
            MyApplicationTheme {
                PostBloodRequestDialog(
                    language = AppLanguage.ENGLISH,
                    defaultDistrict = "Mirpur 10, Dhaka",
                    defaultHospital = "",
                    onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("submit_blood_request_button").assertIsDisplayed()
        // "1 bag" stays on one line: no taller than the stepper's buttons.
        val bags = composeRule.onNodeWithTag("bags_value").fetchSemanticsNode().size.height
        val plus = composeRule.onNodeWithTag("bags_plus").fetchSemanticsNode().size.height
        assertTrue("bags label wrapped ($bags > $plus)", bags <= plus)

        System.getenv("ROKKHON_SCREENSHOTS")?.let { dir ->
            val view = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(dir).mkdirs()
            FileOutputStream(File(dir, "22_request_form_large_font.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
