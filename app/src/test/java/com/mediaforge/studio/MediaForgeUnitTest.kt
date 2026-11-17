package com.mediaforge.studio

import com.mediaforge.studio.ai.ProductCopy
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.data.DEFAULT_MODEL
import com.mediaforge.studio.data.RECOMMENDED_MODELS
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.MockupRenderer
import com.mediaforge.studio.pdf.DocxBuilder
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
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
        assertEquals("gemini-2.5-flash", DEFAULT_MODEL)
        assertEquals(
            listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"),
            RECOMMENDED_MODELS.map { it.id }
        )
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
}
