package com.pridalac.radarcapri.util

import android.location.Location
import com.pridalac.radarcapri.data.SpeedCamera

data class NearbyCamera(
    val camera: SpeedCamera,
    val distanceMeters: Float
)

object ProximityChecker {

    private const val LAT_DEGREE_METERS = 111_000.0

    fun boundingBoxDelta(radiusMeters: Float): Double {
        return radiusMeters / LAT_DEGREE_METERS
    }

    fun lonDeltaForRadius(lat: Double, radiusMeters: Float): Double {
        val cosLat = Math.cos(Math.toRadians(lat)).coerceAtLeast(0.001)
        return radiusMeters / (LAT_DEGREE_METERS * cosLat)
    }

    fun filterByRadius(
        location: Location,
        candidates: List<SpeedCamera>,
        radiusMeters: Float
    ): List<NearbyCamera> {
        val results = mutableListOf<NearbyCamera>()
        val tmp = FloatArray(1)
        for (cam in candidates) {
            Location.distanceBetween(
                location.latitude, location.longitude,
                cam.lat, cam.lng,
                tmp
            )
            if (tmp[0] <= radiusMeters) {
                results.add(NearbyCamera(cam, tmp[0]))
            }
        }
        results.sortBy { it.distanceMeters }
        return results
    }

    fun closestCamera(
        location: Location,
        candidates: List<SpeedCamera>,
        maxRadiusMeters: Float
    ): NearbyCamera? = filterByRadius(location, candidates, maxRadiusMeters).firstOrNull()
}
