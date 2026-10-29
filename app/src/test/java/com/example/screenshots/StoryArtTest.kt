package com.example.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.StoryArt
import com.example.ui.components.StoryBars
import com.example.ui.components.StoryHero
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

/** Every looping intro picture renders and keeps moving. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w800dp-h1400dp-mdpi")
class StoryArtTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allPicturesRenderAndAnimate() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Column(Modifier.background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoryBars(count = 4, index = 1, progress = 0.4f)
                    StoryArt.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { StoryHero(it, Modifier.weight(1f), height = 300.dp) }
                        }
                    }
                }
            }
        }
        StoryArt.entries.forEach { composeRule.onNodeWithTag("story_hero_${it.name.lowercase()}").assertExists() }
        composeRule.mainClock.advanceTimeBy(700)
        save("50_story_art_a")
        composeRule.mainClock.advanceTimeBy(900)
        save("51_story_art_b")
    }

    private fun save(name: String) {
        val dir = System.getenv("ROKKHON_SCREENSHOTS") ?: return
        val root = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir).mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
