package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.trimvideopro.localization.EnglishStrings
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.trimvideopro.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class HomeScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun home_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        CompositionLocalProvider(LocalAppStrings provides EnglishStrings) {
          HomeScreen(exportedClipCount = 3, onOpenTool = {})
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/home.png")
  }
}
