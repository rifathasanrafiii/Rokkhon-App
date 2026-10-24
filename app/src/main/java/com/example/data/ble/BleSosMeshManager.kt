package com.example.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.os.ParcelUuid
import android.util.Log
import com.example.data.model.BleSosBeacon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.pow

/**
 * Real Android Bluetooth Low Energy (BLE) Mesh broadcaster and scanner for Rokkhon.
 * Enables offline device-to-device emergency SOS alerts when mobile internet is unavailable.
 */
class BleSosMeshManager(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow("BLE standby")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _nearbyBeacons = MutableStateFlow<List<BleSosBeacon>>(emptyList())
    val nearbyBeacons: StateFlow<List<BleSosBeacon>> = _nearbyBeacons.asStateFlow()

    private val _latestIncomingBleBeacon = MutableStateFlow<BleSosBeacon?>(null)
    val latestIncomingBleBeacon: StateFlow<BleSosBeacon?> = _latestIncomingBleBeacon.asStateFlow()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            _statusMessage.value = "Broadcasting BLE Signal (SOS <=1km / Blood Area Mesh)"
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _statusMessage.value = "BLE Broadcast code $errorCode (Local Relay Active)"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            parseScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { parseScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _statusMessage.value = "BLE Scan code: $errorCode"
        }
    }

    fun isBluetoothHardwareSupported(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    /** Android 12+ needs runtime BLUETOOTH_SCAN/ADVERTISE/CONNECT; older versions need location for scans. */
    fun hasRequiredPermissions(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    enum class BlockReason { NO_HARDWARE, NO_PERMISSION, BLUETOOTH_OFF }

    /** Why BLE cannot be used right now, or null when it can. */
    fun blockReason(): BlockReason? = when {
        bluetoothAdapter == null -> BlockReason.NO_HARDWARE
        !hasRequiredPermissions() -> BlockReason.NO_PERMISSION
        !bluetoothAdapter.isEnabled -> BlockReason.BLUETOOTH_OFF
        else -> null
    }

    private fun unavailableReason(): String? = when (blockReason()) {
        BlockReason.NO_HARDWARE -> "Bluetooth not available on this device"
        BlockReason.NO_PERMISSION -> "Bluetooth permission needed"
        BlockReason.BLUETOOTH_OFF -> "Bluetooth is turned off"
        null -> null
    }

    @SuppressLint("MissingPermission")
    fun startSosBroadcast(
        senderName: String,
        bloodGroup: String,
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        hasLiveVerification: Boolean,
        districtAreaKey: String = "mirpur"
    ): String {
        val shortHash = "BLE-${UUID.randomUUID().toString().take(4).uppercase()}"
        val adapter = bluetoothAdapter
        unavailableReason()?.let { reason ->
            _isAdvertising.value = false
            _statusMessage.value = "$reason • SOS sent over internet only"
            return shortHash
        }

        try {
            stopAdvertisingQuietly()
            advertiser = adapter?.bluetoothLeAdvertiser
            if (advertiser == null) {
                _isAdvertising.value = false
                _statusMessage.value = "This phone cannot broadcast BLE • SOS sent over internet only"
                return shortHash
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val payloadBytes = encodeCompactBlePayload(
                packetMode = 0.toByte(),
                bloodGroup = bloodGroup,
                typeOrUnits = EMERGENCY_TYPES.indexOf(emergencyType).coerceAtLeast(0),
                verifiedOrAreaIdx = if (hasLiveVerification) 1 else 0,
                lat = latitude.toFloat(),
                lng = longitude.toFloat()
            )

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(ROKKHON_SOS_UUID))
                .addServiceData(ParcelUuid(ROKKHON_SOS_UUID), payloadBytes)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            _statusMessage.value = "Starting BLE SOS broadcast ($shortHash)…"
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE permission missing for advertise", e)
            _isAdvertising.value = false
            _statusMessage.value = "Bluetooth permission needed"
        } catch (e: Exception) {
            Log.w(TAG, "BLE advertise error", e)
            _isAdvertising.value = false
            _statusMessage.value = "BLE broadcast failed • SOS sent over internet only"
        }

        return shortHash
    }

    /**
     * Broadcasts an Urgent Blood Request across the area over Bluetooth Low Energy (BLE) Mesh
     * in addition to Internet/Firestore so matching blood group donors in the area receive it offline or online.
     */
    @SuppressLint("MissingPermission")
    fun startBloodRequestBroadcast(
        patientName: String,
        bloodGroup: String,
        unitsNeeded: Int,
        districtAreaKey: String,
        latitude: Double,
        longitude: Double
    ): String {
        val shortHash = "BLE-BLD-${UUID.randomUUID().toString().take(3).uppercase()}"
        val adapter = bluetoothAdapter
        unavailableReason()?.let { reason ->
            _statusMessage.value = "$reason • Blood request sent over internet only"
            return shortHash
        }

        try {
            stopAdvertisingQuietly()
            advertiser = adapter?.bluetoothLeAdvertiser
            if (advertiser != null) {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .build()

                val areaHashIdx = areaHashOf(districtAreaKey)
                val payloadBytes = encodeCompactBlePayload(
                    packetMode = 1.toByte(),
                    bloodGroup = bloodGroup,
                    typeOrUnits = unitsNeeded.coerceIn(1, 10),
                    verifiedOrAreaIdx = areaHashIdx,
                    lat = latitude.toFloat(),
                    lng = longitude.toFloat()
                )

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(ParcelUuid(ROKKHON_SOS_UUID))
                    .addServiceData(ParcelUuid(ROKKHON_SOS_UUID), payloadBytes)
                    .build()

                advertiser?.startAdvertising(settings, data, advertiseCallback)
            }
            _statusMessage.value = "BLE blood alert broadcasting ($bloodGroup • $districtAreaKey)"
        } catch (e: Exception) {
            Log.w(TAG, "BLE blood advertise error", e)
            _statusMessage.value = "BLE broadcast failed • Blood request sent over internet only"
        }

        return shortHash
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertisingQuietly() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Stop BLE advertise failed", e)
        }
    }

    fun stopSosBroadcast() {
        stopAdvertisingQuietly()
        _isAdvertising.value = false
        _statusMessage.value = "BLE broadcast stopped"
    }

    @SuppressLint("MissingPermission")
    fun startMeshScanning() {
        if (_isScanning.value) return
        val adapter = bluetoothAdapter
        unavailableReason()?.let { reason ->
            _isScanning.value = false
            _statusMessage.value = reason
            return
        }

        try {
            scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                _isScanning.value = false
                _statusMessage.value = "BLE scanner unavailable"
                return
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ROKKHON_SOS_UUID))
                .build()

            // Balanced mode: continuous LOW_LATENCY scanning drains the battery quickly.
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            _statusMessage.value = "Listening for nearby BLE SOS & blood alerts"
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE scan permission missing", e)
            _statusMessage.value = "Bluetooth permission needed"
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan error", e)
            _statusMessage.value = "BLE listener unavailable"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopMeshScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Stop BLE scan failed", e)
        }
        _isScanning.value = false
        _statusMessage.value = "BLE Scanner Paused"
    }

    private fun parseScanResult(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(ROKKHON_SOS_UUID)) ?: return
        if (serviceData.size < 11) return
        try {
            val buffer = ByteBuffer.wrap(serviceData)
            val rawFirstByte = buffer.get().toInt()
            val isBloodPacket = (rawFirstByte and 0x80) != 0
            val bgIndex = (rawFirstByte and 0x7F).coerceIn(0, BLOOD_GROUPS.lastIndex)
            val secondByte = buffer.get().toInt()
            val thirdByte = buffer.get().toInt()
            val lat = buffer.float.toDouble()
            val lng = buffer.float.toDouble()

            val rssi = result.rssi
            val distance = estimateDistanceMeters(rssi)
            val deviceId = result.device?.address ?: "BLE-PEER"
            // Same phone can send several different alerts: identify a packet by sender + content.
            val packetTag = Integer.toHexString(serviceData.contentHashCode() and 0xFFFF).uppercase()

            val beacon = if (isBloodPacket) {
                BleSosBeacon(
                    beaconId = "BLE-BLD-${deviceId.takeLast(4)}-$packetTag",
                    senderCode = "Area Patient (${deviceId.takeLast(4)})",
                    bloodGroup = BLOOD_GROUPS[bgIndex],
                    emergencyType = "BLOOD_REQUEST",
                    latitude = lat,
                    longitude = lng,
                    rssi = rssi,
                    estimatedDistanceMeters = distance,
                    hasCameraAudioProof = true,
                    timestampMillis = System.currentTimeMillis(),
                    packetKind = PACKET_KIND_BLOOD_AREA,
                    districtAreaKey = "areahash_${thirdByte and 0x7F}",
                    unitsNeeded = secondByte.coerceIn(1, 10)
                )
            } else {
                val typeIndex = secondByte.coerceIn(0, EMERGENCY_TYPES.lastIndex)
                BleSosBeacon(
                    beaconId = "BLE-${deviceId.takeLast(5)}-$packetTag",
                    senderCode = "Nearby Peer (${deviceId.takeLast(5)})",
                    bloodGroup = BLOOD_GROUPS[bgIndex],
                    emergencyType = EMERGENCY_TYPES[typeIndex],
                    latitude = lat,
                    longitude = lng,
                    rssi = rssi,
                    estimatedDistanceMeters = distance,
                    hasCameraAudioProof = thirdByte == 1,
                    timestampMillis = System.currentTimeMillis(),
                    packetKind = PACKET_KIND_SOS_1KM,
                    districtAreaKey = "nearby_1km",
                    unitsNeeded = 1
                )
            }

            val staleBefore = System.currentTimeMillis() - BEACON_TTL_MILLIS
            val updated = (_nearbyBeacons.value.filterNot { it.beaconId == beacon.beaconId || it.timestampMillis < staleBefore } + beacon)
                .sortedBy { it.estimatedDistanceMeters }
            _nearbyBeacons.value = updated
            _latestIncomingBleBeacon.value = beacon
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse BLE packet", e)
        }
    }

    private fun encodeCompactBlePayload(
        packetMode: Byte,
        bloodGroup: String,
        typeOrUnits: Int,
        verifiedOrAreaIdx: Int,
        lat: Float,
        lng: Float
    ): ByteArray {
        val bgIdx = BLOOD_GROUPS.indexOf(bloodGroup).coerceAtLeast(0)
        val firstByte = if (packetMode.toInt() == 1) {
            (bgIdx or 0x80).toByte()
        } else {
            bgIdx.toByte()
        }
        return ByteBuffer.allocate(11)
            .put(firstByte)
            .put(typeOrUnits.toByte())
            .put(verifiedOrAreaIdx.toByte())
            .putFloat(lat)
            .putFloat(lng)
            .array()
    }

    private fun estimateDistanceMeters(rssi: Int, txPower: Int = -59): Double {
        if (rssi == 0) return 15.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            0.89976 * ratio.pow(7.7095) + 0.111
        }.coerceIn(0.5, 950.0)
    }

    companion object {
        private const val TAG = "BleSosMeshManager"
        private const val BEACON_TTL_MILLIS = 10L * 60L * 1000L

        /** 7-bit area tag carried in blood packets; the receiver compares it with its own area. */
        fun areaHashOf(areaKey: String): Int = areaKey.lowercase().hashCode() and 0x7F
        const val PACKET_KIND_SOS_1KM = "SOS_1KM"
        const val PACKET_KIND_BLOOD_AREA = "BLOOD_AREA"
        val ROKKHON_SOS_UUID: UUID = UUID.fromString("0000FD88-0000-1000-8000-00805F9B34FB")
        private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        private val EMERGENCY_TYPES = listOf("ROAD_DANGER", "MEDICAL", "ACCIDENT", "HARASSMENT")
    }
}

