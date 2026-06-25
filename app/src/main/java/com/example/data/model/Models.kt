package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class ScooterTelemetry(
    val speedKmh: Float = 0f,
    val totalMileageKm: Float = 0f,
    val tripMileageKm: Float = 0f,
    val batteryPercent: Int = 0,
    val voltage: Float = 0f,
    val currentAmps: Float = 0f,
    val tempC: Float = 0f,
    val powerWatts: Float = 0f,
    val errorCodes: List<String> = emptyList(),
    val currentGear: Int = 1, // 1: Eco, 2: Sport, 3: Turbo
    val dualMotorEnabled: Boolean = false,
    val speedLimitUnlocked: Boolean = false,
    val lightStatus: Boolean = false,
    val lockStatus: Boolean = false,
    val turboModeEnabled: Boolean = false,
    val lowBatteryLimitBypassed: Boolean = false,
    val customMaxSpeed: Float = 55f
)

@Entity(tableName = "rides")
data class RideLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val durationSeconds: Long,
    val maxSpeedKmh: Double
)
