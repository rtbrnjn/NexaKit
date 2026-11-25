package com.mediaforge.studio

import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.AiRouting
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.GroqClient
import com.mediaforge.studio.ai.ProductCopy
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.DEFAULT_MODEL
import com.mediaforge.studio.data.RECOMMENDED_MODELS
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.data.UpdateChecker
import com.mediaforge.studio.data.migrateStoredModel
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.CollageAspect
import com.mediaforge.studio.media.CollagePhotoMode
import com.mediaforge.studio.media.CollageRatioCategory
import com.mediaforge.studio.media.CollageRenderer
import com.mediaforge.studio.media.CompressOptions
import com.mediaforge.studio.media.CompressionPreset
import com.mediaforge.studio.media.DeviceFrameStyle
import com.mediaforge.studio.media.MockupRenderer
import com.mediaforge.studio.media.STUDIO_SOLID_PRESETS
import com.mediaforge.studio.pdf.DocxBuilder
import com.mediaforge.studio.pdf.LocalOcrEngine
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.ui.screens.settings.FeedbackKind
import com.mediaforge.studio.ui.screens.settings.FeedbackReportFormatter
import com.mediaforge.studio.vm.ExtractDepth
import com.mediaforge.studio.vm.SplitPdfViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class MediaForgeUnitTest {

    @Test
    fun parseRanges_handlesSinglesRangesAndOpenEnd() {
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), PdfEngine.parseRanges("1-3, 5, 8-", 10))
        assertEquals(listOf(0, 1, 2, 3), PdfEngine.parseRanges("all", 4))
        assertEquals(listOf(1, 2, 3), PdfEngine.parseRanges("2 - 4", 5))
    }

    @Test
    fun parseRanges_rejectsInvalidOrOutOfBounds() {
        assertThrows(PdfException::class.java) { PdfEngine.parseRanges("", 5) }
        assertThrows(PdfException::class.java) { PdfEngine.parseRanges("1-9", 5) }
        assertThrows(PdfException::class.java) { PdfEngine.parseRanges("4-2", 5) }
        assertThrows(PdfException::class.java) { PdfEngine.parseRanges("abc", 5) }
    }

    @Test
    fun formatRanges_roundTripsWithParseRanges() {
        assertEquals("1-3, 5, 8-10", SplitPdfViewModel.formatRanges(setOf(0, 1, 2, 4, 7, 8, 9)))
        assertEquals("", SplitPdfViewModel.formatRanges(emptySet()))
    }

    @Test
    fun docxBuilder_createsValidOoxmlArchiveWithEscapedText() {
        val bos = ByteArrayOutputStream()
        DocxBuilder.build(
            pagesText = listOf("Title & Intro <1>\n\nSecond paragraph", "Page two content"),
            out = bos,
            pageBreaks = true
        )
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bos.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/_rels/document.xml.rels"))
        assertTrue(entries.containsKey("word/document.xml"))

        val docXml = entries.getValue("word/document.xml")
        assertTrue(docXml.contains("Title &amp; Intro &lt;1&gt;"))
        assertTrue(docXml.contains("Second paragraph"))
        assertTrue(docXml.contains("Page two content"))
        assertTrue(docXml.contains("""<w:br w:type="page"/>"""))
        assertFalse(docXml.contains("\u0000"))
    }

    @Test
    fun docxBuilder_rejectsBlankPages() {
        assertThrows(PdfException::class.java) {
            DocxBuilder.build(listOf("   ", "\n"), ByteArrayOutputStream())
        }
    }

    @Test
    fun fileUtils_formatsAndSanitizes() {
        assertEquals("0 B", FileUtils.formatBytes(0))
        assertEquals("512 B", FileUtils.formatBytes(512))
        assertEquals("165 KB", FileUtils.formatBytes(165 * 1024L))
        assertEquals("4.5 MB", FileUtils.formatBytes((4.5 * 1024 * 1024).toLong()))
        assertEquals(75, FileUtils.savingsPercent(1000, 250))
        assertEquals("photo_final", FileUtils.baseName("photo:final.jpg"))
    }

    @Test
    fun mockupOutputSize_matchesAspectRatios() {
        assertEquals(1440 to 1440, MockupRenderer.outputSize(CanvasRatio.SQUARE, 1440))
        assertEquals(1440 to 1800, MockupRenderer.outputSize(CanvasRatio.PORTRAIT, 1440))
        assertEquals(1440 to 2560, MockupRenderer.outputSize(CanvasRatio.STORY, 1440))
    }

    @Test
    fun presetsAndEnums_exposeExpectedOptions() {
        assertEquals(listOf("Low", "Balanced", "High"), PdfCompressPreset.entries.map { it.label })
        assertEquals(listOf("System", "Light", "Dark"), ThemeMode.entries.map { it.label })
        assertEquals("gemini-3.8-flash", DEFAULT_MODEL)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.8-flash-lite", "gemini-3.5-flash-lite", "gemini-4-hydrogen"),
            RECOMMENDED_MODELS.map { it.id }
        )
        assertEquals("gemini-3.8-flash", migrateStoredModel("gemini-2.5-flash"))
        assertEquals("gemini-3.8-flash-lite", migrateStoredModel("gemini-2.5-flash-lite"))
        assertEquals("gemini-4-hydrogen", migrateStoredModel("gemini-4-hydrogen"))
        assertEquals("gemini-3.8-flash", migrateStoredModel(null))
    }

    @Test
    fun aiRouting_selectsGroqDefaultAndFallsBackCleanly() {
        assertEquals(AiEngine.GROQ, AiRouting.resolvePrimaryEngine(AiPreference.AUTO, hasGroqKey = true, hasGeminiKey = true))
        assertEquals(AiEngine.GEMINI, AiRouting.resolvePrimaryEngine(AiPreference.AUTO, hasGroqKey = false, hasGeminiKey = true))
        assertEquals(null, AiRouting.resolvePrimaryEngine(AiPreference.AUTO, hasGroqKey = false, hasGeminiKey = false))
        assertEquals(
            listOf(AiEngine.GROQ, AiEngine.GEMINI),
            AiRouting.resolveEngineSequence(AiPreference.AUTO, hasGroqKey = true, hasGeminiKey = true)
        )
        assertEquals(
            listOf(AiEngine.GEMINI, AiEngine.GROQ),
            AiRouting.resolveEngineSequence(AiPreference.GEMINI, hasGroqKey = true, hasGeminiKey = true)
        )
        assertTrue(AiRouting.shouldAttemptFallback(GeminiException(GeminiException.Kind.SERVER, "503"), emittedAny = false))
        assertFalse(AiRouting.shouldAttemptFallback(GeminiException(GeminiException.Kind.SERVER, "503"), emittedAny = true))
        assertEquals("llama-3.3-70b-versatile", GroqClient.TEXT_MODEL)
        assertEquals("meta-llama/llama-4-scout-17b-16e-instruct", GroqClient.VISION_PRIMARY_MODEL)
    }

    @Test
    fun productCopy_formatsHashtagsAndPlainText() {
        val copy = ProductCopy(
            title = "Ceramic Mug",
            hooks = listOf("Hook one", "Hook two", "Hook three"),
            caption = "Keep coffee warm longer.",
            hashtags = listOf("coffee", "#mug")
        )
        assertEquals("#coffee #mug", copy.hashtagLine)
        assertTrue(copy.asPlainText().contains("1. Hook one"))
        assertTrue(copy.asPlainText().contains("#coffee #mug"))
    }

    @Test
    fun productCopy_parseStreamingExtractsSections() {
        val raw = """
            TITLE:
            Minimalist Matte Ceramic Coffee Mug

            HOOKS:
            1. Stop drinking lukewarm coffee at your desk!
            2. This matte mug keeps heat twice as long.
            3. Upgrade your morning routine in seconds.

            CAPTION:
            Built for long workdays with a comfortable handle.
            Grab yours today with free shipping!

            HASHTAGS:
            #coffeemug desksetup ceramic #morningcoffee
        """.trimIndent()
        val parsed = ProductCopy.parseStreaming(raw)
        assertEquals("Minimalist Matte Ceramic Coffee Mug", parsed.title)
        assertEquals(3, parsed.hooks.size)
        assertTrue(parsed.hooks[0].startsWith("Stop drinking"))
        assertTrue(parsed.caption.contains("comfortable handle"))
        assertEquals(listOf("#coffeemug", "#desksetup", "#ceramic", "#morningcoffee"), parsed.hashtags)
        assertEquals(12_000, ExtractDepth.FAST.maxChars)
    }

    @Test
    fun localOcr_truncatesOnlyBeyondLimitAndUsesAdaptiveBudget() {
        assertEquals(1120, LocalOcrEngine.TARGET_WIDTH_PX)
        assertEquals(2000, LocalOcrEngine.MAX_HEIGHT_PX)

        val shortText = "Short invoice page"
        assertEquals(shortText, LocalOcrEngine.truncateIfNeeded(shortText, 100))

        val longText = "A".repeat(150)
        val truncated = LocalOcrEngine.truncateIfNeeded(longText, 100)
        assertTrue(truncated.startsWith("A".repeat(100)))
        assertTrue(truncated.contains("[…OCR truncated at 100 chars]"))
    }

    @Test
    fun feedbackFormatter_formatsMarkdownAndGitHubIssueUrl() {
        val body = FeedbackReportFormatter.formatBody(
            kind = FeedbackKind.SUGGESTION,
            category = "Media Lab",
            title = "Tambah preset Instagram 4:5",
            detail = "Agar ekspor langsung cocok untuk feed.",
            includeDeviceInfo = true,
            appVersion = "DocPix v1.4.2 (Build 9)",
            deviceInfo = "Pixel Test • Android 14 (API 34)"
        )
        assertTrue(body.contains("### Saran Fitur (Media Lab)"))
        assertTrue(body.contains("DocPix v1.4.2 (Build 9)"))
        assertTrue(body.contains("Pixel Test"))

        val url = FeedbackReportFormatter.buildGitHubIssueUrl(
            kind = FeedbackKind.BUG,
            category = "PDF Suite",
            title = "Crash saat merge",
            detail = "Terjadi pada file besar",
            includeDeviceInfo = false,
            deviceInfo = "Ignored"
        )
        assertTrue(url.startsWith("https://github.com/rtbrnjn/NexaKit/issues/new?"))
        assertTrue(url.contains("labels=bug"))
        assertTrue(url.contains("Crash"))
    }

    @Test
    fun docPixBrandingAndUpdateVersionComparison_behaveCorrectly() {
        assertEquals("DocPix", Storage.ROOT)

        assertTrue(UpdateChecker.isNewerVersion("v1.4.3", "1.4.2"))
        assertTrue(UpdateChecker.isNewerVersion("v2.0.0", "1.4.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.4.2.1", "1.4.2"))
        assertTrue(UpdateChecker.isNewerVersion("v1.5.0-rc1", "1.4.2"))
        assertFalse(UpdateChecker.isNewerVersion("v1.4.2", "1.4.2"))
        assertFalse(UpdateChecker.isNewerVersion("v1.4.1", "1.4.2"))
        assertFalse(UpdateChecker.isNewerVersion("", "1.4.2"))
    }

    @Test
    fun pdfFileNamer_generatesCleanDefaultAndNormalizesPdfExtension() {
        val defaultMerged = FileUtils.defaultPdfStem("Merged")
        assertTrue(defaultMerged.startsWith("DocPix_Merged_"))
        assertTrue(defaultMerged.matches(Regex("^DocPix_Merged_\\d{8}$")))

        assertEquals("Laporan_Mei.pdf", FileUtils.normalizePdfFilename("Laporan_Mei", defaultMerged))
        assertEquals("Laporan_Mei.pdf", FileUtils.normalizePdfFilename("Laporan_Mei.pdf", defaultMerged))
        assertEquals("Laporan_Mei.pdf", FileUtils.normalizePdfFilename("Laporan_Mei.PDF", defaultMerged))
        assertEquals("$defaultMerged.pdf", FileUtils.normalizePdfFilename("   ", defaultMerged))
        assertEquals("data_2026_oke.pdf", FileUtils.normalizePdfFilename("data/2026:oke.", defaultMerged))
    }

    @Test
    fun collageAndMockupRatiosAndOptions_matchSocialMediaAndFrameSpecs() {
        assertEquals(2160 to 2160, CollageRenderer.outputSize(CollageAspect.SQUARE, 2160))
        assertEquals(2160 to 2700, CollageRenderer.outputSize(CollageAspect.PORTRAIT_4_5, 2160))
        assertEquals(2160 to 3840, CollageRenderer.outputSize(CollageAspect.PORTRAIT_9_16, 2160))
        assertEquals(2160 to 1215, CollageRenderer.outputSize(CollageAspect.LANDSCAPE_16_9, 2160))
        assertEquals(2160 to 1620, CollageRenderer.outputSize(CollageAspect.LANDSCAPE_4_3, 2160))
        assertEquals(
            listOf(CollageRatioCategory.SQUARE, CollageRatioCategory.PORTRAIT, CollageRatioCategory.LANDSCAPE),
            CollageRatioCategory.entries
        )
        assertEquals(listOf(CollagePhotoMode.FILL, CollagePhotoMode.FIT), CollagePhotoMode.entries)

        assertEquals(1440 to 810, MockupRenderer.outputSize(CanvasRatio.LANDSCAPE, 1440))
        assertEquals(
            listOf(
                DeviceFrameStyle.FLAT_SMARTPHONE,
                DeviceFrameStyle.FRAMELESS_GLASS,
                DeviceFrameStyle.BROWSER_WINDOW,
                DeviceFrameStyle.TABLET
            ),
            DeviceFrameStyle.entries
        )
        assertTrue(STUDIO_SOLID_PRESETS.any { it.color == 0xFFFFD1DC.toInt() && it.label.contains("Pink", ignoreCase = true) })
        assertTrue(STUDIO_SOLID_PRESETS.any { it.color == 0xFF0B0E13.toInt() })
        assertTrue(STUDIO_SOLID_PRESETS.any { it.color == 0xFFFFFFFF.toInt() })
    }

    @Test
    fun pagerGestureCoordinator_tracksConcurrentLocksSafely() {
        val coord = com.mediaforge.studio.ui.components.PagerGestureCoordinator()
        assertFalse(coord.isLocked)

        val relA = coord.acquire()
        assertTrue(coord.isLocked)

        val relB = coord.acquire()
        assertTrue(coord.isLocked)

        // Calling release twice on relA must not drop relB's active lock
        relA()
        relA()
        assertTrue(coord.isLocked)

        relB()
        assertFalse(coord.isLocked)
    }

    @Test
    fun imageToPdfPageModesAndCompressorPresets_matchV144Specs() {
        assertEquals(
            listOf("Sesuai Ukuran Foto", "A4 Portrait", "A4 Landscape"),
            PdfPageMode.entries.map { it.label }
        )
        assertEquals(PdfPageMode.A4_PORTRAIT, PdfPageMode.fromStored("A4"))
        assertEquals(PdfPageMode.A4_LANDSCAPE, PdfPageMode.fromStored("A4_LANDSCAPE"))
        assertEquals(PdfPageMode.FIT, PdfPageMode.fromStored("FIT"))
        assertEquals(PdfPageMode.A4_PORTRAIT, PdfPageMode.fromStored(null))

        val (pw, ph) = PdfEngine.pageSizeFor(PdfPageMode.A4_PORTRAIT, 1200, 800)
        val (lw, lh) = PdfEngine.pageSizeFor(PdfPageMode.A4_LANDSCAPE, 1200, 800)
        assertTrue(ph > pw)
        assertTrue(lw > lh)
        assertEquals(pw, lh, 0.01f)
        assertEquals(ph, lw, 0.01f)

        val (fw, fh) = PdfEngine.pageSizeFor(PdfPageMode.FIT, 1500, 750)
        assertEquals(720f, fw, 0.01f)
        assertEquals(360f, fh, 0.01f)

        assertEquals(
            listOf("Kecil 40%" to 40, "Seimbang 70%" to 70, "Tajam 85%" to 85),
            CompressionPreset.entries.map { it.label to it.quality }
        )
        assertTrue(CompressOptions().stripExif)
    }

    @Test
    fun liquidGlassBlurGate_enablesNativeRenderEffectOnlyOnApi31Plus() {
        assertFalse(com.mediaforge.studio.ui.components.supportsNativeLiquidGlassBlur(26))
        assertFalse(com.mediaforge.studio.ui.components.supportsNativeLiquidGlassBlur(30))
        assertTrue(com.mediaforge.studio.ui.components.supportsNativeLiquidGlassBlur(31))
        assertTrue(com.mediaforge.studio.ui.components.supportsNativeLiquidGlassBlur(36))
    }

    @Test
    fun fileUtilsCreateZip_packsMultipleFilesAndDeduplicatesNames() {
        val f1 = java.io.File.createTempFile("docpix_zip_a_", ".jpg").apply {
            writeBytes("photo-one-bytes".toByteArray())
            deleteOnExit()
        }
        val f2 = java.io.File.createTempFile("docpix_zip_b_", ".jpg").apply {
            writeBytes("photo-two-bytes".toByteArray())
            deleteOnExit()
        }
        try {
            val bos = ByteArrayOutputStream()
            val count = FileUtils.createZip(listOf("produk.jpg" to f1, "produk.jpg" to f2), bos)
            assertEquals(2, count)

            val entries = LinkedHashMap<String, String>()
            ZipInputStream(ByteArrayInputStream(bos.toByteArray())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                    entry = zis.nextEntry
                }
            }
            assertEquals("photo-one-bytes", entries["produk.jpg"])
            assertEquals("photo-two-bytes", entries["produk (1).jpg"])
        } finally {
            f1.delete()
            f2.delete()
        }
    }
}
