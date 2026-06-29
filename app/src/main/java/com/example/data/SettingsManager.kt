package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scooter_settings")

class SettingsManager(private val context: Context) {
    private val TACHO_BRIGHTNESS_KEY = floatPreferencesKey("tacho_brightness")
    private val LED_MODE_KEY = stringPreferencesKey("led_mode")
    private val LAST_SCOOTER_ADDRESS_KEY = stringPreferencesKey("last_scooter_address")

    val tachoBrightness: Flow<Float> = context.dataStore.data.map { it[TACHO_BRIGHTNESS_KEY] ?: 1.0f }
    val ledMode: Flow<String> = context.dataStore.data.map { it[LED_MODE_KEY] ?: "Solid" }
    val lastConnectedAddress: Flow<String?> = context.dataStore.data.map { it[LAST_SCOOTER_ADDRESS_KEY] }

    suspend fun saveSettings(brightness: Float, mode: String) {
        context.dataStore.edit {
            it[TACHO_BRIGHTNESS_KEY] = brightness
            it[LED_MODE_KEY] = mode
        }
    }

    suspend fun saveLastConnectedAddress(address: String) {
        context.dataStore.edit {
            it[LAST_SCOOTER_ADDRESS_KEY] = address
        }
    }
}
