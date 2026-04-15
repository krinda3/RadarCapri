package com.pridalac.radarcapri.service

import com.pridalac.radarcapri.data.SpeedCamera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServiceState(
    val speedKmh: Float = 0f,
    val zoneCount: Int = 0,
    val networkOk: Boolean = true,
    val distanceKm: Float = 0f,
    val routePointCount: Int = 0,
    val nearestCamera: SpeedCamera? = null,
    val nearestCameraDistance: Float = 0f
)

object ServiceStateHolder {
    private val _state = MutableStateFlow(ServiceState())
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    fun updateSpeed(speedKmh: Float) {
        _state.value = _state.value.copy(speedKmh = speedKmh)
    }

    fun updateCamera(speedKmh: Float, zoneCount: Int, networkOk: Boolean, distanceKm: Float, routePointCount: Int, camera: SpeedCamera?, distance: Float) {
        _state.value = _state.value.copy(
            speedKmh = speedKmh,
            zoneCount = zoneCount,
            networkOk = networkOk,
            distanceKm = distanceKm,
            routePointCount = routePointCount,
            nearestCamera = camera,
            nearestCameraDistance = distance
        )
    }

    fun reset() {
        _state.value = ServiceState()
    }
}