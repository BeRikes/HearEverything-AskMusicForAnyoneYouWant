package com.example.aimusicplayer.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class BackPressHandlerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun back_press_handler_does_not_crash() {
        compose.setContent {
            BackPressHandler(onBack = { })
            Text("Screen content")
        }
        compose.onNode(hasText("Screen content")).assertIsDisplayed()
    }
}
