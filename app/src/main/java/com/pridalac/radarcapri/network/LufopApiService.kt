package com.pridalac.radarcapri.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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
        private const val TAG = "LufopApi"
        private const val BASE_URL = "https://api.lufop.net/"
        const val API_KEY = "469a28a4863c8291e7d3b3ca5226c75e"

        fun create(): LufopApiService {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LufopApiService::class.java)
        }
    }
}
