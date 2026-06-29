package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.model.ScooterTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val device: BluetoothDevice? = null,
    val isScooter: Boolean = false
)

@SuppressLint("MissingPermission")
class ScooterBleManager(private val context: Context) {

    private val TAG = "ScooterBleManager"

    // BLE Service and Characteristic UUIDs (Standard Serial and Nordic UART)
    private val SERVICE_UUID_UART = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val CHAR_UUID_RX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write
    private val CHAR_UUID_TX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify

    private val SERVICE_UUID_COMMON = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID_COMMON = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb") // Write & Notify

    private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Bluetooth APIs
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    // State Flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(ScooterTelemetry())
    val telemetry: StateFlow<ScooterTelemetry> = _telemetry.asStateFlow()

    private val _rawBleTraffic = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 50)
    val rawBleTraffic: kotlinx.coroutines.flow.SharedFlow<String> = _rawBleTraffic.asSharedFlow()

    private val _isSimulationMode = MutableStateFlow(false) // ALWAYS FALSE, simulation disabled
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val bluetoothStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
            }
        }
    }

    // Simulation & Management Coroutines
    private val scope = CoroutineScope(Dispatchers.Default)
    private var simulationJob: Job? = null
    private var soundSynthJob: Job? = null
    private var scanTimeoutHandler = Handler(Looper.getMainLooper())

    // Simulation Variables (State)
    private var targetSpeed = 0f
    private var currentThrottle = 0f
    private var currentBrake = false
    private var simulatedOdometer = 124.5f // Start with some kilometers
    private var simulatedTrip = 0.0f
    private var batteryCapacityAh = 15.0f // 15Ah battery
    private var batteryRemainingAh = 12.8f // ~85% battery
    private var isSimulatingRide = false

    init {
        try {
            val filter = android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.applicationContext.registerReceiver(bluetoothStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register bluetoothStateReceiver: ${e.message}")
        }
        startSoundSynth()
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = false
    }

    // --- BLE Scan Operations ---
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "Bluetooth not available.")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Filter for common names or service UUIDs, but also show all devices for flexibility
        val filters = listOf(
            ScanFilter.Builder().setDeviceName("KuKirin G2").build(),
            ScanFilter.Builder().setDeviceName("Kirin").build(),
            ScanFilter.Builder().setDeviceName("G2-Pro").build(),
            ScanFilter.Builder().setDeviceName("KuKirin").build()
        )

        try {
            scanner.startScan(null, settings, scanCallback)
            // Stop scanning after 10 seconds automatically
            scanTimeoutHandler.postDelayed({
                stopScan()
            }, 10000)
        } catch (e: SecurityException) {
            Log.e(TAG, "No BLE Scan Permission: ${e.message}")
            _isScanning.value = false
        }
    }

    fun stopScan() {
        _isScanning.value = false
        scanTimeoutHandler.removeCallbacksAndMessages(null)
        if (_isSimulationMode.value) return

        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during stopScan: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name
            val address = device.address
            val rssi = result.rssi
            val isScooter = name?.contains("Kirin", ignoreCase = true) == true || 
                            name?.contains("Kirin", ignoreCase = true) == true ||
                            name?.contains("G2", ignoreCase = true) == true

            _scannedDevices.update { currentList ->
                if (currentList.none { it.address == address }) {
                    currentList + BleDevice(address, name, rssi, device, isScooter)
                } else {
                    currentList.map {
                        if (it.address == address) it.copy(rssi = rssi, name = name ?: it.name, isScooter = isScooter) else it
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    // --- BLE Connection Operations ---
    fun connect(address: String) {
        if (_isSimulationMode.value) {
            connectToSimulatedDevice(address)
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) return

        _connectionState.value = ConnectionState.CONNECTING
        val device = adapter.getRemoteDevice(address)

        try {
            // Connect with autoConnect = false for immediate connection
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during connectGatt: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        if (_isSimulationMode.value) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        try {
            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnect: ${e.message}")
        } finally {
            bluetoothGatt = null
            rxCharacteristic = null
            txCharacteristic = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private val _discoveredServices = MutableStateFlow<List<String>>(emptyList())
    val discoveredServices: StateFlow<List<String>> = _discoveredServices.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT Connection error status: $status")
                disconnect()
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                _connectionState.value = ConnectionState.CONNECTED
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException during discoverServices: ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status: $status")
                return
            }

            val services = gatt.services
            val serviceList = services.map { service ->
                "Service: ${service.uuid}\n" + 
                service.characteristics.joinToString("\n") { "  Char: ${it.uuid} (Props: ${it.properties})" }
            }
            _discoveredServices.value = serviceList

            // Find either Nordic UART or Common Serial service
            val uartService = gatt.getService(SERVICE_UUID_UART)
            if (uartService != null) {
                rxCharacteristic = uartService.getCharacteristic(CHAR_UUID_RX)
                txCharacteristic = uartService.getCharacteristic(CHAR_UUID_TX)
            } else {
                val commonService = gatt.getService(SERVICE_UUID_COMMON)
                if (commonService != null) {
                    val commonChar = commonService.getCharacteristic(CHAR_UUID_COMMON)
                    rxCharacteristic = commonChar
                    txCharacteristic = commonChar
                }
            }

            // Enable notification on TX/Common Characteristic
            txCharacteristic?.let { characteristic ->
                try {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.d(TAG, "Notifications enabled for characteristic: ${characteristic.uuid}")
                    
                    // Request current state query packet
                    queryScooterStatus()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException when enabling notifications: ${e.message}")
                }
            } ?: Log.e(TAG, "No matching TX / Notify characteristic found.")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            scope.launch { _rawBleTraffic.emit("IN: ${data.joinToString(" ") { String.format("%02X", it) }}") }
            parseScooterData(data)
        }
    }

    // --- Scooter BLE Data Write / Query Protocol ---
    private fun queryScooterStatus() {
        // Query command packet for KuKirin G2: common protocol uses prefix 0xAA 0x55 or 0x5A 0xA5
        // Let's write a standard status request: AA 55 01 02 03 00 FC (Header, Length, Command, CRC)
        val queryPacket = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x00.toByte(), 0xFC.toByte())
        writeCharacteristic(queryPacket)
    }

    fun sendCustomCommand(command: ByteArray) {
        writeCharacteristic(command)
    }

    private fun writeCharacteristic(data: ByteArray) {
        val characteristic = rxCharacteristic
        val gatt = bluetoothGatt
        if (gatt == null || characteristic == null) return

        scope.launch { _rawBleTraffic.emit("OUT: ${data.joinToString(" ") { String.format("%02X", it) }}") }

        try {
            characteristic.value = data
            gatt.writeCharacteristic(characteristic)
            Log.d(TAG, "Wrote characteristic: ${data.joinToString { String.format("%02X", it) }}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException writing characteristic: ${e.message}")
        }
    }

    // --- BLE Parser for KuKirin G2 Telemetry ---
    private fun parseScooterData(data: ByteArray) {
        if (data.size < 5) return

        // Verify Header: e.g., 0xAA 0x55 or 0x55 0xAA
        if (data[0] != 0xAA.toByte() || data[1] != 0x55.toByte()) {
            return
        }

        // Example Protocol Decoding for KuKirin G2 (Simulated real byte parser):
        // data[2] = payload length
        // data[3] = speed MSB
        // data[4] = speed LSB (Speed in 0.1 km/h)
        // data[5] = battery level % (0-100)
        // data[6,7] = voltage in 0.1V (e.g. 480 = 48.0V)
        // data[8,9] = current in 0.1A (e.g. 150 = 15.0A)
        // data[10] = temperature (Signed byte, offset or actual)
        // data[11,12,13,14] = Total mileage in 0.1km
        // data[15] = error code bits
        // data[16] = speed mode & flags: Bit 0-1: gear (1-3), Bit 2: dual motor, Bit 3: lock, Bit 4: speed unlock
        
        try {
            val length = data[2].toInt() and 0xFF
            if (data.size < length + 4) return // Verify packet length

            val speedRaw = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val speed = speedRaw / 10.0f

            val battery = data[5].toInt() and 0xFF

            val voltRaw = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            val voltage = voltRaw / 10.0f

            val currRaw = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
            // Handle negative current if appropriate
            val current = currRaw / 10.0f

            val temp = data[10].toInt().toFloat()

            val odoRaw = ((data[11].toLong() and 0xFF) shl 24) or
                         ((data[12].toLong() and 0xFF) shl 16) or
                         ((data[13].toLong() and 0xFF) shl 8) or
                         (data[14].toLong() and 0xFF)
            val odometer = odoRaw / 10.0f

            val errorByte = data[15].toInt() and 0xFF
            val errors = mutableListOf<String>()
            if (errorByte != 0) {
                if ((errorByte and 0x01) != 0) errors.add("E-01: Hall-Sensor Fehler")
                if ((errorByte and 0x02) != 0) errors.add("E-02: Gasgriff Fehler")
                if ((errorByte and 0x04) != 0) errors.add("E-03: Bremssensor Fehler")
                if ((errorByte and 0x08) != 0) errors.add("E-04: Unterspannungsschutz")
                if ((errorByte and 0x10) != 0) errors.add("E-05: Überspannungsschutz")
                if ((errorByte and 0x20) != 0) errors.add("E-06: Kommunikationsfehler")
                if ((errorByte and 0x40) != 0) errors.add("E-07: Motorphasen-Fehler")
                if ((errorByte and 0x80) != 0) errors.add("E-08: Übertemperatur")
            }

            val flags = data[16].toInt() and 0xFF
            val gear = (flags and 0x03) + 1
            val dual = (flags and 0x04) != 0
            val lock = (flags and 0x08) != 0
            val speedUnlock = (flags and 0x10) != 0
            val light = (flags and 0x20) != 0
            val turbo = (flags and 0x40) != 0
            val batteryBypass = (flags and 0x80) != 0

            _telemetry.update {
                it.copy(
                    speedKmh = speed,
                    batteryPercent = battery,
                    voltage = voltage,
                    currentAmps = current,
                    tempC = temp,
                    powerWatts = voltage * current,
                    totalMileageKm = odometer,
                    errorCodes = errors,
                    currentGear = gear,
                    dualMotorEnabled = dual,
                    speedLimitUnlocked = speedUnlock,
                    lockStatus = lock,
                    lightStatus = light,
                    turboModeEnabled = turbo,
                    lowBatteryLimitBypassed = batteryBypass
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing characteristic data: ${e.message}")
        }
    }

    // --- Scooter Command API ---
    fun setGear(gear: Int) { // 1, 2, or 3
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(currentGear = gear) }
            return
        }

        // Custom command for writing gear change: AA 55 02 01 01 [gear-1] [checksum]
        val payload = gear - 1
        val checksum = (0x01 + 0x01 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x01.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
    }

    fun toggleDualMotor(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(dualMotorEnabled = enabled) }
            return
        }

        // Custom command for toggling Dual Motor (Tuning): AA 55 02 01 02 [0 or 1] [checksum]
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x02 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x02.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
    }

    fun toggleSpeedLimit(unlocked: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(speedLimitUnlocked = unlocked) }
            return
        }

        // Speed limit unlock command (Tuning: bypass 20/25 km/h restriction up to 65 km/h)
        // Protocol packet: AA 55 02 01 03 [0 or 1] [checksum]
        val payload = if (unlocked) 1 else 0
        val checksum = (0x01 + 0x03 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x03.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
    }

    fun toggleLight(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(lightStatus = enabled) }
            return
        }

        // Light control packet: AA 55 02 01 04 [0 or 1] [checksum]
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x04 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x04.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
    }

    fun toggleLock(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(lockStatus = enabled) }
            return
        }

        // Lock/Unlock scooter command packet: AA 55 02 01 05 [0 or 1] [checksum]
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x05 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x05.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
    }

    fun toggleTurboMode(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(turboModeEnabled = enabled) }
            return
        }

        // Send G2 Turbo Mode command (Protocol packet: AA 55 02 01 06 [0 or 1] [checksum])
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x06 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(turboModeEnabled = enabled) }
    }

    fun toggleLowBatteryLimit(bypass: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(lowBatteryLimitBypassed = bypass) }
            return
        }

        // Send G2 low battery limit bypass command (Protocol packet: AA 55 02 01 07 [0 or 1] [checksum])
        val payload = if (bypass) 1 else 0
        val checksum = (0x01 + 0x07 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x07.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(lowBatteryLimitBypassed = bypass) }
    }

    fun setCustomMaxSpeed(speed: Float) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(customMaxSpeed = speed) }
            return
        }

        // Send G2 Custom Speed Limit command (Protocol packet: AA 55 02 01 08 [speed value 0-255] [checksum])
        val payload = speed.toInt() and 0xFF
        val checksum = (0x01 + 0x08 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x08.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(customMaxSpeed = speed) }
    }

    fun toggleCruiseControl(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(cruiseControlEnabled = enabled) }
            return
        }

        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x0A + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0A.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(cruiseControlEnabled = enabled) }
    }

    fun toggleZeroStart(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(zeroStartEnabled = enabled) }
            return
        }

        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x0B + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0B.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(zeroStartEnabled = enabled) }
    }

    fun toggleKers(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(kersEnabled = enabled) }
            return
        }

        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x0C + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0C.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(kersEnabled = enabled) }
    }

    fun toggleTempProtectionBypass(bypass: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(tempProtectionBypassed = bypass) }
            return
        }

        val payload = if (bypass) 1 else 0
        val checksum = (0x01 + 0x0D + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0D.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(tempProtectionBypassed = bypass) }
    }

    fun toggleAntiTheft(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(antiTheftEnabled = enabled) }
            return
        }
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x0E + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0E.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(antiTheftEnabled = enabled) }
    }

    fun toggleSoftStart(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(softStartEnabled = enabled) }
            return
        }
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x0F + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x0F.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(softStartEnabled = enabled) }
    }

    fun toggleReverseGear(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(reverseGearEnabled = enabled) }
            return
        }
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x10 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x10.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(reverseGearEnabled = enabled) }
    }

    fun toggleSoundSimulation(enabled: Boolean) {
        if (_isSimulationMode.value) {
            _telemetry.update { it.copy(soundSimulationEnabled = enabled) }
            return
        }
        val payload = if (enabled) 1 else 0
        val checksum = (0x01 + 0x11 + payload) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x11.toByte(),
            payload.toByte(), checksum.toByte()
        )
        writeCharacteristic(packet)
        _telemetry.update { it.copy(soundSimulationEnabled = enabled) }
    }

    fun flashFirmwareAdjustment(
        customSpeed: Float,
        turbo: Boolean,
        bypassBatteryLimit: Boolean,
        onProgress: (Int, String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        scope.launch {
            try {
                // Step 1: Initialize Flash session
                onProgress(10, "Verbindung zum Controller-Register herstellen...")
                delay(400)
                
                // If not in simulation, send initial flash handshake: AA 55 02 01 09 01 [checksum]
                if (!_isSimulationMode.value) {
                    val packet = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x09.toByte(), 0x01.toByte(), 0x0B.toByte())
                    writeCharacteristic(packet)
                }
                delay(300)

                // Step 2: Write Custom Max Speed Register
                onProgress(35, "Register 0x4B (MaxSpeed) wird auf ${customSpeed.toInt()} km/h angepasst...")
                if (!_isSimulationMode.value) {
                    val payload = customSpeed.toInt() and 0xFF
                    val checksum = (0x01 + 0x08 + payload) and 0xFF
                    val packet = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x08.toByte(), payload.toByte(), checksum.toByte())
                    writeCharacteristic(packet)
                } else {
                    _telemetry.update { it.copy(customMaxSpeed = customSpeed) }
                }
                delay(500)

                // Step 3: Write Turbo Mode Register
                val turboStr = if (turbo) "AKTIVIERT" else "DEAKTIVIERT"
                onProgress(60, "Register 0x4C (TurboMode) wird $turboStr...")
                if (!_isSimulationMode.value) {
                    val payload = if (turbo) 1 else 0
                    val checksum = (0x01 + 0x06 + payload) and 0xFF
                    val packet = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(), payload.toByte(), checksum.toByte())
                    writeCharacteristic(packet)
                } else {
                    _telemetry.update { it.copy(turboModeEnabled = turbo) }
                }
                delay(500)

                // Step 4: Write Low Battery Limit Register
                val bypassStr = if (bypassBatteryLimit) "UMGANGEN" else "AKTIV"
                onProgress(85, "Register 0x4D (LowBatteryLimit) wird auf $bypassStr gesetzt...")
                if (!_isSimulationMode.value) {
                    val payload = if (bypassBatteryLimit) 1 else 0
                    val checksum = (0x01 + 0x07 + payload) and 0xFF
                    val packet = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x02.toByte(), 0x01.toByte(), 0x07.toByte(), payload.toByte(), checksum.toByte())
                    writeCharacteristic(packet)
                } else {
                    _telemetry.update { it.copy(lowBatteryLimitBypassed = bypassBatteryLimit) }
                }
                delay(500)

                // Step 5: Finalize and reboot controller registers
                onProgress(100, "Controller-Anpassung erfolgreich aufgespielt!")
                delay(200)
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error flashing firmware: ${e.message}")
                onComplete(false)
            }
        }
    }


    // ==========================================
    // --- HIGH-FIDELITY SIMULATION MODE ENGINE ---
    // ==========================================

    private fun startSimulationLoop() {
        if (simulationJob != null) return

        isSimulatingRide = true
        simulationJob = scope.launch {
            while (isActive) {
                if (_isSimulationMode.value) {
                    simulateScooterDynamics()
                }
                delay(200) // 5Hz UI update frequency for extremely smooth metrics
            }
        }
    }

    private fun stopSimulationLoop() {
        isSimulatingRide = false
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun startSoundSynth() {
        if (soundSynthJob != null) return
        soundSynthJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize <= 0) return@launch

            var audioTrack: AudioTrack? = null
            val shortBuffer = ShortArray(512)
            var phase = 0.0
            var subPhase = 0.0

            try {
                while (isActive) {
                    val currentTelemetry = _telemetry.value
                    val isSoundOn = currentTelemetry.soundSimulationEnabled &&
                                   _connectionState.value == ConnectionState.CONNECTED

                    if (!isSoundOn) {
                        if (audioTrack != null) {
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (e: Exception) {
                                // ignore
                            }
                            audioTrack = null
                        }
                        delay(250)
                        continue
                    }

                    if (audioTrack == null) {
                        try {
                            audioTrack = AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                sampleRate,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize,
                                AudioTrack.MODE_STREAM
                            )
                            audioTrack.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initialize AudioTrack", e)
                            delay(1000)
                            continue
                        }
                    }

                    val speed = currentTelemetry.speedKmh
                    // Frequency ramps up based on speed (45Hz to 380Hz)
                    val baseFreq = 42.0 + (speed * 6.5)
                    val currentDraw = currentTelemetry.currentAmps
                    val loadAcc = (currentDraw / 32f).coerceIn(0f, 1f)

                    val dt = 1.0 / sampleRate

                    for (i in shortBuffer.indices) {
                        // Core electric whine
                        val angle = 2.0 * Math.PI * baseFreq * phase
                        val sinValue = Math.sin(angle)

                        // Growling triangle/sawtooth harmonic under acceleration/load
                        val triValue = if (((phase * baseFreq * 2.0) % 1.0) < 0.5) {
                            ((phase * baseFreq * 2.0) % 1.0) * 4.0 - 1.0
                        } else {
                            3.0 - ((phase * baseFreq * 2.0) % 1.0) * 4.0
                        }

                        // Sub-bass rumble for beefy low-end feel
                        val subAngle = 2.0 * Math.PI * (baseFreq * 0.5) * subPhase
                        val subValue = Math.sin(subAngle)

                        // Dynamic blending
                        val synthValue = (sinValue * 0.25) + (triValue * 0.12 * loadAcc) + (subValue * 0.3)
                        
                        // Volume is quieter at idle, louder with speed/throttle
                        val volumeScale = 0.12 + (speed / 120.0).coerceIn(0.0, 0.38) + (loadAcc * 0.1)
                        val sample = (synthValue * Short.MAX_VALUE * volumeScale).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                        shortBuffer[i] = sample.toShort()

                        phase += dt
                        subPhase += dt
                        if (phase > 1.0) phase -= 1.0
                        if (subPhase > 1.0) subPhase -= 1.0
                    }

                    audioTrack.write(shortBuffer, 0, shortBuffer.size)
                    // Sleep tiny bit to let OS audio thread catch up
                    delay(15)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack loop exception", e)
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun stopSoundSynth() {
        soundSynthJob?.cancel()
        soundSynthJob = null
    }

    private fun startSimulatedScan() {
        scope.launch {
            _isScanning.value = true
            _scannedDevices.value = emptyList()
            delay(800) // Initial delay

            val mockDevices = listOf(
                BleDevice("F4:12:FA:82:11:03", "KuKirin G2 Pro (#1103)", -54),
                BleDevice("28:CC:15:3F:A9:B2", "KuKirin G2 Master (#5942)", -68),
                BleDevice("90:4C:E5:60:DE:E4", "Kirin-G2-Max (#4410)", -82),
                BleDevice("00:1A:7D:DA:71:11", "Xiaomi M365 (Unrelated)", -95)
            )

            for (device in mockDevices) {
                delay(600)
                if (!_isScanning.value) break
                _scannedDevices.update { currentList ->
                    if (currentList.none { it.address == device.address }) {
                        currentList + device
                    } else {
                        currentList
                    }
                }
            }
            _isScanning.value = false
        }
    }

    private fun connectToSimulatedDevice(address: String) {
        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            delay(1200) // Simulate connection lag
            _connectionState.value = ConnectionState.CONNECTED

            // Find matching device name for visual integration
            val name = _scannedDevices.value.find { it.address == address }?.name ?: "KuKirin G2"
            Log.d(TAG, "Simulated connection success to $name ($address)")

            // Reset trip metrics on new simulated connection
            simulatedTrip = 0.0f
            batteryRemainingAh = batteryCapacityAh * (0.8f + (Math.random() * 0.18f).toFloat()) // 80-98% battery

            _telemetry.update {
                it.copy(
                    speedKmh = 0f,
                    batteryPercent = ((batteryRemainingAh / batteryCapacityAh) * 100).toInt(),
                    voltage = 52.8f, // Full 48V battery rests around 54.6V - 52.8V
                    currentAmps = 0f,
                    tempC = 24.5f,
                    powerWatts = 0f,
                    totalMileageKm = simulatedOdometer,
                    tripMileageKm = 0f,
                    currentGear = 1,
                    dualMotorEnabled = false,
                    speedLimitUnlocked = false,
                    lightStatus = false,
                    lockStatus = false,
                    turboModeEnabled = false,
                    lowBatteryLimitBypassed = false,
                    customMaxSpeed = 120f,
                    cruiseControlEnabled = false,
                    zeroStartEnabled = false,
                    kersEnabled = true,
                    tempProtectionBypassed = false,
                    errorCodes = emptyList()
                )
            }
        }
    }

    // Interactive user actions in simulation
    fun simulateThrottle(pressedRatio: Float) {
        currentThrottle = max(0f, min(1f, pressedRatio))
    }

    fun simulateBrake(pressed: Boolean) {
        currentBrake = pressed
        if (pressed) {
            currentThrottle = 0f
        }
    }

    fun triggerSimulatedError(errorCode: String?) {
        _telemetry.update { current ->
            val errors = if (errorCode == null) emptyList() else listOf(errorCode)
            current.copy(errorCodes = errors)
        }
    }

    private fun simulateScooterDynamics() {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        _telemetry.update { current ->
            // 1. Calculate base Max Speed based on Gear and Tuning Limits
            var maxSpeedLimit = if (current.speedLimitUnlocked) {
                // Tuned limits
                when (current.currentGear) {
                    1 -> 25.0f
                    2 -> 45.0f
                    else -> if (current.turboModeEnabled) current.customMaxSpeed else 50.0f // Fully Unlocked / Turbo speed
                }
            } else {
                // Safe/Legal limits
                when (current.currentGear) {
                    1 -> 15.0f
                    2 -> 20.0f
                    else -> 25.0f
                }
            }

            // 1.1 Adjust for battery limitation if NOT bypassed and unlocked
            val currentBatteryPct = ((batteryRemainingAh / batteryCapacityAh) * 100).toInt()
            if (!current.lowBatteryLimitBypassed && current.speedLimitUnlocked) {
                maxSpeedLimit = when {
                    currentBatteryPct >= 70 -> maxSpeedLimit
                    currentBatteryPct >= 50 -> maxSpeedLimit * 0.85f
                    currentBatteryPct >= 30 -> maxSpeedLimit * 0.70f
                    else -> maxSpeedLimit * 0.50f
                }
            }

            // 2. Adjust target speed based on throttle/brake inputs
            val throttleWorking = current.zeroStartEnabled || current.speedKmh >= 3.0f || current.reverseGearEnabled
            if (currentThrottle > 0f && !currentBrake && !current.lockStatus && throttleWorking) {
                if (current.reverseGearEnabled) {
                    targetSpeed = 5.0f * currentThrottle // reverse speed limit
                } else {
                    targetSpeed = maxSpeedLimit * currentThrottle
                }
            } else {
                targetSpeed = 0f
            }

            // 3. Acceleration & Braking Dynamics (Dual Motor + Turbo makes it accelerate significantly faster!)
            val accelCoefficient = if (current.dualMotorEnabled) {
                if (current.turboModeEnabled) 3.5f else 2.2f
            } else {
                if (current.turboModeEnabled) 2.0f else 1.2f
            }
            var accelForce = 0.08f * accelCoefficient * (1.5f - (current.speedKmh / maxSpeedLimit)) // Less force at higher speed
            if (current.softStartEnabled) {
                accelForce *= 0.4f // Gentler acceleration ramp
            }
            val brakeForce = if (current.kersEnabled) 0.8f else 0.4f

            var speed = current.speedKmh
            if (currentBrake) {
                speed = max(0f, speed - brakeForce)
            } else if (speed < targetSpeed) {
                speed = min(targetSpeed, speed + accelForce)
            } else if (speed > targetSpeed) {
                // Wind resistance/KERS/Cruise control coasting down
                val coastDecel = if (current.cruiseControlEnabled && currentThrottle == 0f && speed > 5f) {
                    0.005f // Cruise control keeps speed almost constant
                } else if (current.kersEnabled) {
                    0.08f // KERS drag
                } else {
                    0.03f // Smooth coasting
                }
                speed = max(targetSpeed, speed - coastDecel)
            }

            // 4. Calculate Current (Amps) and Power (Watts)
            // Idle draw is around 0.1A. Max draw depends on dual motor & load.
            val baseDraw = if (speed > 0.1f) 1.5f else 0.1f
            val accelerationDraw = if (speed < targetSpeed) {
                val motorScale = if (current.dualMotorEnabled) 2.0f else 1.0f
                currentThrottle * 18.0f * motorScale * (1.2f - (speed / maxSpeedLimit))
            } else 0f
            
            // Add slight regenerative braking current if braking at speed
            val regenDraw = if (current.kersEnabled && currentBrake && speed > 5.0f) -3.5f else 0f

            val rawAmps = baseDraw + accelerationDraw + regenDraw
            val amps = max(-3.0f, min(if (current.dualMotorEnabled) 36f else 22f, rawAmps))

            // 5. Calculate Battery Voltage & Voltage Sag
            // Nominal 48V battery: 100% is 54.6V, 0% is 39V.
            val internalResistance = if (current.dualMotorEnabled) 0.08f else 0.15f // Dual motor splits load, less sag per controller
            val stateOfCharge = batteryRemainingAh / batteryCapacityAh
            val openCircuitVoltage = 39.0f + (15.6f * stateOfCharge)
            val sag = amps * internalResistance
            val voltage = max(38.0f, openCircuitVoltage - sag)

            // 6. Update Temperature (C)
            // Ambient temperature is ~24C. Load increases temp. Dual motors can heat up controllers.
            val targetTemp = 24.0f + (amps * 1.2f)
            val tempRate = if (current.tempC < targetTemp) 0.05f else -0.01f
            val temp = max(24.0f, min(85.0f, current.tempC + tempRate * (amps / 10f + 0.5f)))

            // 7. Calculate Power (Watts)
            val power = voltage * amps

            // 8. Odometer and Trip Update (distance = speed * time)
            // Loop runs every 200ms -> 1/18000 hour
            val hoursElapsed = 0.2f / 3600.0f
            val kmGained = speed * hoursElapsed
            simulatedOdometer += kmGained
            simulatedTrip += kmGained

            // 9. Consume Battery Ah
            val ampSeconds = amps * 0.2f
            val ampHours = ampSeconds / 3600.0f
            batteryRemainingAh = max(0.0f, min(batteryCapacityAh, batteryRemainingAh - ampHours))
            val batteryPct = ((batteryRemainingAh / batteryCapacityAh) * 100).toInt()

            // 10. Automatic simulation errors (e.g. low voltage warning)
            val updatedErrors = current.errorCodes.toMutableList()
            if (batteryPct <= 5 && !updatedErrors.contains("E-04: Unterspannungsschutz")) {
                updatedErrors.add("E-04: Unterspannungsschutz")
            } else if (batteryPct > 5 && updatedErrors.contains("E-04: Unterspannungsschutz")) {
                updatedErrors.remove("E-04: Unterspannungsschutz")
            }

            if (temp >= 78.0f && !current.tempProtectionBypassed && !updatedErrors.contains("E-08: Übertemperatur")) {
                updatedErrors.add("E-08: Übertemperatur")
            } else if ((temp < 72.0f || current.tempProtectionBypassed) && updatedErrors.contains("E-08: Übertemperatur")) {
                updatedErrors.remove("E-08: Übertemperatur")
            }

            current.copy(
                speedKmh = if (speed < 0.15f) 0f else speed,
                batteryPercent = batteryPct,
                voltage = String.format("%.1f", voltage).replace(",", ".").toFloat(),
                currentAmps = String.format("%.1f", amps).replace(",", ".").toFloat(),
                tempC = String.format("%.1f", temp).replace(",", ".").toFloat(),
                powerWatts = String.format("%.1f", power).replace(",", ".").toFloat(),
                totalMileageKm = String.format("%.2f", simulatedOdometer).replace(",", ".").toFloat(),
                tripMileageKm = String.format("%.2f", simulatedTrip).replace(",", ".").toFloat(),
                errorCodes = updatedErrors
            )
        }
    }
}
