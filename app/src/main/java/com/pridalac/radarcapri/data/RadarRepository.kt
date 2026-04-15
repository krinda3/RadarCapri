package com.pridalac.radarcapri.data

import android.util.Log
import com.pridalac.radarcapri.network.LufopApiService

class RadarRepository(
    private val api: LufopApiService = LufopApiService.create()
) {
    suspend fun fetchNearby(lat: Double, lng: Double): Result<List<SpeedCamera>> {
        return try {
            val remoteList = api.getCamerasByLocation(coordinates = "$lat,$lng")
            val cameras = remoteList.mapNotNull { cam ->
                val id = cam.id ?: return@mapNotNull null
                val camLat = cam.lat ?: return@mapNotNull null
                val camLng = cam.lng ?: return@mapNotNull null
                SpeedCamera(
                    id = id,
                    name = cam.name ?: "Radar",
                    lat = camLat,
                    lng = camLng,
                    type = cam.type ?: "",
                    commune = cam.commune ?: "",
                    voie = cam.voie ?: "",
                    vitesse = cam.vitesse?.toIntOrNull() ?: 0,
                    typeradar = cam.typeradar ?: "",
                    azimut = cam.azimut ?: -1f
                )
            }
            Result.success(cameras)
        } catch (e: Exception) {
            Log.e("RadarRepository", "Fetch failed", e)
            Result.failure(e)
        }
    }
}
