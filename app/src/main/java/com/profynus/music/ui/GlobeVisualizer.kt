package com.profynus.music.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.profynus.music.data.AppearanceSettings
import com.profynus.music.data.ShapeProfile
import com.profynus.music.playback.AudioEnergy
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Tuning knobs. Everything that changes how the globe *feels* lives here.
// ---------------------------------------------------------------------------------------------

/** Vertices on the sphere. More = denser web, more CPU. 600-900 is the sweet spot on phones. */
private const val MESH_POINTS = 720

/** Each vertex links to its N nearest neighbours; this is what makes the tangled "plexus" look. */
private const val MESH_NEIGHBORS = 5

/** Edges are sorted into this many depth layers so far lines are dim and near lines are bright. */
private const val DEPTH_BUCKETS = 4

/** Auto-gain floor: quiet tracks are boosted until their peak reaches this level. */
private const val MIN_PEAK = 0.12f

private const val TWO_PI = 6.2831855f

// ---------------------------------------------------------------------------------------------
// Public composable
// ---------------------------------------------------------------------------------------------

/**
 * Glowing wire-mesh sphere driven by the local player's decoded PCM envelope.
 *
 * - [reducedMotion] freezes the globe (it keeps its current pose and relaxes smoothly, no snapping).
 * - Animation state is read in the draw phase only, so nothing recomposes per frame.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun GlobeVisualizer(
    accent: Color,
    profile: ShapeProfile,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    audioReactive: Boolean = false,
) {
    val mesh = remember { sphereMesh }
    val scratch = remember(mesh) { GlobeScratch(mesh) }
    val driver = remember { GlobeDriver() }

    LaunchedEffect(reducedMotion, audioReactive) {
        driver.run(animate = !reducedMotion, audioReactive = audioReactive)
    }

    Canvas(modifier.fillMaxWidth().height(300.dp)) {
        driver.frame // read to subscribe: the canvas redraws whenever the driver ticks
        drawGlobeMesh(
            mesh = mesh,
            scratch = scratch,
            accent = accent,
            profile = profile,
            spin = driver.spin,
            time = driver.time,
            energy = driver.energy,
            beat = driver.beat,
        )
    }
}

/** A single still for system media controls, rendered off the main thread. */
internal fun sphereArtwork(settings: AppearanceSettings): ByteArray {
    val palette = ProfynusPalettes.all.firstOrNull { it.id == settings.paletteId }
        ?: ProfynusPalettes.all.first()
    val side = 512
    val bitmap = ImageBitmap(side, side)
    val mesh = sphereMesh
    val scratch = GlobeScratch(mesh)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        GraphicsCanvas(bitmap),
        Size(side.toFloat(), side.toFloat()),
    ) {
        drawRect(Color(0xFF050B13))
        drawGlobeMesh(
            mesh = mesh,
            scratch = scratch,
            accent = palette.accent,
            profile = settings.shape,
            spin = 0.6f,
            time = 4.2f,
            energy = 0.45f,
            beat = 0.25f,
        )
    }
    return ByteArrayOutputStream().use { output ->
        val android = bitmap.asAndroidBitmap()
        android.compress(Bitmap.CompressFormat.PNG, 100, output)
        android.recycle()
        output.toByteArray()
    }
}

// ---------------------------------------------------------------------------------------------
// Animation driver: one frame clock, smoothed audio energy, onset ("beat") detection.
// ---------------------------------------------------------------------------------------------

@Stable
private class GlobeDriver {
    /** Bumped every frame; reading it inside the Canvas lambda invalidates only the draw phase. */
    var frame by mutableLongStateOf(0L)
    var spin = 0.6f
    var time = 0f
    var energy = 0f
    var beat = 0f

    private var average = 0f
    private var peak = MIN_PEAK

    suspend fun run(animate: Boolean, audioReactive: Boolean) {
        var last = withFrameNanos { it }
        while (true) {
            // Once paused/frozen, let the shape relax for a moment, then stop ticking entirely.
            if (!animate && energy < 0.01f && beat < 0.01f) {
                energy = 0f
                beat = 0f
                frame++
                return
            }
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.08f)
                last = now
                step(dt, animate, audioReactive)
                frame++
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun step(dt: Float, animate: Boolean, audioReactive: Boolean) {
        if (animate) {
            time += dt
            spin = (spin + dt * 0.32f) % TWO_PI
        }
        val fresh = SystemClock.elapsedRealtime() - AudioEnergy.updatedAt < 250
        val raw = if (animate && audioReactive && fresh) AudioEnergy.level.coerceIn(0f, 1f) else 0f

        // Auto-gain: normalise against a slowly decaying peak so quiet and loud tracks look alike.
        peak = max(MIN_PEAK, max(raw, peak * exp(-dt * 0.2f)))
        val target = (raw / peak).coerceIn(0f, 1f)

        // Fast attack, slower release.
        val rate = if (target > energy) 30f else 7f
        energy += (target - energy) * (1f - exp(-dt * rate))

        // Beat = how far the energy jumps above its own recent average.
        average += (energy - average) * (1f - exp(-dt * 2.2f))
        val onset = (energy - average).coerceAtLeast(0f) * 2.4f
        beat = max(beat * exp(-dt * 6f), onset).coerceAtMost(1f)
    }
}

// ---------------------------------------------------------------------------------------------
// Mesh: jittered Fibonacci sphere + nearest-neighbour edges, built once.
// ---------------------------------------------------------------------------------------------

private class SphereMesh(
    /** Unit directions, xyz-interleaved. */
    val xyz: FloatArray,
    /** Per-vertex random 0..1, used to roughen spikes so they look hairy rather than smooth. */
    val seed: FloatArray,
    /** Vertex index pairs, a0 b0 a1 b1 ... */
    val edges: IntArray,
) {
    val vertexCount: Int get() = seed.size
    val edgeCount: Int get() = edges.size / 2
}

private val sphereMesh: SphereMesh by lazy(LazyThreadSafetyMode.PUBLICATION) { buildSphereMesh() }

private fun buildSphereMesh(count: Int = MESH_POINTS, neighbors: Int = MESH_NEIGHBORS): SphereMesh {
    val random = java.util.Random(0x5EEDL)
    val golden = PI * (3.0 - sqrt(5.0))
    val spacing = sqrt(4.0 * PI / count)
    val xyz = FloatArray(count * 3)
    val seed = FloatArray(count) { random.nextFloat() }

    for (i in 0 until count) {
        val y = 1.0 - 2.0 * (i + 0.5) / count
        val ring = sqrt(1.0 - y * y)
        val theta = golden * i
        // Jitter breaks the regular triangulation into an organic, tangled web.
        val px = cos(theta) * ring + (random.nextDouble() - 0.5) * spacing * 0.9
        val py = y + (random.nextDouble() - 0.5) * spacing * 0.9
        val pz = sin(theta) * ring + (random.nextDouble() - 0.5) * spacing * 0.9
        val length = sqrt(px * px + py * py + pz * pz)
        xyz[i * 3] = (px / length).toFloat()
        xyz[i * 3 + 1] = (py / length).toFloat()
        xyz[i * 3 + 2] = (pz / length).toFloat()
    }

    val keys = HashSet<Long>()
    val best = IntArray(neighbors)
    val bestDot = FloatArray(neighbors)
    for (i in 0 until count) {
        best.fill(-1)
        bestDot.fill(-2f)
        val ix = xyz[i * 3]
        val iy = xyz[i * 3 + 1]
        val iz = xyz[i * 3 + 2]
        for (j in 0 until count) {
            if (j == i) continue
            val d = ix * xyz[j * 3] + iy * xyz[j * 3 + 1] + iz * xyz[j * 3 + 2]
            if (d <= bestDot[neighbors - 1]) continue
            var p = neighbors - 1
            while (p > 0 && bestDot[p - 1] < d) {
                bestDot[p] = bestDot[p - 1]
                best[p] = best[p - 1]
                p--
            }
            bestDot[p] = d
            best[p] = j
        }
        for (j in best) {
            if (j >= 0) keys.add(min(i, j).toLong() * count + max(i, j))
        }
    }

    val edges = IntArray(keys.size * 2)
    var cursor = 0
    for (key in keys) {
        edges[cursor++] = (key / count).toInt()
        edges[cursor++] = (key % count).toInt()
    }
    return SphereMesh(xyz, seed, edges)
}

// ---------------------------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------------------------

/** Reusable per-view buffers so a frame allocates nothing. Main-thread only. */
private class GlobeScratch(mesh: SphereMesh) {
    val sx = FloatArray(mesh.vertexCount)
    val sy = FloatArray(mesh.vertexCount)
    val sz = FloatArray(mesh.vertexCount)
    val buckets = Array(DEPTH_BUCKETS) { FloatArray(mesh.edgeCount * 4) }
    val bucketLength = IntArray(DEPTH_BUCKETS)
    val dots = FloatArray(mesh.vertexCount * 2)

    /** Additive blending: overlapping threads add up and glow, exactly like the desktop globe. */
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }
}

private fun DrawScope.drawGlobeMesh(
    mesh: SphereMesh,
    scratch: GlobeScratch,
    accent: Color,
    profile: ShapeProfile,
    spin: Float,
    time: Float,
    energy: Float,
    beat: Float,
) {
    val count = mesh.vertexCount
    val base = mesh.xyz
    val seeds = mesh.seed
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * (0.29f + 0.03f * energy + 0.02f * beat)
    val unit = 1.dp.toPx()

    // Shape profile -> how wobbly the idle surface is and how many lobes it has.
    val deform = profile.deformation.toFloat()
    val lobes = profile.frequency.toFloat().coerceIn(1f, 12f) * 0.45f
    val surfaceAmp = deform * 1.5f
    val spikeAmp = 0.20f + deform * 0.9f
    val f1 = lobes
    val f2 = lobes * 2.3f
    val f3 = 1.6f + lobes * 0.5f
    val drive = energy * 0.55f + beat * 0.45f
    val breathe = 1f + 0.012f * sin(time * 1.2f)

    val cosS = cos(spin)
    val sinS = sin(spin)
    val tilt = -0.28f
    val cosT = cos(tilt)
    val sinT = sin(tilt)

    val sx = scratch.sx
    val sy = scratch.sy
    val sz = scratch.sz
    val dots = scratch.dots
    var dotLength = 0

    for (i in 0 until count) {
        val dx = base[i * 3]
        val dy = base[i * 3 + 1]
        val dz = base[i * 3 + 2]

        // Slow organic surface noise (two octaves of cheap trig noise).
        val low = 0.5f * (
                sin(dx * f1 + time * 0.55f) * cos(dy * f1 * 1.1f - time * 0.43f) +
                        sin(dz * f1 * 0.9f + dx * 1.7f + time * 0.37f)
                )
        val mid = sin(dx * f2 + dy * f2 * 0.7f + time * 0.9f) * sin(dz * f2 - time * 0.7f)
        var r = breathe + surfaceAmp * (0.7f * low + 0.3f * mid)

        // Audio spikes: localised bumps that grow with energy and kick on beats.
        if (drive > 0.01f) {
            val s = (
                    sin(dx * f3 + time * 1.6f) +
                            sin(dy * f3 * 1.3f - time * 1.3f) +
                            sin(dz * f3 * 0.8f + time * 1.1f)
                    ) / 3f
            val p = ((s - 0.2f) * 1.25f).coerceAtLeast(0f)
            r += p * p * drive * spikeAmp * 2.4f * (0.65f + 0.7f * seeds[i])
        }
        r = r.coerceIn(0.6f, 1.5f)

        // Spin around Y, tilt around X, then a light perspective squeeze.
        val x = dx * r
        val y = dy * r
        val z = dz * r
        val xr = x * cosS + z * sinS
        val zr = -x * sinS + z * cosS
        val yt = y * cosT - zr * sinT
        val zt = y * sinT + zr * cosT
        val persp = 3f / (3f - zt * 0.5f)

        val px = cx + xr * radius * persp
        val py = cy + yt * radius * persp
        sx[i] = px
        sy[i] = py
        sz[i] = zt

        if (zt > 0.12f) {
            dots[dotLength++] = px
            dots[dotLength++] = py
        }
    }

    // Sort edges into depth layers (index grows toward the viewer).
    val edges = mesh.edges
    val buckets = scratch.buckets
    val lengths = scratch.bucketLength
    lengths.fill(0)
    for (e in 0 until mesh.edgeCount) {
        val a = edges[e * 2]
        val b = edges[e * 2 + 1]
        val depth = ((sz[a] + sz[b]) * 0.5f + 1.2f) / 2.4f
        val bucket = (depth.coerceIn(0f, 0.9999f) * DEPTH_BUCKETS).toInt()
        val target = buckets[bucket]
        val at = lengths[bucket]
        target[at] = sx[a]
        target[at + 1] = sy[a]
        target[at + 2] = sx[b]
        target[at + 3] = sy[b]
        lengths[bucket] = at + 4
    }

    // Soft halo behind the mesh; swells with the music.
    val glowRadius = size.minDimension * 0.5f
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            0f to accent.copy(alpha = 0.16f + 0.22f * energy),
            0.5f to accent.copy(alpha = 0.05f + 0.08f * energy),
            1f to Color.Transparent,
            center = center,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = center,
    )

    val canvas = drawContext.canvas.nativeCanvas
    val paint = scratch.paint

    for (bucket in 0 until DEPTH_BUCKETS) {
        val length = lengths[bucket]
        if (length == 0) continue
        val depth = (bucket + 0.5f) / DEPTH_BUCKETS // 0 = far side, 1 = nearest

        // Bloom pass for the two nearest layers: wide, faint, additive.
        if (bucket >= DEPTH_BUCKETS - 2) {
            paint.strokeWidth = unit * 3.4f
            paint.color = accent.copy(alpha = 0.05f + 0.07f * energy).toArgb()
            canvas.drawLines(buckets[bucket], 0, length, paint)
        }

        // Crisp pass: far threads are dim and thin, near threads are bright and drift toward white.
        val alpha = ((0.06f + 0.42f * depth) * (0.9f + 0.5f * energy)).coerceAtMost(0.9f)
        paint.strokeWidth = unit * (0.6f + 0.55f * depth)
        paint.color = lerp(accent, Color.White, 0.08f + 0.30f * depth * depth * (0.4f + energy))
            .copy(alpha = alpha)
            .toArgb()
        canvas.drawLines(buckets[bucket], 0, length, paint)
    }

    // Bright nodes on the visible hemisphere.
    if (dotLength > 0) {
        paint.strokeWidth = unit * 1.9f
        paint.color = lerp(accent, Color.White, 0.45f).copy(alpha = 0.55f + 0.25f * energy).toArgb()
        canvas.drawPoints(dots, 0, dotLength, paint)
    }
}