package com.mediaforge.studio.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.mediaforge.studio.ui.theme.Glass

/**
 * Coordinates touch locks so inner horizontal controls (Sliders, horizontal strips)
 * never conflict with an outer `HorizontalPager` navigation swipe.
 */
class PagerGestureCoordinator {
    private var activeLocks by mutableIntStateOf(0)
    val isLocked: Boolean get() = activeLocks > 0

    fun acquire(): () -> Unit {
        activeLocks += 1
        var released = false
        return {
            if (!released) {
                released = true
                activeLocks = (activeLocks - 1).coerceAtLeast(0)
            }
        }
    }
}

val LocalPagerGestureCoordinator = compositionLocalOf<PagerGestureCoordinator?> { null }

/** Prevents outer HorizontalPager swipes while the pointer is interacting with this control. */
@Composable
fun Modifier.blockParentPagerSwipe(): Modifier {
    val coordinator = LocalPagerGestureCoordinator.current ?: return this
    return this.pointerInput(coordinator) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val release = coordinator.acquire()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                release()
            }
        }
    }
}

private object ConsumeHorizontalOverflowConnection : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

/** Horizontal scroll modifier that absorbs boundary overflow and locks outer pager swipes cleanly. */
@Composable
fun Modifier.horizontalCarouselScroll(state: ScrollState = rememberScrollState()): Modifier =
    this
        .blockParentPagerSwipe()
        .nestedScroll(ConsumeHorizontalOverflowConnection)
        .horizontalScroll(state)

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

    val borderBrush = if (isDark) Color.White.copy(alpha = if (primary) 0.20f else 0.12f)
    else Color(0xFF0F172A).copy(alpha = if (primary) 0.16f else 0.09f)
    val primaryFill = if (isDark) Color(0xFFF1F5F9) else Color(0xFF111827)
    val contentColor = if (primary) {
        if (isDark) Color(0xFF0B0E13) else Color.White
    } else {
        Glass.TextPrimary
    }

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.44f }
            .height(if (compact) 40.dp else 52.dp)
            .shadow(
                elevation = if (primary) (if (isDark) 4.dp else 4.dp) else 0.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.10f)
            )
            .clip(shape)
            .then(
                if (primary) Modifier.background(primaryFill).border(1.dp, borderBrush, shape)
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
    val fill = when {
        selected && isDark -> Color.White.copy(alpha = 0.15f)
        selected -> Color(0xFF0F172A).copy(alpha = 0.10f)
        else -> Glass.CardFill
    }
    val stroke = when {
        selected && isDark -> Color.White.copy(alpha = 0.28f)
        selected -> Color(0xFF0F172A).copy(alpha = 0.22f)
        else -> Glass.Stroke
    }
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) Glass.TextPrimary else Glass.TextSecondary,
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
    Column(modifier.blockParentPagerSwipe()) {
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
        keyboardActions = keyboardActions,
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
