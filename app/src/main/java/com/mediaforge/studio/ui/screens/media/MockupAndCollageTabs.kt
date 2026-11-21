package com.mediaforge.studio.ui.screens.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterFrames
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.media.COLLAGE_BACKGROUNDS
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.CollageAspect
import com.mediaforge.studio.media.CollageStyle
import com.mediaforge.studio.media.MeshPreset
import com.mediaforge.studio.media.MockBackground
import com.mediaforge.studio.media.SOLID_SWATCHES
import com.mediaforge.studio.ui.components.BitmapImage
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
import com.mediaforge.studio.vm.CollageViewModel
import com.mediaforge.studio.vm.MockupViewModel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun MockupTab(vm: MockupViewModel, topPadding: Dp, hazeState: HazeState?) {
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
                    ColorSwatchCircle(
                        argb = argb,
                        selected = (params.background as? MockBackground.Solid)?.color == argb,
                        onClick = { vm.setBackground(MockBackground.Solid(argb)) }
                    )
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
                placeholder = "e.g. @docpix.app",
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

@Composable
internal fun CollageTab(vm: CollageViewModel, topPadding: Dp, hazeState: HazeState?) {
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
                subtitle = if (photos.size == 1) "1 photo picked — select 2 to 4 images for a collage" else "Exports crisp 2160px PNGs to Pictures/DocPix",
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
                    ColorSwatchCircle(
                        argb = argb,
                        selected = params.background == argb,
                        onClick = { vm.update { it.copy(background = argb) } }
                    )
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

@Composable
private fun ColorSwatchCircle(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val swatchColor = Color(argb)
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Glass.Cyan else Glass.Stroke,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            val darkCheck = swatchColor.red * 0.3f + swatchColor.green * 0.6f > 0.5f
            Icon(Icons.Rounded.Check, null, tint = if (darkCheck) Color.Black else Color.White, modifier = Modifier.size(15.dp))
        }
    }
}
