package com.mediaforge.studio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ui.theme.Glass

@Composable
fun LiquidGlassPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val isDark = Glass.IsDark
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.974f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "pillBtn"
    )

    val borderBrush = diagonalGlassBorder(isDark = isDark, strong = primary)
    val primaryBrush = remember(isDark) {
        if (isDark) {
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.34f),
                    Color(0xFF6366F1).copy(alpha = 0.30f),
                    Color(0xFF10B981).copy(alpha = 0.32f)
                )
            )
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF059669)))
        }
    }
    val contentColor = if (primary) Color.White else Glass.TextPrimary

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.44f }
            .height(if (compact) 40.dp else 52.dp)
            .shadow(
                elevation = if (primary) (if (isDark) 10.dp else 6.dp) else 0.dp,
                shape = shape,
                ambientColor = if (primary) Glass.Cyan.copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (primary) Glass.Cyan.copy(alpha = 0.35f) else Color.Transparent
            )
            .clip(shape)
            .then(
                if (primary) Modifier.background(primaryBrush).border(1.dp, borderBrush, shape)
                else Modifier.background(Glass.CardFill).border(1.dp, borderBrush, shape)
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 15.dp else 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(if (compact) 17.dp else 19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) = LiquidGlassPill(
    text = text,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    primary = primary,
    enabled = enabled,
    compact = compact,
    trailing = trailing
)

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Glass.TextPrimary
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, tint = if (enabled) tint else Glass.TextDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Glass.Cyan
) {
    val shape = RoundedCornerShape(50)
    val isDark = Glass.IsDark
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) color.copy(alpha = if (isDark) 0.22f else 0.15f)
                else Glass.CardFillSubtle
            )
            .then(
                if (selected) Modifier.border(1.dp, color.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            maxLines = 1,
            color = if (selected) (if (isDark) Color.White else color) else Glass.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { GlassChip(label(it), it == selected, { onSelect(it) }) }
    }
}

// ------------------------------------------------------------------ Inputs & Labels

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Glass.TextSecondary, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = Glass.Cyan)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Glass.Cyan,
                activeTrackColor = Glass.Cyan,
                inactiveTrackColor = Glass.Stroke,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Glass.TextDim) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Glass.CardFill,
            unfocusedContainerColor = Glass.CardFillSubtle,
            focusedBorderColor = Glass.Cyan.copy(alpha = 0.7f),
            unfocusedBorderColor = Glass.Stroke,
            focusedLabelColor = Glass.Cyan,
            unfocusedLabelColor = Glass.TextSecondary,
            cursorColor = Glass.Cyan,
            focusedTextColor = Glass.TextPrimary,
            unfocusedTextColor = Glass.TextPrimary
        )
    )
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Glass.OnAccent,
            checkedTrackColor = Glass.Cyan,
            uncheckedThumbColor = Glass.TextSecondary,
            uncheckedTrackColor = Glass.CardFillSubtle,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 2.dp),
        color = Glass.Cyan,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Glass.TextSecondary, style = MaterialTheme.typography.bodySmall)
}
