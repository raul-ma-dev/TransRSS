package com.rama.rss.ui.rocket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
internal fun RocketSparkParticles(
    state: RocketLaunchState,
    modifier: Modifier = Modifier,
    rocketOffsetY: () -> Float = { 0f }
) {
    val density = LocalDensity.current.density
    val preview = LocalInspectionMode.current
    val system = remember(density, preview) {
        RocketParticleSystem(if (preview) Random(7) else Random.Default).apply {
            if (preview) {
                emitBurst(26, 0f, 0f, launching = false)
                advance(0.12f)
            }
        }
    }
    val currentRocketOffset by rememberUpdatedState(rocketOffsetY)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var drawTick by remember { mutableIntStateOf(if (preview) 1 else 0) }

    LaunchedEffect(state, canvasSize, density, preview) {
        if (preview || canvasSize == IntSize.Zero) return@LaunchedEffect
        val emitting = state == RocketLaunchState.IGNITION || state == RocketLaunchState.LAUNCHING
        val launching = state == RocketLaunchState.LAUNCHING
        fun emitterY(): Float = canvasSize.height / density - 12f + currentRocketOffset() / density
        if (state == RocketLaunchState.IGNITION) {
            system.emitBurst(18, canvasSize.width / density / 2f, emitterY(), launching = false)
            drawTick++
        }
        var previousFrame = withFrameNanos { it }
        while (isActive && (emitting || system.particles.isNotEmpty())) {
            val frame = withFrameNanos { it }
            // Cap physics steps after a pause, avoiding a burst of work on resume.
            val delta = ((frame - previousFrame).coerceAtLeast(0L) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            system.advance(
                deltaSeconds = delta,
                emitterX = canvasSize.width / density / 2f,
                emitterY = emitterY(),
                emissionRate = if (!emitting) 0f else if (launching) 110f else 75f,
                launching = launching
            )
            // Read only in the draw phase: no per-frame recomposition of the rocket.
            drawTick++
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { canvasSize = it }.testTag("RocketSparkParticles")) {
        if (drawTick == 0) return@Canvas
        val previewOrigin = if (preview) Offset(size.width / 2f, size.height - 12f * density) else Offset.Zero
        for (spark in system.particles) {
            val remaining = spark.remainingFraction
            val alpha = remaining * remaining
            val center = Offset(spark.x * density, spark.y * density) + previewOrigin
            val radius = spark.radius * density * (0.4f + 0.6f * remaining)
            val initialColor = lerp(Color(0xFFFF8A25), Color(0xFFFFF1B5), spark.warmth)
            val color = lerp(initialColor, Color(0xFFD9430A), 1f - remaining)
            val trail = Offset(spark.velocityX, spark.velocityY) * (0.025f * density)
            drawCircle(color.copy(alpha = alpha * 0.14f), radius = radius * 3.5f, center = center)
            drawLine(
                color = color.copy(alpha = alpha * 0.7f),
                start = center - trail,
                end = center,
                strokeWidth = radius,
                cap = StrokeCap.Round
            )
            drawCircle(color.copy(alpha = alpha), radius = radius, center = center)
        }
    }
}

@Preview(name = "Chispas · Canvas", group = "Cohete", widthDp = 360, heightDp = 300, showBackground = true, backgroundColor = 0xFF181113)
@Composable
private fun RocketSparkParticlesPreview() {
    RocketSparkParticles(RocketLaunchState.IGNITION)
}

