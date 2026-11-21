package com.mediaforge.studio.ui.screens.pdf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.ImagesToPdfViewModel
import com.mediaforge.studio.vm.PdfToDocxViewModel
import com.mediaforge.studio.vm.PdfToImagesViewModel
import com.mediaforge.studio.vm.RasterFormat
import com.mediaforge.studio.vm.TextToPdfViewModel

@Composable
internal fun PdfToDocxScreen(vm: PdfToDocxViewModel, onBack: () -> Unit) {
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
                subtitle = "Saves editable .docx into Documents/DocPix",
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

@Composable
internal fun TextToPdfScreen(vm: TextToPdfViewModel, onBack: () -> Unit) {
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

@Composable
internal fun ImagesToPdfScreen(vm: ImagesToPdfViewModel, onBack: () -> Unit) {
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
        subtitle = "Saves to Documents/DocPix",
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

@Composable
internal fun PdfToImagesScreen(vm: PdfToImagesViewModel, onBack: () -> Unit) {
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
        subtitle = "Exports pages to Pictures/DocPix/PDF_Export",
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
