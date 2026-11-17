package com.mediaforge.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.LiquidGlassBackdrop
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.OfflineBadge
import com.mediaforge.studio.ui.components.OnlineBadge
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.ui.theme.MediaForgeTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class NexaKitMainNavigationUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun initHilt() {
        hiltRule.inject()
    }

    @Test
    fun bottomBarUploadFirstFlowAndFeedbackSheet_workEndToEnd() {
        // 1. Starts on clean upload-first Media Lab without tool menu clutter
        composeRule.onNodeWithText("NexaKit · Media Lab").assertIsDisplayed()
        composeRule.onNodeWithText("Unggah Foto untuk Memulai").assertIsDisplayed()
        composeRule.onNodeWithText("Pilih Foto dari Galeri").assertIsDisplayed()
        composeRule.onNodeWithText("Compression Settings").assertDoesNotExist()

        // 2. Navigate to PDF Suite, open Merge PDFs, then navigate back to Hub
        composeRule.onNodeWithText("PDF Suite").performClick()
        composeRule.onNodeWithText("NexaKit · PDF Suite").assertIsDisplayed()
        composeRule.onNodeWithText("Merge PDFs").performScrollTo().performClick()
        composeRule.onNodeWithText("Add PDF Files").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("NexaKit · PDF Suite").assertIsDisplayed()

        // 3. Navigate to AI Studio and switch to AI Vault tab
        composeRule.onNodeWithText("AI Studio").performClick()
        composeRule.onNodeWithText("NexaKit · AI Studio").assertIsDisplayed()
        composeRule.onNodeWithText("AI Vault").performClick()
        composeRule.onNodeWithText("AI History Vault is empty").assertIsDisplayed()

        // 4. Navigate to Settings, open Interactive API Key Guide, then Bug/Feedback Sheet
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("NexaKit · Settings").assertIsDisplayed()
        composeRule.onNodeWithText("NexaKit v1.4.1 (Build 8)").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Cara Mendapatkan API Key Gratis?").performScrollTo().performClick()
        composeRule.onNodeWithText("Cara Mendapatkan API Key Gratis").assertIsDisplayed()
        composeRule.onNodeWithText("Google Gemini").performClick()
        composeRule.onNodeWithText("Buka Google AI Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Tutup Panduan").performClick()

        composeRule.onNodeWithText("Laporkan Bug atau Kirim Saran").performScrollTo().performClick()
        composeRule.onNodeWithText("Laporkan Bug atau Saran").assertIsDisplayed()
        composeRule.onNodeWithText("Salin Laporan").assertIsNotEnabled()
        composeRule.onNodeWithText("Saran Fitur").performClick()
        composeRule.onNodeWithText("Judul Singkat").performTextInput("Tambah batch rename")
        composeRule.onNodeWithText("Detail Saran / Ide Fitur").performTextInput("Otomatis ubah nama output PDF.")
        composeRule.onNodeWithText("Salin Laporan").assertIsEnabled()
        composeRule.onNodeWithText("Kirim GitHub").assertIsEnabled()
        composeRule.onNodeWithText("Tutup").performClick()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LiquidGlassComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private enum class DemoTab(val label: String, val icon: ImageVector) {
        FIRST("Groq", Icons.Rounded.Bolt),
        SECOND("Gemini", Icons.Rounded.AutoAwesome)
    }

    @Test
    fun segmentedBarAndBadges_updateSelectionAndRenderInLightAndDarkModes() {
        composeRule.setContent {
            var selectedTab by remember { mutableStateOf(DemoTab.FIRST) }
            var themeMode by remember { mutableStateOf(ThemeMode.DARK) }

            MediaForgeTheme(themeMode = themeMode) {
                CompositionLocalProvider {
                    LiquidGlassBackdrop {
                        Column {
                            LiquidGlassTopHeader(
                                title = "NexaKit · UI Check",
                                subtitle = "Active: ${selectedTab.label}",
                                trailing = {
                                    if (selectedTab == DemoTab.FIRST) {
                                        OnlineBadge(label = "Groq LPU Ready", accent = Glass.Emerald)
                                    } else {
                                        OfflineBadge(compact = true)
                                    }
                                }
                            )
                            GlassSegmentedBar(
                                items = DemoTab.entries,
                                selected = selectedTab,
                                label = { it.label },
                                icon = { it.icon },
                                onSelect = {
                                    selectedTab = it
                                    themeMode = if (it == DemoTab.SECOND) ThemeMode.LIGHT else ThemeMode.DARK
                                }
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("NexaKit · UI Check").assertIsDisplayed()
        composeRule.onNodeWithText("Active: Groq").assertIsDisplayed()
        composeRule.onNodeWithText("Groq LPU Ready").assertIsDisplayed()

        composeRule.onNodeWithText("Gemini").performClick()
        composeRule.onNodeWithText("Active: Gemini").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Mode").assertIsDisplayed()
    }
}
