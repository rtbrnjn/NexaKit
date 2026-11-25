package com.mediaforge.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.RecentCreationsCard
import com.mediaforge.studio.ui.screens.pdf.CompressPdfScreen
import com.mediaforge.studio.ui.screens.pdf.DocumentScannerScreen
import com.mediaforge.studio.ui.screens.pdf.EditPdfScreen
import com.mediaforge.studio.ui.screens.pdf.ExtractPdfTextScreen
import com.mediaforge.studio.ui.screens.pdf.ImagesToPdfScreen
import com.mediaforge.studio.ui.screens.pdf.MergePdfScreen
import com.mediaforge.studio.ui.screens.pdf.PageNumbersScreen
import com.mediaforge.studio.ui.screens.pdf.PdfToDocxScreen
import com.mediaforge.studio.ui.screens.pdf.PdfToImagesScreen
import com.mediaforge.studio.ui.screens.pdf.ProtectPdfScreen
import com.mediaforge.studio.ui.screens.pdf.SplitPdfScreen
import com.mediaforge.studio.ui.screens.pdf.TextToPdfScreen
import com.mediaforge.studio.ui.screens.pdf.UnlockPdfScreen
import com.mediaforge.studio.ui.screens.pdf.WatermarkPdfScreen
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.CompressPdfViewModel
import com.mediaforge.studio.vm.DocumentScannerViewModel
import com.mediaforge.studio.vm.EditPdfViewModel
import com.mediaforge.studio.vm.ExtractPdfTextViewModel
import com.mediaforge.studio.vm.ImagesToPdfViewModel
import com.mediaforge.studio.vm.MergePdfViewModel
import com.mediaforge.studio.vm.PageNumbersViewModel
import com.mediaforge.studio.vm.PdfSource
import com.mediaforge.studio.vm.PdfToDocxViewModel
import com.mediaforge.studio.vm.PdfToImagesViewModel
import com.mediaforge.studio.vm.ProtectPdfViewModel
import com.mediaforge.studio.vm.SplitPdfViewModel
import com.mediaforge.studio.vm.TextToPdfViewModel
import com.mediaforge.studio.vm.UnlockPdfViewModel
import com.mediaforge.studio.vm.WatermarkPdfViewModel
import dev.chrisbanes.haze.hazeSource

private enum class ToolTone { PRIMARY, SECONDARY, TERTIARY }

enum class PdfTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    private val tone: ToolTone
) {
    SCAN_DOCUMENT("Scan Document", "Auto-crop paper to PDF/JPG", Icons.Rounded.DocumentScanner, ToolTone.PRIMARY),
    EXTRACT_TEXT("Ekstrak Teks PDF", "Salin teks & OCR lokal ke AI", Icons.Rounded.TextFields, ToolTone.SECONDARY),
    PDF_TO_WORD("PDF to Word", "Extract text to .docx", Icons.Rounded.Description, ToolTone.PRIMARY),
    WORD_TO_PDF("Word to PDF", ".docx, Markdown or text", Icons.AutoMirrored.Rounded.TextSnippet, ToolTone.SECONDARY),
    IMAGES_TO_PDF("Images to PDF", "Photos to A4 or fit PDF", Icons.Rounded.PictureAsPdf, ToolTone.PRIMARY),
    PDF_TO_IMAGES("PDF to Images", "Render pages to JPG/PNG", Icons.Rounded.Image, ToolTone.SECONDARY),
    MERGE("Merge PDFs", "Combine & reorder files", Icons.AutoMirrored.Rounded.MergeType, ToolTone.PRIMARY),
    SPLIT("Split / Extract", "Visual page range picker", Icons.Rounded.ContentCut, ToolTone.TERTIARY),
    COMPRESS("Compress PDF", "Low, Balanced or High", Icons.Rounded.Compress, ToolTone.SECONDARY),
    ROTATE_DELETE("Rotate & Delete", "Fix orientation or drop", Icons.Rounded.Rotate90DegreesCw, ToolTone.PRIMARY),
    PAGE_NUMBERS("Page Numbers", "Stamp 1 of N cleanly", Icons.Rounded.FormatListNumbered, ToolTone.SECONDARY),
    WATERMARK("Text Watermark", "Diagonal translucent ink", Icons.Rounded.WaterDrop, ToolTone.TERTIARY),
    PROTECT("Protect PDF", "Encrypt with password", Icons.Rounded.Lock, ToolTone.PRIMARY),
    UNLOCK("Unlock PDF", "Remove PDF password", Icons.Rounded.LockOpen, ToolTone.TERTIARY);

    @Composable
    fun accentColor(): Color = when (tone) {
        ToolTone.PRIMARY -> Glass.Cyan
        ToolTone.SECONDARY -> Glass.Emerald
        ToolTone.TERTIARY -> Glass.Amber
    }
}

@Composable
fun PdfSuiteScreen(
    scannerVm: DocumentScannerViewModel = viewModel(),
    extractTextVm: ExtractPdfTextViewModel = viewModel(),
    pdfToDocxVm: PdfToDocxViewModel = viewModel(),
    textToPdfVm: TextToPdfViewModel = viewModel(),
    imagesToPdfVm: ImagesToPdfViewModel = viewModel(),
    pdfToImagesVm: PdfToImagesViewModel = viewModel(),
    mergeVm: MergePdfViewModel = viewModel(),
    splitVm: SplitPdfViewModel = viewModel(),
    compressVm: CompressPdfViewModel = viewModel(),
    editVm: EditPdfViewModel = viewModel(),
    pageNumbersVm: PageNumbersViewModel = viewModel(),
    watermarkVm: WatermarkPdfViewModel = viewModel(),
    protectVm: ProtectPdfViewModel = viewModel(),
    unlockVm: UnlockPdfViewModel = viewModel(),
    requestedTool: PdfTool? = null,
    onToolHandled: () -> Unit = {},
    isTabActive: Boolean = true,
    recentFiles: List<SavedFile> = emptyList(),
    onRemoveRecent: (SavedFile) -> Unit = {},
    onSendExtractedToAi: (PdfSource, String, Boolean) -> Unit = { _, _, _ -> }
) {
    var activeTool by rememberSaveable { mutableStateOf<PdfTool?>(null) }

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            activeTool = requestedTool
            onToolHandled()
        }
    }

    BackHandler(enabled = isTabActive && activeTool != null) { activeTool = null }

    AnimatedContent(
        targetState = activeTool,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(180)))
        },
        label = "pdfToolTransition"
    ) { tool ->
        when (tool) {
            null -> PdfHubGrid(
                onSelect = { activeTool = it },
                recentFiles = recentFiles,
                onRemoveRecent = onRemoveRecent
            )
            PdfTool.SCAN_DOCUMENT -> DocumentScannerScreen(scannerVm, onBack = { activeTool = null })
            PdfTool.EXTRACT_TEXT -> ExtractPdfTextScreen(
                vm = extractTextVm,
                onBack = { activeTool = null },
                onSendToAi = onSendExtractedToAi
            )
            PdfTool.PDF_TO_WORD -> PdfToDocxScreen(pdfToDocxVm, onBack = { activeTool = null })
            PdfTool.WORD_TO_PDF -> TextToPdfScreen(textToPdfVm, onBack = { activeTool = null })
            PdfTool.IMAGES_TO_PDF -> ImagesToPdfScreen(imagesToPdfVm, onBack = { activeTool = null })
            PdfTool.PDF_TO_IMAGES -> PdfToImagesScreen(pdfToImagesVm, onBack = { activeTool = null })
            PdfTool.MERGE -> MergePdfScreen(mergeVm, onBack = { activeTool = null })
            PdfTool.SPLIT -> SplitPdfScreen(splitVm, onBack = { activeTool = null })
            PdfTool.COMPRESS -> CompressPdfScreen(compressVm, onBack = { activeTool = null })
            PdfTool.ROTATE_DELETE -> EditPdfScreen(editVm, onBack = { activeTool = null })
            PdfTool.PAGE_NUMBERS -> PageNumbersScreen(pageNumbersVm, onBack = { activeTool = null })
            PdfTool.WATERMARK -> WatermarkPdfScreen(watermarkVm, onBack = { activeTool = null })
            PdfTool.PROTECT -> ProtectPdfScreen(protectVm, onBack = { activeTool = null })
            PdfTool.UNLOCK -> UnlockPdfScreen(unlockVm, onBack = { activeTool = null })
        }
    }
}

@Composable
private fun PdfHubGrid(
    onSelect: (PdfTool) -> Unit,
    recentFiles: List<SavedFile>,
    onRemoveRecent: (SavedFile) -> Unit
) {
    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 10.dp

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .let { if (chromeHaze != null) it.hazeSource(chromeHaze, zIndex = 1f) else it }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = topPadding, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfTool.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { tool ->
                        val accent = tool.accentColor()
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(tool) },
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(alpha = if (Glass.IsDark) 0.16f else 0.11f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(tool.icon, null, tint = accent, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(tool.title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary, maxLines = 2, minLines = 2)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            RecentCreationsCard(
                files = recentFiles,
                onRemove = onRemoveRecent
            )
        }

        LiquidGlassTopHeader(
            title = "DocPix · PDF Suite",
            subtitle = "14 Offline Tools • On-Device Only",
            hazeState = chromeHaze,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            trailing = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Glass.CardFillSubtle)
                        .border(1.dp, Glass.Cyan.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Glass.Cyan))
                    Spacer(Modifier.width(6.dp))
                    Text("Offline", color = Glass.TextPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }
}
