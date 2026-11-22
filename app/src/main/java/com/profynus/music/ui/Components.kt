package com.profynus.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.profynus.music.R
import java.util.Locale

data class NavItem(val title: String, val icon: ImageVector)

/** Cyan of the logo artwork; used only for the soft halo behind it. */
private val LogoCyan = Color(0xFF32EBFE)

/**
 * Brand mark (res/drawable-nodpi/profynus_logo.png). The artwork is fixed cyan, so it keeps its
 * identity under every palette; only the halo and wordmark follow the theme.
 */
@Composable
fun ProfynusLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glow: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                if (glow) {
                    val haloRadius = this.size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(LogoCyan.copy(alpha = 0.28f), Color.Transparent),
                            center = center,
                            radius = haloRadius,
                        ),
                        radius = haloRadius,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.profynus_logo),
            contentDescription = "Profynus logo",
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

/** Logo + tracked-out "PROFYNUS" lettering in the palette gradient. */
@Composable
fun ProfynusWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 30.dp,
    textStyle: TextStyle = ProfynusWordmarkStyle,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfynusLogo(size = logoSize)
        Text("PROFYNUS", style = textStyle.copy(brush = accentBrush()), maxLines = 1)
    }
}

/** Primary → secondary gradient of the current palette. */
@Composable
fun accentBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(colors.primary, colors.secondary))
}

/** Full-screen background: deep base with two soft glows taken from the palette. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.10f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height * 0.38f),
                        radius = size.width * 0.85f,
                    ),
                )
            },
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Rounded themed container with a hairline border. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Placeholder cover art: a tonal gradient that varies per track. */
@Composable
fun ArtTile(
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    active: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val mix = remember(seed) { ((seed.hashCode() and 0x7fffffff) % 100) / 100f }
    val start = lerp(colors.surfaceContainerHighest, lerp(colors.primary, colors.secondary, mix), 0.55f)
    val end = lerp(colors.surfaceContainerHighest, lerp(colors.secondary, colors.primary, mix), 0.30f)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) Icons.Default.GraphicEq else Icons.Default.MusicNote,
            contentDescription = null,
            tint = colors.onSurface.copy(alpha = 0.92f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** The one big call-to-action on the player: gradient disc with a colored glow. */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .shadow(
                elevation = if (enabled) 20.dp else 0.dp,
                shape = CircleShape,
                ambientColor = colors.primary,
                spotColor = colors.primary,
            )
            .clip(CircleShape)
            .background(accentBrush())
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = colors.onPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Quiet previous / next button. */
@Composable
fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(32.dp),
        )
    }
}

private val ThumbPad = 12.dp

private fun fractionOf(x: Float, pad: Float, width: Float): Float =
    ((x - pad) / (width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)

/**
 * Custom seek bar: thin gradient track with a glowing thumb. Tap to jump, drag to scrub.
 * [onSeekPreview] fires while dragging, [onSeekFinished] when the user lets go or taps.
 */
@Composable
fun SeekBar(
    progress: Float,
    enabled: Boolean,
    onSeekPreview: (Float) -> Unit,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    val trackColor = colors.onSurface.copy(alpha = 0.12f)
    val currentPreview by rememberUpdatedState(onSeekPreview)
    val currentFinished by rememberUpdatedState(onSeekFinished)
    val clamped = progress.coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = "Seek position"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        currentFinished(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbPad.toPx()
                detectTapGestures { offset ->
                    currentFinished(fractionOf(offset.x, pad, size.width.toFloat()))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = ThumbPad.toPx()
                var fraction = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        fraction = fractionOf(offset.x, pad, size.width.toFloat())
                        currentPreview(fraction)
                    },
                    onDragEnd = { currentFinished(fraction) },
                    onDragCancel = { currentFinished(fraction) },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        fraction = (fraction + delta / (size.width - 2f * pad).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        currentPreview(fraction)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = ThumbPad.toPx()
            val usable = size.width - 2f * pad
            val trackHeight = 5.dp.toPx()
            val centerY = size.height / 2f
            val top = centerY - trackHeight / 2f
            val corner = CornerRadius(trackHeight / 2f)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(pad, top),
                size = Size(usable, trackHeight),
                cornerRadius = corner,
            )
            val fillWidth = usable * clamped
            if (fillWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primary, secondary),
                        startX = pad,
                        endX = pad + usable,
                    ),
                    topLeft = Offset(pad, top),
                    size = Size(fillWidth, trackHeight),
                    cornerRadius = corner,
                    alpha = if (enabled) 1f else 0.4f,
                )
            }
            if (enabled) {
                val center = Offset(pad + fillWidth, centerY)
                drawCircle(primary.copy(alpha = 0.22f), radius = 13.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 6.dp.toPx(), center = center)
            }
        }
    }
}

/** Floating pill navigation: the selected item expands to show its label. */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val background by animateColorAsState(
                    if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                    label = "navBackground",
                )
                val foreground by animateColorAsState(
                    if (selected) colors.primary else colors.onSurfaceVariant,
                    label = "navForeground",
                )
                Row(
                    Modifier
                        .animateContentSize()
                        .clip(RoundedCornerShape(26.dp))
                        .background(background)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = if (selected) null else item.title,
                        tint = foreground,
                        modifier = Modifier.size(22.dp),
                    )
                    if (selected) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = foreground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    hero: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hero != null) {
            hero()
        } else {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.28f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Shared chip colors so selected chips use the palette accent. */
@Composable
fun profynusChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)
