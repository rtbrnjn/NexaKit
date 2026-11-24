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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
        // 1. Starts on clean upload-first Media Lab without tool menu clutter (includes Document Scanner shortcut & Recent Creations)
        composeRule.onNodeWithText("DocPix · Media Lab").assertIsDisplayed()
        composeRule.onNodeWithText("Unggah Foto untuk Memulai").assertIsDisplayed()
        composeRule.onNodeWithText("Pilih Foto dari Galeri").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Dokumen (Kamera)").assertIsDisplayed()
        composeRule.onAllNodesWithText("Recent Creations")[0].performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Compression Settings").assertDoesNotExist()

        // 2. Test HorizontalPager swipe left/right between tabs as well as bottom bar tap
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("DocPix · PDF Suite").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Document").assertIsDisplayed()
        composeRule.onAllNodesWithText("Recent Creations")[1].performScrollTo().assertIsDisplayed()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("DocPix · AI Studio").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.onNodeWithText("DocPix · PDF Suite").assertIsDisplayed()

        composeRule.onNodeWithText("Merge PDFs").performScrollTo().performClick()
        composeRule.onNodeWithText("Add PDF Files").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("DocPix · PDF Suite").assertIsDisplayed()

        // 3. Navigate to AI Studio and switch to AI Vault tab
        composeRule.onNodeWithText("AI Studio").performClick()
        composeRule.onNodeWithText("DocPix · AI Studio").assertIsDisplayed()
        composeRule.onNodeWithText("AI Vault").performClick()
        composeRule.onNodeWithText("AI History Vault is empty").assertIsDisplayed()

        // 4. Navigate to Settings, verify About DocPix + Update Checker button, API Key Guide & Feedback Sheet
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("DocPix · Settings").assertIsDisplayed()
        composeRule.onNodeWithText("DocPix v1.4.4 (Build 11)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cek Pembaruan").performScrollTo().assertIsDisplayed()

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
                                title = "DocPix · UI Check",
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

        composeRule.onNodeWithText("DocPix · UI Check").assertIsDisplayed()
        composeRule.onNodeWithText("Active: Groq").assertIsDisplayed()
        composeRule.onNodeWithText("Groq LPU Ready").assertIsDisplayed()

        composeRule.onNodeWithText("Gemini").performClick()
        composeRule.onNodeWithText("Active: Gemini").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Mode").assertIsDisplayed()
    }

    @Test
    fun storagePathsAndGithubUpdateParser_resolveDocPixCorrectly() {
        val docPath = com.mediaforge.studio.core.Storage.folderPath(com.mediaforge.studio.core.OutputKind.DOCUMENT)
        val imgPath = com.mediaforge.studio.core.Storage.folderPath(com.mediaforge.studio.core.OutputKind.IMAGE)
        org.junit.Assert.assertTrue(docPath.endsWith("/DocPix"))
        org.junit.Assert.assertTrue(imgPath.endsWith("/DocPix"))

        val releaseJson = """
            {
              "tag_name": "v1.5.0",
              "name": "DocPix v1.5.0",
              "body": "- Faster Document Scanner\n- Improved monochrome glass",
              "html_url": "https://github.com/rtbrnjn/DocPix/releases/tag/v1.5.0",
              "assets": [
                {
                  "name": "app-release.apk",
                  "browser_download_url": "https://example.com/app-release.apk"
                },
                {
                  "name": "DocPix.apk",
                  "browser_download_url": "https://example.com/DocPix.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = com.mediaforge.studio.data.UpdateChecker.parseReleaseJson(releaseJson)
        org.junit.Assert.assertEquals("v1.5.0", parsed?.tagName)
        org.junit.Assert.assertEquals("1.5.0", parsed?.versionName)
        org.junit.Assert.assertEquals("https://example.com/DocPix.apk", parsed?.apkDownloadUrl)
        org.junit.Assert.assertEquals("https://github.com/rtbrnjn/DocPix/releases/tag/v1.5.0", parsed?.htmlUrl)
    }

    @Test
    fun pdfRenameDialog_prefillsDefaultAndSubmitsEditedName() {
        val tempFile = java.io.File.createTempFile("docpix_test_", ".pdf").apply {
            writeBytes(ByteArray(1024))
            deleteOnExit()
        }
        var committedName: String? = null
        val pending = PendingPdfSave(
            tempFile = tempFile,
            defaultStem = "DocPix_Merged_20261123",
            toolName = "Merge PDFs"
        )

        composeRule.setContent {
            MediaForgeTheme(themeMode = ThemeMode.DARK) {
                com.mediaforge.studio.ui.screens.PdfRenameDialog(
                    pending = pending,
                    saving = false,
                    onConfirm = { committedName = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Simpan File PDF").assertIsDisplayed()
        composeRule.onNodeWithText("DocPix_Merged_20261123").performTextReplacement("Invoice_November")
        composeRule.onNodeWithText("Documents/DocPix/Invoice_November.pdf", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Simpan PDF").performClick()

        org.junit.Assert.assertEquals("Invoice_November", committedName)
        tempFile.delete()
    }

    @Test
    fun mockupAndCollageRenderers_renderAllFramesBlurAndAspectRatiosCleanly() {
        val src = android.graphics.Bitmap.createBitmap(120, 200, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(66, 133, 244))
        }
        val second = android.graphics.Bitmap.createBitmap(200, 120, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(52, 168, 83))
        }

        try {
            com.mediaforge.studio.media.DeviceFrameStyle.entries.forEach { frame ->
                val out = com.mediaforge.studio.media.MockupRenderer.render(
                    product = src,
                    p = com.mediaforge.studio.media.MockupParams(
                        ratio = com.mediaforge.studio.media.CanvasRatio.LANDSCAPE,
                        frameStyle = frame,
                        background = com.mediaforge.studio.media.MockBackground.BlurredImage,
                        glassReflection = true,
                        watermark = "@docpix"
                    ),
                    outWidth = 320
                )
                org.junit.Assert.assertEquals(320, out.width)
                org.junit.Assert.assertEquals(180, out.height)
                out.recycle()
            }

            val collagePortrait = com.mediaforge.studio.media.CollageRenderer.render(
                photos = listOf(src, second),
                p = com.mediaforge.studio.media.CollageParams(
                    aspect = com.mediaforge.studio.media.CollageAspect.PORTRAIT_9_16,
                    photoMode = com.mediaforge.studio.media.CollagePhotoMode.FIT
                ),
                outWidth = 360
            )
            org.junit.Assert.assertEquals(360, collagePortrait.width)
            org.junit.Assert.assertEquals(640, collagePortrait.height)
            collagePortrait.recycle()

            val collageWide = com.mediaforge.studio.media.CollageRenderer.render(
                photos = listOf(src, second),
                p = com.mediaforge.studio.media.CollageParams(
                    aspect = com.mediaforge.studio.media.CollageAspect.LANDSCAPE_16_9,
                    photoMode = com.mediaforge.studio.media.CollagePhotoMode.FILL
                ),
                outWidth = 320
            )
            org.junit.Assert.assertEquals(320, collageWide.width)
            org.junit.Assert.assertEquals(180, collageWide.height)
            collageWide.recycle()
        } finally {
            src.recycle()
            second.recycle()
        }
    }

    @Test
    fun labeledSliderInsideHorizontalPager_locksPagerSwipeDuringDrag() {
        val coord = com.mediaforge.studio.ui.components.PagerGestureCoordinator()
        var settledPage = -1

        composeRule.setContent {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1, pageCount = { 3 })
            var sliderValue by remember { mutableStateOf(50f) }
            settledPage = pagerState.currentPage

            MediaForgeTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(
                    com.mediaforge.studio.ui.components.LocalPagerGestureCoordinator provides coord
                ) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !coord.isLocked
                    ) { page ->
                        Column {
                            com.mediaforge.studio.ui.components.SectionLabel("Page $page")
                            if (page == 1) {
                                com.mediaforge.studio.ui.components.LabeledSlider(
                                    label = "Padding Test",
                                    valueText = "${sliderValue.toInt()}%",
                                    value = sliderValue,
                                    range = 0f..100f,
                                    onChange = { sliderValue = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Swiping horizontally directly on LabeledSlider must stay on page 1 instead of flipping to page 0/2
        composeRule.onNodeWithText("Padding Test").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(1, settledPage)
        org.junit.Assert.assertFalse(coord.isLocked)
    }

    @Test
    fun recentCreationsCard_capsAtFiveItemsAndTriggersRemoveCallback() {
        val items = (1..7).map { idx ->
            com.mediaforge.studio.core.SavedFile(
                uri = android.net.Uri.parse("content://com.mediaforge.studio.fileprovider/doc/$idx"),
                name = "DocPix_File_$idx.pdf",
                mime = "application/pdf",
                sizeBytes = idx * 1024L * 100L,
                folder = "Documents/DocPix",
                tool = "Merge PDFs",
                timestamp = 1_795_000_000_000L + idx * 60_000L
            )
        }
        var removed: com.mediaforge.studio.core.SavedFile? = null

        composeRule.setContent {
            MediaForgeTheme(themeMode = ThemeMode.DARK) {
                com.mediaforge.studio.ui.components.RecentCreationsCard(
                    files = items,
                    onRemove = { removed = it }
                )
            }
        }

        composeRule.onNodeWithText("Recent Creations").assertIsDisplayed()
        composeRule.onNodeWithText("5 terakhir").assertIsDisplayed()
        composeRule.onNodeWithText("DocPix_File_1.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("DocPix_File_5.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("DocPix_File_6.pdf").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Hapus DocPix_File_1.pdf dari riwayat").performClick()
        org.junit.Assert.assertEquals("DocPix_File_1.pdf", removed?.name)
    }
}
