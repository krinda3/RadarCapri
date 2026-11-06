package com.pridalac.radarcapri.data

/**
 * Radar de la base de datos local (radares.json).
 * [type]: "fijo", "tramo", "semaforo" o "movil".
 * [vitesse]: límite en km/h (0 = desconocido).
 * [azimut]: sentido al que apunta, en grados (-1 = desconocido).
 */
data class SpeedCamera(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String,
    val commune: String,
    val vitesse: Int,
    val azimut: Float = -1f
) {
    val typeInSpanish: String get() = when (type) {
        "tramo" -> "de tramo"
        "semaforo" -> "de semáforo"
        "movil" -> "móvil"
        else -> "fijo"
    }

    val ttsSpeechType: String get() = "Radar $typeInSpanish"

    /** True si se conoce el sentido del radar. */
    val hasAzimut: Boolean get() = azimut >= 0f
}
