package com.mediaforge.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FilterFrames
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.ui.components.GlassIconButton
import com.mediaforge.studio.ui.components.GlassSegmentedBar
import com.mediaforge.studio.ui.components.LiquidGlassTopHeader
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.screens.media.CollageTab
import com.mediaforge.studio.ui.screens.media.CompressorTab
import com.mediaforge.studio.ui.screens.media.MediaActionChooser
import com.mediaforge.studio.ui.screens.media.MediaUploadWelcome
import com.mediaforge.studio.ui.screens.media.MockupTab
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.CollageViewModel
import com.mediaforge.studio.vm.CompressorViewModel
import com.mediaforge.studio.vm.MockupViewModel

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
    onToolHandled: () -> Unit = {},
    onScanDocument: () -> Unit = {},
    isTabActive: Boolean = true,
    recentFiles: List<SavedFile> = emptyList(),
    onRemoveRecent: (SavedFile) -> Unit = {}
) {
    var active by rememberSaveable { mutableStateOf<MediaTool?>(null) }
    val chromeHaze = LocalHazeState.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeightPx.toDp() } + 10.dp

    val compressItems by compressorVm.items.collectAsState()
    val mockupUri by mockupVm.uri.collectAsState()
    val collagePhotos by collageVm.photos.collectAsState()

    val loadedUris = remember(compressItems, mockupUri, collagePhotos) {
        buildList<Uri> {
            compressItems.forEach { if (it.uri !in this) add(it.uri) }
            mockupUri?.let { if (it !in this) add(it) }
            collagePhotos.forEach { if (it !in this) add(it) }
        }
    }

    fun loadUploadedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val distinct = uris.distinct()
        compressorVm.clear()
        compressorVm.addImages(distinct)
        mockupVm.setProduct(distinct.first())
        if (distinct.size >= 2) {
            collageVm.setPhotos(distinct)
        } else {
            collageVm.clear()
        }
        active = null
    }

    fun resetWorkspace() {
        active = null
        compressorVm.clear()
        mockupVm.clear()
        collageVm.clear()
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) loadUploadedPhotos(uris)
    }

    LaunchedEffect(requestedTool) {
        if (requestedTool != null) {
            active = requestedTool
            onToolHandled()
        }
    }

    BackHandler(enabled = isTabActive && active != null) {
        active = null
    }

    val stageKey: Any = active ?: if (loadedUris.isNotEmpty()) "chooser" else "upload"

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stageKey,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "mediaStage",
            modifier = Modifier.fillMaxSize()
        ) { stage ->
            when (stage) {
                is MediaTool -> when (stage) {
                    MediaTool.COMPRESS -> CompressorTab(compressorVm, topPadding, chromeHaze)
                    MediaTool.MOCKUP -> MockupTab(mockupVm, topPadding, chromeHaze)
                    MediaTool.COLLAGE -> CollageTab(collageVm, topPadding, chromeHaze)
                }
                "chooser" -> MediaActionChooser(
                    uris = loadedUris,
                    topPadding = topPadding,
                    hazeState = chromeHaze,
                    onSelectTool = { active = it },
                    onPickNewPhotos = { uploadPicker.launch("image/*") },
                    onReset = ::resetWorkspace,
                    recentFiles = recentFiles,
                    onRemoveRecent = onRemoveRecent
                )
                else -> MediaUploadWelcome(
                    topPadding = topPadding,
                    hazeState = chromeHaze,
                    onPickPhotos = { uploadPicker.launch("image/*") },
                    onScanDocument = onScanDocument,
                    recentFiles = recentFiles,
                    onRemoveRecent = onRemoveRecent
                )
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
                title = "DocPix · Media Lab",
                subtitle = when {
                    active != null -> active?.label
                    loadedUris.isNotEmpty() -> "${loadedUris.size} foto siap diproses"
                    else -> "Pilih foto terlebih dahulu untuk memulai"
                },
                hazeState = chromeHaze,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active != null) {
                            GlassIconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                description = "Back to photo upload",
                                onClick = { active = null },
                                tint = Glass.Cyan
                            )
                            Spacer(Modifier.width(4.dp))
                        }
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
                }
            )

            if (active != null) {
                GlassSegmentedBar(
                    items = MediaTool.entries,
                    selected = active!!,
                    label = { it.label },
                    icon = { it.icon },
                    onSelect = { active = it },
                    hazeState = chromeHaze
                )
            }
        }
    }
}
