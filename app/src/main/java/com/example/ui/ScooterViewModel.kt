package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.RideRepository
import com.example.data.model.RideLog
import com.example.data.model.ScooterTelemetry
import com.example.service.BleDevice
import com.example.service.ConnectionState
import com.example.service.ScooterBleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelemetryDataPoint(
    val timestamp: Long,
    val speed: Float,
    val amps: Float,
    val voltage: Float
)

class ScooterViewModel(
    application: Application,
    private val bleManager: ScooterBleManager,
    private val rideRepository: RideRepository
) : AndroidViewModel(application) {

    // Proxy StateFlows from BLE Manager
    val isScanning = bleManager.isScanning
    val scannedDevices = bleManager.scannedDevices
    val connectionState = bleManager.connectionState
    val telemetry = bleManager.telemetry
    val isSimulationMode = bleManager.isSimulationMode

    // Saved Rides from Database
    val savedRides: StateFlow<List<RideLog>> = rideRepository.allRides
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Realtime Chart Data (Limit to last 40 seconds)
    private val _chartPoints = MutableStateFlow<List<TelemetryDataPoint>>(emptyList())
    val chartPoints: StateFlow<List<TelemetryDataPoint>> = _chartPoints.asStateFlow()

    // Active Ride Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordStartTime = MutableStateFlow<Long>(0)
    private val _rideSeconds = MutableStateFlow<Long>(0)
    val rideSeconds: StateFlow<Long> = _rideSeconds.asStateFlow()

    private val _rideDistance = MutableStateFlow<Double>(0.0)
    val rideDistance: StateFlow<Double> = _rideDistance.asStateFlow()

    private val _rideMaxSpeed = MutableStateFlow<Double>(0.0)
    val rideMaxSpeed: StateFlow<Double> = _rideMaxSpeed.asStateFlow()

    private var rideRecordingJob: Job? = null
    private var chartRecordingJob: Job? = null
    private val speedPointsList = mutableListOf<Double>()

    init {
        // Start collection of chart data points
        startChartDataRecording()
    }

    private fun startChartDataRecording() {
        chartRecordingJob = viewModelScope.launch {
            while (true) {
                if (connectionState.value == ConnectionState.CONNECTED) {
                    val currentTelemetry = telemetry.value
                    val point = TelemetryDataPoint(
                        timestamp = System.currentTimeMillis(),
                        speed = currentTelemetry.speedKmh,
                        amps = currentTelemetry.currentAmps,
                        voltage = currentTelemetry.voltage
                    )
                    _chartPoints.update { currentList ->
                        val updated = currentList + point
                        if (updated.size > 40) updated.drop(1) else updated
                    }
                } else {
                    // Smooth clear or keep chart static when disconnected
                    if (_chartPoints.value.isNotEmpty() && connectionState.value == ConnectionState.DISCONNECTED) {
                        _chartPoints.value = emptyList()
                    }
                }
                delay(1000) // Append every 1 second
            }
        }
    }

    // --- Scooter BLE Controls ---
    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(address: String) = bleManager.connect(address)
    fun disconnect() = bleManager.disconnect()
    
    fun setGear(gear: Int) = bleManager.setGear(gear)
    fun toggleDualMotor(enabled: Boolean) = bleManager.toggleDualMotor(enabled)
    fun toggleSpeedLimit(unlocked: Boolean) = bleManager.toggleSpeedLimit(unlocked)
    fun toggleLight(enabled: Boolean) = bleManager.toggleLight(enabled)
    fun toggleLock(enabled: Boolean) = bleManager.toggleLock(enabled)
    fun setSimulationMode(enabled: Boolean) = bleManager.setSimulationMode(enabled)
    
    fun toggleTurboMode(enabled: Boolean) = bleManager.toggleTurboMode(enabled)
    fun toggleLowBatteryLimit(bypass: Boolean) = bleManager.toggleLowBatteryLimit(bypass)
    fun setCustomMaxSpeed(speed: Float) = bleManager.setCustomMaxSpeed(speed)
    fun toggleCruiseControl(enabled: Boolean) = bleManager.toggleCruiseControl(enabled)
    fun toggleZeroStart(enabled: Boolean) = bleManager.toggleZeroStart(enabled)
    fun toggleKers(enabled: Boolean) = bleManager.toggleKers(enabled)
    fun toggleTempProtectionBypass(bypass: Boolean) = bleManager.toggleTempProtectionBypass(bypass)
    fun toggleAntiTheft(enabled: Boolean) = bleManager.toggleAntiTheft(enabled)
    fun toggleSoftStart(enabled: Boolean) = bleManager.toggleSoftStart(enabled)
    fun toggleReverseGear(enabled: Boolean) = bleManager.toggleReverseGear(enabled)
    fun toggleSoundSimulation(enabled: Boolean) = bleManager.toggleSoundSimulation(enabled)

    fun applyDriveModePreset(presetName: String) {
        when (presetName) {
            "ECO" -> {
                bleManager.setCustomMaxSpeed(20f)
                bleManager.toggleSoftStart(true)
                bleManager.toggleDualMotor(false)
                bleManager.toggleTurboMode(false)
                bleManager.toggleZeroStart(false)
                bleManager.toggleKers(true)
                bleManager.toggleSpeedLimit(false)
                bleManager.setGear(1)
            }
            "DE_STVZO" -> {
                bleManager.setCustomMaxSpeed(22f)
                bleManager.toggleSoftStart(true)
                bleManager.toggleDualMotor(false)
                bleManager.toggleTurboMode(false)
                bleManager.toggleZeroStart(false)
                bleManager.toggleKers(true)
                bleManager.toggleSpeedLimit(false)
                bleManager.setGear(2)
            }
            "SPORT" -> {
                bleManager.setCustomMaxSpeed(38f)
                bleManager.toggleSoftStart(false)
                bleManager.toggleDualMotor(true)
                bleManager.toggleTurboMode(false)
                bleManager.toggleZeroStart(true)
                bleManager.toggleKers(true)
                bleManager.toggleSpeedLimit(true)
                bleManager.setGear(3)
            }
            "RACE" -> {
                bleManager.setCustomMaxSpeed(55f)
                bleManager.toggleSoftStart(false)
                bleManager.toggleDualMotor(true)
                bleManager.toggleTurboMode(true)
                bleManager.toggleZeroStart(true)
                bleManager.toggleKers(false)
                bleManager.toggleSpeedLimit(true)
                bleManager.setGear(3)
            }
        }
    }
    
    fun flashFirmwareAdjustment(
        customSpeed: Float,
        turbo: Boolean,
        bypassBatteryLimit: Boolean,
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) = bleManager.flashFirmwareAdjustment(customSpeed, turbo, bypassBatteryLimit, onProgress, onComplete)
    
    fun simulateThrottle(pressedRatio: Float) = bleManager.simulateThrottle(pressedRatio)
    fun simulateBrake(pressed: Boolean) = bleManager.simulateBrake(pressed)
    fun triggerSimulatedError(errorCode: String?) = bleManager.triggerSimulatedError(errorCode)

    // --- Active Ride Persistence ---
    fun toggleRideRecording() {
        if (_isRecording.value) {
            stopAndSaveRide()
        } else {
            startRideRecording()
        }
    }

    private fun startRideRecording() {
        _isRecording.value = true
        _recordStartTime.value = System.currentTimeMillis()
        _rideSeconds.value = 0
        _rideDistance.value = 0.0
        _rideMaxSpeed.value = 0.0
        speedPointsList.clear()

        // Capture initial trip mileage to offset
        val initialTrip = telemetry.value.tripMileageKm

        rideRecordingJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(1000)
                _rideSeconds.update { it + 1 }
                
                val currentTelemetry = telemetry.value
                val currentSpeed = currentTelemetry.speedKmh.toDouble()
                
                speedPointsList.add(currentSpeed)
                if (currentSpeed > _rideMaxSpeed.value) {
                    _rideMaxSpeed.value = currentSpeed
                }

                // Distance gained inside recording
                val distanceGained = currentTelemetry.tripMileageKm.toDouble() - initialTrip
                _rideDistance.value = if (distanceGained > 0.0) distanceGained else 0.0
            }
        }
    }

    private fun stopAndSaveRide() {
        _isRecording.value = false
        rideRecordingJob?.cancel()
        rideRecordingJob = null

        val distance = _rideDistance.value
        val avgSpeed = if (speedPointsList.isNotEmpty()) speedPointsList.average() else 0.0
        val duration = _rideSeconds.value
        val maxSpeed = _rideMaxSpeed.value

        // Only save ride logs with meaningful distance (> 0.01 km) or duration (> 2 sec)
        if (distance > 0.005 || duration > 3) {
            viewModelScope.launch {
                rideRepository.insertRide(
                    RideLog(
                        dateTimestamp = System.currentTimeMillis(),
                        distanceKm = String.format("%.3f", distance).replace(",", ".").toDouble(),
                        avgSpeedKmh = String.format("%.1f", avgSpeed).replace(",", ".").toDouble(),
                        durationSeconds = duration,
                        maxSpeedKmh = String.format("%.1f", maxSpeed).replace(",", ".").toDouble()
                    )
                )
            }
        }
    }

    fun deleteRide(id: Int) {
        viewModelScope.launch {
            rideRepository.deleteRide(id)
        }
    }

    fun clearAllRides() {
        viewModelScope.launch {
            rideRepository.clearAllRides()
        }
    }

    override fun onCleared() {
        super.onCleared()
        chartRecordingJob?.cancel()
        rideRecordingJob?.cancel()
        bleManager.disconnect()
    }

    // Custom Factory inside ScooterViewModel
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScooterViewModel::class.java)) {
                val db = AppDatabase.getDatabase(application)
                val repository = RideRepository(db.rideDao())
                val bleManager = ScooterBleManager(application)
                return ScooterViewModel(application, bleManager, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
