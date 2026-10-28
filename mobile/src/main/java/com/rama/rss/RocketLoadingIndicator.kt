package com.rama.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.rama.rss.ui.rocket.RocketLaunchAnimation
import com.rama.rss.ui.rocket.RocketLaunchScreen
import com.rama.rss.ui.rocket.RocketLaunchState
import com.rama.rss.ui.rocket.RocketLaunchViewModel
import com.rama.rss.ui.theme.RssTheme

@Composable
internal fun RocketLoadingIndicator(animating: Boolean, modifier: Modifier = Modifier) {
    val taggedModifier = modifier.testTag("RocketLoadingIndicator")
    if (!animating || LocalInspectionMode.current) {
        RocketLaunchAnimation(
            state = RocketLaunchState.IDLE,
            modifier = taggedModifier,
            onIgnitionFinished = {},
            onLaunchFinished = {}
        )
        return
    }
    val store = remember { ViewModelStore() }
    val viewModel = remember(store) {
        ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[RocketLaunchViewModel::class.java]
    }
    DisposableEffect(store) {
        onDispose { store.clear() }
    }
    RocketLaunchScreen(
        viewModel = viewModel,
        modifier = taggedModifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        },
        automatic = true,
        backgroundColor = Color.Transparent
    )
}

@Preview(name = "Indicador de carga", group = "Cohete", showBackground = true, widthDp = 240, heightDp = 240)
@Composable
private fun RocketLoadingIndicatorPreview() {
    RssTheme(darkTheme = true, dynamicColor = false) {
        RocketLoadingIndicator(animating = true, modifier = Modifier.size(240.dp))
    }
}
