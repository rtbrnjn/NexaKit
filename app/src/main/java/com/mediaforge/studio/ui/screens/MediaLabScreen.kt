package com.mediaforge.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterFrames
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.media.COLLAGE_BACKGROUNDS
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.CollageAspect
import com.mediaforge.studio.media.CollageStyle
import com.mediaforge.studio.media.MaxDimension
import com.mediaforge.studio.media.MeshPreset
import com.mediaforge.studio.media.MockBackground
import com.mediaforge.studio.media.OutputFormat
import com.mediaforge.studio.media.SOLID_SWATCHES
import com.mediaforge.studio.ui.components.BitmapImage
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
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
import com.mediaforge.studio.vm.CollageViewModel
import com.mediaforge.studio.vm.CompressorViewModel
import com.mediaforge.studio.vm.MockupViewModel
import dev.chrisbanes.haze.HazeState

enum class MediaTool(val label: String, val icon: ImageVector) {
    COMPRESS("Compressor", Icons.Rounded.Compress),
    MOCKUP("Mockup", Icons.Rounded.FilterFrames),
    COLLAGE("Collage", Icons.Rounded.AutoAwesomeMosaic)
}

@Composable
fun MediaLabScreen(
    compressorVm: CompressorViewModel = viewModel(),
    mockupVm: MockupViewModel = viewModel(),
    collageVm: CollageViewModel = viewModel(),
    requestedTool: MediaTool? = null,
    onToolHandled: () -> Unit = {}
) {
    var active by rememberSaveable { mutableStateOf(MediaTool.COMPRESS) }
    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 10.dp

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            active = requestedTool
            onToolHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "mediaSubTab",
            modifier = Modifier.fillMaxSize()
        ) { tool ->
            when (tool) {
                MediaTool.COMPRESS -> CompressorTab(compressorVm, topPadding, chromeHaze)
                MediaTool.MOCKUP -> MockupTab(mockupVm, topPadding, chromeHaze)
                MediaTool.COLLAGE -> CollageTab(collageVm, topPadding, chromeHaze)
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassTopHeader(
                title = "NexaKit · Media Lab",
                hazeState = chromeHaze,
                trailing = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Glass.CardFillSubtle)
                            .border(1.dp, Glass.Emerald.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Glass.Emerald))
                        Spacer(Modifier.width(6.dp))
                        Text("Offline", color = Glass.TextPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )

            GlassSegmentedBar(
                items = MediaTool.entries,
                selected = active,
                label = { it.label },
                icon = { it.icon },
                onSelect = { active = it },
                hazeState = chromeHaze
            )
        }
    }
}

// ============================================================ 1. Batch Compressor + EXIF Stripper

@Composable
private fun CompressorTab(vm: CompressorViewModel, topPadding: Dp, hazeState: HazeState?) {
    val items by vm.items.collectAsState()
    val opts by vm.options.collectAsState()
    val estimating by vm.estimating.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    ToolPage(
        title = null,
        subtitle = null,
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        topPadding = topPadding,
        hazeState = hazeState
    ) {
        if (items.isEmpty()) {
            EmptyPicker(
                title = "Select Photos to Compress",
                subtitle = "Multi-select gallery images • Real-time size & EXIF cleaner",
                icon = Icons.Rounded.AddPhotoAlternate,
                onClick = { picker.launch("image/*") }
            )
        } else {
            val totalBefore = items.sumOf { it.originalBytes }
            val readyEst = items.all { it.estimatedBytes != null }
            val totalAfter = items.sumOf { it.estimatedBytes ?: it.originalBytes }
            val pct = FileUtils.savingsPercent(totalBefore, totalAfter)

            GlassCard(Modifier.fillMaxWidth(), accent = Glass.Emerald) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${items.size} image(s) queued", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                        val summary = if (estimating && !readyEst) {
                            "${FileUtils.formatBytes(totalBefore)} → calculating…"
                        } else {
                            "${FileUtils.formatBytes(totalBefore)} → ${FileUtils.formatBytes(totalAfter)}"
                        }
                        Text(summary, style = MaterialTheme.typography.bodyMedium, color = Glass.Cyan)
                    }
                    if (readyEst && totalBefore > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    (if (pct >= 0) Glass.Emerald else Glass.Amber).copy(alpha = 0.14f)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (pct >= 0) "-$pct%" else "+${-pct}%",
                                color = if (pct >= 0) Glass.Emerald else Glass.Amber,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Add More",
                        icon = Icons.Rounded.AddPhotoAlternate,
                        primary = false,
                        compact = true,
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Clear",
                        icon = Icons.Rounded.DeleteSweep,
                        primary = false,
                        compact = true,
                        onClick = vm::clear
                    )
                }
            }

            items.forEach { item ->
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UriImage(
                            item.uri,
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            maxDim = 220
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Glass.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val est = item.estimatedBytes
                            val sizeLine = if (est != null && est > 0) {
                                val s = FileUtils.savingsPercent(item.originalBytes, est)
                                "${FileUtils.formatBytes(item.originalBytes)} → ${FileUtils.formatBytes(est)}" +
                                    if (s > 0) " (-$s%)" else ""
                            } else {
                                "${FileUtils.formatBytes(item.originalBytes)} → estimating…"
                            }
                            Text(sizeLine, style = MaterialTheme.typography.bodySmall, color = Glass.Cyan)
                            if (opts.stripExif && item.hasGps) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LocationOff, null, tint = Glass.Amber, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("GPS EXIF detected • will strip", style = MaterialTheme.typography.labelSmall, color = Glass.Amber)
                                }
                            } else {
                                Text(
                                    if (opts.stripExif) "EXIF & GPS metadata will be stripped" else "Basic camera EXIF retained",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Glass.TextDim
                                )
                            }
                        }
                        GlassIconButton(Icons.Rounded.Close, "Remove", onClick = { vm.remove(item.uri) })
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Compression Settings")
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Quality",
                valueText = "${opts.quality}%",
                value = opts.quality.toFloat(),
                range = 10f..95f,
                onChange = { vm.setQuality(it.toInt()) }
            )
            SectionLabel("Max Resolution")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(MaxDimension.entries, opts.maxDimension, { it.label }, vm::setMaxDimension)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Output Format")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(OutputFormat.entries, opts.format, { it.label }, vm::setFormat)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strip EXIF & GPS Metadata", style = MaterialTheme.typography.bodyMedium, color = Glass.TextPrimary)
                    Hint("Removes location & identifying device tags")
                }
                GlassSwitch(opts.stripExif, vm::setStripExif)
            }
        }

        GlassButton(
            text = if (items.isEmpty()) "Compress Selected Files" else if (opts.stripExif) "Compress & Strip EXIF (${items.size})" else "Compress Selected Files (${items.size})",
            icon = Icons.Rounded.Download,
            enabled = items.isNotEmpty(),
            onClick = vm::compressAll,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================================ 2. Minimalist Product Mockup Framer

@Composable
private fun MockupTab(vm: MockupViewModel, topPadding: Dp, hazeState: HazeState?) {
    val uri by vm.uri.collectAsState()
    val params by vm.params.collectAsState()
    val preview by vm.preview.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) vm.setProduct(u)
    }

    ToolPage(
        title = null,
        subtitle = null,
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        topPadding = topPadding,
        hazeState = hazeState
    ) {
        if (uri == null) {
            EmptyPicker(
                title = "Pick a Product Photo",
                subtitle = "Frame cleanly on 1:1, 4:5, or 9:16 backdrops",
                icon = Icons.Rounded.FilterFrames,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass.CardFillSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    BitmapImage(preview, Modifier.fillMaxSize().padding(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Change Product Photo",
                    icon = Icons.Rounded.PhotoLibrary,
                    primary = false,
                    compact = true,
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Canvas Ratio")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CanvasRatio.entries, params.ratio, { it.label }, vm::setRatio)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Soft Gradient Backdrops")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MeshPreset.entries.forEach { preset ->
                    val selected = (params.background as? MockBackground.Mesh)?.preset == preset
                    val colors = listOf(Color(preset.base)) + preset.blobs.take(2).map { Color(it.color).copy(alpha = 0.92f) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Glass.Cyan else Glass.Stroke,
                                    shape = CircleShape
                                )
                                .clickable { vm.setBackground(MockBackground.Mesh(preset)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(preset.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Glass.Cyan else Glass.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("Solid Studio Backdrops")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SOLID_SWATCHES.forEach { argb ->
                    val selected = (params.background as? MockBackground.Solid)?.color == argb
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Glass.Cyan else Glass.Stroke,
                                shape = CircleShape
                            )
                            .clickable { vm.setBackground(MockBackground.Solid(argb)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            val darkCheck = Color(argb).red * 0.3f + Color(argb).green * 0.6f > 0.5f
                            Icon(Icons.Rounded.Check, null, tint = if (darkCheck) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Frosted Glass Bezel", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Subtle translucent frame around the product")
                }
                GlassSwitch(params.glassFrame) { on -> vm.update { it.copy(glassFrame = on) } }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Padding",
                valueText = "${(params.padding * 100).toInt()}%",
                value = params.padding,
                range = 0.02f..0.32f,
                onChange = { v -> vm.update { it.copy(padding = v) } }
            )
            LabeledSlider(
                label = "Corner Radius",
                valueText = "${params.cornerRadius.toInt()} px",
                value = params.cornerRadius,
                range = 0f..100f,
                onChange = { v -> vm.update { it.copy(cornerRadius = v) } }
            )
            LabeledSlider(
                label = "Elevation Shadow",
                valueText = "${(params.shadow * 100).toInt()}%",
                value = params.shadow,
                range = 0f..1f,
                onChange = { v -> vm.update { it.copy(shadow = v) } }
            )
            Spacer(Modifier.height(4.dp))
            GlassTextField(
                value = params.watermark,
                onValueChange = { txt -> vm.update { it.copy(watermark = txt.take(40)) } },
                label = "Optional Watermark Text",
                placeholder = "e.g. @nexakit.app",
                singleLine = true
            )
        }

        GlassButton(
            text = "Export High-Res Mockup PNG",
            icon = Icons.Rounded.Download,
            enabled = uri != null,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================================ 3. Clean Grid Collage Maker

@Composable
private fun CollageTab(vm: CollageViewModel, topPadding: Dp, hazeState: HazeState?) {
    val photos by vm.photos.collectAsState()
    val params by vm.params.collectAsState()
    val preview by vm.preview.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.setPhotos(uris)
    }

    ToolPage(
        title = null,
        subtitle = null,
        progress = progress,
        message = message,
        onDismissMessage = vm::dismissMessage,
        topPadding = topPadding,
        hazeState = hazeState
    ) {
        if (photos.size < 2) {
            EmptyPicker(
                title = "Select 2 to 4 Photos",
                subtitle = if (photos.size == 1) "1 photo picked — select 2 to 4 images for a collage" else "Exports crisp 2160px PNGs to Pictures/NexaKit",
                icon = Icons.Rounded.AutoAwesomeMosaic,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass.CardFillSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    BitmapImage(preview, Modifier.fillMaxSize().padding(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEachIndexed { index, uri ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Glass.CardFillSubtle)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
                                UriImage(uri, Modifier.fillMaxSize(), maxDim = 220)
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable { vm.remove(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Left", { vm.move(index, -1) }, enabled = index > 0)
                                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Right", { vm.move(index, 1) }, enabled = index < photos.lastIndex)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    text = "Re-pick Photos (2–4)",
                    icon = Icons.Rounded.PhotoLibrary,
                    primary = false,
                    compact = true,
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Layout Style")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CollageStyle.entries, params.style, { it.label }, vm::setStyle)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Aspect Ratio")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CollageAspect.entries, params.aspect, { it.label }, vm::setAspect)

            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Spacing",
                valueText = "${params.spacing.toInt()} px",
                value = params.spacing,
                range = 0f..60f,
                onChange = { v -> vm.update { it.copy(spacing = v) } }
            )
            LabeledSlider(
                label = "Corner Radius",
                valueText = "${params.cornerRadius.toInt()} px",
                value = params.cornerRadius,
                range = 0f..64f,
                onChange = { v -> vm.update { it.copy(cornerRadius = v) } }
            )

            Spacer(Modifier.height(6.dp))
            SectionLabel("Frame Color")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLLAGE_BACKGROUNDS.forEach { argb ->
                    val selected = params.background == argb
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Glass.Cyan else Glass.Stroke, CircleShape)
                            .clickable { vm.update { it.copy(background = argb) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            val darkCheck = Color(argb).red * 0.3f + Color(argb).green * 0.6f > 0.5f
                            Icon(Icons.Rounded.Check, null, tint = if (darkCheck) Color.Black else Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        GlassButton(
            text = "Export PNG Collage",
            icon = Icons.Rounded.Download,
            enabled = photos.size >= 2,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
