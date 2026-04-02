package com.pridalac.radarcapri.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface LufopApiService {

    @GET("api")
    suspend fun getCamerasByLocation(
        @Query("key") apiKey: String = API_KEY,
        @Query("pays") country: String = "es",
        @Query("format") format: String = "json",
        @Query("q") coordinates: String,
        @Query("m") margin: Int = 50
    ): LufopResponse

    companion object {
        private const val BASE_URL = "https://api.lufop.net/"
        const val API_KEY = "469a28a4863c8291e7d3b3ca5226c75e"

        fun create(): LufopApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LufopApiService::class.java)
        }
    }
}
