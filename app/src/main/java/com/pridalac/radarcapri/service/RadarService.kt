package com.pridalac.radarcapri.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pridalac.radarcapri.MainActivity
import com.pridalac.radarcapri.R
import com.pridalac.radarcapri.data.RadarRepository
import com.pridalac.radarcapri.data.SpeedCamera
import com.pridalac.radarcapri.preferences.PreferencesManager
import com.pridalac.radarcapri.util.ProximityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class RadarService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: RadarRepository
    private lateinit var prefsManager: PreferencesManager
    private lateinit var notificationManager: NotificationManager

    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

    // In-memory camera list
    @Volatile private var nearbyCameras: List<SpeedCamera> = emptyList()
    @Volatile private var networkOk = true
    @Volatile private var lastQueryTimestamp = 0L
    @Volatile private var lastQueryLocation: Location? = null

    // Alert state
    private val recentlyAnnouncedCameras = mutableMapOf<String, Long>()
    private var lastBeepTime = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            serviceScope.launch {
                processLocation(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = RadarRepository()
        prefsManager = PreferencesManager(this)
        notificationManager = getSystemService(NotificationManager::class.java)

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 80)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed", e)
            null
        }

        initTts()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(0))
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        tts?.stop()
        tts?.shutdown()
        toneGenerator?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Location
    // -------------------------------------------------------------------------

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private suspend fun processLocation(location: Location) {
        maybeRefreshCameras(location)

        val alertDistance = prefsManager.alertDistance.first()
        val minSpeedKmh = prefsManager.minSpeed.first()
        val ttsOn = prefsManager.ttsEnabled.first()
        val beepOn = prefsManager.beepEnabled.first()

        val currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f

        if (currentSpeedKmh < minSpeedKmh) {
            broadcastStatus(currentSpeedKmh, null)
            return
        }

        val nearby = ProximityChecker.closestCamera(location, nearbyCameras, alertDistance)

        broadcastStatus(currentSpeedKmh, nearby?.let { it.camera to it.distanceMeters })

        if (nearby == null) {
            recentlyAnnouncedCameras.entries.removeIf { (_, ts) ->
                System.currentTimeMillis() - ts > ANNOUNCE_COOLDOWN_MS
            }
            return
        }

        val cam = nearby.camera
        val distanceMeters = nearby.distanceMeters.toInt()
        val now = System.currentTimeMillis()

        if (ttsOn) {
            val lastAnnounced = recentlyAnnouncedCameras[cam.id]
            val cooldownExpired = lastAnnounced == null ||
                    (now - lastAnnounced) >= ANNOUNCE_COOLDOWN_MS

            if (cooldownExpired) {
                val text = "Radar ${cam.typeInSpanish} en $distanceMeters metros, " +
                        "límite ${cam.vitesse} kilómetros por hora"
                speakTts(text)
                recentlyAnnouncedCameras[cam.id] = now
            }
        }

        if (beepOn && cam.vitesse > 0 && currentSpeedKmh > cam.vitesse) {
            if (now - lastBeepTime >= BEEP_INTERVAL_MS) {
                playBeep()
                lastBeepTime = now
            }
        }
    }

    private suspend fun maybeRefreshCameras(location: Location) {
        val now = System.currentTimeMillis()
        val lastLoc = lastQueryLocation

        val shouldQuery = if (lastLoc == null) {
            true
        } else {
            val tmp = FloatArray(1)
            Location.distanceBetween(
                lastLoc.latitude, lastLoc.longitude,
                location.latitude, location.longitude,
                tmp
            )
            tmp[0] >= LOCATION_REFRESH_THRESHOLD_M &&
                    (now - lastQueryTimestamp) >= MIN_QUERY_INTERVAL_MS
        }

        if (!shouldQuery) return

        lastQueryLocation = location
        lastQueryTimestamp = now

        val result = repository.fetchNearby(location.latitude, location.longitude)
        if (result.isSuccess) {
            nearbyCameras = result.getOrElse { emptyList() }
            networkOk = true
        } else {
            networkOk = false
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(nearbyCameras.size))
    }

    // -------------------------------------------------------------------------
    // TTS
    // -------------------------------------------------------------------------

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("es", "ES"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "TTS Spanish not available, falling back to default")
                    tts?.setLanguage(Locale.getDefault())
                }
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    private fun speakTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    // -------------------------------------------------------------------------
    // Beep
    // -------------------------------------------------------------------------

    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
    }

    // -------------------------------------------------------------------------
    // Broadcast to UI
    // -------------------------------------------------------------------------

    private fun broadcastStatus(speedKmh: Float, nearest: Pair<SpeedCamera, Float>?) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_SPEED_KMH, speedKmh)
            putExtra(EXTRA_ZONE_COUNT, nearbyCameras.size)
            putExtra(EXTRA_NETWORK_OK, networkOk)
            if (nearest != null) {
                putExtra(EXTRA_CAMERA_ID, nearest.first.id)
                putExtra(EXTRA_CAMERA_NAME, nearest.first.name)
                putExtra(EXTRA_CAMERA_TYPE, nearest.first.typeInSpanish)
                putExtra(EXTRA_CAMERA_DISTANCE, nearest.second)
                putExtra(EXTRA_CAMERA_SPEED_LIMIT, nearest.first.vitesse)
            }
        }
        sendBroadcast(intent)
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(zoneCount: Int): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RadarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("$zoneCount radares en zona")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_stop_action),
                    stopIntent
                ).build()
            )
            .build()
    }

    companion object {
        private const val TAG = "RadarService"
        const val CHANNEL_ID = "radar_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.pridalac.radarcapri.ACTION_STOP"
        const val ACTION_STATUS_UPDATE = "com.pridalac.radarcapri.STATUS_UPDATE"

        const val EXTRA_SPEED_KMH = "speed_kmh"
        const val EXTRA_ZONE_COUNT = "zone_count"
        const val EXTRA_NETWORK_OK = "network_ok"
        const val EXTRA_CAMERA_ID = "camera_id"
        const val EXTRA_CAMERA_NAME = "camera_name"
        const val EXTRA_CAMERA_TYPE = "camera_type"
        const val EXTRA_CAMERA_DISTANCE = "camera_distance"
        const val EXTRA_CAMERA_SPEED_LIMIT = "camera_speed_limit"

        private const val ANNOUNCE_COOLDOWN_MS = 30_000L
        private const val BEEP_INTERVAL_MS = 5_000L
        private const val LOCATION_REFRESH_THRESHOLD_M = 500f
        private const val MIN_QUERY_INTERVAL_MS = 30_000L
    }
}
