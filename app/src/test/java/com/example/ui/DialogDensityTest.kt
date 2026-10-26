package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.FullScreenDialog
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pages in their own window (dialogs, sheets) must get the same compact scale as the screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class DialogDensityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialogsAndSheetsUseTheAppScale() {
        var screen = 0f
        var dialog = 0f
        var sheet = 0f
        composeRule.setContent {
            MyApplicationTheme {
                val d = LocalDensity.current
                SideEffect { screen = d.density * d.fontScale }
                FullScreenDialog(onDismiss = {}) {
                    val dd = LocalDensity.current
                    SideEffect { dialog = dd.density * dd.fontScale }
                }
                RokkhonBottomSheet(onDismiss = {}) {
                    val sd = LocalDensity.current
                    SideEffect { sheet = sd.density * sd.fontScale }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(screen, dialog, 0.001f)
        assertEquals(screen, sheet, 0.001f)
    }
}
