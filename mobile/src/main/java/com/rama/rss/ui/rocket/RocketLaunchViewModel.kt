package com.rama.rss.ui.rocket

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RocketLaunchViewModel : ViewModel() {

    private val _launchState = MutableStateFlow(RocketLaunchState.IDLE)
    val launchState: StateFlow<RocketLaunchState> = _launchState.asStateFlow()

    fun startLaunch() {
        if (_launchState.value != RocketLaunchState.IDLE) return
        _launchState.value = RocketLaunchState.IGNITION
    }

    fun startFlying() {
        if (_launchState.value == RocketLaunchState.IGNITION) {
            _launchState.value = RocketLaunchState.LAUNCHING
        }
    }

    fun finishLaunch() {
        if (_launchState.value == RocketLaunchState.LAUNCHING) {
            _launchState.value = RocketLaunchState.FINISHED
        }
    }

    fun reset() {
        _launchState.value = RocketLaunchState.IDLE
    }
}