package com.rama.rss.ui.rocket

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rama.rss.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RocketLaunchAnimation(
    state: RocketLaunchState,
    modifier: Modifier = Modifier,
    onIgnitionFinished: () -> Unit,
    onLaunchFinished: () -> Unit
) {
    if (state == RocketLaunchState.IDLE || LocalInspectionMode.current) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Image(
                painter = painterResource(R.drawable.rocket),
                contentDescription = "Cohete despegando",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(165.dp)
            )
        }
        return
    }
    val ignitionFinished by rememberUpdatedState(onIgnitionFinished)
    val launchFinished by rememberUpdatedState(onLaunchFinished)
    val firstFlamePainter = painterResource(R.drawable.flame_01)
    val secondFlamePainter = painterResource(R.drawable.flame_02)
    var launchHeightPx by remember { mutableIntStateOf(0) }
    val extraTravelPx = with(LocalDensity.current) { 330.dp.toPx() }
    val rocketOffsetY = remember { Animatable(0f) }
    val smokeScale = remember { Animatable(0.40f) }
    val smokeAlpha = remember { Animatable(0f) }
    val secondSmokeScale = remember { Animatable(0.35f) }
    val secondSmokeAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(
        label = "rocket-effects"
    )

    val vibrationX by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 45,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rocket-vibration-x"
    )

    val vibrationY by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 60,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rocket-vibration-y"
    )

    val flameScaleY by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 90,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame-scale-y"
    )

    val flameScaleX by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 110,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame-scale-x"
    )

    var flameFrame by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        if (
            state == RocketLaunchState.IGNITION ||
            state == RocketLaunchState.LAUNCHING
        ) {
            while (true) {
                flameFrame = if (flameFrame == 0) 1 else 0
                delay(85)
            }
        }
    }

    LaunchedEffect(state) {
        if (state == RocketLaunchState.IGNITION) {
            rocketOffsetY.snapTo(0f)

            smokeScale.snapTo(0.40f)
            smokeAlpha.snapTo(0f)

            secondSmokeScale.snapTo(0.35f)
            secondSmokeAlpha.snapTo(0f)

            launch {
                smokeScale.animateTo(
                    targetValue = 1.20f,
                    animationSpec = tween(1200)
                )
            }

            launch {
                smokeAlpha.animateTo(
                    targetValue = 0.95f,
                    animationSpec = tween(250)
                )
            }

            launch {
                delay(180)

                secondSmokeScale.animateTo(
                    targetValue = 1.45f,
                    animationSpec = tween(1350)
                )
            }

            launch {
                delay(180)

                secondSmokeAlpha.animateTo(
                    targetValue = 0.80f,
                    animationSpec = tween(300)
                )
            }

            delay(950)
            ignitionFinished()
        }
    }

    LaunchedEffect(state, launchHeightPx, extraTravelPx) {
        if (state == RocketLaunchState.LAUNCHING) {
            launch {
                delay(350)

                smokeAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(1600)
                )
            }

            launch {
                delay(500)

                secondSmokeAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(1500)
                )
            }

            rocketOffsetY.animateTo(
                targetValue = -(launchHeightPx + extraTravelPx),
                animationSpec = tween(
                    durationMillis = 2400,
                    easing = CubicBezierEasing(
                        0.45f,
                        0.0f,
                        1.0f,
                        1.0f
                    )
                )
            )

            launchFinished()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { launchHeightPx = it.height },
        contentAlignment = Alignment.BottomCenter
    ) {

        /*
         * Humo trasero.
         * No pertenece al contenedor del cohete, por eso permanece
         * en el punto de lanzamiento mientras el cohete sube.
         */
        if (state != RocketLaunchState.IDLE) {
            Image(
                painter = painterResource(R.drawable.smoke_02),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(390.dp)
                    .offset(y = 30.dp)
                    .graphicsLayer {
                        scaleX = secondSmokeScale.value
                        scaleY = secondSmokeScale.value
                        alpha = secondSmokeAlpha.value
                    }
                    .zIndex(0f)
            )

            Image(
                painter = painterResource(R.drawable.smoke_01),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(360.dp)
                    .offset(y = 48.dp)
                    .graphicsLayer {
                        scaleX = smokeScale.value
                        scaleY = smokeScale.value
                        alpha = smokeAlpha.value
                    }
                    .zIndex(1f)
            )
        }

        RocketSparkParticles(
            state = state,
            modifier = Modifier.matchParentSize().zIndex(2f),
            rocketOffsetY = { rocketOffsetY.value }
        )

        /*
         * Todo lo que esté aquí se mueve junto con el cohete.
         */
        Box(
            modifier = Modifier
                .offset {
                    val ignitionX =
                        if (state == RocketLaunchState.IGNITION) {
                            vibrationX
                        } else {
                            0f
                        }

                    val ignitionY =
                        if (state == RocketLaunchState.IGNITION) {
                            vibrationY
                        } else {
                            0f
                        }

                    IntOffset(
                        x = ignitionX.roundToInt(),
                        y = (rocketOffsetY.value + ignitionY).roundToInt()
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {

            /*
             * Llama debajo del cohete.
             */
            if (
                state == RocketLaunchState.IGNITION ||
                state == RocketLaunchState.LAUNCHING
            ) {
                Image(
                    painter = if (flameFrame == 0) firstFlamePainter else secondFlamePainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(118.dp)
                        .offset(y = 82.dp)
                        .graphicsLayer {
                            scaleX = flameScaleX
                            scaleY = flameScaleY
                            transformOrigin = TransformOrigin(
                                pivotFractionX = 0.5f,
                                pivotFractionY = 0f
                            )
                        }
                        .zIndex(3f)
                )
            }

            /*
             * Cohete.
             */
            Image(
                painter = painterResource(R.drawable.rocket),
                contentDescription = "Cohete despegando",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(165.dp)
                    .zIndex(4f)
            )
        }
    }
}
