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
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to hold a recorded GPS point for KML export
data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val speedKmh: Float
)

class RadarService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: RadarRepository
    private lateinit var prefsManager: PreferencesManager
    private lateinit var notificationManager: NotificationManager

    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private var passedToneGenerator: ToneGenerator? = null

    // In-memory camera list
    @Volatile private var nearbyCameras: List<SpeedCamera> = emptyList()
    @Volatile private var networkOk = true
    @Volatile private var lastQueryTimestamp = 0L
    @Volatile private var lastQueryLocation: Location? = null

    // Alert state
    private val recentlyAnnouncedCameras = mutableMapOf<String, Long>()
    private var lastBeepTime = 0L
    private var lastSpeedLocation: Location? = null

    // --- Feature 2: Distance tracking ---
    @Volatile private var totalDistanceMeters = 0f
    private var lastDistanceLocation: Location? = null

    // --- Feature 3: KML route recording ---
    private val routePoints = mutableListOf<RoutePoint>()

    // --- Feature 6: Passed-radar detection ---
    // Maps camera ID -> timestamp when it first entered the alert radius
    private val activeCamerasInRadius = mutableMapOf<String, Long>()
    // Set of camera IDs for which we already played the pass-by sound
    private val passedCameraIds = mutableSetOf<String>()

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

        passedToneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.w(TAG, "passedToneGenerator init failed", e)
            null
        }

        initTts()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SAVE_KML -> {
                serviceScope.launch {
                    saveKmlFile()
                }
                return START_NOT_STICKY
            }
        }

        // Reset distance and route on fresh start
        totalDistanceMeters = 0f
        lastDistanceLocation = null
        routePoints.clear()
        activeCamerasInRadius.clear()
        passedCameraIds.clear()
        recentlyAnnouncedCameras.clear()

        startForeground(NOTIFICATION_ID, buildNotification(0))
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        tts?.stop()
        tts?.shutdown()
        toneGenerator?.release()
        passedToneGenerator?.release()
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
            .setMinUpdateDistanceMeters(5f)
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

        // --- Feature 1: Reliable speed from GPS hardware ---
        val currentSpeedKmh: Float = computeSpeed(location)
        lastSpeedLocation = location

        // --- Feature 2: Accumulate distance ---
        accumulateDistance(location, currentSpeedKmh)

        // --- Feature 3: Record route point ---
        recordRoutePoint(location, currentSpeedKmh)

        if (currentSpeedKmh < minSpeedKmh) {
            broadcastStatus(currentSpeedKmh, null)
            return
        }

        // --- Feature 4: Filter cameras by direction ---
        val vehicleBearing: Float? = if (location.hasBearing()) location.bearing else null

        val filteredCameras = filterCamerasByDirection(nearbyCameras, vehicleBearing)

        val nearby = ProximityChecker.closestCamera(location, filteredCameras, alertDistance)

        // --- Feature 6: Detect passed radars ---
        handlePassedRadars(location, alertDistance)

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

        // Track this camera as currently active in radius (for pass-by detection)
        if (!activeCamerasInRadius.containsKey(cam.id)) {
            activeCamerasInRadius[cam.id] = now
        }

        if (ttsOn) {
            val lastAnnounced = recentlyAnnouncedCameras[cam.id]
            val cooldownExpired = lastAnnounced == null ||
                    (now - lastAnnounced) >= ANNOUNCE_COOLDOWN_MS

            if (cooldownExpired) {
                // Feature 5: Full type name in TTS
                val text = "${cam.ttsSpeechType} en $distanceMeters metros, " +
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

    // -------------------------------------------------------------------------
    // Feature 1: Speed computation
    // -------------------------------------------------------------------------

    private fun computeSpeed(location: Location): Float {
        if (location.hasSpeed()) {
            return location.speed * 3.6f
        }

        val lastLoc = lastSpeedLocation
        if (lastLoc != null) {
            val timeDeltaSec = (location.time - lastLoc.time) / 1000f
            if (timeDeltaSec > 0 && timeDeltaSec < 10f) {
                val distArray = FloatArray(1)
                Location.distanceBetween(
                    lastLoc.latitude, lastLoc.longitude,
                    location.latitude, location.longitude,
                    distArray
                )
                val calculatedSpeedMs = distArray[0] / timeDeltaSec
                if (calculatedSpeedMs > 0.5f && calculatedSpeedMs < 50f) {
                    return calculatedSpeedMs * 3.6f
                }
            }
        }

        return 0f
    }

    // -------------------------------------------------------------------------
    // Feature 2: Distance accumulation
    // -------------------------------------------------------------------------

    private fun accumulateDistance(location: Location, currentSpeedKmh: Float) {
        val speedMs = currentSpeedKmh / 3.6f
        val last = lastDistanceLocation

        if (last != null && speedMs > 1.5f) {
            val dist = FloatArray(1)
            Location.distanceBetween(
                last.latitude, last.longitude,
                location.latitude, location.longitude,
                dist
            )
            totalDistanceMeters += dist[0]
        }

        lastDistanceLocation = location
    }

    // -------------------------------------------------------------------------
    // Feature 3: Route point recording
    // -------------------------------------------------------------------------

    private fun recordRoutePoint(location: Location, speedKmh: Float) {
        routePoints.add(
            RoutePoint(
                lat = location.latitude,
                lng = location.longitude,
                timestamp = System.currentTimeMillis(),
                speedKmh = speedKmh
            )
        )
    }

    private fun saveKmlFile() {
        val points = routePoints.toList()
        if (points.isEmpty()) {
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@RadarService, "No hay puntos de ruta grabados", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val fileName = "RadarCapri_$dateStr.kml"

        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null) {
            Log.e(TAG, "External documents directory not available")
            return
        }
        dir.mkdirs()
        val file = File(dir, fileName)

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("  <Document>")
        sb.appendLine("    <name>RadarCapri_$dateStr</name>")
        sb.appendLine("    <Placemark>")
        sb.appendLine("      <name>Ruta</name>")
        sb.appendLine("      <LineString>")
        sb.appendLine("        <coordinates>")
        for (p in points) {
            sb.appendLine("          ${p.lng},${p.lat},0")
        }
        sb.appendLine("        </coordinates>")
        sb.appendLine("      </LineString>")
        sb.appendLine("    </Placemark>")
        sb.appendLine("  </Document>")
        sb.appendLine("</kml>")

        try {
            file.writeText(sb.toString())
            val path = file.absolutePath
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@RadarService, "KML guardado: $path", Toast.LENGTH_LONG).show()
            }
            Log.i(TAG, "KML saved to $path")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving KML", e)
            serviceScope.launch(Dispatchers.Main) {
                Toast.makeText(this@RadarService, "Error al guardar KML: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Feature 4: Direction filtering
    // -------------------------------------------------------------------------

    private fun filterCamerasByDirection(
        cameras: List<SpeedCamera>,
        vehicleBearing: Float?
    ): List<SpeedCamera> {
        // If we don't know the bearing (low speed), show all radars
        if (vehicleBearing == null) return cameras
        return cameras.filter { cam ->
            // If radar has no azimut, always show it
            if (!cam.hasAzimut) return@filter true
            // Keep radar only if difference <= 90 degrees
            angleDiff(vehicleBearing, cam.azimut) <= 90f
        }
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var diff = Math.abs(a - b) % 360f
        if (diff > 180f) diff = 360f - diff
        return diff
    }

    // -------------------------------------------------------------------------
    // Feature 6: Passed-radar sound
    // -------------------------------------------------------------------------

    private fun handlePassedRadars(location: Location, alertDistance: Float) {
        val now = System.currentTimeMillis()
        val PASS_THRESHOLD_M = 350f
        val ACTIVE_WINDOW_MS = 30_000L

        // Find cameras that were recently active (in radius) but are now beyond PASS_THRESHOLD_M
        val iterator = activeCamerasInRadius.entries.iterator()
        while (iterator.hasNext()) {
            val (camId, enteredAt) = iterator.next()
            val cam = nearbyCameras.find { it.id == camId } ?: run {
                // Camera no longer in list (route change) — remove without sound
                iterator.remove()
                passedCameraIds.remove(camId)
                continue
            }

            val dist = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                cam.lat, cam.lng,
                dist
            )

            if (dist[0] > PASS_THRESHOLD_M) {
                // Camera is now beyond threshold
                val wasRecentlyActive = (now - enteredAt) <= ACTIVE_WINDOW_MS
                if (wasRecentlyActive && !passedCameraIds.contains(camId)) {
                    playPassedRadarSound()
                    passedCameraIds.add(camId)
                }
                iterator.remove()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Refresh cameras
    // -------------------------------------------------------------------------

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
    // Beep / Tones
    // -------------------------------------------------------------------------

    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
    }

    private fun playPassedRadarSound() {
        passedToneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_REORDER, 600)
    }

    // -------------------------------------------------------------------------
    // Broadcast to UI
    // -------------------------------------------------------------------------

    private fun broadcastStatus(speedKmh: Float, nearest: Pair<SpeedCamera, Float>?) {
        ServiceStateHolder.updateCamera(
            speedKmh = speedKmh,
            zoneCount = nearbyCameras.size,
            networkOk = networkOk,
            distanceKm = totalDistanceMeters / 1000f,
            routePointCount = routePoints.size,
            camera = nearest?.first,
            distance = nearest?.second ?: 0f
        )
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
        const val ACTION_SAVE_KML = "com.pridalac.radarcapri.ACTION_SAVE_KML"

        private const val ANNOUNCE_COOLDOWN_MS = 30_000L
        private const val BEEP_INTERVAL_MS = 5_000L
        private const val LOCATION_REFRESH_THRESHOLD_M = 500f
        private const val MIN_QUERY_INTERVAL_MS = 30_000L
    }
}
