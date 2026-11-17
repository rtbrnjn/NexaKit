package com.mediaforge.studio.ui.screens.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.ui.components.GlassButton
import com.mediaforge.studio.ui.components.GlassCard
import com.mediaforge.studio.ui.components.GlassTextField
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.vm.PdfSource
import com.mediaforge.studio.vm.PdfThumbs

@Composable
internal fun PdfSourceCard(source: PdfSource, onChange: () -> Unit) {
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
internal fun PdfThumbImage(
    thumbs: PdfThumbs?,
    index: Int,
    modifier: Modifier = Modifier,
    rotation: Float = 0f
) {
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

@Composable
internal fun PdfPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    showPassword: Boolean,
    onToggleVisibility: (() -> Unit)? = null
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = onToggleVisibility?.let { toggle ->
            {
                IconButton(onClick = toggle) {
                    Icon(
                        if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle password",
                        tint = Glass.TextSecondary
                    )
                }
            }
        }
    )
}
