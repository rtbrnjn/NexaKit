package com.mediaforge.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NexaKitInstrumentedUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun mainNavigationAndSettingsFeedbackSheet_displayCleanlyOnDevice() {
        composeRule.onNodeWithText("DocPix · Media Lab").assertIsDisplayed()
        composeRule.onNodeWithText("Unggah Foto untuk Memulai").assertIsDisplayed()

        composeRule.onNodeWithText("PDF Suite").performClick()
        composeRule.onNodeWithText("DocPix · PDF Suite").assertIsDisplayed()

        composeRule.onNodeWithText("AI Studio").performClick()
        composeRule.onNodeWithText("DocPix · AI Studio").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Dual-Engine AI Vault").assertIsDisplayed()
        composeRule.onNodeWithText("Laporkan Bug atau Kirim Saran").performScrollTo().performClick()
        composeRule.onNodeWithText("Laporkan Bug atau Saran").assertIsDisplayed()
    }
}
