package com.mediaforge.studio.ui.screens.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FilterFrames
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.Hint
import com.mediaforge.studio.ui.components.SectionLabel
import com.mediaforge.studio.ui.components.UriImage
import com.mediaforge.studio.ui.screens.MediaTool
import com.mediaforge.studio.ui.theme.Glass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Clean, distraction-free upload hero shown before the user picks photo(s). */
@Composable
internal fun MediaUploadWelcome(
    topPadding: Dp,
    hazeState: HazeState?,
    onPickPhotos: () -> Unit,
    onScanDocument: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .let { if (hazeState != null) it.hazeSource(hazeState, zIndex = 1f) else it }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = topPadding + 24.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard(
            Modifier.fillMaxWidth(),
            accent = Glass.Cyan,
            onClick = onPickPhotos,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 34.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Glass.Cyan.copy(alpha = if (Glass.IsDark) 0.18f else 0.12f))
                        .border(1.dp, Glass.Cyan.copy(alpha = 0.38f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = Glass.Cyan,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Unggah Foto untuk Memulai",
                    style = MaterialTheme.typography.titleLarge,
                    color = Glass.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pilih 1 foto atau beberapa foto dari galeri. Opsi Compressor, Mockup, dan Collage akan terbuka setelah foto siap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Glass.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                GlassButton(
                    text = "Pilih Foto dari Galeri",
                    icon = Icons.Rounded.AddPhotoAlternate,
                    primary = true,
                    onClick = onPickPhotos,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        MediaActionRowCard(
            title = "Scan Dokumen (Kamera)",
            subtitle = "Auto-crop kertas, filter bersih & simpan ke PDF atau Gambar JPG",
            icon = Icons.Rounded.DocumentScanner,
            accent = Glass.Cyan,
            onClick = onScanDocument
        )

        Hint("100% diproses luring di perangkat • Privasi EXIF/GPS terjaga")
    }
}

/** Focused tool chooser revealed after photo(s) are uploaded. */
@Composable
internal fun MediaActionChooser(
    uris: List<Uri>,
    topPadding: Dp,
    hazeState: HazeState?,
    onSelectTool: (MediaTool) -> Unit,
    onPickNewPhotos: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .let { if (hazeState != null) it.hazeSource(hazeState, zIndex = 1f) else it }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = topPadding, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth(), accent = Glass.Emerald, contentPadding = PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${uris.size} Foto Siap Diproses", style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                    Hint("Pilih alat yang ingin digunakan untuk foto di bawah ini")
                }
                GlassButton(
                    text = "Ganti",
                    icon = Icons.Rounded.AddPhotoAlternate,
                    primary = false,
                    compact = true,
                    onClick = onPickNewPhotos
                )
                Spacer(Modifier.width(6.dp))
                GlassButton(
                    text = "Reset",
                    icon = Icons.Rounded.Refresh,
                    primary = false,
                    compact = true,
                    onClick = onReset
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uris.take(10).forEach { uri ->
                    UriImage(
                        uri = uri,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        maxDim = 220
                    )
                }
            }
        }

        SectionLabel("Pilih Tindakan")

        MediaActionRowCard(
            title = "Compress & Bersihkan EXIF",
            subtitle = "Perkecil ukuran JPG/WEBP (${uris.size} foto) dan hapus tag lokasi GPS",
            icon = Icons.Rounded.Compress,
            accent = Glass.Emerald,
            onClick = { onSelectTool(MediaTool.COMPRESS) }
        )

        MediaActionRowCard(
            title = "Minimalist Product Mockup",
            subtitle = "Bingkai foto dengan backdrop estetik rasio 1:1, 4:5, atau 9:16",
            icon = Icons.Rounded.FilterFrames,
            accent = Glass.Cyan,
            onClick = { onSelectTool(MediaTool.MOCKUP) }
        )

        MediaActionRowCard(
            title = "Clean Grid Collage",
            subtitle = if (uris.size >= 2) {
                "Susun ${uris.size.coerceAtMost(4)} foto menjadi kolase PNG tajam 2160px"
            } else {
                "Butuh 2–4 foto • tambah foto di dalam editor Kolase"
            },
            icon = Icons.Rounded.AutoAwesomeMosaic,
            accent = Glass.Amber,
            onClick = { onSelectTool(MediaTool.COLLAGE) }
        )
    }
}

@Composable
private fun MediaActionRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        accent = accent,
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = if (Glass.IsDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Glass.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Hint(subtitle)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}
