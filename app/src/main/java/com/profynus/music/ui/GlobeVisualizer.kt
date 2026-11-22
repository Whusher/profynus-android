package com.profynus.music.ui


import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.profynus.music.data.ShapeProfile
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.profynus.music.data.AppearanceSettings
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Efficient Compose 3D-projected wire sphere, with no internet access or audio coupling.
 * Important: phase 1 uses time-based deformation; it is NOT yet PCM audio-reactive.
 * Later the same drawing contract can receive real PCM energy from the local player.
 */
@Composable
fun GlobeVisualizer(
    accent: Color,
    profile: ShapeProfile,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    if (reducedMotion) {
        GlobeCanvas(accent, profile, angle = 0f, modifier = modifier)
    } else {
        val transition = rememberInfiniteTransition(label = "Profynus globe rotation")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(26000), repeatMode = RepeatMode.Restart,
            ),
            label = "globe angle",
        )
        GlobeCanvas(accent, profile, angle = angle, modifier = modifier)
    }
}

@Composable
private fun GlobeCanvas(
    accent: Color,
    profile: ShapeProfile,
    angle: Float,
    modifier: Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(300.dp)) {
        drawWireSphere(accent, profile, angle)
    }
}

/** A single still for system media controls, rendered off the main thread. */
internal fun sphereArtwork(settings: AppearanceSettings): ByteArray {
    val palette = ProfynusPalettes.all.firstOrNull { it.id == settings.paletteId }
        ?: ProfynusPalettes.all.first()
    val bitmap = ImageBitmap(384, 384)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(384f, 384f)) {
        drawRect(Color(0xFF050B13))
        drawWireSphere(palette.accent, settings.shape, angle = 35f)
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.asAndroidBitmap().recycle()
        output.toByteArray()
    }
}

private fun DrawScope.drawWireSphere(
    accent: Color,
    profile: ShapeProfile,
    angle: Float,
) {
    val latitudes = 17
    val longitudes = 28
    val spin = angle * PI / 180.0
    val tilt = -0.24
    val radius = size.minDimension * 0.34f
    val center = Offset(size.width / 2f, size.height / 2f)
    val points = Array(latitudes + 1) { arrayOfNulls<Offset>(longitudes + 1) }
    val depths = Array(latitudes + 1) { FloatArray(longitudes + 1) }

    for (latitude in 0..latitudes) {
        val polar = PI * latitude / latitudes
        for (longitude in 0..longitudes) {
            val azimuth = 2.0 * PI * longitude / longitudes + spin
            val ripple = 1.0 + profile.deformation * sin(
                polar * profile.frequency + azimuth * 2.0 + spin,
            )
            val x = sin(polar) * cos(azimuth) * ripple
            val y = cos(polar) * ripple
            val z = sin(polar) * sin(azimuth) * ripple
            val yt = y * cos(tilt) - z * sin(tilt)
            val zt = y * sin(tilt) + z * cos(tilt)
            val perspective = 2.4 / (2.4 - zt * 0.4)
            points[latitude][longitude] = Offset(
                center.x + (x * radius * perspective).toFloat(),
                center.y + (yt * radius * perspective).toFloat(),
            )
            depths[latitude][longitude] = zt.toFloat()
        }
    }
    for (lat in 0..latitudes) {
        for (lon in 0..longitudes) {
            val p = points[lat][lon] ?: continue
            val alpha = (0.24f + (depths[lat][lon] + 1f) * 0.26f).coerceIn(0.14f, 0.80f)
            if (lat < latitudes) {
                points[lat + 1][lon]?.let { q ->
                    drawLine(accent.copy(alpha = alpha), p, q, strokeWidth = 0.85.dp.toPx())
                }
            }
            if (lon < longitudes) {
                points[lat][lon + 1]?.let { q ->
                    drawLine(accent.copy(alpha = alpha), p, q, strokeWidth = 0.85.dp.toPx())
                }
            }
        }
    }
    drawCircle(accent.copy(alpha = 0.055f), radius * 1.35f, center)
}
