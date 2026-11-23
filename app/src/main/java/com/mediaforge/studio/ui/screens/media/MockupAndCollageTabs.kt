package com.mediaforge.studio.ui.screens.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.CropSquare
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.media.COLLAGE_BACKGROUNDS
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.CollageAspect
import com.mediaforge.studio.media.CollagePhotoMode
import com.mediaforge.studio.media.CollageRatioCategory
import com.mediaforge.studio.media.CollageRenderer
import com.mediaforge.studio.media.CollageStyle
import com.mediaforge.studio.media.DeviceFrameStyle
import com.mediaforge.studio.media.MeshPreset
import com.mediaforge.studio.media.MockBackground
import com.mediaforge.studio.media.MockupRenderer
import com.mediaforge.studio.media.STUDIO_SOLID_PRESETS
import com.mediaforge.studio.ui.components.BitmapImage
import com.mediaforge.studio.ui.components.ChoiceRow
import com.mediaforge.studio.ui.components.EmptyPicker
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassChip
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.GlassSwitch
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.LabeledSlider
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.ToolPage
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.components.horizontalCarouselScroll
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
    val (exportW, exportH) = MockupRenderer.outputSize(params.ratio, 1440)

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
                title = "Pilih Foto / Screenshot",
                subtitle = "Bingkai perangkat modern rasio 1:1, 4:5, 9:16, atau 16:9",
                icon = Icons.Rounded.FilterFrames,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        params.frameStyle.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Glass.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${params.ratio.w}:${params.ratio.h} • ${exportW}×${exportH} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Cyan
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glass.CardFillSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    BitmapImage(preview, Modifier.fillMaxSize().padding(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Ganti Foto / Screenshot",
                    icon = Icons.Rounded.PhotoLibrary,
                    primary = false,
                    compact = true,
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Bingkai Perangkat Modern")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(DeviceFrameStyle.entries, params.frameStyle, { it.label }, vm::setFrameStyle)
            Spacer(Modifier.height(6.dp))
            Hint(params.frameStyle.subtitle)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Rasio Ekspor")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CanvasRatio.entries, params.ratio, { it.label }, vm::setRatio)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Latar Belakang Canvas")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalCarouselScroll(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                val blurredSelected = params.background is MockBackground.BlurredImage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Glass.Cyan.copy(alpha = 0.45f), Glass.Emerald.copy(alpha = 0.35f))
                                )
                            )
                            .border(
                                width = if (blurredSelected) 2.5.dp else 1.dp,
                                color = if (blurredSelected) Glass.Cyan else Glass.Stroke,
                                shape = CircleShape
                            )
                            .clickable { vm.setBackground(MockBackground.BlurredImage) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.BlurOn,
                            contentDescription = "Blurred Photo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Blurred Photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (blurredSelected) Glass.Cyan else Glass.TextSecondary
                    )
                }

                STUDIO_SOLID_PRESETS.forEach { preset ->
                    val selected = (params.background as? MockBackground.Solid)?.color == preset.color
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ColorSwatchCircle(
                            argb = preset.color,
                            selected = selected,
                            sizeDp = 46.dp,
                            onClick = { vm.setBackground(MockBackground.Solid(preset.color)) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Glass.Cyan else Glass.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Ambient Gradient Halus")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalCarouselScroll(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeshPreset.entries.forEach { preset ->
                    val selected = (params.background as? MockBackground.Mesh)?.preset == preset
                    val colors = listOf(Color(preset.base)) + preset.blobs.take(2).map { Color(it.color).copy(alpha = 0.92f) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(42.dp)
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
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint = if (preset.light) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Glass.Cyan else Glass.TextSecondary
                        )
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pantulan Kaca Tipis", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Kilau kaca halus pada layar & bingkai perangkat")
                }
                GlassSwitch(params.glassReflection) { on -> vm.update { it.copy(glassReflection = on) } }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Ukuran / Padding Canvas",
                valueText = "${(params.padding * 100).toInt()}%",
                value = params.padding,
                range = 0.04f..0.30f,
                onChange = { v -> vm.update { it.copy(padding = v) } }
            )
            LabeledSlider(
                label = "Sudut Lengkung (Radius)",
                valueText = "${params.cornerRadius.toInt()} px",
                value = params.cornerRadius,
                range = 0f..90f,
                onChange = { v -> vm.update { it.copy(cornerRadius = v) } }
            )
            LabeledSlider(
                label = "Soft Drop Shadow",
                valueText = "${(params.shadow * 100).toInt()}%",
                value = params.shadow,
                range = 0f..1f,
                onChange = { v -> vm.update { it.copy(shadow = v) } }
            )
            Spacer(Modifier.height(4.dp))
            GlassTextField(
                value = params.watermark,
                onValueChange = { txt -> vm.update { it.copy(watermark = txt.take(40)) } },
                label = "Watermark Opsional",
                placeholder = "mis. @docpix.app",
                singleLine = true
            )
        }

        GlassButton(
            text = "Ekspor Mockup PNG (${params.ratio.w}:${params.ratio.h})",
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
    val (exportW, exportH) = CollageRenderer.outputSize(params.aspect, 2160)

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
                title = "Pilih 2 hingga 4 Foto",
                subtitle = if (photos.size == 1) "1 foto dipilih — pilih 2–4 gambar untuk kolase" else "Mendukung rasio Square, Portrait (4:5, 9:16) & Landscape (16:9, 4:3)",
                icon = Icons.Rounded.AutoAwesomeMosaic,
                onClick = { picker.launch("image/*") }
            )
        } else {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${params.aspect.category.label} ${params.aspect.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Glass.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${exportW}×${exportH} px • ${params.photoMode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Glass.Cyan
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
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
                    text = "Pilih Ulang Foto (2–4)",
                    icon = Icons.Rounded.PhotoLibrary,
                    primary = false,
                    compact = true,
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Aspect Ratio")
            Spacer(Modifier.height(8.dp))
            GlassSegmentedBar(
                items = CollageRatioCategory.entries,
                selected = params.aspect.category,
                label = { it.label },
                icon = { cat ->
                    when (cat) {
                        CollageRatioCategory.SQUARE -> Icons.Rounded.CropSquare
                        CollageRatioCategory.PORTRAIT -> Icons.Rounded.CropPortrait
                        CollageRatioCategory.LANDSCAPE -> Icons.Rounded.CropLandscape
                    }
                },
                onSelect = vm::setRatioCategory,
                hazeState = null
            )

            Spacer(Modifier.height(10.dp))
            val categoryRatios = CollageAspect.entries.filter { it.category == params.aspect.category }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalCarouselScroll(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryRatios.forEach { ratio ->
                    val pillLabel = when (ratio) {
                        CollageAspect.SQUARE -> "1:1 Feed Persegi"
                        CollageAspect.PORTRAIT_4_5 -> "4:5 Portrait Feed"
                        CollageAspect.PORTRAIT_9_16 -> "9:16 Reels / TikTok"
                        CollageAspect.LANDSCAPE_16_9 -> "16:9 YouTube / Banner"
                        CollageAspect.LANDSCAPE_4_3 -> "4:3 Presentasi"
                    }
                    GlassChip(
                        text = pillLabel,
                        selected = params.aspect == ratio,
                        onClick = { vm.setAspect(ratio) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint(params.aspect.description)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Pola Grid")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CollageStyle.entries, params.style, { it.label }, vm::setStyle)

            Spacer(Modifier.height(12.dp))
            SectionLabel("Penyesuaian Foto")
            Spacer(Modifier.height(6.dp))
            ChoiceRow(CollagePhotoMode.entries, params.photoMode, { it.label }, vm::setPhotoMode)
            Spacer(Modifier.height(4.dp))
            Hint(params.photoMode.description)

            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Grid Spacing",
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
            SectionLabel("Warna Bingkai")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalCarouselScroll(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
            text = "Ekspor Kolase PNG (${params.aspect.label})",
            icon = Icons.Rounded.Download,
            enabled = photos.size >= 2,
            onClick = vm::export,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorSwatchCircle(
    argb: Int,
    selected: Boolean,
    sizeDp: Dp = 38.dp,
    onClick: () -> Unit
) {
    val swatchColor = Color(argb)
    Box(
        Modifier
            .size(sizeDp)
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
            Icon(Icons.Rounded.Check, null, tint = if (darkCheck) Color.Black else Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
