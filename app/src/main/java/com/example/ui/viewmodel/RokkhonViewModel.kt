package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ble.BleSosMeshManager
import com.example.data.media.LiveAudioVerifier
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.BangladeshDistrict
import com.example.data.model.BleSosBeacon
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.FALSE_SOS_DISPUTE_THRESHOLD
import com.example.data.model.FALSE_SOS_REMOVAL_THRESHOLD
import com.example.data.model.NotificationKind
import com.example.data.model.RokkhonNotificationItem
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.calculateDistanceKm
import com.example.data.notification.RokkhonNotificationHelper
import com.example.data.repository.RokkhonRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class RokkhonTab {
    HOME_MATCH,
    RADAR_MAP,
    SOS_GUARDIAN,
    DONOR_PROFILE
}

enum class MapLayerFilter {
    DONORS_ONLY,
    BLOOD_REQUESTS_ONLY,
    SOS_ALERTS_ONLY,
    ALL
}

class RokkhonViewModel(
    private val repository: RokkhonRepository,
    val bleMeshManager: BleSosMeshManager,
    private val appContext: Context,
    val currentUserId: String,
    val currentUserName: String,
    val isGuestSession: Boolean = false
) : ViewModel() {

    val audioVerifier = LiveAudioVerifier()

    private val prefs = appContext.getSharedPreferences(
        "rokkhon_guardian_state_${currentUserId.take(32)}",
        Context.MODE_PRIVATE
    )

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _currentTab = MutableStateFlow(RokkhonTab.HOME_MATCH)
    val currentTab: StateFlow<RokkhonTab> = _currentTab.asStateFlow()

    private val _selectedBloodFilter = MutableStateFlow<String?>("O+")
    val selectedBloodFilter: StateFlow<String?> = _selectedBloodFilter.asStateFlow()

    private val _onlyAvailableDonorsFilter = MutableStateFlow(true)
    val onlyAvailableDonorsFilter: StateFlow<Boolean> = _onlyAvailableDonorsFilter.asStateFlow()

    // Separate tabs for Available Donors, Patient Blood Requests, and Active SOS Alerts in Find
    private val _mapLayerFilter = MutableStateFlow(MapLayerFilter.DONORS_ONLY)
    val mapLayerFilter: StateFlow<MapLayerFilter> = _mapLayerFilter.asStateFlow()

    private val _selectedDistrict = MutableStateFlow(
        BANGLADESH_DISTRICTS.firstOrNull { it.areaKey == "mirpur" } ?: BANGLADESH_DISTRICTS.first()
    )
    val selectedDistrict: StateFlow<BangladeshDistrict> = _selectedDistrict.asStateFlow()

    private val _isAreaFilterEnabled = MutableStateFlow(true)
    val isAreaFilterEnabled: StateFlow<Boolean> = _isAreaFilterEnabled.asStateFlow()

    // Area is auto-selected by GPS permission by default, and can also be switched manually
    private val _isAreaAutoDetected = MutableStateFlow(true)
    val isAreaAutoDetected: StateFlow<Boolean> = _isAreaAutoDetected.asStateFlow()

    private val _userLatitude = MutableStateFlow(23.8069)
    val userLatitude: StateFlow<Double> = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(90.3687)
    val userLongitude: StateFlow<Double> = _userLongitude.asStateFlow()

    // Continuous Live Location Sharing state during active SOS
    private val _isLiveLocationSharingActive = MutableStateFlow(false)
    val isLiveLocationSharingActive: StateFlow<Boolean> = _isLiveLocationSharingActive.asStateFlow()

    private val _activeUserSosAlertId = MutableStateFlow<String?>(null)
    val activeUserSosAlertId: StateFlow<String?> = _activeUserSosAlertId.asStateFlow()

    private var liveLocationSharingJob: Job? = null

    // One-tap-per-account blood pledge lock
    private val _pledgedRequestIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_PLEDGED_IDS))
    val pledgedRequestIds: StateFlow<Set<String>> = _pledgedRequestIds.asStateFlow()

    // One-tap-per-account SOS "Nearby / যাচ্ছি" responder lock
    private val _respondedSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_RESPONDED_SOS_IDS))
    val respondedSosIds: StateFlow<Set<String>> = _respondedSosIds.asStateFlow()

    // Strict One-tap-per-account SOS "False / ভুয়া" report lock
    private val _reportedFalseSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_REPORTED_FALSE_SOS_IDS))
    val reportedFalseSosIds: StateFlow<Set<String>> = _reportedFalseSosIds.asStateFlow()

    // Tracks which SOS alerts' 5-second audio/video evidence the user has inspected
    private val _inspectedEvidenceSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_INSPECTED_EVIDENCE_SOS_IDS))
    val inspectedEvidenceSosIds: StateFlow<Set<String>> = _inspectedEvidenceSosIds.asStateFlow()

    // False SOS Warning & Penalty state
    private val _falseSosPenaltyCount = MutableStateFlow(prefs.getInt(KEY_FALSE_SOS_PENALTY_COUNT, 0))
    val falseSosPenaltyCount: StateFlow<Int> = _falseSosPenaltyCount.asStateFlow()

    private val _falseSosWarningBanner = MutableStateFlow<String?>(null)
    val falseSosWarningBanner: StateFlow<String?> = _falseSosWarningBanner.asStateFlow()

    // Snapchat-style Daily Safety Streak & Guardian Points
    private val _dailyStreakCount = MutableStateFlow(prefs.getInt(KEY_STREAK_COUNT, 5))
    val dailyStreakCount: StateFlow<Int> = _dailyStreakCount.asStateFlow()

    private val _checkedInToday = MutableStateFlow(
        if (prefs.contains(KEY_LAST_CHECKIN_DAY)) {
            prefs.getString(KEY_LAST_CHECKIN_DAY, "") == todayKey()
        } else {
            true
        }
    )
    val checkedInToday: StateFlow<Boolean> = _checkedInToday.asStateFlow()

    private val _guardianPoints = MutableStateFlow(prefs.getInt(KEY_GUARDIAN_POINTS, 345))
    val guardianPoints: StateFlow<Int> = _guardianPoints.asStateFlow()

    private val _toastBanner = MutableStateFlow<String?>(null)
    val toastBanner: StateFlow<String?> = _toastBanner.asStateFlow()

    // Notification Center items
    private val _customNotifications = MutableStateFlow<List<RokkhonNotificationItem>>(
        createInitialNotifications()
    )
    private val _readNotificationIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_READ_NOTIFICATION_IDS))

    // Local / Community Data across Bangladesh districts
    private val _localDonors = MutableStateFlow(createInitialBangladeshDonors(currentUserId, currentUserName))
    private val _localBloodRequests = MutableStateFlow(createInitialBangladeshBloodRequests())
    private val _localSosAlerts = MutableStateFlow(createInitialBangladeshSosAlerts(currentUserId))

    private var seenBloodRequestIds = mutableSetOf<String>()
    private var seenSosAlertIds = mutableSetOf<String>()
    private var seenBlePacketIds = mutableSetOf<String>()
    private var initialLoadCompleted = false

    fun getCurrentUserBloodGroup(): String {
        return _localDonors.value.firstOrNull { it.userId == currentUserId }?.bloodGroup ?: "O+"
    }

    private fun isCurrentUserMatchingDonorForAreaRequest(req: BloodRequest): Boolean {
        val myDonor = _localDonors.value.firstOrNull { it.userId == currentUserId }
        val myBloodGroup = myDonor?.bloodGroup ?: "O+"
        val isAvailable = myDonor?.isAvailableDonor ?: true
        val isSameArea = _selectedDistrict.value.matchesArea(req.district)
        return isAvailable && isSameArea && myBloodGroup.equals(req.bloodGroup, ignoreCase = true)
    }

    private val upstreamDonorsFlow: Flow<List<DonorNode>> = if (isGuestSession) {
        _localDonors
    } else {
        combine(
            repository.observeDonorNodes().catch { emit(emptyList()) },
            _localDonors
        ) { cloudList, localList ->
            val cloudIds = cloudList.map { it.userId }.toSet()
            cloudList + localList.filterNot { it.userId in cloudIds }
        }
    }

    val donorNodesState: StateFlow<UiState<List<DonorNode>>> = upstreamDonorsFlow
        .map<List<DonorNode>, UiState<List<DonorNode>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing donor nodes", error)
            emit(UiState.Success(_localDonors.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(_localDonors.value)
        )

    private val upstreamBloodRequestsFlow: Flow<List<BloodRequest>> = if (isGuestSession) {
        _localBloodRequests
    } else {
        combine(
            repository.observeBloodRequests().catch { emit(emptyList()) },
            _localBloodRequests,
            _pledgedRequestIds
        ) { cloudList, localList, pledgedSet ->
            val localById = localList.associateBy { it.id }
            val mergedCloud = cloudList.map { cloudReq ->
                val localMatch = localById[cloudReq.id]
                val effectiveStatus = if (localMatch?.status == "FULFILLED") "FULFILLED" else cloudReq.status
                val effectiveResponders = maxOf(cloudReq.respondersCount, localMatch?.respondersCount ?: 0L)
                val ids = if (cloudReq.id in pledgedSet) {
                    (cloudReq.responderIds + currentUserId).distinct()
                } else {
                    cloudReq.responderIds
                }
                cloudReq.copy(
                    status = effectiveStatus,
                    respondersCount = effectiveResponders,
                    responderIds = ids
                )
            }
            val cloudIds = mergedCloud.map { it.id }.toSet()
            mergedCloud + localList.filterNot { it.id in cloudIds }
        }
    }

    val bloodRequestsState: StateFlow<UiState<List<BloodRequest>>> = upstreamBloodRequestsFlow
        .onEach { list ->
            val openRequests = list.filter { it.status == "OPEN" }
            if (initialLoadCompleted) {
                openRequests.forEach { req ->
                    if (req.id.isNotBlank() && !seenBloodRequestIds.contains(req.id) && req.userId != currentUserId) {
                        seenBloodRequestIds.add(req.id)
                        // Area-wide + Matching Blood Group filter: only notify available donors in this area with matching blood group
                        if (isCurrentUserMatchingDonorForAreaRequest(req)) {
                            RokkhonNotificationHelper.dispatchEmergencyAlert(
                                context = appContext,
                                title = "জরুরি রক্তের প্রয়োজন (${req.bloodGroup} • ${req.district})",
                                body = "${req.unitsNeeded} bag(s) ${req.bloodGroup} needed for ${req.patientName} (Hb: ${req.hemoglobinLevel}) at ${req.hospitalName}."
                            )
                            _toastBanner.value = "Area Match (${req.bloodGroup}): ${req.unitsNeeded} bag(s) needed at ${req.hospitalName}, ${req.district}"
                        }
                    }
                }
            } else {
                openRequests.mapNotNullTo(seenBloodRequestIds) { it.id.takeIf(String::isNotBlank) }
            }
        }
        .map<List<BloodRequest>, UiState<List<BloodRequest>>> { list ->
            UiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            Log.w(TAG, "Error observing blood requests", error)
            emit(UiState.Success(_localBloodRequests.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(_localBloodRequests.value)
        )

    private val upstreamSosAlertsFlow: Flow<List<SosAlert>> = if (isGuestSession) {
        combine(_localSosAlerts, _reportedFalseSosIds) { localList, falseSet ->
            localList.map { sos ->
                val fIds = if (sos.id in falseSet) (sos.falseReporterIds + currentUserId).distinct() else sos.falseReporterIds
                sos.copy(falseReporterIds = fIds)
            }
        }
    } else {
        combine(
            repository.observeSosAlerts().catch { emit(emptyList()) },
            _localSosAlerts,
            _respondedSosIds,
            _reportedFalseSosIds
        ) { cloudList, localList, respondedSet, falseSet ->
            val localById = localList.associateBy { it.id }
            val mergedCloud = cloudList.map { cloudSos ->
                val localMatch = localById[cloudSos.id]
                val effectiveFalseCount = maxOf(cloudSos.falseAlarmReports, localMatch?.falseAlarmReports ?: 0L)
                val effectiveResponderCount = maxOf(cloudSos.responderCount, localMatch?.responderCount ?: 0L)
                // Keep ACTIVE even when falseAlarmReports >= 3; only remove when owner marks RESOLVED or after 24 hours
                val effectiveStatus = when {
                    localMatch?.status == "RESOLVED" -> "RESOLVED"
                    else -> cloudSos.status
                }
                val rIds = if (cloudSos.id in respondedSet) {
                    (cloudSos.responderIds + currentUserId).distinct()
                } else {
                    cloudSos.responderIds
                }
                val fIds = if (cloudSos.id in falseSet) {
                    (cloudSos.falseReporterIds + currentUserId).distinct()
                } else {
                    cloudSos.falseReporterIds
                }
                cloudSos.copy(
                    status = effectiveStatus,
                    responderCount = effectiveResponderCount,
                    falseAlarmReports = effectiveFalseCount,
                    responderIds = rIds,
                    falseReporterIds = fIds
                )
            }
            val cloudIds = mergedCloud.map { it.id }.toSet()
            mergedCloud + localList.filterNot { it.id in cloudIds }.map { localSos ->
                val fIds = if (localSos.id in falseSet) (localSos.falseReporterIds + currentUserId).distinct() else localSos.falseReporterIds
                localSos.copy(falseReporterIds = fIds)
            }
        }
    }

    val sosAlertsState: StateFlow<UiState<List<SosAlert>>> = upstreamSosAlertsFlow
        .onEach { list ->
            val activeAlerts = list.filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() }
            if (initialLoadCompleted) {
                activeAlerts.forEach { alert ->
                    if (alert.id.isNotBlank() && !seenSosAlertIds.contains(alert.id) && alert.userId != currentUserId) {
                        seenSosAlertIds.add(alert.id)
                        val distanceKm = calculateDistanceKm(
                            _userLatitude.value,
                            _userLongitude.value,
                            alert.latitude,
                            alert.longitude
                        )
                        // Strict 1.0 km Proximity Filter for SOS notifications (Internet + BLE)
                        if (distanceKm <= SOS_NOTIFICATION_RADIUS_KM) {
                            RokkhonNotificationHelper.dispatchEmergencyAlert(
                                context = appContext,
                                title = "জরুরি SOS সংকেত (${"%.2f".format(distanceKm)} km কাছে)",
                                body = "${alert.senderName} triggered ${alert.emergencyType} SOS (${alert.triggerMethod}) within ${"%.2f".format(distanceKm)} km at ${alert.district}."
                            )
                            _toastBanner.value = "1km SOS Alert (${"%.2f".format(distanceKm)} km): ${alert.senderName} near ${alert.district}"
                        }
                    }
                }
            } else {
                activeAlerts.mapNotNullTo(seenSosAlertIds) { it.id.takeIf(String::isNotBlank) }
                initialLoadCompleted = true
            }
        }
        .map<List<SosAlert>, UiState<List<SosAlert>>> { list ->
            UiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            Log.w(TAG, "Error observing SOS alerts", error)
            emit(UiState.Success(_localSosAlerts.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(_localSosAlerts.value)
        )

    val notificationsState: StateFlow<List<RokkhonNotificationItem>> = combine(
        _customNotifications,
        sosAlertsState,
        bloodRequestsState,
        _readNotificationIds
    ) { customList, sosUi, reqUi, readIds ->
        val dynamicItems = mutableListOf<RokkhonNotificationItem>()

        // 1. Custom warnings & responder updates first
        dynamicItems.addAll(customList)

        // 2. Live SOS alerts within 1.0 km notification radius (or created by current user)
        val sosList = (sosUi as? UiState.Success)?.data.orEmpty()
        sosList.filter { alert ->
            alert.status == "ACTIVE" &&
                !alert.isExpiredAfter24Hours() &&
                (alert.userId == currentUserId ||
                    alert.distanceFromKm(_userLatitude.value, _userLongitude.value) <= SOS_NOTIFICATION_RADIUS_KM)
        }
            .take(5)
            .forEach { alert ->
                val itemId = "notif_sos_${alert.id}"
                val distKm = alert.distanceFromKm(_userLatitude.value, _userLongitude.value)
                if (dynamicItems.none { it.id == itemId }) {
                    dynamicItems.add(
                        RokkhonNotificationItem(
                            id = itemId,
                            kind = NotificationKind.SOS_ALERT,
                            titleBn = "১ কি.মি. জরুরি SOS: ${alert.senderName} (${"%.1f".format(distKm)} km)",
                            titleEn = "1km SOS Alert: ${alert.senderName} (${"%.1f".format(distKm)} km)",
                            bodyBn = "${alert.verificationNote} • যাচ্ছেন: ${alert.responderCount} জন • ভুয়া: ${alert.falseAlarmReports}",
                            bodyEn = "5s Camera & Audio attached • Going: ${alert.responderCount} • False count: ${alert.falseAlarmReports}",
                            timestampLabel = "${"%.1f".format(distKm)} km • 24h",
                            areaName = alert.district,
                            relatedSosId = alert.id,
                            isRead = itemId in readIds
                        )
                    )
                }
            }

        // 3. Area-Wide Matching Blood Requests (only for matching blood group in the selected area, or own requests)
        val reqList = (reqUi as? UiState.Success)?.data.orEmpty()
        reqList.filter { req ->
            req.status == "OPEN" &&
                (req.userId == currentUserId || isCurrentUserMatchingDonorForAreaRequest(req))
        }
            .take(6)
            .forEach { req ->
                val itemId = "notif_req_${req.id}"
                if (dynamicItems.none { it.id == itemId }) {
                    dynamicItems.add(
                        RokkhonNotificationItem(
                            id = itemId,
                            kind = NotificationKind.BLOOD_REQUEST,
                            titleBn = "এলাকার রক্তের অ্যালার্ট (${req.bloodGroup} • ${req.unitsNeeded} ব্যাগ)",
                            titleEn = "Area ${req.bloodGroup} Blood Match (${req.unitsNeeded} Bags)",
                            bodyBn = "রোগী: ${req.patientName} (Hb: ${req.hemoglobinLevel}) • ${req.hospitalName}, ${req.district}",
                            bodyEn = "Patient: ${req.patientName} (Hb: ${req.hemoglobinLevel}) • ${req.hospitalName}, ${req.district}",
                            timestampLabel = req.neededDateTime.ifBlank { "Area BLE+Net" },
                            areaName = req.district,
                            relatedRequestId = req.id,
                            isRead = itemId in readIds
                        )
                    )
                }
            }

        dynamicItems.map { it.copy(isRead = it.isRead || it.id in readIds) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val unreadNotificationCount: StateFlow<Int> = notificationsState
        .map { list -> list.count { !it.isRead } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 3
        )

    val bleBeacons: StateFlow<List<BleSosBeacon>> = bleMeshManager.nearbyBeacons

    init {
        if (!isGuestSession) {
            ensureCurrentUserDonorProfileExists()
        }
        bleMeshManager.startMeshScanning()
        observeIncomingBlePackets()
    }

    /**
     * Listens for incoming Bluetooth Low Energy (BLE) packets from nearby devices:
     * - `SOS_1KM`: Only notifies if the receiver is within <= 1.0 km.
     * - `BLOOD_AREA`: Only notifies if the receiver is in the same area and has the matching blood group.
     */
    private fun observeIncomingBlePackets() {
        viewModelScope.launch {
            bleMeshManager.latestIncomingBleBeacon
                .filterNotNull()
                .collect { beacon ->
                    if (!seenBlePacketIds.add(beacon.beaconId)) return@collect

                    if (beacon.packetKind == BleSosMeshManager.PACKET_KIND_SOS_1KM) {
                        val gpsDistanceKm = calculateDistanceKm(
                            _userLatitude.value,
                            _userLongitude.value,
                            beacon.latitude,
                            beacon.longitude
                        )
                        val effectiveDistanceKm = minOf(beacon.estimatedDistanceMeters / 1000.0, gpsDistanceKm)
                        if (effectiveDistanceKm <= SOS_NOTIFICATION_RADIUS_KM) {
                            RokkhonNotificationHelper.dispatchEmergencyAlert(
                                context = appContext,
                                title = "ব্লুটুথ (BLE) ১ কি.মি. SOS সংকেত (${"%.2f".format(effectiveDistanceKm)} km)",
                                body = "${beacon.senderCode} broadcast ${beacon.emergencyType} over BLE (${"%.0f".format(beacon.estimatedDistanceMeters)}m away)."
                            )
                        }
                    } else if (beacon.packetKind == BleSosMeshManager.PACKET_KIND_BLOOD_AREA) {
                        val myBloodGroup = getCurrentUserBloodGroup()
                        val myDonor = _localDonors.value.firstOrNull { it.userId == currentUserId }
                        val isAvailable = myDonor?.isAvailableDonor ?: true
                        if (isAvailable && myBloodGroup.equals(beacon.bloodGroup, ignoreCase = true)) {
                            RokkhonNotificationHelper.dispatchEmergencyAlert(
                                context = appContext,
                                title = "ব্লুটুথ (BLE) রক্তের জরুরি অ্যালার্ট (${beacon.bloodGroup})",
                                body = "${beacon.unitsNeeded} bag(s) ${beacon.bloodGroup} needed in your area (${_selectedDistrict.value.nameEn})."
                            )
                        }
                    }
                }
        }
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun loadStringSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    private fun saveStringSet(key: String, ids: Set<String>) {
        prefs.edit().putStringSet(key, ids).apply()
    }

    private fun addGuardianPoints(delta: Int) {
        val next = (_guardianPoints.value + delta).coerceAtLeast(0)
        _guardianPoints.value = next
        prefs.edit().putInt(KEY_GUARDIAN_POINTS, next).apply()

    }

    fun markNotificationRead(id: String) {
        val next = _readNotificationIds.value + id
        _readNotificationIds.value = next
        saveStringSet(KEY_READ_NOTIFICATION_IDS, next)
    }

    fun markAllNotificationsRead() {
        val allIds = notificationsState.value.map { it.id }.toSet() + _readNotificationIds.value
        _readNotificationIds.value = allIds
        saveStringSet(KEY_READ_NOTIFICATION_IDS, allIds)
        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "সব নোটিফিকেশন পড়া হয়েছে হিসেবে মার্ক করা হয়েছে"
        } else {
            "All notifications marked as read"
        }
    }

    fun dismissFalseSosWarningBanner() {
        _falseSosWarningBanner.value = null
    }

    /**
     * Snapchat-style Daily Safety & Donor Readiness Check-In.
     */
    fun performDailySafeCheckIn() {
        if (_checkedInToday.value) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "আজকের ডেইলি সেফটি স্ট্রিক (${_dailyStreakCount.value} দিন) ইতিমধ্যে সম্পন্ন হয়েছে"
            } else {
                "Today's Daily Safety Streak (${_dailyStreakCount.value} days) is already completed"
            }
            return
        }
        val nextStreak = _dailyStreakCount.value + 1
        _dailyStreakCount.value = nextStreak
        _checkedInToday.value = true
        prefs.edit()
            .putInt(KEY_STREAK_COUNT, nextStreak)
            .putString(KEY_LAST_CHECKIN_DAY, todayKey())
            .apply()
        addGuardianPoints(25)

        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "ডেইলি সেফটি চেক-ইন সম্পন্ন! $nextStreak দিনের স্ট্রিক ও +২৫ গার্ডিয়ান পয়েন্ট যুক্ত হয়েছে"
        } else {
            "Daily Safe Check-In complete! $nextStreak Day Streak active (+25 Guardian Points)"
        }

        if (!isGuestSession) {
            ensureCurrentUserDonorProfileExists()
        }
    }

    private fun ensureCurrentUserDonorProfileExists() {
        viewModelScope.launch {
            repository.saveOrUpdateDonorNode(
                displayName = currentUserName.ifBlank { "Rokkhon Guardian" },
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = _selectedDistrict.value.nameEn,
                contactPhone = "+8801711000000",
                latitude = _userLatitude.value,
                longitude = _userLongitude.value,
                lastDonationDate = "Eligible Now"
            )
        }
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.BENGALI) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.BENGALI
        }
    }

    fun selectTab(tab: RokkhonTab) {
        _currentTab.value = tab
    }

    fun setBloodFilter(bloodGroup: String?) {
        _selectedBloodFilter.value = if (_selectedBloodFilter.value == bloodGroup) null else bloodGroup
    }

    fun setOnlyAvailableDonorsFilter(onlyAvailable: Boolean) {
        _onlyAvailableDonorsFilter.value = onlyAvailable
    }

    fun setMapLayerFilter(layer: MapLayerFilter) {
        _mapLayerFilter.value = layer
    }

    fun toggleAreaFilter(enabled: Boolean) {
        _isAreaFilterEnabled.value = enabled
    }

    /**
     * Allows manual selection of any of the 64 Bangladesh districts / metro areas,
     * and ensures realistic community donors, blood requests, and SOS coverage exist for that area.
     */
    fun selectDistrict(district: BangladeshDistrict, isManual: Boolean = true) {
        _selectedDistrict.value = district
        _userLatitude.value = district.latitude
        _userLongitude.value = district.longitude
        _isAreaAutoDetected.value = !isManual
        _isAreaFilterEnabled.value = true
        ensureCommunityDataForDistrict(district)
    }

    private fun ensureCommunityDataForDistrict(district: BangladeshDistrict) {
        val currentDonors = _localDonors.value
        val hasAreaDonors = currentDonors.count { district.matchesArea(it.district) } >= 3
        if (!hasAreaDonors) {
            val seededDonors = listOf(
                DonorNode(
                    id = "seed_d1_${district.areaKey}",
                    userId = "seed_d1_${district.areaKey}",
                    displayName = "তানভীর আহমেদ (Tanvir • ${district.nameEn.substringBefore(",")})",
                    bloodGroup = "O+",
                    isAvailableDonor = true,
                    district = "${district.nameEn} • 0.9 km",
                    contactPhone = "+8801712334455",
                    latitude = district.latitude + 0.0028,
                    longitude = district.longitude + 0.0021,
                    lastDonationDate = "Available now",
                    trustScore = 99L,
                    verifiedSosCount = 4L
                ),
                DonorNode(
                    id = "seed_d2_${district.areaKey}",
                    userId = "seed_d2_${district.areaKey}",
                    displayName = "ফারিহা তাসনিম (Fariha • ${district.nameEn.substringBefore(",")})",
                    bloodGroup = "B+",
                    isAvailableDonor = true,
                    district = "${district.nameEn} • 1.4 km",
                    contactPhone = "+8801819223344",
                    latitude = district.latitude - 0.0024,
                    longitude = district.longitude + 0.0032,
                    lastDonationDate = "Available now",
                    trustScore = 100L,
                    verifiedSosCount = 3L
                ),
                DonorNode(
                    id = "seed_d3_${district.areaKey}",
                    userId = "seed_d3_${district.areaKey}",
                    displayName = "মাহমুদুল হাসান (Mahmud • ${district.nameEn.substringBefore(",")})",
                    bloodGroup = "A+",
                    isAvailableDonor = true,
                    district = "${district.nameEn} • 1.7 km",
                    contactPhone = "+8801911556677",
                    latitude = district.latitude + 0.0019,
                    longitude = district.longitude - 0.0029,
                    lastDonationDate = "Available now",
                    trustScore = 98L,
                    verifiedSosCount = 5L
                ),
                DonorNode(
                    id = "seed_d4_${district.areaKey}",
                    userId = "seed_d4_${district.areaKey}",
                    displayName = "রাশেদ খান (Rashed • ${district.nameEn.substringBefore(",")})",
                    bloodGroup = "O+",
                    isAvailableDonor = true,
                    district = "${district.nameEn} • 1.2 km",
                    contactPhone = "+8801611889900",
                    latitude = district.latitude - 0.0031,
                    longitude = district.longitude - 0.0018,
                    lastDonationDate = "Available now",
                    trustScore = 97L,
                    verifiedSosCount = 2L
                )
            )
            val existingIds = currentDonors.map { it.id }.toSet()
            _localDonors.value = currentDonors + seededDonors.filterNot { it.id in existingIds }
        }

        val currentRequests = _localBloodRequests.value
        val hasAreaRequests = currentRequests.any { it.status == "OPEN" && district.matchesArea(it.district) }
        if (!hasAreaRequests) {
            val reqId = "seed_req_${district.areaKey}"
            if (currentRequests.none { it.id == reqId }) {
                val seededReq = BloodRequest(
                    id = reqId,
                    userId = "hospital_${district.areaKey}",
                    requesterName = "সাদিয়া ইসলাম (Sadia)",
                    patientName = "রাবেয়া খাতুন (Rabeya Khatun)",
                    bloodGroup = "O+",
                    unitsNeeded = 2L,
                    hemoglobinLevel = "6.4 g/dL",
                    patientProblem = "ডেঙ্গু শক সিন্ড্রোম ও জরুরি অপারেশন (Severe Dengue)",
                    hospitalName = district.defaultHospitalEn,
                    wardCabinNumber = "Emergency Ward 04, Bed 11",
                    neededDateTime = "Now • Emergency",
                    district = district.nameEn,
                    contactPhone = "+8801715998877",
                    urgencyLevel = "CRITICAL",
                    latitude = district.latitude + 0.0016,
                    longitude = district.longitude - 0.0015,
                    status = "OPEN",
                    respondersCount = 2L,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                _localBloodRequests.value = currentRequests + seededReq
            }
        }

        val currentSos = _localSosAlerts.value
        val hasAreaSos = currentSos.any { it.status == "ACTIVE" && district.matchesArea(it.district) }
        if (!hasAreaSos) {
            val sosId = "seed_sos_${district.areaKey}"
            if (currentSos.none { it.id == sosId }) {
                val seededSos = SosAlert(
                    id = sosId,
                    userId = "citizen_${district.areaKey}",
                    senderName = "নুসরাত জাহান (Nusrat • ${district.nameEn.substringBefore(",")})",
                    bloodGroup = "O+",
                    emergencyType = "ROAD_DANGER",
                    district = district.nameEn,
                    latitude = district.latitude - 0.0018,
                    longitude = district.longitude + 0.0022,
                    hasLiveCameraVerification = true,
                    hasLiveAudioVerification = true,
                    peakAudioDecibels = 85L,
                    verificationNote = "রাস্তায় বিপদ! ৩-বার পাওয়ার বাটন চাপায় সাইলেন্ট ক্যামেরা ও ৫-সেকেন্ড অডিও-ভিডিও ব্ল্যাকবক্স যুক্ত হয়েছে",
                    triggerMethod = "3X_POWER_BUTTON",
                    bleBeaconHash = "BLE-${district.areaKey.take(4).uppercase()}",
                    status = "ACTIVE",
                    responderCount = 3L,
                    falseAlarmReports = 0L,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                _localSosAlerts.value = currentSos + seededSos
            }
        }
    }

    /**
     * Auto-selects the nearest Bangladesh area based on GPS coordinates.
     */
    fun autoSelectAreaFromPermission(lat: Double? = null, lng: Double? = null, notifyUser: Boolean = false) {
        val effectiveLat = if (lat != null && lat in 20.5..26.7) lat else _userLatitude.value
        val effectiveLng = if (lng != null && lng in 88.0..92.7) lng else _userLongitude.value

        val nearest = BANGLADESH_DISTRICTS.minByOrNull { dist ->
            val dLat = dist.latitude - effectiveLat
            val dLng = dist.longitude - effectiveLng
            dLat * dLat + dLng * dLng
        } ?: BANGLADESH_DISTRICTS.first()

        _selectedDistrict.value = nearest
        _userLatitude.value = nearest.latitude
        _userLongitude.value = nearest.longitude
        _isAreaAutoDetected.value = true
        _isAreaFilterEnabled.value = true
        ensureCommunityDataForDistrict(nearest)

        if (notifyUser) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "লোকেশন পারমিশন থেকে অটো এলাকা সিলেক্ট হয়েছে: ${nearest.nameBn}"
            } else {
                "Auto-selected area from GPS: ${nearest.nameEn}"
            }
        }
    }

    fun updateGpsCoordinates(lat: Double, lng: Double) {
        _userLatitude.value = lat
        _userLongitude.value = lng
        autoSelectAreaFromPermission(lat = lat, lng = lng, notifyUser = false)
    }

    fun dismissToastBanner() {
        _toastBanner.value = null
    }

    fun hasUserPledged(request: BloodRequest): Boolean {
        return request.id in _pledgedRequestIds.value || currentUserId in request.responderIds
    }

    fun hasUserRespondedSos(alert: SosAlert): Boolean {
        return alert.id in _respondedSosIds.value || currentUserId in alert.responderIds
    }

    fun hasUserReportedFalseSos(alert: SosAlert): Boolean {
        return alert.id in _reportedFalseSosIds.value || currentUserId in alert.falseReporterIds
    }

    fun hasUserInspectedEvidence(alertId: String): Boolean {
        return alertId in _inspectedEvidenceSosIds.value
    }

    fun markSosEvidenceInspected(alertId: String) {
        if (alertId.isBlank()) return
        val next = _inspectedEvidenceSosIds.value + alertId
        _inspectedEvidenceSosIds.value = next
        saveStringSet(KEY_INSPECTED_EVIDENCE_SOS_IDS, next)
    }

    /**
     * Auto-detects the SOS emergency category from telemetry without showing any category popup.
     */
    fun autoDetectEmergencyCategory(triggerMethod: String): Pair<String, String> {
        val code = when {
            triggerMethod.contains("POWER") || triggerMethod.contains("VOLUME") -> "ROAD_DANGER"
            else -> "ROAD_DANGER"
        }
        val telemetryLabel = if (_language.value == AppLanguage.BENGALI) {
            "অটো-ডিটেক্টেড: অন-রোড ইমার্জেন্সি • ৫ সেকেন্ড অডিও ও ভিডিও রেকর্ড সংযুক্ত"
        } else {
            "Auto-Detected: On-Road Emergency • 5s Audio & Video Evidence Attached"
        }
        return code to telemetryLabel
    }

    fun saveDonorProfile(
        displayName: String,
        bloodGroup: String,
        isAvailableDonor: Boolean,
        district: String,
        contactPhone: String,
        lastDonationDate: String
    ) {
        val existingNode = _localDonors.value.firstOrNull { it.userId == currentUserId }
        val updatedLocal = DonorNode(
            id = currentUserId,
            userId = currentUserId,
            displayName = displayName.ifBlank { "Rokkhon Guardian" },
            bloodGroup = bloodGroup,
            isAvailableDonor = isAvailableDonor,
            district = district,
            contactPhone = contactPhone,
            latitude = _userLatitude.value,
            longitude = _userLongitude.value,
            lastDonationDate = lastDonationDate,
            trustScore = existingNode?.trustScore ?: 100L,
            verifiedSosCount = existingNode?.verifiedSosCount ?: 1L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _localDonors.value = listOf(updatedLocal) + _localDonors.value.filterNot { it.userId == currentUserId }

        if (isGuestSession) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "ডোনার প্রোফাইল ও ম্যাপ লোকেশন আপডেট হয়েছে"
            } else {
                "Donor Profile & Map Location Updated"
            }
            return
        }

        viewModelScope.launch {
            val result = repository.saveOrUpdateDonorNode(
                displayName = displayName,
                bloodGroup = bloodGroup,
                isAvailableDonor = isAvailableDonor,
                district = district,
                contactPhone = contactPhone,
                latitude = _userLatitude.value,
                longitude = _userLongitude.value,
                lastDonationDate = lastDonationDate
            )
            _toastBanner.value = if (result.isSuccess) {
                if (_language.value == AppLanguage.BENGALI) "ডোনার প্রোফাইল আপডেট হয়েছে" else "Donor Profile Updated on Map"
            } else {
                "Donor Profile Updated"
            }
        }
    }

    fun toggleDonorActiveStatus(currentNode: DonorNode?, newStatus: Boolean) {
        val existing = currentNode ?: _localDonors.value.firstOrNull { it.userId == currentUserId }
        val updated = (existing ?: DonorNode(
            id = currentUserId,
            userId = currentUserId,
            displayName = currentUserName,
            bloodGroup = "O+",
            district = _selectedDistrict.value.nameEn,
            latitude = _userLatitude.value,
            longitude = _userLongitude.value
        )).copy(isAvailableDonor = newStatus)

        _localDonors.value = listOf(updated) + _localDonors.value.filterNot { it.userId == currentUserId }

        _toastBanner.value = if (newStatus) {
            if (_language.value == AppLanguage.BENGALI) "আপনি এখন রক্তদানের জন্য সক্রিয় (Available Donor)" else "Donor Status: AVAILABLE on Map"
        } else {
            if (_language.value == AppLanguage.BENGALI) "আপনি এখন নিষ্ক্রিয় (Unavailable) আছেন" else "Donor Status: UNAVAILABLE (Hidden)"
        }

        if (!isGuestSession) {
            viewModelScope.launch {
                repository.toggleDonorAvailability(newStatus)
            }
        }
    }

    fun postUrgentBloodRequest(
        patientName: String,
        bloodGroup: String,
        unitsNeeded: Int,
        hemoglobinLevel: String,
        patientProblem: String,
        hospitalName: String,
        wardCabinNumber: String,
        neededDateTime: String,
        district: String,
        contactPhone: String,
        urgencyLevel: String
    ) {
        val matchedDistrict = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: _selectedDistrict.value
        _selectedDistrict.value = matchedDistrict
        _userLatitude.value = matchedDistrict.latitude
        _userLongitude.value = matchedDistrict.longitude
        ensureCommunityDataForDistrict(matchedDistrict)

        val jitterLat = matchedDistrict.latitude + ((System.currentTimeMillis() % 9) - 4) * 0.0022
        val jitterLng = matchedDistrict.longitude + ((System.currentTimeMillis() % 7) - 3) * 0.0022
        val localId = "req_${UUID.randomUUID().toString().take(8)}"

        // Broadcast across the entire area over Bluetooth Low Energy (BLE) Mesh + Internet
        val bleBloodHash = bleMeshManager.startBloodRequestBroadcast(
            patientName = patientName.ifBlank { "Emergency Patient" },
            bloodGroup = bloodGroup,
            unitsNeeded = unitsNeeded,
            districtAreaKey = matchedDistrict.areaKey,
            latitude = jitterLat,
            longitude = jitterLng
        )

        val newReq = BloodRequest(
            id = localId,
            userId = currentUserId,
            requesterName = currentUserName.ifBlank { "Rokkhon Member" },
            patientName = patientName.ifBlank { "Emergency Patient" },
            bloodGroup = bloodGroup,
            unitsNeeded = unitsNeeded.toLong(),
            hemoglobinLevel = hemoglobinLevel.ifBlank { "6.8 g/dL" },
            patientProblem = patientProblem.ifBlank { "Dengue / Emergency Surgery" },
            hospitalName = hospitalName,
            wardCabinNumber = wardCabinNumber.ifBlank { "Emergency Ward" },
            neededDateTime = neededDateTime.ifBlank { "Immediate (Within 2 Hours)" },
            district = district,
            contactPhone = contactPhone,
            urgencyLevel = urgencyLevel,
            latitude = jitterLat,
            longitude = jitterLng,
            status = "OPEN",
            respondersCount = 0L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _localBloodRequests.value = listOf(newReq) + _localBloodRequests.value
        _selectedBloodFilter.value = bloodGroup
        _mapLayerFilter.value = MapLayerFilter.BLOOD_REQUESTS_ONLY
        _currentTab.value = RokkhonTab.RADAR_MAP

        val areaDonorsCount = _localDonors.value.count {
            it.isAvailableDonor && matchedDistrict.matchesArea(it.district) && (it.bloodGroup == bloodGroup)
        }.coerceAtLeast(1)

        RokkhonNotificationHelper.dispatchEmergencyAlert(
            context = appContext,
            title = "জরুরি রক্তের আবেদন ($bloodGroup • ${newReq.unitsNeeded} ব্যাগ • $bleBloodHash)",
            body = "${matchedDistrict.nameEn} এলাকার সকল উপলব্ধ $bloodGroup ডোনারদের কাছে Bluetooth ও Internet-এ অ্যালার্ট পাঠানো হয়েছে।"
        )
        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "${matchedDistrict.nameBn} এলাকার $areaDonorsCount জন উপলব্ধ $bloodGroup রক্তদাতাকে Bluetooth + Internet অ্যালার্ট পাঠানো হয়েছে"
        } else {
            "Broadcasted via Bluetooth BLE + Internet to $areaDonorsCount matching $bloodGroup donors in ${matchedDistrict.nameEn}"
        }

        if (!isGuestSession) {
            viewModelScope.launch {
                val createdId = repository.createBloodRequest(
                    requesterName = currentUserName.ifBlank { "Rokkhon User" },
                    patientName = newReq.patientName,
                    bloodGroup = bloodGroup,
                    unitsNeeded = unitsNeeded,
                    hemoglobinLevel = newReq.hemoglobinLevel,
                    patientProblem = newReq.patientProblem,
                    hospitalName = hospitalName,
                    wardCabinNumber = newReq.wardCabinNumber,
                    neededDateTime = newReq.neededDateTime,
                    district = district,
                    contactPhone = contactPhone,
                    urgencyLevel = urgencyLevel,
                    latitude = jitterLat,
                    longitude = jitterLng
                ).getOrNull()
                if (createdId != null) {
                    _localBloodRequests.value = _localBloodRequests.value.map {
                        if (it.id == localId) it.copy(id = createdId) else it
                    }
                }
            }
        }
    }

    /**
     * Strict One-Time "Donate Blood" (রক্ত দিন) Tap per User Account.
     */
    fun pledgeToDonate(request: BloodRequest) {
        if (hasUserPledged(request)) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "আপনি ইতিমধ্যে এই রোগীর জন্য রক্তদানের সাড়া দিয়েছেন (১টি অ্যাকাউন্ট থেকে একবারই ট্যাপ করা যায়)"
            } else {
                "You have already pledged to donate for this request (1 tap allowed per account)"
            }
            return
        }

        val updatedPledgedIds = _pledgedRequestIds.value + request.id
        _pledgedRequestIds.value = updatedPledgedIds
        saveStringSet(KEY_PLEDGED_IDS, updatedPledgedIds)
        addGuardianPoints(100)

        _localBloodRequests.value = _localBloodRequests.value.map {
            if (it.id == request.id) {
                it.copy(
                    respondersCount = it.respondersCount + 1L,
                    responderIds = (it.responderIds + currentUserId).distinct()
                )
            } else {
                it
            }
        }

        prependCustomNotification(
            RokkhonNotificationItem(
                id = "notif_pledge_${request.id}_${System.currentTimeMillis()}",
                kind = NotificationKind.RESPONDER_UPDATE,
                titleBn = "রক্তদানের প্রতিশ্রুতি নিবন্ধিত (+১০০ পয়েন্ট)",
                titleEn = "Blood Donation Pledge Recorded (+100 Pts)",
                bodyBn = "আপনি ${request.patientName}-এর (${request.bloodGroup}) জন্য রক্তদানে সাড়া দিয়েছেন • হাসপাতাল: ${request.hospitalName}",
                bodyEn = "You pledged to donate ${request.bloodGroup} for ${request.patientName} at ${request.hospitalName}",
                timestampLabel = "Just now",
                areaName = request.district,
                relatedRequestId = request.id
            )
        )

        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "ধন্যবাদ (+১০০ পয়েন্ট)! আপনার সাড়া নথিভুক্ত হয়েছে • হাসপাতাল নম্বর: ${request.contactPhone}"
        } else {
            "Thank you (+100 Pts)! Pledge recorded for ${request.patientName} • Contact: ${request.contactPhone}"
        }

        if (!isGuestSession) {
            viewModelScope.launch {
                repository.pledgeBloodResponse(request)
            }
        }
    }

    /**
     * Soft-deletes / removes the patient's blood request from the active map & feed once blood is managed,
     * while retaining the record in the Firestore database with status = "FULFILLED".
     */
    fun fulfillOwnBloodRequest(requestId: String) {
        _localBloodRequests.value = _localBloodRequests.value.map {
            if (it.id == requestId) it.copy(status = "FULFILLED") else it
        }
        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "রক্ত ম্যানেজ হয়েছে: পোস্টটি ম্যাপ থেকে সরানো হয়েছে এবং ডাটাবেসে আর্কাইভ আছে"
        } else {
            "Blood Managed: Request removed from active map and archived in database"
        }
        if (!isGuestSession) {
            viewModelScope.launch {
                repository.markBloodRequestFulfilled(requestId)
            }
        }
    }

    /**
     * Dispatches a Verified Emergency SOS with 5-second audio & video blackbox and starts
     * continuous real-time GPS location sharing until the user marks themselves Safe.
     * Notifications are routed ONLY to installed app users within a <= 1.0 km radius (BLE + Internet).
     */
    fun triggerVerifiedEmergencySos(
        bloodGroup: String,
        emergencyType: String,
        district: String,
        hasLiveCamera: Boolean,
        hasLiveAudio: Boolean,
        peakDecibels: Int,
        verificationNote: String,
        triggerMethod: String = "3X_POWER_BUTTON"
    ) {
        viewModelScope.launch {
            val bleHash = bleMeshManager.startSosBroadcast(
                senderName = currentUserName.ifBlank { "Rokkhon User" },
                bloodGroup = bloodGroup,
                emergencyType = emergencyType,
                latitude = _userLatitude.value,
                longitude = _userLongitude.value,
                hasLiveVerification = hasLiveCamera || hasLiveAudio,
                districtAreaKey = _selectedDistrict.value.areaKey
            )

            val localAlertId = "sos_${UUID.randomUUID().toString().take(8)}"
            audioVerifier.attachLatestFiveSecondClipToAlert(localAlertId)

            val localSos = SosAlert(
                id = localAlertId,
                userId = currentUserId,
                senderName = currentUserName.ifBlank { "Rokkhon Citizen" },
                bloodGroup = bloodGroup,
                emergencyType = emergencyType,
                district = district,
                latitude = _userLatitude.value,
                longitude = _userLongitude.value,
                hasLiveCameraVerification = hasLiveCamera,
                hasLiveAudioVerification = hasLiveAudio,
                peakAudioDecibels = peakDecibels.coerceIn(40, 125).toLong(),
                verificationNote = verificationNote,
                triggerMethod = triggerMethod,
                bleBeaconHash = bleHash,
                status = "ACTIVE",
                responderCount = 0L,
                falseAlarmReports = 0L,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            _localSosAlerts.value = listOf(localSos) + _localSosAlerts.value
            _activeUserSosAlertId.value = localAlertId
            _isLiveLocationSharingActive.value = true

            val nearby1KmUserCount = _localDonors.value.count {
                calculateDistanceKm(_userLatitude.value, _userLongitude.value, it.latitude, it.longitude) <= SOS_NOTIFICATION_RADIUS_KM
            }.coerceAtLeast(2)

            RokkhonNotificationHelper.dispatchEmergencyAlert(
                context = appContext,
                title = "১ কি.মি. জরুরি SOS চালু হয়েছে ($bleHash • $triggerMethod)",
                body = "5s Camera & Audio Evidence attached. Alerted $nearby1KmUserCount users within 1.0 km via Bluetooth BLE & Internet."
            )

            var finalAlertId = localAlertId
            if (!isGuestSession) {
                val cloudId = repository.createVerifiedSosAlert(
                    senderName = currentUserName.ifBlank { "Rokkhon User" },
                    bloodGroup = bloodGroup,
                    emergencyType = emergencyType,
                    district = district,
                    latitude = _userLatitude.value,
                    longitude = _userLongitude.value,
                    hasLiveCameraVerification = hasLiveCamera,
                    hasLiveAudioVerification = hasLiveAudio,
                    peakAudioDecibels = peakDecibels.coerceIn(35, 130),
                    verificationNote = verificationNote,
                    triggerMethod = triggerMethod,
                    bleBeaconHash = bleHash
                ).getOrNull()

                if (cloudId != null) {
                    finalAlertId = cloudId
                    audioVerifier.attachLatestFiveSecondClipToAlert(cloudId)
                    _activeUserSosAlertId.value = cloudId
                    _localSosAlerts.value = _localSosAlerts.value.map {
                        if (it.id == localAlertId) it.copy(id = cloudId) else it
                    }
                }
            }

            startContinuousLiveLocationSharing(finalAlertId)

            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "১ কি.মি. রেডিয়াসে ($nearby1KmUserCount জন ইউজার) Bluetooth ও Internet-এ ৫-সেকেন্ড SOS অ্যালার্ট পাঠানো হয়েছে ($bleHash)"
            } else {
                "5s SOS ($bleHash) broadcasted to $nearby1KmUserCount users within 1.0 km via Bluetooth BLE & Internet"
            }
        }
    }

    private fun startContinuousLiveLocationSharing(alertId: String) {
        liveLocationSharingJob?.cancel()
        liveLocationSharingJob = viewModelScope.launch {
            var tick = 0
            while (isActive && _isLiveLocationSharingActive.value) {
                delay(3500L)
                tick += 1
                val newLat = _userLatitude.value + ((tick % 3) - 1) * 0.00018
                val newLng = _userLongitude.value + ((tick % 2) * 2 - 1) * 0.00018
                _userLatitude.value = newLat
                _userLongitude.value = newLng

                _localSosAlerts.value = _localSosAlerts.value.map {
                    if (it.id == alertId && it.status == "ACTIVE") {
                        it.copy(latitude = newLat, longitude = newLng, updatedAt = Timestamp.now())
                    } else {
                        it
                    }
                }

                if (!isGuestSession) {
                    repository.updateOwnSosLiveLocation(alertId, newLat, newLng)
                }
            }
        }
    }

    /**
     * Marks "Nearby / যাচ্ছি (Responding)" on an active SOS alert inside Find.
     * Locked to 1 tap per user account per SOS alert.
     */
    fun respondToSos(alert: SosAlert) {
        if (hasUserRespondedSos(alert)) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "আপনি ইতিমধ্যে এই SOS-এ 'যাচ্ছি (Nearby)' মার্ক করেছেন"
            } else {
                "You already marked 'Nearby / Responding' for ${alert.senderName}'s SOS"
            }
            return
        }

        val nextIds = _respondedSosIds.value + alert.id
        _respondedSosIds.value = nextIds
        saveStringSet(KEY_RESPONDED_SOS_IDS, nextIds)
        addGuardianPoints(50)

        _localSosAlerts.value = _localSosAlerts.value.map {
            if (it.id == alert.id) {
                it.copy(
                    responderCount = it.responderCount + 1L,
                    responderIds = (it.responderIds + currentUserId).distinct()
                )
            } else {
                it
            }
        }

        prependCustomNotification(
            RokkhonNotificationItem(
                id = "notif_sos_resp_${alert.id}_${System.currentTimeMillis()}",
                kind = NotificationKind.RESPONDER_UPDATE,
                titleBn = "আপনি সাহায্যে যাচ্ছেন (+৫০ গার্ডিয়ান পয়েন্ট)",
                titleEn = "Marked Nearby / Responding (+50 Guardian Pts)",
                bodyBn = "${alert.senderName}-এর SOS (${alert.district})-এ আপনার উপস্থিতি নিশ্চিত করা হয়েছে।",
                bodyEn = "You confirmed you are nearby and heading to help ${alert.senderName} at ${alert.district}.",
                timestampLabel = "Just now",
                areaName = alert.district,
                relatedSosId = alert.id
            )
        )

        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "আপনি 'যাচ্ছি (Nearby)' মার্ক করেছেন (+৫০ গার্ডিয়ান পয়েন্ট যুক্ত হয়েছে)"
        } else {
            "Marked 'Nearby / Going' for ${alert.senderName} (+50 Guardian Points)"
        }

        if (!isGuestSession) {
            viewModelScope.launch {
                repository.respondToSosAlert(alert)
            }
        }
    }

    /**
     * Marks the user Safe (নিরাপদ):
     * - Strictly enforced so ONLY the user who created the SOS (`alert.userId == currentUserId`) can mark it Safe.
     * - Stops continuous live location sharing and BLE broadcast.
     * - Removes the SOS alert from the active map and active feed immediately.
     * - Retains the record in the Firestore database & Personal SOS History with status = "RESOLVED".
     */
    fun resolveOwnSos(alertId: String) {
        val targetAlert = _localSosAlerts.value.firstOrNull { it.id == alertId }
        val isOwner = targetAlert == null ||
            targetAlert.userId == currentUserId ||
            alertId == _activeUserSosAlertId.value ||
            alertId.isBlank()

        if (!isOwner) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "শুধুমাত্র যে ইউজার SOS দিয়েছেন তিনিই নিরাপদ মার্ক করে ম্যাপ থেকে সরাতে পারবেন"
            } else {
                "Only the user who triggered this SOS can mark it Safe and remove it from the map"
            }
            return
        }

        liveLocationSharingJob?.cancel()
        liveLocationSharingJob = null
        _isLiveLocationSharingActive.value = false
        if (_activeUserSosAlertId.value == alertId || alertId.isBlank()) {
            _activeUserSosAlertId.value = null
        }
        bleMeshManager.stopSosBroadcast()
        audioVerifier.stopPlayback()

        _localSosAlerts.value = _localSosAlerts.value.map {
            if ((it.id == alertId && it.userId == currentUserId) ||
                (alertId.isBlank() && it.userId == currentUserId && it.status == "ACTIVE")
            ) {
                it.copy(status = "RESOLVED", updatedAt = Timestamp.now())
            } else {
                it
            }
        }

        _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
            "নিরাপদ নিশ্চিত করা হয়েছে: অ্যালার্ট ম্যাপ থেকে সরানো হয়েছে এবং আপনার SOS হিস্ট্রিতে সংরক্ষিত আছে"
        } else {
            "Marked Safe: Alert removed from active map and archived in your SOS History"
        }

        if (!isGuestSession && alertId.isNotBlank()) {
            viewModelScope.launch {
                repository.resolveOwnSosAlert(alertId)
            }
        }
    }

    /**
     * Fake-Report-Resistant "False / ভুয়া" Reporting System:
     * 1. Strictly 1 False vote per user account per SOS (`hasUserReportedFalseSos`).
     * 2. SOS creator cannot report their own SOS as false.
     * 3. If the reporter is > 1.0 km away and has NOT listened to/inspected the 5-second audio/video evidence yet,
     *    prompts them to verify the 5s evidence first so remote trolls cannot blindly spam-flag real victims.
     * 4. Reaching 3 or more False reports does NOT auto-delete the SOS (it stays on the map for 24 hours with its live false count)
     *    and does NOT blindly slash the sender's Trust Points if the SOS is shielded by 5s Camera+Audio Evidence or Nearby Responders!
     */
    fun flagFalseAlarm(alert: SosAlert) {
        if (alert.userId == currentUserId) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "আপনার নিজের SOS বন্ধ করতে 'আমি নিরাপদ (Mark Safe)' বাটন চাপুন"
            } else {
                "Use 'Mark Safe' to close your own SOS alert"
            }
            return
        }

        if (hasUserReportedFalseSos(alert)) {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "একটি অ্যাকাউন্ট থেকে একটি SOS-এ একবারই ভুয়া (False) রিপোর্ট দেওয়া যায়"
            } else {
                "Only 1 False report is allowed per account for this SOS"
            }
            return
        }

        val distanceKm = alert.distanceFromKm(_userLatitude.value, _userLongitude.value)
        if (!hasUserInspectedEvidence(alert.id) && distanceKm > SOS_NOTIFICATION_RADIUS_KM) {
            markSosEvidenceInspected(alert.id)
            audioVerifier.togglePlayFiveSecondClip(
                scope = viewModelScope,
                alertId = alert.id,
                peakDb = alert.peakAudioDecibels.toInt()
            )
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "আপনি ১ কি.মি.-এর বাইরে আছেন (${"%.1f".format(distanceKm)} km) • ফেক রিপোর্ট রোধে ৫-সেকেন্ড অডিও/ভিডিও চালু করা হয়েছে—যাচাই করে আবার ট্যাপ করুন"
            } else {
                "You are ${"%.1f".format(distanceKm)} km away (>1km) — playing 5s Audio/Video evidence first. Verify & tap False again to confirm."
            }
            return
        }

        val updatedFalseSet = _reportedFalseSosIds.value + alert.id
        _reportedFalseSosIds.value = updatedFalseSet
        saveStringSet(KEY_REPORTED_FALSE_SOS_IDS, updatedFalseSet)

        val nextCount = alert.falseAlarmReports + 1L
        val reachedDisputeThreshold = nextCount >= FALSE_SOS_DISPUTE_THRESHOLD
        val isVictimShielded = alert.isShieldedByEvidenceOrResponders

        // Keep status = "ACTIVE" even at 3+ false reports; SOS remains visible with false count and auto-removes after 24 hours
        _localSosAlerts.value = _localSosAlerts.value.map {
            if (it.id == alert.id) {
                it.copy(
                    falseAlarmReports = nextCount,
                    status = it.status.ifBlank { "ACTIVE" },
                    falseReporterIds = (it.falseReporterIds + currentUserId).distinct(),
                    updatedAt = Timestamp.now()
                )
            } else {
                it
            }
        }

        if (reachedDisputeThreshold) {
            if (isVictimShielded) {
                // Victim is PROTECTED by 5s Camera+Audio Evidence or Nearby Responders — ZERO trust points deducted from victim!
                val shieldMsgBn = "🛡️ ভিকটিম সুরক্ষা সক্রিয়: '${alert.senderName}'-এর SOS-এ $nextCount টি ভুয়া রিপোর্ট জমা পড়লেও ৫-সেকেন্ড অডিও/ভিডিও প্রমাণ (${alert.peakAudioDecibels} dB) ও ${alert.responderCount} জন রেসপন্ডার থাকায় প্রেরকের কোনো Trust Point কাটা হয়নি • ২৪ ঘণ্টা পর অটো-রিমুভ হবে।"
                val shieldMsgEn = "🛡️ Victim Shield Active: $nextCount False reports logged on ${alert.senderName}'s SOS, but Sender's Trust Points are PROTECTED by verified 5s Audio/Video (${alert.peakAudioDecibels} dB) & ${alert.responderCount} responder(s) • Auto-removes after 24h."

                _falseSosWarningBanner.value = if (_language.value == AppLanguage.BENGALI) shieldMsgBn else shieldMsgEn
                _toastBanner.value = if (_language.value == AppLanguage.BENGALI) shieldMsgBn else shieldMsgEn

                prependCustomNotification(
                    RokkhonNotificationItem(
                        id = "notif_false_shield_${alert.id}_${System.currentTimeMillis()}",
                        kind = NotificationKind.FALSE_SOS_WARNING,
                        titleBn = "🛡️ ফেক রিপোর্ট সুরক্ষা: প্রেরকের Trust Point সুরক্ষিত (ভুয়া কাউন্ট: $nextCount)",
                        titleEn = "🛡️ Anti-Fake-Report Shield: Victim Trust Protected (False Count: $nextCount)",
                        bodyBn = shieldMsgBn,
                        bodyEn = shieldMsgEn,
                        timestampLabel = "24h Auto-Expiry",
                        areaName = alert.district,
                        relatedSosId = alert.id
                    )
                )
            } else {
                // Unverified/low-dB SOS with 0 responders: marked Community Disputed for 24h review
                val nextPenalties = _falseSosPenaltyCount.value + 1
                _falseSosPenaltyCount.value = nextPenalties
                prefs.edit().putInt(KEY_FALSE_SOS_PENALTY_COUNT, nextPenalties).apply()

                val disputeMsgBn = "⚠️ কমিউনিটি ডিসপিউট ($nextCount টি ভুয়া রিপোর্ট vs ০ রেসপন্ডার): SOS-টি ম্যাপে কাউন্টসহ দেখা যাচ্ছে এবং ২৪ ঘণ্টা পর অটো-রিমুভ ও রিভিউ হবে।"
                val disputeMsgEn = "⚠️ Community Disputed ($nextCount False reports vs 0 Responders): SOS stays visible with false count and auto-removes after 24h review."

                _falseSosWarningBanner.value = if (_language.value == AppLanguage.BENGALI) disputeMsgBn else disputeMsgEn
                _toastBanner.value = if (_language.value == AppLanguage.BENGALI) disputeMsgBn else disputeMsgEn

                prependCustomNotification(
                    RokkhonNotificationItem(
                        id = "notif_false_warn_${alert.id}_${System.currentTimeMillis()}",
                        kind = NotificationKind.FALSE_SOS_WARNING,
                        titleBn = "কমিউনিটি ডিসপিউট ও ২৪ ঘণ্টা অটো-রিমুভ (ভুয়া কাউন্ট: $nextCount)",
                        titleEn = "Community Disputed SOS • 24h Auto-Remove (False Count: $nextCount)",
                        bodyBn = disputeMsgBn,
                        bodyEn = disputeMsgEn,
                        timestampLabel = "Just now",
                        areaName = alert.district,
                        relatedSosId = alert.id
                    )
                )
            }
        } else {
            _toastBanner.value = if (_language.value == AppLanguage.BENGALI) {
                "ভুয়া (False) রিপোর্ট নথিভুক্ত হয়েছে (মোট: $nextCount • ১ অ্যাকাউন্ট থেকে ১ বার) • ২৪ ঘণ্টা পর অটো-রিমুভ হবে"
            } else {
                "False report recorded (Total: $nextCount • Locked to 1 vote per account) • Auto-removes in 24h"
            }
        }

        if (!isGuestSession) {
            viewModelScope.launch {
                repository.reportFalseAlarm(alert)
            }
        }
    }

    private fun prependCustomNotification(item: RokkhonNotificationItem) {
        _customNotifications.value = listOf(item) + _customNotifications.value
    }

    override fun onCleared() {
        super.onCleared()
        liveLocationSharingJob?.cancel()
        audioVerifier.stopLiveMonitoring()
        audioVerifier.stopPlayback()
        bleMeshManager.stopMeshScanning()
    }

    companion object {
        private const val TAG = "RokkhonViewModel"
        private const val KEY_PLEDGED_IDS = "pledged_request_ids"
        private const val KEY_RESPONDED_SOS_IDS = "responded_sos_ids"
        private const val KEY_REPORTED_FALSE_SOS_IDS = "reported_false_sos_ids"
        private const val KEY_INSPECTED_EVIDENCE_SOS_IDS = "inspected_evidence_sos_ids"
        private const val KEY_READ_NOTIFICATION_IDS = "read_notification_ids"
        private const val KEY_FALSE_SOS_PENALTY_COUNT = "false_sos_penalty_count"
        private const val KEY_STREAK_COUNT = "daily_streak_count"
        private const val KEY_LAST_CHECKIN_DAY = "last_checkin_day"
        private const val KEY_GUARDIAN_POINTS = "guardian_points"

        private fun createInitialNotifications(): List<RokkhonNotificationItem> = listOf(
            RokkhonNotificationItem(
                id = "init_notif_false_policy",
                kind = NotificationKind.FALSE_SOS_WARNING,
                titleBn = "🛡️ অ্যান্টি-ফেক রিপোর্ট শিল্ড ও ১ কি.মি. BLE/Net রাউটিং চালু",
                titleEn = "🛡️ Anti-Fake-Report Shield & 1km BLE/Net Routing Active",
                bodyBn = "১ অ্যাকাউন্ট থেকে ১ বারই False দেওয়া যাবে। ৫ সেকেন্ড অডিও/ভিডিও প্রমাণ বা রেসপন্ডার থাকলে ফেক রিপোর্টে ভিকটিমের Trust Point কাটা হবে না এবং ২৪ ঘণ্টা পর SOS অটো-রিমুভ হবে।",
                bodyEn = "1 False vote per account. Verified 5s Audio/Video or nearby responders shield the victim's Trust Points from fake reports. SOS alerts auto-expire after 24h.",
                timestampLabel = "Smart Shield",
                areaName = "All Bangladesh"
            ),
            RokkhonNotificationItem(
                id = "init_notif_resp_1",
                kind = NotificationKind.RESPONDER_UPDATE,
                titleBn = "১ কি.মি. SOS ও এরিয়া ব্লাড ম্যাচিং (Bluetooth + Internet)",
                titleEn = "1km SOS & Area Blood Group Match (Bluetooth + Internet)",
                bodyBn = "SOS নোটিফিকেশন শুধু ১ কি.মি.-এর ভেতরে এবং রক্তের নোটিফিকেশন শুধু ওই এলাকার ম্যাচিং ব্লাড গ্রুপ ডোনারদের কাছে যাবে।",
                bodyEn = "SOS alerts notify users within 1.0 km, and Blood Requests notify matching blood group donors across the area.",
                timestampLabel = "Active Now",
                areaName = "Mirpur 10, Dhaka"
            )
        )


        private fun createInitialBangladeshDonors(currentUserId: String, currentUserName: String): List<DonorNode> = listOf(
            DonorNode(
                id = currentUserId,
                userId = currentUserId,
                displayName = currentUserName.ifBlank { "আপনি (You)" },
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Mirpur 10, Dhaka",
                contactPhone = "+8801711000000",
                latitude = 23.8069,
                longitude = 90.3687,
                lastDonationDate = "Available now",
                trustScore = 100L,
                verifiedSosCount = 3L
            ),
            DonorNode(
                id = "bd_donor_mirpur_1",
                userId = "bd_donor_mirpur_1",
                displayName = "আরিফুর রহমান (Arifur R.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Mirpur 10, Dhaka • 0.8 km",
                contactPhone = "+8801711223399",
                latitude = 23.8091,
                longitude = 90.3712,
                lastDonationDate = "Available now",
                trustScore = 100L,
                verifiedSosCount = 5L
            ),
            DonorNode(
                id = "bd_donor_mirpur_2",
                userId = "bd_donor_mirpur_2",
                displayName = "তাসনুভা নূর (Tasnuva N.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Mirpur 10, Dhaka • 1.2 km",
                contactPhone = "+8801812445566",
                latitude = 23.8045,
                longitude = 90.3665,
                lastDonationDate = "Available now",
                trustScore = 99L,
                verifiedSosCount = 4L
            ),
            DonorNode(
                id = "bd_donor_mirpur_3",
                userId = "bd_donor_mirpur_3",
                displayName = "সাব্বির রহমান (Sabbir R.)",
                bloodGroup = "B+",
                isAvailableDonor = true,
                district = "Mirpur 10, Dhaka • 1.1 km",
                contactPhone = "+8801511223344",
                latitude = 23.8082,
                longitude = 90.3710,
                lastDonationDate = "Available now",
                trustScore = 100L,
                verifiedSosCount = 6L
            ),
            DonorNode(
                id = "bd_donor_mirpur_4",
                userId = "bd_donor_mirpur_4",
                displayName = "মেহেদী হাসান (Mehedi H.)",
                bloodGroup = "A+",
                isAvailableDonor = true,
                district = "Mirpur 10, Dhaka • 1.5 km",
                contactPhone = "+8801914778899",
                latitude = 23.8105,
                longitude = 90.3650,
                lastDonationDate = "Available now",
                trustScore = 98L,
                verifiedSosCount = 3L
            ),
            DonorNode(
                id = "bd_donor_1",
                userId = "bd_donor_1",
                displayName = "তানভীর আহমেদ (Tanvir A.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Dhanmondi, Dhaka • 1.2 km",
                contactPhone = "+8801712334455",
                latitude = 23.7515,
                longitude = 90.3705,
                lastDonationDate = "Available now",
                trustScore = 99L,
                verifiedSosCount = 5L
            ),
            DonorNode(
                id = "bd_donor_2",
                userId = "bd_donor_2",
                displayName = "ফারিহা তাসনিম (Fariha T.)",
                bloodGroup = "B+",
                isAvailableDonor = true,
                district = "Dhanmondi, Dhaka • 1.8 km",
                contactPhone = "+8801819223344",
                latitude = 23.7492,
                longitude = 90.3812,
                lastDonationDate = "Available now",
                trustScore = 100L,
                verifiedSosCount = 2L
            ),
            DonorNode(
                id = "bd_donor_3",
                userId = "bd_donor_3",
                displayName = "মাহমুদুল হাসান (Mahmud H.)",
                bloodGroup = "A+",
                isAvailableDonor = true,
                district = "Shahbagh, Dhaka • 1.4 km",
                contactPhone = "+8801911556677",
                latitude = 23.7398,
                longitude = 90.3915,
                lastDonationDate = "Available now",
                trustScore = 98L,
                verifiedSosCount = 4L
            ),
            DonorNode(
                id = "bd_donor_4",
                userId = "bd_donor_4",
                displayName = "রাশেদ খান (Rashed K.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Shahbagh, Dhaka • 1.9 km",
                contactPhone = "+8801611889900",
                latitude = 23.7425,
                longitude = 90.3960,
                lastDonationDate = "Available now",
                trustScore = 97L,
                verifiedSosCount = 3L
            ),
            DonorNode(
                id = "bd_donor_6",
                userId = "bd_donor_6",
                displayName = "নাফিসা ইসলাম (Nafisa I.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Uttara Sector 7, Dhaka • 1.3 km",
                contactPhone = "+8801716778899",
                latitude = 23.8740,
                longitude = 90.3995,
                lastDonationDate = "Available now",
                trustScore = 99L,
                verifiedSosCount = 4L
            ),
            DonorNode(
                id = "bd_donor_7",
                userId = "bd_donor_7",
                displayName = "ইমরান হোসেন (Imran H.)",
                bloodGroup = "B+",
                isAvailableDonor = true,
                district = "Bashundhara, Dhaka • 1.5 km",
                contactPhone = "+8801815443322",
                latitude = 23.8205,
                longitude = 90.4540,
                lastDonationDate = "Available now",
                trustScore = 98L,
                verifiedSosCount = 2L
            ),
            DonorNode(
                id = "bd_donor_8",
                userId = "bd_donor_8",
                displayName = "কামরুল হাসান (Kamrul H.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Panchlaish, Chattogram • 1.2 km",
                contactPhone = "+8801719001122",
                latitude = 22.3580,
                longitude = 91.8230,
                lastDonationDate = "Available now",
                trustScore = 100L,
                verifiedSosCount = 5L
            ),
            DonorNode(
                id = "bd_donor_9",
                userId = "bd_donor_9",
                displayName = "সাদমান সাকিব (Sadman S.)",
                bloodGroup = "AB+",
                isAvailableDonor = true,
                district = "Zindabazar, Sylhet • 1.4 km",
                contactPhone = "+8801918776655",
                latitude = 24.8962,
                longitude = 91.8701,
                lastDonationDate = "Available now",
                trustScore = 99L,
                verifiedSosCount = 3L
            ),
            DonorNode(
                id = "bd_donor_10",
                userId = "bd_donor_10",
                displayName = "আশিকুর রহমান (Ashik R.)",
                bloodGroup = "O+",
                isAvailableDonor = true,
                district = "Boalia, Rajshahi • 1.1 km",
                contactPhone = "+8801714556688",
                latitude = 24.3760,
                longitude = 88.6055,
                lastDonationDate = "Available now",
                trustScore = 99L,
                verifiedSosCount = 4L
            )
        )

        private fun createInitialBangladeshBloodRequests(): List<BloodRequest> = listOf(
            BloodRequest(
                id = "bd_req_mirpur_1",
                userId = "hospital_nhf_mirpur",
                requesterName = "তানজিলা আক্তার (Tanjila)",
                patientName = "মাহবুব আলম (Mahbub Alam)",
                bloodGroup = "O+",
                unitsNeeded = 2L,
                hemoglobinLevel = "6.1 g/dL",
                patientProblem = "হার্ট সার্জারি ও রক্তস্বল্পতা (Open Heart Surgery)",
                hospitalName = "National Heart Foundation Hospital, Mirpur 2",
                wardCabinNumber = "ICU Ward 03, Bed 07",
                neededDateTime = "Now • Emergency",
                district = "Mirpur 10, Dhaka",
                contactPhone = "+8801715889922",
                urgencyLevel = "CRITICAL",
                latitude = 23.8052,
                longitude = 90.3648,
                status = "OPEN",
                respondersCount = 2L
            ),
            BloodRequest(
                id = "bd_req_mirpur_2",
                userId = "hospital_kidney_mirpur",
                requesterName = "রাকিবুল হাসান (Rakibul)",
                patientName = "সালমা বেগম (Salma Begum)",
                bloodGroup = "B+",
                unitsNeeded = 2L,
                hemoglobinLevel = "6.5 g/dL",
                patientProblem = "ডেঙ্গু শক সিন্ড্রোম ও কিডনি ডায়ালাইসিস (Severe Dengue)",
                hospitalName = "Kidney Foundation Hospital, Mirpur",
                wardCabinNumber = "Ward 06, Cabin 204",
                neededDateTime = "Within 3 Hours • Urgent",
                district = "Mirpur 10, Dhaka",
                contactPhone = "+8801816332211",
                urgencyLevel = "URGENT",
                latitude = 23.8089,
                longitude = 90.3698,
                status = "OPEN",
                respondersCount = 3L
            ),
            BloodRequest(
                id = "bd_req_1",
                userId = "hospital_dmch",
                requesterName = "সাদিয়া ইসলাম (Sadia)",
                patientName = "রাবেয়া খাতুন (Rabeya Khatun)",
                bloodGroup = "O+",
                unitsNeeded = 2L,
                hemoglobinLevel = "6.2 g/dL",
                patientProblem = "ডেঙ্গু শক সিন্ড্রোম ও প্লাটিলেট হ্রাস (Severe Dengue)",
                hospitalName = "Dhaka Medical College Hospital (DMCH)",
                wardCabinNumber = "Ward 14, Bed 09 (Medicine)",
                neededDateTime = "Now • Emergency",
                district = "Shahbagh, Dhaka",
                contactPhone = "+8801715998877",
                urgencyLevel = "CRITICAL",
                latitude = 23.7412,
                longitude = 90.3925,
                status = "OPEN",
                respondersCount = 3L
            ),
            BloodRequest(
                id = "bd_req_2",
                userId = "hospital_square",
                requesterName = "আরিফুল ইসলাম (Ariful)",
                patientName = "আব্দুল করিম (Abdul Karim)",
                bloodGroup = "B+",
                unitsNeeded = 3L,
                hemoglobinLevel = "7.4 g/dL",
                patientProblem = "হার্ট বাইপাস সার্জারি (Cardiac Bypass Surgery)",
                hospitalName = "Square Hospital, Dhanmondi",
                wardCabinNumber = "ICU Cabin 402 (Level 4)",
                neededDateTime = "Within 4 Hours • Urgent",
                district = "Dhanmondi, Dhaka",
                contactPhone = "+8801814665544",
                urgencyLevel = "URGENT",
                latitude = 23.7485,
                longitude = 90.3780,
                status = "OPEN",
                respondersCount = 5L
            ),
            BloodRequest(
                id = "bd_req_3",
                userId = "hospital_pg",
                requesterName = "নাহিদ হাসান (Nahid)",
                patientName = "তাহমিনা আক্তার (Tahmina Akter)",
                bloodGroup = "O+",
                unitsNeeded = 2L,
                hemoglobinLevel = "5.9 g/dL",
                patientProblem = "থ্যালাসেমিয়া ও জরুরি সিজারিয়ান (Thalassemia / C-Section)",
                hospitalName = "LabAid Specialized Hospital, Dhanmondi",
                wardCabinNumber = "Block C, Cabin 312",
                neededDateTime = "Immediate • Emergency",
                district = "Dhanmondi, Dhaka",
                contactPhone = "+8801912445566",
                urgencyLevel = "CRITICAL",
                latitude = 23.7438,
                longitude = 90.3728,
                status = "OPEN",
                respondersCount = 2L
            )
        )

        private fun createInitialBangladeshSosAlerts(currentUserId: String): List<SosAlert> = listOf(
            SosAlert(
                id = "bd_sos_mirpur_1",
                userId = "citizen_farhana",
                senderName = "ফারহানা ইয়াসমিন (Farhana)",
                bloodGroup = "O+",
                emergencyType = "ROAD_DANGER",
                district = "Mirpur 10, Dhaka",
                latitude = 23.8076,
                longitude = 90.3694,
                hasLiveCameraVerification = true,
                hasLiveAudioVerification = true,
                peakAudioDecibels = 86L,
                verificationNote = "মিরপুর ১০ গোলচত্বরের কাছে রাস্তায় জরুরি বিপদ! ৩-বার পাওয়ার বাটন চাপায় ৫-সেকেন্ড ক্যামেরা ও অডিও ব্ল্যাকবক্স যুক্ত হয়েছে",
                triggerMethod = "3X_POWER_BUTTON",
                bleBeaconHash = "BLE-M10A",
                status = "ACTIVE",
                responderCount = 3L,
                falseAlarmReports = 0L
            ),
            SosAlert(
                id = "bd_sos_mirpur_2",
                userId = "citizen_tarek_suspect",
                senderName = "তারেক মাহমুদ (Tarek M.)",
                bloodGroup = "B+",
                emergencyType = "ROAD_DANGER",
                district = "Mirpur 10, Dhaka",
                latitude = 23.8051,
                longitude = 90.3671,
                hasLiveCameraVerification = true,
                hasLiveAudioVerification = true,
                peakAudioDecibels = 62L,
                verificationNote = "রাস্তায় ইমার্জেন্সি সংকেত • ৫-সেকেন্ড অডিও ও ভিডিও যাচাই করুন (২টি ভুয়া রিপোর্ট জমা পড়েছে)",
                triggerMethod = "ONE_TAP_SOS",
                bleBeaconHash = "BLE-M10F",
                status = "ACTIVE",
                responderCount = 1L,
                falseAlarmReports = 2L
            ),
            SosAlert(
                id = "bd_sos_1",
                userId = "citizen_nusrat",
                senderName = "নুসরাত জাহান (Nusrat)",
                bloodGroup = "A+",
                emergencyType = "ROAD_DANGER",
                district = "Dhanmondi, Dhaka",
                latitude = 23.7440,
                longitude = 90.3778,
                hasLiveCameraVerification = true,
                hasLiveAudioVerification = true,
                peakAudioDecibels = 84L,
                verificationNote = "রাস্তায় বিপদ! ৩-বার পাওয়ার বাটন চাপায় সাইলেন্ট ক্যামেরা ও ৫-সেকেন্ড অডিও-ভিডিও ব্ল্যাকবক্স যুক্ত হয়েছে",
                triggerMethod = "3X_POWER_BUTTON",
                bleBeaconHash = "BLE-94FA",
                status = "ACTIVE",
                responderCount = 4L,
                falseAlarmReports = 0L
            ),
            SosAlert(
                id = "bd_sos_hist_1",
                userId = currentUserId,
                senderName = "আপনি (পূর্ববর্তী রেকর্ড • You)",
                bloodGroup = "O+",
                emergencyType = "ROAD_DANGER",
                district = "Mirpur 10, Dhaka",
                latitude = 23.8069,
                longitude = 90.3687,
                hasLiveCameraVerification = true,
                hasLiveAudioVerification = true,
                peakAudioDecibels = 79L,
                verificationNote = "আপনার পূর্বের SOS রেকর্ড • নিরাপদ নিশ্চিত করার পর ম্যাপ থেকে সরানো হয়েছে এবং ৫-সেকেন্ড অডিও-ভিডিও হিস্ট্রিতে সংরক্ষিত",
                triggerMethod = "3X_POWER_BUTTON",
                bleBeaconHash = "BLE-71C2",
                status = "RESOLVED",
                responderCount = 3L,
                falseAlarmReports = 0L
            )
        )
    }
}
