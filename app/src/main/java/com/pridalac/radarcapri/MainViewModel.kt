package com.pridalac.radarcapri

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pridalac.radarcapri.preferences.PreferencesManager
import com.pridalac.radarcapri.service.RadarService
import com.pridalac.radarcapri.service.ServiceStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NearestCameraUiState(
    val name: String,
    val type: String,
    val distanceMeters: Float,
    val speedLimit: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0f)
    val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()

    private val _nearestCamera = MutableStateFlow<NearestCameraUiState?>(null)
    val nearestCamera: StateFlow<NearestCameraUiState?> = _nearestCamera.asStateFlow()

    private val _zoneCount = MutableStateFlow(0)
    val zoneCount: StateFlow<Int> = _zoneCount.asStateFlow()

    private val _networkOk = MutableStateFlow(true)
    val networkOk: StateFlow<Boolean> = _networkOk.asStateFlow()

    private val _distanceTraveledKm = MutableStateFlow(0f)
    val distanceTraveledKm: StateFlow<Float> = _distanceTraveledKm.asStateFlow()

    private val _routePointCount = MutableStateFlow(0)
    val routePointCount: StateFlow<Int> = _routePointCount.asStateFlow()

    init {
        // Observar el StateFlow compartido del servicio
        viewModelScope.launch {
            ServiceStateHolder.state.collect { state ->
                _currentSpeedKmh.value = state.speedKmh
                _zoneCount.value = state.zoneCount
                _networkOk.value = state.networkOk
                _distanceTraveledKm.value = state.distanceKm
                _routePointCount.value = state.routePointCount
                _nearestCamera.value = state.nearestCamera?.let { cam ->
                    NearestCameraUiState(
                        name = cam.name,
                        type = cam.typeInSpanish,
                        distanceMeters = state.nearestCameraDistance,
                        speedLimit = cam.vitesse
                    )
                }
            }
        }
    }

    fun startService() {
        getApplication<Application>().startForegroundService(
            Intent(getApplication(), RadarService::class.java)
        )
        _serviceRunning.value = true
        _distanceTraveledKm.value = 0f
        _routePointCount.value = 0
    }

    fun stopService() {
        getApplication<Application>().stopService(
            Intent(getApplication(), RadarService::class.java)
        )
        _serviceRunning.value = false
        _currentSpeedKmh.value = 0f
        _nearestCamera.value = null
        _zoneCount.value = 0
        ServiceStateHolder.reset()
    }

    fun saveKml() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, RadarService::class.java).also { it.action = RadarService.ACTION_SAVE_KML }
        )
    }
}