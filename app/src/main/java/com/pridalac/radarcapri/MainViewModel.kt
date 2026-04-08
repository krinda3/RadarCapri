package com.pridalac.radarcapri

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.pridalac.radarcapri.preferences.PreferencesManager
import com.pridalac.radarcapri.service.RadarService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _currentSpeedKmh.value = intent.getFloatExtra(RadarService.EXTRA_SPEED_KMH, 0f)
            _zoneCount.value = intent.getIntExtra(RadarService.EXTRA_ZONE_COUNT, 0)
            _networkOk.value = intent.getBooleanExtra(RadarService.EXTRA_NETWORK_OK, true)
            _distanceTraveledKm.value = intent.getFloatExtra(RadarService.EXTRA_DISTANCE_KM, 0f)
            _routePointCount.value = intent.getIntExtra(RadarService.EXTRA_ROUTE_POINT_COUNT, 0)

            val cameraId = intent.getStringExtra(RadarService.EXTRA_CAMERA_ID)
            _nearestCamera.value = if (cameraId != null) {
                NearestCameraUiState(
                    name = intent.getStringExtra(RadarService.EXTRA_CAMERA_NAME) ?: "",
                    type = intent.getStringExtra(RadarService.EXTRA_CAMERA_TYPE) ?: "",
                    distanceMeters = intent.getFloatExtra(RadarService.EXTRA_CAMERA_DISTANCE, 0f),
                    speedLimit = intent.getIntExtra(RadarService.EXTRA_CAMERA_SPEED_LIMIT, 0)
                )
            } else null
        }
    }

    init {
        val filter = IntentFilter(RadarService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(statusReceiver, filter)
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(statusReceiver)
        super.onCleared()
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
    }

    fun saveKml() {
        val app = getApplication<Application>()
        app.startService(
            Intent(app, RadarService::class.java).also { it.action = RadarService.ACTION_SAVE_KML }
        )
    }
}
