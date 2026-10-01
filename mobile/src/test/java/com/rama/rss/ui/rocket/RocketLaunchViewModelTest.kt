package com.rama.rss.ui.rocket

import org.junit.Assert.assertEquals
import org.junit.Test

class RocketLaunchViewModelTest {
    @Test
    fun launchFollowsSequenceAndCanRepeatAfterReset() {
        val viewModel = RocketLaunchViewModel()
        repeat(2) {
            assertEquals(RocketLaunchState.IDLE, viewModel.launchState.value)
            viewModel.startLaunch()
            assertEquals(RocketLaunchState.IGNITION, viewModel.launchState.value)
            viewModel.startFlying()
            assertEquals(RocketLaunchState.LAUNCHING, viewModel.launchState.value)
            viewModel.finishLaunch()
            assertEquals(RocketLaunchState.FINISHED, viewModel.launchState.value)
            viewModel.reset()
        }
    }

    @Test
    fun outOfOrderCallbacksCannotAdvanceLaunch() {
        val viewModel = RocketLaunchViewModel()
        viewModel.startFlying()
        viewModel.finishLaunch()
        assertEquals(RocketLaunchState.IDLE, viewModel.launchState.value)
        viewModel.startLaunch()
        viewModel.startLaunch()
        viewModel.finishLaunch()
        assertEquals(RocketLaunchState.IGNITION, viewModel.launchState.value)
        viewModel.startFlying()
        viewModel.startLaunch()
        assertEquals(RocketLaunchState.LAUNCHING, viewModel.launchState.value)
        viewModel.reset()
        viewModel.finishLaunch()
        assertEquals(RocketLaunchState.IDLE, viewModel.launchState.value)
    }
}

