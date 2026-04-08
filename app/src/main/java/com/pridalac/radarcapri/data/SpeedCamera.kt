package com.pridalac.radarcapri.data

data class SpeedCamera(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String,
    val commune: String,
    val voie: String,
    val vitesse: Int,
    val typeradar: String,
    val azimut: Float = -1f   // -1 means unknown/not provided
) {
    val typeInSpanish: String get() {
        // Fixed cameras
        if (type in listOf("13", "21", "24", "11", "14")) return "fijo"
        // Mobile cameras
        if (type in listOf("2", "3", "4", "5")) return "móvil"
        // Average-speed (tramo) cameras
        if (type in listOf("7", "8", "9", "37", "38")) return "de tramo"
        // Construction cameras
        if (type == "154") return "de obra"
        // Fallback: check name field
        return when {
            name.contains("mobile", ignoreCase = true) ||
                    name.contains("movil", ignoreCase = true) ||
                    name.contains("móvil", ignoreCase = true) -> "móvil"
            name.contains("tramo", ignoreCase = true) ||
                    name.contains("section", ignoreCase = true) -> "de tramo"
            else -> "fijo"
        }
    }

    val ttsSpeechType: String get() = when (typeInSpanish) {
        "fijo"     -> "Radar fijo"
        "móvil"    -> "Radar móvil"
        "de tramo" -> "Radar de tramo"
        "de obra"  -> "Radar de obra"
        else       -> "Radar"
    }

    /** Returns true if the azimut is known (API provided a value). */
    val hasAzimut: Boolean get() = azimut >= 0f
}
