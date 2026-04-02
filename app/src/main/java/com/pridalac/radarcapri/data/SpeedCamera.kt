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
    val typeradar: String
) {
    val typeInSpanish: String get() = when {
        type == "13" || type == "21" || type == "24" -> "fijo"
        type == "154" -> "de obra"
        name.contains("mobile", ignoreCase = true) ||
                name.contains("movil", ignoreCase = true) ||
                name.contains("móvil", ignoreCase = true) -> "móvil"
        else -> "fijo"
    }
}
