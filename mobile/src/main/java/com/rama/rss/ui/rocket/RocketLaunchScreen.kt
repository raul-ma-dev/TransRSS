package com.rama.rss.ui.rocket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun RocketLaunchScreen(
    viewModel: RocketLaunchViewModel,
    modifier: Modifier = Modifier,
    automatic: Boolean = false,
    backgroundColor: Color = Color(0xFF090B0F)
) {
    val state by viewModel.launchState.collectAsStateWithLifecycle()
    val isPreview = LocalInspectionMode.current
    LaunchedEffect(automatic, state, isPreview) {
        if (automatic && !isPreview) {
            when (state) {
                RocketLaunchState.IDLE -> viewModel.startLaunch()
                RocketLaunchState.FINISHED -> {
                    delay(250)
                    viewModel.reset()
                }
                else -> Unit
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(backgroundColor)
            .testTag("RocketLaunchScreen")
            .semantics {
                contentDescription = "Cargando entradas"
                stateDescription = state.name
            }
    ) {

        RocketLaunchAnimation(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onIgnitionFinished = {
                viewModel.startFlying()
            },
            onLaunchFinished = {
                viewModel.finishLaunch()
            }
        )

        if (!automatic) when (state) {
            RocketLaunchState.IDLE -> {
                Button(
                    onClick = viewModel::startLaunch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    Text("Despegar")
                }
            }

            RocketLaunchState.FINISHED -> {
                Button(
                    onClick = viewModel::reset,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    Text("Repetir")
                }
            }

            RocketLaunchState.IGNITION,
            RocketLaunchState.LAUNCHING -> Unit
        }
    }
}
