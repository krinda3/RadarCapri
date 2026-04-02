package com.pridalac.radarcapri.network

import com.google.gson.annotations.SerializedName

data class LufopCamera(
    @SerializedName("ID") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lng") val lng: Double?,
    @SerializedName("type") val type: String?,
    @SerializedName("commune") val commune: String?,
    @SerializedName("voie") val voie: String?,
    @SerializedName("vitesse") val vitesse: String?,
    @SerializedName("typeradar") val typeradar: String?
)

typealias LufopResponse = List<LufopCamera>
