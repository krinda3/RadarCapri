package com.pridalac.radarcapri.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "radar_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_ALERT_DISTANCE = floatPreferencesKey("alert_distance_meters")
        val KEY_MIN_SPEED = floatPreferencesKey("min_speed_kmh")
        val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val KEY_BEEP_ENABLED = booleanPreferencesKey("beep_enabled")
        val KEY_AUTO_START = booleanPreferencesKey("auto_start")

        const val DEFAULT_ALERT_DISTANCE = 300f
        const val DEFAULT_MIN_SPEED = 5f
        const val DEFAULT_TTS_ENABLED = true
        const val DEFAULT_BEEP_ENABLED = true
        const val DEFAULT_AUTO_START = true
    }

    val alertDistance: Flow<Float> = context.dataStore.data
        .map { it[KEY_ALERT_DISTANCE] ?: DEFAULT_ALERT_DISTANCE }

    val minSpeed: Flow<Float> = context.dataStore.data
        .map { it[KEY_MIN_SPEED] ?: DEFAULT_MIN_SPEED }

    val ttsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_TTS_ENABLED] ?: DEFAULT_TTS_ENABLED }

    val beepEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_BEEP_ENABLED] ?: DEFAULT_BEEP_ENABLED }

    val autoStart: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_START] ?: DEFAULT_AUTO_START }

    suspend fun setAutoStart(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START] = value }
    }

    suspend fun setAlertDistance(value: Float) {
        context.dataStore.edit { it[KEY_ALERT_DISTANCE] = value }
    }

    suspend fun setMinSpeed(value: Float) {
        context.dataStore.edit { it[KEY_MIN_SPEED] = value }
    }

    suspend fun setTtsEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_TTS_ENABLED] = value }
    }

    suspend fun setBeepEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_BEEP_ENABLED] = value }
    }
}
