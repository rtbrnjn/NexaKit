package com.mediaforge.studio.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.pdf.PageNumberFormat
import com.mediaforge.studio.pdf.PageNumberPosition
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.theme.Glass
import dev.chrisbanes.haze.hazeSource
import com.mediaforge.studio.vm.CompressPdfViewModel
import com.mediaforge.studio.vm.EditPdfViewModel
import com.mediaforge.studio.vm.ImagesToPdfViewModel
import com.mediaforge.studio.vm.MergePdfViewModel
import com.mediaforge.studio.vm.PageNumbersViewModel
import com.mediaforge.studio.vm.PdfSource
import com.mediaforge.studio.vm.PdfThumbs
import com.mediaforge.studio.vm.PdfToDocxViewModel
import com.mediaforge.studio.vm.PdfToImagesViewModel
import com.mediaforge.studio.vm.ProtectPdfViewModel
import com.mediaforge.studio.vm.RasterFormat
import com.mediaforge.studio.vm.SplitPdfViewModel
import com.mediaforge.studio.vm.TextToPdfViewModel
import com.mediaforge.studio.vm.UnlockPdfViewModel
import com.mediaforge.studio.vm.WatermarkPdfViewModel

private enum class ToolTone { PRIMARY, SECONDARY, TERTIARY }

enum class PdfTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    private val tone: ToolTone
) {
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
    fun accentColor(): Color = tone.color()
}

@Composable
private fun ToolTone.color(): Color = when (this) {
    ToolTone.PRIMARY -> Glass.Cyan
    ToolTone.SECONDARY -> Glass.Emerald
    ToolTone.TERTIARY -> Glass.Amber
}

@Composable
fun PdfSuiteScreen(
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
    onToolHandled: () -> Unit = {}
) {
    var activeTool by rememberSaveable { mutableStateOf<PdfTool?>(null) }

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            activeTool = requestedTool
            onToolHandled()
        }
    }

    BackHandler(enabled = activeTool != null) { activeTool = null }

    AnimatedContent(
        targetState = activeTool,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(180)))
        },
        label = "pdfToolTransition"
    ) { tool ->
        when (tool) {
            null -> PdfHubGrid(onSelect = { activeTool = it })
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
private fun PdfHubGrid(onSelect: (PdfTool) -> Unit) {
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
        }

        LiquidGlassTopHeader(
            title = "NexaKit · PDF Suite",
            subtitle = "12 Offline Tools • On-Device Only",
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

// ---------------------------------------------------------------- Shared PDF helpers

@Composable
private fun PdfSourceCard(source: PdfSource, onChange: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.14f else 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = Glass.Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${source.pageCount} page(s) • ${FileUtils.formatBytes(source.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
            }
            GlassButton("Change", onClick = onChange, primary = false, compact = true)
        }
    }
}

@Composable
private fun PdfThumbImage(thumbs: PdfThumbs?, index: Int, modifier: Modifier = Modifier, rotation: Float = 0f) {
    val bmp by produceState<Bitmap?>(null, thumbs, index) {
        value = thumbs?.get(index, 280)
    }
    Box(modifier.background(Glass.CardFillSubtle), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bmp!!.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation },
                contentScale = ContentScale.Fit
            )
        } else {
            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = Glass.TextDim)
        }
    }
}

// ================================================================ 1. PDF to Word (.docx)

@Composable
private fun PdfToDocxScreen(vm: PdfToDocxViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val pageBreaks by vm.pageBreaks.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "PDF to Word (.docx)",
        subtitle = "Extracts paragraphs into standards-compliant .docx",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Saves editable .docx into Documents/NexaKit",
                icon = Icons.Rounded.Description,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Preserve Page Breaks", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Insert a Word page break between PDF pages")
                }
                GlassSwitch(pageBreaks, vm::setPageBreaks)
            }
            Spacer(Modifier.height(6.dp))
            Hint("Works with text-based PDFs offline using PDFTextStripper + OOXML ZipOutputStream.")
        }

        GlassButton(
            text = "Convert to Word (.docx)",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 2. Word (.docx) & Text to PDF

@Composable
private fun TextToPdfScreen(vm: TextToPdfViewModel, onBack: () -> Unit) {
    val text by vm.text.collectAsState()
    val markdown by vm.markdown.collectAsState()
    val docx by vm.docx.collectAsState()
    val fileName by vm.fileName.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadFile(uri)
    }

    ToolPage(
        title = "Word (.docx) to PDF",
        subtitle = "Cleanly paginated A4 documents offline",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Description, null, tint = Glass.Emerald)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName ?: "Import .docx / .md / .txt", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint(
                        if (docx != null) "${docx!!.size} structured blocks parsed from word/document.xml"
                        else "Or type / paste Markdown below"
                    )
                }
                GlassButton(
                    text = "Import",
                    icon = Icons.Rounded.FileOpen,
                    primary = false,
                    compact = true,
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                                "text/markdown",
                                "*/*"
                            )
                        )
                    }
                )
            }
            if (fileName != null) {
                Spacer(Modifier.height(8.dp))
                GlassButton("Clear Imported File", primary = false, compact = true, onClick = vm::clearFile, modifier = Modifier.fillMaxWidth())
            }
        }

        if (docx != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("Word Document Preview")
                Spacer(Modifier.height(6.dp))
                val previewText = docx!!.take(8).joinToString("\n\n") { it.text.toString() }
                Text(previewText, style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Markdown Formatting", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint("Renders # headings, **bold**, *italic*, `code`, > quotes & lists")
                    }
                    GlassSwitch(markdown, vm::setMarkdown)
                }
                Spacer(Modifier.height(10.dp))
                GlassTextField(
                    value = text,
                    onValueChange = vm::setText,
                    label = "Document Content",
                    placeholder = "# Meeting Notes\n\nWrite or paste content here…",
                    minLines = 8,
                    maxLines = 18
                )
            }
        }

        GlassButton(
            text = "Render A4 PDF",
            icon = Icons.Rounded.Download,
            enabled = docx != null || text.isNotBlank(),
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 3. Images to PDF

@Composable
private fun ImagesToPdfScreen(vm: ImagesToPdfViewModel, onBack: () -> Unit) {
    val images by vm.images.collectAsState()
    val mode by vm.mode.collectAsState()
    val quality by vm.quality.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.add(uris)
    }

    ToolPage(
        title = "Images to PDF",
        subtitle = "Saves to Documents/NexaKit",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        if (images.isEmpty()) {
            EmptyPicker(
                title = "Select Images",
                subtitle = "Multi-select photos and arrange page order",
                icon = Icons.Rounded.AddPhotoAlternate,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${images.size} page(s) ready", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, modifier = Modifier.weight(1f))
                    GlassButton("Add", icon = Icons.Rounded.AddPhotoAlternate, primary = false, compact = true, onClick = { picker.launch("image/*") })
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Clear", icon = Icons.Rounded.DeleteSweep, primary = false, compact = true, onClick = vm::clear)
                }
            }

            images.forEachIndexed { i, entry ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Glass.Cyan, modifier = Modifier.width(26.dp))
                        UriImage(entry.uri, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), maxDim = 180)
                        Spacer(Modifier.width(12.dp))
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        GlassIconButton(Icons.Rounded.ArrowUpward, "Up", { vm.move(i, -1) }, enabled = i > 0)
                        GlassIconButton(Icons.Rounded.ArrowDownward, "Down", { vm.move(i, 1) }, enabled = i < images.lastIndex)
                        GlassIconButton(Icons.Rounded.Close, "Remove", { vm.remove(i) })
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Page Size")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PdfPageMode.entries, mode, { it.label }, vm::setMode)
            Spacer(Modifier.height(10.dp))
            LabeledSlider("Image Quality", "$quality%", quality.toFloat(), 45f..100f, { vm.setQuality(it.toInt()) })
        }

        GlassButton(
            text = "Create PDF (${images.size} pages)",
            icon = Icons.Rounded.Download,
            enabled = images.isNotEmpty(),
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 4. PDF to Images

@Composable
private fun PdfToImagesScreen(vm: PdfToImagesViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val format by vm.format.collectAsState()
    val dpi by vm.dpi.collectAsState()
    val pageRange by vm.pageRange.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "PDF to Images",
        subtitle = "Exports pages to Pictures/NexaKit/PDF_Export",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Render all or selected pages cleanly offline",
                icon = Icons.Rounded.PictureAsPdf,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Pages to Export")
            Spacer(Modifier.height(6.dp))
            GlassTextField(
                value = pageRange,
                onValueChange = vm::setPageRange,
                label = "Pages (\"all\" or e.g. 1-3, 5)",
                placeholder = "all",
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Image Format")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(RasterFormat.entries, format, { it.label }, vm::setFormat)
            Spacer(Modifier.height(12.dp))
            SectionLabel("Resolution (DPI)")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(listOf(100, 150, 200, 300), dpi, { "$it DPI" }, vm::setDpi)
            Spacer(Modifier.height(6.dp))
            Hint("200 DPI is crisp for sharing; 300 DPI is print-grade.")
        }

        GlassButton(
            text = if (src == null) "Pick PDF First" else "Export Pages to ${format.label}",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 5. Merge PDFs

@Composable
private fun MergePdfScreen(vm: MergePdfViewModel, onBack: () -> Unit) {
    val sources by vm.sources.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.add(uris)
    }

    ToolPage(
        title = "Merge PDFs",
        subtitle = "Combine & reorder multiple PDFs",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        if (sources.isEmpty()) {
            EmptyPicker(
                title = "Add PDF Files",
                subtitle = "Select 2 or more PDFs to merge in order",
                icon = Icons.AutoMirrored.Rounded.MergeType,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sources.size} PDF(s) • ${sources.sumOf { it.pageCount }} pages", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        Hint("Total input size: ${FileUtils.formatBytes(sources.sumOf { it.sizeBytes })}")
                    }
                    GlassButton("Add", icon = Icons.Rounded.FileOpen, primary = false, compact = true, onClick = { picker.launch(arrayOf("application/pdf")) })
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Clear", icon = Icons.Rounded.DeleteSweep, primary = false, compact = true, onClick = vm::clear)
                }
            }

            sources.forEachIndexed { i, src ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = Glass.Cyan, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(src.name, style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${src.pageCount} pages • ${FileUtils.formatBytes(src.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = Glass.TextSecondary)
                        }
                        GlassIconButton(Icons.Rounded.ArrowUpward, "Up", { vm.move(i, -1) }, enabled = i > 0)
                        GlassIconButton(Icons.Rounded.ArrowDownward, "Down", { vm.move(i, 1) }, enabled = i < sources.lastIndex)
                        GlassIconButton(Icons.Rounded.Close, "Remove", { vm.remove(i) })
                    }
                }
            }
        }

        GlassButton(
            text = "Merge ${sources.size} PDFs",
            icon = Icons.Rounded.Download,
            enabled = sources.size >= 2,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 6. Split / Extract Pages

@Composable
private fun SplitPdfScreen(vm: SplitPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val selected by vm.selected.collectAsState()
    val rangeText by vm.rangeText.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var visibleLimit by remember(source) { mutableIntStateOf(30) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Split / Extract Pages",
        subtitle = "Tap thumbnails or enter ranges like 1-3, 6",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Preview page thumbnails & extract selected pages",
                icon = Icons.Rounded.ContentCut,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }

            GlassCard(Modifier.fillMaxWidth()) {
                GlassTextField(
                    value = rangeText,
                    onValueChange = vm::setRangeText,
                    label = "Page Range (1-based)",
                    placeholder = "e.g. 1-3, 6",
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Select All", primary = false, compact = true, onClick = vm::selectAll, modifier = Modifier.weight(1f))
                    GlassButton("Clear (${selected.size})", primary = false, compact = true, onClick = vm::clearSelection, modifier = Modifier.weight(1f))
                }
            }

            val showCount = minOf(src.pageCount, visibleLimit)
            (0 until showCount).chunked(3).forEach { rowIndexes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowIndexes.forEach { idx ->
                        val isPicked = idx in selected
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(13.dp))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) Glass.Cyan else Glass.Stroke,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { vm.toggle(idx) }
                        ) {
                            PdfThumbImage(vm.thumbs, idx, Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            if (isPicked) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Glass.Cyan),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Check, null, tint = Glass.OnAccent, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                    repeat(3 - rowIndexes.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (src.pageCount > visibleLimit) {
                GlassButton(
                    text = "Show More Pages ($visibleLimit / ${src.pageCount})",
                    primary = false,
                    compact = true,
                    onClick = { visibleLimit += 30 },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassButton(
                text = "Extract Selected Pages",
                icon = Icons.Rounded.Download,
                enabled = rangeText.isNotBlank(),
                onClick = vm::export,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ================================================================ 7. Compress PDF

@Composable
private fun CompressPdfScreen(vm: CompressPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val preset by vm.preset.collectAsState()
    val result by vm.result.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Compress PDF",
        subtitle = "Optimise embedded page images offline",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF to Shrink",
                subtitle = "Best for scanned PDFs & photo-heavy documents",
                icon = Icons.Rounded.Compress,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        result?.let { r ->
            val pct = FileUtils.savingsPercent(r.before, r.after)
            GlassCard(Modifier.fillMaxWidth(), accent = if (pct > 0) Glass.Emerald else Glass.Amber) {
                SectionLabel("Compression Result")
                Spacer(Modifier.height(4.dp))
                Text(
                    "${FileUtils.formatBytes(r.before)} → ${FileUtils.formatBytes(r.after)}" +
                        if (pct > 0) "  (-$pct%)" else "  (already minimal)",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pct > 0) Glass.Emerald else Glass.Amber
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Compression Preset")
            Spacer(Modifier.height(8.dp))
            ChoiceRow(PdfCompressPreset.entries, preset, { it.label }, vm::setPreset)
            Spacer(Modifier.height(8.dp))
            Hint("${preset.detail} • ${(preset.jpegQuality * 100).toInt()}% JPEG image quality")
        }

        GlassButton(
            text = "Compress PDF (${preset.label})",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 8. Rotate & Delete Pages

@Composable
private fun EditPdfScreen(vm: EditPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val rotations by vm.rotations.collectAsState()
    val deleted by vm.deleted.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var visibleLimit by remember(source) { mutableIntStateOf(24) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Rotate & Delete Pages",
        subtitle = "Adjust page angles (90°/180°/270°) or remove pages",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Preview pages to rotate or delete individually",
                icon = Icons.Rounded.Rotate90DegreesCw,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }

            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("All +90°", icon = Icons.AutoMirrored.Rounded.RotateRight, primary = false, compact = true, onClick = { vm.rotateAll(90) }, modifier = Modifier.weight(1f))
                    GlassButton("All 180°", icon = Icons.Rounded.Rotate90DegreesCw, primary = false, compact = true, onClick = { vm.rotateAll(180) }, modifier = Modifier.weight(1f))
                    GlassButton("Reset", icon = Icons.Rounded.Restore, primary = false, compact = true, onClick = vm::reset)
                }
            }

            val showCount = minOf(src.pageCount, visibleLimit)
            (0 until showCount).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { idx ->
                        val rot = rotations[idx] ?: 0
                        val isDel = idx in deleted
                        GlassCard(
                            Modifier
                                .weight(1f)
                                .alpha(if (isDel) 0.45f else 1f),
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                PdfThumbImage(vm.thumbs, idx, Modifier.fillMaxSize(), rotation = rot.toFloat())
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.68f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isDel) "DEL" else if (rot != 0) "P${idx + 1} • ${rot}°" else "P${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDel) Glass.Danger else Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                GlassIconButton(Icons.AutoMirrored.Rounded.RotateRight, "Rotate 90°", { vm.rotate(idx, 90) }, enabled = !isDel, tint = Glass.Cyan)
                                GlassIconButton(Icons.Rounded.Rotate90DegreesCw, "Rotate 180°", { vm.rotate(idx, 180) }, enabled = !isDel)
                                GlassIconButton(
                                    if (isDel) Icons.Rounded.Restore else Icons.Rounded.Delete,
                                    if (isDel) "Restore" else "Delete",
                                    { vm.toggleDelete(idx) },
                                    tint = if (isDel) Glass.Emerald else Glass.Danger
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (src.pageCount > visibleLimit) {
                GlassButton(
                    text = "Show More Pages ($visibleLimit / ${src.pageCount})",
                    primary = false,
                    compact = true,
                    onClick = { visibleLimit += 24 },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GlassButton(
                text = "Save Edited PDF",
                icon = Icons.Rounded.Download,
                onClick = vm::export,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ================================================================ 9. Add Page Numbers

@Composable
private fun PageNumbersScreen(vm: PageNumbersViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val opts by vm.options.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Add Page Numbers",
        subtitle = "Stamp clean footer numbering across pages",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Applies crisp footer numbers on every page",
                icon = Icons.Rounded.FormatListNumbered,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Number Format")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PageNumberFormat.entries, opts.format, { it.label }, vm::setFormat)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Position")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(PageNumberPosition.entries, opts.position, { it.label }, vm::setPosition)

            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Font Size",
                valueText = "${opts.fontSize.toInt()} pt",
                value = opts.fontSize,
                range = 8f..16f,
                onChange = vm::setFontSize
            )
            LabeledSlider(
                label = "Start Number",
                valueText = "${opts.startNumber}",
                value = opts.startNumber.toFloat(),
                range = 1f..50f,
                onChange = { vm.setStartNumber(it.toInt()) }
            )
        }

        GlassButton(
            text = "Apply Page Numbers",
            icon = Icons.Rounded.Download,
            enabled = src != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 10. Add Text Watermark

@Composable
private fun WatermarkPdfScreen(vm: WatermarkPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val text by vm.text.collectAsState()
    val opacity by vm.opacity.collectAsState()
    val color by vm.color.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Add Text Watermark",
        subtitle = "Diagonal translucent stamp across every page",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF File",
                subtitle = "Stamp CONFIDENTIAL, DRAFT or custom text",
                icon = Icons.Rounded.WaterDrop,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(
                value = text,
                onValueChange = vm::setText,
                label = "Watermark Text",
                placeholder = "CONFIDENTIAL",
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            LabeledSlider(
                label = "Opacity",
                valueText = "${(opacity * 100).toInt()}%",
                value = opacity,
                range = 0.05f..0.75f,
                onChange = vm::setOpacity
            )
            SectionLabel("Ink Color")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF64748B.toInt(), 0xFF0284C7.toInt(), 0xFF059669.toInt(), 0xFFE11D48.toInt(), 0xFF1E293B.toInt()).forEach { argb ->
                    val selected = color == argb
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Glass.Cyan else Glass.Stroke, CircleShape)
                            .clickable { vm.setColor(argb) }
                    )
                }
            }
        }

        GlassButton(
            text = "Apply Watermark",
            icon = Icons.Rounded.Download,
            enabled = src != null && text.isNotBlank(),
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 11. Protect / Encrypt PDF

@Composable
private fun ProtectPdfScreen(vm: ProtectPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val userPass by vm.userPassword.collectAsState()
    val ownerPass by vm.ownerPassword.collectAsState()
    val allowPrint by vm.allowPrint.collectAsState()
    val allowCopy by vm.allowCopy.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var showPass by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Protect / Encrypt PDF",
        subtitle = "128-bit password protection via PDFBox",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a PDF to Encrypt",
                subtitle = "Requires password to open the exported PDF",
                icon = Icons.Rounded.Lock,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            PdfSourceCard(src) { picker.launch(arrayOf("application/pdf")) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            GlassTextField(
                value = userPass,
                onValueChange = vm::setUserPassword,
                label = "Open Password (min 4 chars)",
                placeholder = "Enter password",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password",
                            tint = Glass.TextSecondary
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            GlassTextField(
                value = ownerPass,
                onValueChange = vm::setOwnerPassword,
                label = "Owner Password (optional)",
                placeholder = "Defaults to open password",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Printing", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Permits PDF viewers to print the document")
                }
                GlassSwitch(allowPrint, vm::setAllowPrint)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Text Copy", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Permits copying text content from the PDF")
                }
                GlassSwitch(allowCopy, vm::setAllowCopy)
            }
        }

        GlassButton(
            text = "Encrypt & Save PDF",
            icon = Icons.Rounded.Lock,
            enabled = src != null && userPass.length >= 4,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================================================================ 12. Unlock / Decrypt PDF

@Composable
private fun UnlockPdfScreen(vm: UnlockPdfViewModel, onBack: () -> Unit) {
    val source by vm.source.collectAsState()
    val password by vm.password.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var showPass by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(uri)
    }

    ToolPage(
        title = "Unlock / Decrypt PDF",
        subtitle = "Remove password restrictions from owned PDFs",
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        onBack = onBack
    ) {
        val src = source
        if (src == null) {
            EmptyPicker(
                title = "Choose a Protected PDF",
                subtitle = "Enter the current password to save an unlocked copy",
                icon = Icons.Rounded.LockOpen,
                onClick = { picker.launch(arrayOf("application/pdf")) }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background((if (src.isEncrypted) Glass.Amber else Glass.Emerald).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (src.isEncrypted) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            null,
                            tint = if (src.isEncrypted) Glass.Amber else Glass.Emerald
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (src.isEncrypted) "Password Protected" else "Already Unlocked") + " • ${FileUtils.formatBytes(src.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (src.isEncrypted) Glass.Amber else Glass.Emerald
                        )
                    }
                    GlassButton("Change", onClick = { picker.launch(arrayOf("application/pdf")) }, primary = false, compact = true)
                }
            }

            if (src.isEncrypted) {
                GlassCard(Modifier.fillMaxWidth()) {
                    GlassTextField(
                        value = password,
                        onValueChange = vm::setPassword,
                        label = "PDF Password",
                        placeholder = "Enter document password",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = Glass.TextSecondary
                                )
                            }
                        }
                    )
                }
            }
        }

        GlassButton(
            text = "Unlock & Save PDF",
            icon = Icons.Rounded.LockOpen,
            enabled = src != null && src.isEncrypted,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
