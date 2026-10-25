package com.example.data.ble

import android.Manifest
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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.BleSosBeacon
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.calculateDistanceKm
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.pow
import kotlin.random.Random

/**
 * Offline Bluetooth Low Energy SOS mesh.
 *
 * A single BLE hop only reaches ~50-100 m, so every Rokkhon phone that hears an SOS
 * re-broadcasts it for a short while ("relay"). Each packet carries the sender's GPS position,
 * a random packet id and a hop counter:
 *  - receivers drop packets they have already handled (same packet id),
 *  - a receiver only alerts its user, and only relays further, while it is within 1 km of where
 *    the SOS started, so the alert spreads to every Rokkhon user in that 1 km circle and stops there,
 *  - a phone that doesn't know its own location falls back to a small hop limit.
 *
 * One instance per process ([get]) so the screen and the background guard service share the
 * same scanner, relay slot and de-duplication state.
 */
class BleSosMeshManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isRelaying = MutableStateFlow(false)
    val isRelaying: StateFlow<Boolean> = _isRelaying.asStateFlow()

    private val _relayedCount = MutableStateFlow(0)
    /** How many other people's SOS packets this phone has passed on since the app started. */
    val relayedCount: StateFlow<Int> = _relayedCount.asStateFlow()

    private val _statusMessage = MutableStateFlow("BLE standby")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _nearbyBeacons = MutableStateFlow<List<BleSosBeacon>>(emptyList())
    /** Distinct alerts heard recently (one entry per packet id), closest first. */
    val nearbyBeacons: StateFlow<List<BleSosBeacon>> = _nearbyBeacons.asStateFlow()

    private val _incomingAlerts = MutableSharedFlow<BleSosBeacon>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    /** Each new alert relevant to this phone (inside the 1 km circle), emitted once per packet id. */
    val incomingAlerts: SharedFlow<BleSosBeacon> = _incomingAlerts.asSharedFlow()

    @Volatile private var ownLat: Double? = null
    @Volatile private var ownLng: Double? = null

    private val seenPacketIds = LinkedHashMap<Int, Long>()
    private val ownPacketIds = mutableSetOf<Int>()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanRequested = false
    @Volatile private var currentOwnPacketId: Int = 0
    private val stopRelayRunnable = Runnable { stopRelayQuietly() }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            _statusMessage.value = "Broadcasting SOS over Bluetooth"
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _statusMessage.value = "Bluetooth broadcast failed (code $errorCode) • internet only"
        }
    }

    private val relayCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isRelaying.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            _isRelaying.value = false
            Log.w(TAG, "Relay advertise failed: $errorCode")
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
            _statusMessage.value = "Bluetooth scan failed (code $errorCode)"
        }
    }

    fun isBluetoothHardwareSupported(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    /** Android 12+ needs runtime BLUETOOTH_SCAN/ADVERTISE; older versions need location for scans. */
    fun hasRequiredPermissions(): Boolean = hasBlePermissions(appContext)

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

    /** The receiver's own position, used for the 1 km decision. Only real GPS fixes belong here. */
    fun updateOwnLocation(latitude: Double, longitude: Double) {
        ownLat = latitude
        ownLng = longitude
    }

    fun hasOwnLocation(): Boolean = ownLat != null && ownLng != null

    private fun advertiseSettings(): AdvertiseSettings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(false)
        .build()

    private fun advertiseData(payload: ByteArray): AdvertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .addServiceUuid(ParcelUuid(ROKKHON_SOS_UUID))
        .addServiceData(ParcelUuid(ROKKHON_SOS_UUID), payload)
        .build()

    @SuppressLint("MissingPermission")
    private fun startOwnAdvertising(payload: ByteArray, packetId: Int, label: String): Boolean {
        unavailableReason()?.let { reason ->
            _isAdvertising.value = false
            _statusMessage.value = "$reason • $label sent over internet only"
            return false
        }
        return try {
            stopAdvertisingQuietly()
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            val adv = advertiser
            if (adv == null) {
                _isAdvertising.value = false
                _statusMessage.value = "This phone cannot broadcast over Bluetooth • $label sent over internet only"
                false
            } else {
                currentOwnPacketId = packetId
                synchronized(seenPacketIds) {
                    ownPacketIds += packetId
                    seenPacketIds[packetId] = System.currentTimeMillis()
                }
                adv.startAdvertising(advertiseSettings(), advertiseData(payload), advertiseCallback)
                _statusMessage.value = "Starting Bluetooth broadcast…"
                true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE permission missing for advertise", e)
            _isAdvertising.value = false
            _statusMessage.value = "Bluetooth permission needed"
            false
        } catch (e: Exception) {
            Log.w(TAG, "BLE advertise error", e)
            _isAdvertising.value = false
            _statusMessage.value = "Bluetooth broadcast failed • $label sent over internet only"
            false
        }
    }

    /** Starts broadcasting this phone's SOS until [stopSosBroadcast]. Returns a short display code. */
    fun startSosBroadcast(
        senderName: String,
        bloodGroup: String,
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        hasLiveVerification: Boolean,
        districtAreaKey: String = ""
    ): String {
        val packetId = newPacketId()
        val payload = encodePayload(
            isBlood = false,
            bloodGroup = bloodGroup,
            typeOrUnits = EMERGENCY_TYPES.indexOf(emergencyType).coerceAtLeast(0),
            verifiedOrAreaIdx = if (hasLiveVerification) 1 else 0,
            lat = latitude.toFloat(),
            lng = longitude.toFloat(),
            packetId = packetId,
            hops = 0
        )
        startOwnAdvertising(payload, packetId, "SOS")
        return "BLE-${Integer.toHexString(packetId and 0xFFFF).uppercase().padStart(4, '0')}"
    }

    /** Broadcasts a blood request to Rokkhon phones nearby (matching donors in the same area are alerted). */
    fun startBloodRequestBroadcast(
        patientName: String,
        bloodGroup: String,
        unitsNeeded: Int,
        districtAreaKey: String,
        latitude: Double,
        longitude: Double
    ): String {
        val packetId = newPacketId()
        val payload = encodePayload(
            isBlood = true,
            bloodGroup = bloodGroup,
            typeOrUnits = unitsNeeded.coerceIn(1, 10),
            verifiedOrAreaIdx = areaHashOf(districtAreaKey),
            lat = latitude.toFloat(),
            lng = longitude.toFloat(),
            packetId = packetId,
            hops = 0
        )
        if (startOwnAdvertising(payload, packetId, "Blood request")) {
            _statusMessage.value = "Bluetooth blood alert broadcasting ($bloodGroup)"
            // A blood alert is a short burst, not a continuous beacon like a live SOS.
            mainHandler.postDelayed({ if (currentOwnPacketId == packetId) stopSosBroadcast() }, OWN_BLOOD_BROADCAST_MS)
        }
        return "BLE-BLD-${Integer.toHexString(packetId and 0xFFF).uppercase()}"
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
        currentOwnPacketId = 0
        stopAdvertisingQuietly()
        _isAdvertising.value = false
        _statusMessage.value = "Bluetooth broadcast stopped"
    }

    /** Re-broadcasts someone else's packet (hop count + 1) for [RELAY_DURATION_MS]. */
    @SuppressLint("MissingPermission")
    private fun relay(beacon: BleSosBeacon, rawPayload: ByteArray) {
        if (blockReason() != null) return
        try {
            val adv = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
            val relayed = rawPayload.copyOf(PAYLOAD_SIZE)
            relayed[PAYLOAD_SIZE - 1] = (beacon.hops + 1).coerceAtMost(MAX_HOPS).toByte()
            mainHandler.removeCallbacks(stopRelayRunnable)
            try {
                adv.stopAdvertising(relayCallback)
            } catch (_: Exception) {
            }
            adv.startAdvertising(advertiseSettings(), advertiseData(relayed), relayCallback)
            _relayedCount.value = _relayedCount.value + 1
            mainHandler.postDelayed(stopRelayRunnable, RELAY_DURATION_MS)
        } catch (e: Exception) {
            Log.w(TAG, "BLE relay failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopRelayQuietly() {
        try {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(relayCallback)
        } catch (_: Exception) {
        }
        _isRelaying.value = false
    }

    @SuppressLint("MissingPermission")
    fun startMeshScanning() {
        scanRequested = true
        if (_isScanning.value) return
        unavailableReason()?.let { reason ->
            _isScanning.value = false
            _statusMessage.value = reason
            return
        }

        try {
            scanner = bluetoothAdapter?.bluetoothLeScanner
            val sc = scanner
            if (sc == null) {
                _isScanning.value = false
                _statusMessage.value = "Bluetooth scanner unavailable"
                return
            }
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ROKKHON_SOS_UUID))
                .build()
            // Balanced mode: continuous LOW_LATENCY scanning drains the battery quickly.
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            sc.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            _statusMessage.value = "Listening for Bluetooth SOS nearby"
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE scan permission missing", e)
            _statusMessage.value = "Bluetooth permission needed"
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan error", e)
            _statusMessage.value = "Bluetooth listener unavailable"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopMeshScanning() {
        scanRequested = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Stop BLE scan failed", e)
        }
        _isScanning.value = false
        _statusMessage.value = "Bluetooth listener paused"
    }

    private fun parseScanResult(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(ROKKHON_SOS_UUID)) ?: return
        val decoded = decodePayload(serviceData) ?: return
        val now = System.currentTimeMillis()
        val rssi = result.rssi

        val isNew = synchronized(seenPacketIds) {
            if (decoded.packetId in ownPacketIds) return
            pruneSeen(now)
            if (seenPacketIds.containsKey(decoded.packetId)) {
                false
            } else {
                seenPacketIds[decoded.packetId] = now
                true
            }
        }

        val lat = ownLat
        val lng = ownLng
        val distanceKm = if (lat != null && lng != null) {
            calculateDistanceKm(lat, lng, decoded.latitude, decoded.longitude)
        } else {
            null
        }
        val beacon = decoded.toBeacon(rssi, estimateDistanceMeters(rssi), distanceKm, now)

        // Keep the freshest copy for the on-screen list (signal strength changes as people move).
        val staleBefore = now - BEACON_TTL_MILLIS
        _nearbyBeacons.value = (_nearbyBeacons.value.filterNot { it.packetId == beacon.packetId || it.timestampMillis < staleBefore } + beacon)
            .sortedBy { it.distanceFromOriginKm ?: (it.estimatedDistanceMeters / 1000.0) }

        if (!isNew) return

        val decision = meshDecision(beacon.packetKind, distanceKm, beacon.hops)
        if (decision.alert) _incomingAlerts.tryEmit(beacon)
        if (decision.relay) relay(beacon, serviceData)
    }

    private fun pruneSeen(now: Long) {
        val iterator = seenPacketIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > SEEN_TTL_MILLIS || seenPacketIds.size > MAX_SEEN) iterator.remove() else break
        }
    }

    /** Payload fields shared by the encoder, decoder and tests. */
    data class DecodedPacket(
        val isBlood: Boolean,
        val bloodGroup: String,
        val typeOrUnits: Int,
        val verifiedOrAreaIdx: Int,
        val latitude: Double,
        val longitude: Double,
        val packetId: Int,
        val hops: Int
    ) {
        fun toBeacon(rssi: Int, estimatedMeters: Double, distanceFromOriginKm: Double?, now: Long): BleSosBeacon {
            val shortId = Integer.toHexString(packetId and 0xFFFF).uppercase().padStart(4, '0')
            return if (isBlood) {
                BleSosBeacon(
                    beaconId = "BLE-BLD-$shortId",
                    senderCode = "BLE-$shortId",
                    bloodGroup = bloodGroup,
                    emergencyType = "BLOOD_REQUEST",
                    latitude = latitude,
                    longitude = longitude,
                    rssi = rssi,
                    estimatedDistanceMeters = estimatedMeters,
                    hasCameraAudioProof = false,
                    timestampMillis = now,
                    packetKind = PACKET_KIND_BLOOD_AREA,
                    districtAreaKey = "areahash_${verifiedOrAreaIdx and 0x7F}",
                    unitsNeeded = typeOrUnits.coerceIn(1, 10),
                    packetId = packetId,
                    hops = hops,
                    distanceFromOriginKm = distanceFromOriginKm
                )
            } else {
                BleSosBeacon(
                    beaconId = "BLE-$shortId",
                    senderCode = "BLE-$shortId",
                    bloodGroup = bloodGroup,
                    emergencyType = EMERGENCY_TYPES[typeOrUnits.coerceIn(0, EMERGENCY_TYPES.lastIndex)],
                    latitude = latitude,
                    longitude = longitude,
                    rssi = rssi,
                    estimatedDistanceMeters = estimatedMeters,
                    hasCameraAudioProof = verifiedOrAreaIdx == 1,
                    timestampMillis = now,
                    packetKind = PACKET_KIND_SOS_1KM,
                    districtAreaKey = "nearby_1km",
                    unitsNeeded = 1,
                    packetId = packetId,
                    hops = hops,
                    distanceFromOriginKm = distanceFromOriginKm
                )
            }
        }
    }

    data class MeshDecision(val alert: Boolean, val relay: Boolean)

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
        private const val SEEN_TTL_MILLIS = 30L * 60L * 1000L
        private const val MAX_SEEN = 512
        private const val RELAY_DURATION_MS = 45_000L
        private const val OWN_BLOOD_BROADCAST_MS = 2L * 60L * 1000L
        const val PAYLOAD_SIZE = 16
        const val MAX_HOPS = 6
        /** Without its own GPS a phone still relays/alerts for a few hops (each hop is ~50-100 m). */
        const val MAX_HOPS_WITHOUT_LOCATION = 3
        const val PACKET_KIND_SOS_1KM = "SOS_1KM"
        const val PACKET_KIND_BLOOD_AREA = "BLOOD_AREA"
        val ROKKHON_SOS_UUID: UUID = UUID.fromString("0000FD88-0000-1000-8000-00805F9B34FB")
        private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        private val EMERGENCY_TYPES = listOf("ROAD_DANGER", "MEDICAL", "ACCIDENT", "HARASSMENT")

        @Volatile private var instance: BleSosMeshManager? = null

        fun get(context: Context): BleSosMeshManager =
            instance ?: synchronized(this) {
                instance ?: BleSosMeshManager(context.applicationContext).also { instance = it }
            }

        fun hasBlePermissions(context: Context): Boolean {
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        /** 7-bit area tag carried in blood packets; the receiver compares it with its own area. */
        fun areaHashOf(areaKey: String): Int = areaKey.lowercase().hashCode() and 0x7F

        private fun newPacketId(): Int {
            var id = 0
            while (id == 0) id = Random.nextInt()
            return id
        }

        /**
         * Should this phone alert its user / pass the packet on?
         * SOS: inside the 1 km circle around the sender (or, without own GPS, within a few hops).
         * Blood: relayed a couple of hops so it crosses a building or a street; the area/blood-group
         * match decides whether the user is alerted.
         */
        fun meshDecision(packetKind: String, distanceFromOriginKm: Double?, hops: Int): MeshDecision {
            return if (packetKind == PACKET_KIND_SOS_1KM) {
                val inside = if (distanceFromOriginKm != null) {
                    distanceFromOriginKm <= SOS_NOTIFICATION_RADIUS_KM
                } else {
                    hops < MAX_HOPS_WITHOUT_LOCATION
                }
                MeshDecision(alert = inside, relay = inside && hops < MAX_HOPS)
            } else {
                MeshDecision(alert = true, relay = hops < 2)
            }
        }

        fun encodePayload(
            isBlood: Boolean,
            bloodGroup: String,
            typeOrUnits: Int,
            verifiedOrAreaIdx: Int,
            lat: Float,
            lng: Float,
            packetId: Int,
            hops: Int
        ): ByteArray {
            val bgIdx = BLOOD_GROUPS.indexOf(bloodGroup).coerceAtLeast(0)
            val firstByte = if (isBlood) (bgIdx or 0x80) else bgIdx
            return ByteBuffer.allocate(PAYLOAD_SIZE)
                .put(firstByte.toByte())
                .put(typeOrUnits.toByte())
                .put(verifiedOrAreaIdx.toByte())
                .putFloat(lat)
                .putFloat(lng)
                .putInt(packetId)
                .put(hops.toByte())
                .array()
        }

        /** Accepts the current 16-byte packet and the older 11-byte one (no id/hops). */
        fun decodePayload(data: ByteArray): DecodedPacket? {
            if (data.size < 11) return null
            return try {
                val buffer = ByteBuffer.wrap(data)
                val first = buffer.get().toInt()
                val second = buffer.get().toInt()
                val third = buffer.get().toInt()
                val lat = buffer.float.toDouble()
                val lng = buffer.float.toDouble()
                if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
                val hasMesh = data.size >= PAYLOAD_SIZE
                val packetId = if (hasMesh) buffer.int else data.copyOf(11).contentHashCode()
                val hops = if (hasMesh) buffer.get().toInt().coerceIn(0, MAX_HOPS) else 0
                DecodedPacket(
                    isBlood = (first and 0x80) != 0,
                    bloodGroup = BLOOD_GROUPS[(first and 0x7F).coerceIn(0, BLOOD_GROUPS.lastIndex)],
                    typeOrUnits = second,
                    verifiedOrAreaIdx = third,
                    latitude = lat,
                    longitude = lng,
                    packetId = packetId,
                    hops = hops
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
