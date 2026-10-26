package com.example.ui.viewmodel

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.FeatureFlags
import com.example.data.ble.BleSosMeshManager
import com.example.data.guard.GuardPrefs
import com.example.data.guard.MeshAlertNotifier
import com.example.data.guard.RokkhonGuardService
import com.example.data.location.LocationTracker
import com.example.data.media.LiveAudioVerifier
import com.example.data.model.AppLanguage
import com.example.data.model.BANGLADESH_AREAS
import com.example.data.model.BANGLADESH_DISTRICTS
import com.example.data.model.BangladeshDistrict
import com.example.data.model.BleSosBeacon
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.FALSE_SOS_DISPUTE_THRESHOLD
import com.example.data.model.NotificationKind
import com.example.data.model.RokkhonNotificationItem
import com.example.data.model.SOS_NOTIFICATION_RADIUS_KM
import com.example.data.model.SosAlert
import com.example.data.model.TrustedContact
import com.example.data.model.calculateDistanceKm
import com.example.data.model.canDonateTo
import com.example.data.model.isValidBangladeshMobile
import com.example.data.notification.RokkhonNotificationHelper
import com.example.data.repository.RokkhonRepository
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class RokkhonTab {
    HOME_MATCH,
    RADAR_MAP,
    SOS_GUARDIAN,
    REWARDS,
    DONOR_PROFILE
}

enum class MapLayerFilter {
    DONORS_ONLY,
    BLOOD_REQUESTS_ONLY,
    SOS_ALERTS_ONLY,
    ALL
}

/** A hardware-triggered SOS waiting out its cancel window before it is sent. */
data class PendingHardwareSos(
    val triggerMethod: String,
    val secondsLeft: Int
)

class RokkhonViewModel(
    /** Null when there is no cloud: signed out (emergency SOS only) or the build has no Firebase configuration. */
    private val repository: RokkhonRepository?,
    val bleMeshManager: BleSosMeshManager,
    private val appContext: Context,
    val currentUserId: String,
    val currentUserName: String,
    val isGuestSession: Boolean = false,
    private val locationTracker: LocationTracker? = null
) : ViewModel() {

    val audioVerifier = LiveAudioVerifier()

    /**
     * Without a signed-in Firebase user there is no data source: lists stay empty (never sample
     * people) and only the offline SOS tools (Bluetooth, 999, SMS) work.
     */
    val isOfflineSession: Boolean = isGuestSession || repository == null

    private val prefs = appContext.getSharedPreferences(
        "rokkhon_guardian_state_${currentUserId.take(32)}",
        Context.MODE_PRIVATE
    )
    private val appPrefs = appContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(
        if (appPrefs.getString(KEY_LANGUAGE, AppLanguage.BENGALI.code) == AppLanguage.ENGLISH.code) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.BENGALI
        }
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _currentTab = MutableStateFlow(RokkhonTab.HOME_MATCH)
    val currentTab: StateFlow<RokkhonTab> = _currentTab.asStateFlow()

    // null = all groups. The old default ("O+") silently hid every other donor on first launch.
    private val _selectedBloodFilter = MutableStateFlow<String?>(null)
    val selectedBloodFilter: StateFlow<String?> = _selectedBloodFilter.asStateFlow()

    private val _onlyAvailableDonorsFilter = MutableStateFlow(true)
    val onlyAvailableDonorsFilter: StateFlow<Boolean> = _onlyAvailableDonorsFilter.asStateFlow()

    private val _mapLayerFilter = MutableStateFlow(MapLayerFilter.DONORS_ONLY)
    val mapLayerFilter: StateFlow<MapLayerFilter> = _mapLayerFilter.asStateFlow()

    private val _selectedDistrict = MutableStateFlow(
        BANGLADESH_AREAS.firstOrNull { it.areaKey == prefs.getString(KEY_AREA_KEY, "mirpur") }
            ?: BANGLADESH_DISTRICTS.first()
    )
    val selectedDistrict: StateFlow<BangladeshDistrict> = _selectedDistrict.asStateFlow()

    private val _isAreaFilterEnabled = MutableStateFlow(true)
    val isAreaFilterEnabled: StateFlow<Boolean> = _isAreaFilterEnabled.asStateFlow()

    private val _isAreaAutoDetected = MutableStateFlow(false)
    val isAreaAutoDetected: StateFlow<Boolean> = _isAreaAutoDetected.asStateFlow()

    // The user's own position. Until real GPS arrives this is the centre of the selected area.
    private val _userLatitude = MutableStateFlow(_selectedDistrict.value.latitude)
    val userLatitude: StateFlow<Double> = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(_selectedDistrict.value.longitude)
    val userLongitude: StateFlow<Double> = _userLongitude.asStateFlow()

    private val _hasRealGpsFix = MutableStateFlow(false)
    val hasRealGpsFix: StateFlow<Boolean> = _hasRealGpsFix.asStateFlow()

    private val _userBearing = MutableStateFlow(0f)
    val userBearing: StateFlow<Float> = _userBearing.asStateFlow()

    private val _isLiveLocationSharingActive = MutableStateFlow(false)
    val isLiveLocationSharingActive: StateFlow<Boolean> = _isLiveLocationSharingActive.asStateFlow()

    private val _activeUserSosAlertId = MutableStateFlow<String?>(null)
    val activeUserSosAlertId: StateFlow<String?> = _activeUserSosAlertId.asStateFlow()

    private var liveLocationSharingJob: Job? = null

    private val _pendingHardwareSos = MutableStateFlow<PendingHardwareSos?>(null)
    val pendingHardwareSos: StateFlow<PendingHardwareSos?> = _pendingHardwareSos.asStateFlow()
    private var hardwareCountdownJob: Job? = null

    /** Set when the UI should open the SOS details sheet (e.g. before a False report). */
    private val _sosToInspect = MutableStateFlow<SosAlert?>(null)
    val sosToInspect: StateFlow<SosAlert?> = _sosToInspect.asStateFlow()

    // One-tap-per-account locks (also enforced server-side through marker documents)
    private val _pledgedRequestIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_PLEDGED_IDS))
    val pledgedRequestIds: StateFlow<Set<String>> = _pledgedRequestIds.asStateFlow()

    private val _respondedSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_RESPONDED_SOS_IDS))
    val respondedSosIds: StateFlow<Set<String>> = _respondedSosIds.asStateFlow()

    private val _reportedFalseSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_REPORTED_FALSE_SOS_IDS))
    val reportedFalseSosIds: StateFlow<Set<String>> = _reportedFalseSosIds.asStateFlow()

    private val _inspectedEvidenceSosIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_INSPECTED_EVIDENCE_SOS_IDS))
    val inspectedEvidenceSosIds: StateFlow<Set<String>> = _inspectedEvidenceSosIds.asStateFlow()

    private val _falseSosWarningBanner = MutableStateFlow<String?>(null)
    val falseSosWarningBanner: StateFlow<String?> = _falseSosWarningBanner.asStateFlow()

    // Daily check-in streak: starts at 0 and resets if a day is missed.
    private val _dailyStreakCount = MutableStateFlow(currentStreakFromPrefs())
    val dailyStreakCount: StateFlow<Int> = _dailyStreakCount.asStateFlow()

    private val _checkedInToday = MutableStateFlow(prefs.getString(KEY_LAST_CHECKIN_DAY, "") == todayKey())
    val checkedInToday: StateFlow<Boolean> = _checkedInToday.asStateFlow()

    private val _guardianPoints = MutableStateFlow(prefs.getInt(KEY_GUARDIAN_POINTS, 0))
    val guardianPoints: StateFlow<Int> = _guardianPoints.asStateFlow()

    // ---------------- Admin Control Center State (Rewards, Location Off, Account Suspend, Device Ban) ----------------
    val currentDeviceId: String = run {
        val existing = appPrefs.getString(KEY_DEVICE_HW_ID, null)
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val rawAndroidId = try {
                Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) {
                null
            }
            val clean = (rawAndroidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", ""))
                .take(8)
                .uppercase()
            val formatted = "DEV-BD-$clean"
            appPrefs.edit().putString(KEY_DEVICE_HW_ID, formatted).apply()
            formatted
        }
    }

    private val _rewardCheckInPoints = MutableStateFlow(25)
    val rewardCheckInPoints: StateFlow<Int> = _rewardCheckInPoints.asStateFlow()

    private val _rewardBloodPledgePoints = MutableStateFlow(100)
    val rewardBloodPledgePoints: StateFlow<Int> = _rewardBloodPledgePoints.asStateFlow()

    private val _rewardSosRespondPoints = MutableStateFlow(50)
    val rewardSosRespondPoints: StateFlow<Int> = _rewardSosRespondPoints.asStateFlow()

    private val _isLocationDisabledByAdmin = MutableStateFlow(false)
    val isLocationDisabledByAdmin: StateFlow<Boolean> = _isLocationDisabledByAdmin.asStateFlow()

    private val _isCurrentAccountSuspended = MutableStateFlow(false)
    val isCurrentAccountSuspended: StateFlow<Boolean> = _isCurrentAccountSuspended.asStateFlow()

    private val _currentAccountSuspendReason = MutableStateFlow(
        "ভুয়া অ্যালার্ট বা নীতিমালা ভঙ্গের কারণে অ্যাডমিন আপনার অ্যাকাউন্ট সাময়িকভাবে সাসপেন্ড করেছেন।"
    )
    val currentAccountSuspendReason: StateFlow<String> = _currentAccountSuspendReason.asStateFlow()

    private val _isCurrentDeviceBanned = MutableStateFlow(false)
    val isCurrentDeviceBanned: StateFlow<Boolean> = _isCurrentDeviceBanned.asStateFlow()

    private val _currentDeviceBanReason = MutableStateFlow(
        "নিরাপত্তা লঙ্ঘনের কারণে অ্যাডমিন কন্ট্রোল সেন্টার থেকে এই ডিভাইসটি ($currentDeviceId) ব্যান করা হয়েছে।"
    )
    val currentDeviceBanReason: StateFlow<String> = _currentDeviceBanReason.asStateFlow()

    private val _toastBanner = MutableStateFlow<String?>(null)
    val toastBanner: StateFlow<String?> = _toastBanner.asStateFlow()
    private var toastJob: Job? = null

    private val _trustedContacts = MutableStateFlow(loadTrustedContacts())
    val trustedContacts: StateFlow<List<TrustedContact>> = _trustedContacts.asStateFlow()

    private val _customNotifications = MutableStateFlow<List<RokkhonNotificationItem>>(emptyList())
    private val _readNotificationIds = MutableStateFlow<Set<String>>(loadStringSet(KEY_READ_NOTIFICATION_IDS))

    // Local data is only the user's own records (profile, posts, SOS) until Firestore confirms them.
    private val _localDonors = MutableStateFlow(listOfNotNull(loadSavedOwnProfileOrNull()))
    private val _localBloodRequests = MutableStateFlow(loadPersistedBloodRequests().filter { it.userId == currentUserId })
    private val _localSosAlerts = MutableStateFlow(loadPersistedSosAlerts().filter { it.userId == currentUserId })

    private val _cloudLoaded = MutableStateFlow(isOfflineSession)

    private val seenBloodRequestIds = mutableSetOf<String>()
    private val seenSosAlertIds = mutableSetOf<String>()
    private var bloodInitialLoadDone = false
    private var sosInitialLoadDone = false

    fun getCurrentUserBloodGroup(): String? =
        _localDonors.value.firstOrNull { it.userId == currentUserId }?.bloodGroup

    private fun myDonorNode(): DonorNode? =
        (donorNodesState.value as? UiState.Success)?.data?.firstOrNull { it.userId == currentUserId }
            ?: _localDonors.value.firstOrNull { it.userId == currentUserId }

    private fun isCurrentUserMatchingDonorForAreaRequest(req: BloodRequest): Boolean {
        val me = myDonorNode() ?: return false
        return me.isEligibleNow &&
            _selectedDistrict.value.matchesArea(req.district) &&
            me.bloodGroup.equals(req.bloodGroup, ignoreCase = true)
    }

    private val upstreamDonorsFlow: Flow<List<DonorNode>> = if (isOfflineSession || repository == null) {
        _localDonors
    } else {
        combine(
            repository.observeDonorNodes()
                .onEach { _cloudLoaded.value = true }
                .catch { emit(emptyList()) },
            _localDonors
        ) { cloudList, localList ->
            // Local copy of the user's own profile wins until the cloud write round-trips.
            val localIds = localList.map { it.userId }.toSet()
            localList + cloudList.filterNot { it.userId in localIds }
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
            initialValue = if (isOfflineSession) UiState.Success(_localDonors.value) else UiState.Loading
        )

    /** True once we know whether the signed-in user already has a donor profile. */
    val needsProfileSetup: StateFlow<Boolean> = combine(donorNodesState, _cloudLoaded) { state, loaded ->
        if (isOfflineSession || !loaded) {
            false
        } else {
            (state as? UiState.Success)?.data?.none { it.userId == currentUserId } ?: false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val upstreamBloodRequestsFlow: Flow<List<BloodRequest>> = if (isOfflineSession || repository == null) {
        combine(_localBloodRequests, _pledgedRequestIds) { list, pledged -> list.withPledges(pledged) }
    } else {
        combine(
            repository.observeBloodRequests().catch { emit(emptyList()) },
            _localBloodRequests,
            _pledgedRequestIds
        ) { cloudList, localList, pledgedSet ->
            val localById = localList.associateBy { it.id }
            val mergedCloud = cloudList.map { cloudReq ->
                val localMatch = localById[cloudReq.id]
                cloudReq.copy(
                    status = if (localMatch?.status == "FULFILLED") "FULFILLED" else cloudReq.status,
                    respondersCount = maxOf(cloudReq.respondersCount, localMatch?.respondersCount ?: 0L)
                )
            }
            val cloudIds = mergedCloud.map { it.id }.toSet()
            (mergedCloud + localList.filterNot { it.id in cloudIds }).withPledges(pledgedSet)
        }
    }

    private fun List<BloodRequest>.withPledges(pledged: Set<String>): List<BloodRequest> = map { req ->
        if (req.id in pledged) req.copy(responderIds = (req.responderIds + currentUserId).distinct()) else req
    }

    val bloodRequestsState: StateFlow<UiState<List<BloodRequest>>> = upstreamBloodRequestsFlow
        .onEach { list -> notifyNewBloodRequests(list) }
        .map<List<BloodRequest>, UiState<List<BloodRequest>>> { list ->
            UiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE })
        }
        .catch { error ->
            Log.w(TAG, "Error observing blood requests", error)
            emit(UiState.Success(_localBloodRequests.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (isOfflineSession) UiState.Success(_localBloodRequests.value) else UiState.Loading
        )

    private fun notifyNewBloodRequests(list: List<BloodRequest>) {
        val openRequests = list.filter { it.status == "OPEN" && it.id.isNotBlank() }
        if (!bloodInitialLoadDone) {
            // Existing requests at startup are not "new"; only notify for later arrivals.
            openRequests.mapTo(seenBloodRequestIds) { it.id }
            bloodInitialLoadDone = true
            return
        }
        openRequests.forEach { req ->
            if (seenBloodRequestIds.add(req.id) && req.userId != currentUserId &&
                isCurrentUserMatchingDonorForAreaRequest(req)
            ) {
                RokkhonNotificationHelper.notifyOnce(
                    context = appContext,
                    key = "req_${req.id}",
                    title = "জরুরি রক্তের প্রয়োজন (${req.bloodGroup} • ${req.district})",
                    body = "${req.unitsNeeded} bag(s) ${req.bloodGroup} needed for ${req.patientName} at ${req.hospitalName}."
                )
                showToast(
                    bn = "আপনার এলাকায় ${req.bloodGroup} রক্ত দরকার: ${req.hospitalName}",
                    en = "${req.bloodGroup} blood needed in your area: ${req.hospitalName}"
                )
            }
        }
    }

    private val upstreamSosAlertsFlow: Flow<List<SosAlert>> = if (isOfflineSession || repository == null) {
        combine(_localSosAlerts, _respondedSosIds, _reportedFalseSosIds) { localList, respondedSet, falseSet ->
            localList.withVotes(respondedSet, falseSet)
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
                cloudSos.copy(
                    status = if (localMatch?.status == "RESOLVED") "RESOLVED" else cloudSos.status,
                    responderCount = maxOf(cloudSos.responderCount, localMatch?.responderCount ?: 0L),
                    falseAlarmReports = maxOf(cloudSos.falseAlarmReports, localMatch?.falseAlarmReports ?: 0L)
                )
            }
            val cloudIds = mergedCloud.map { it.id }.toSet()
            (mergedCloud + localList.filterNot { it.id in cloudIds }).withVotes(respondedSet, falseSet)
        }
    }

    private fun List<SosAlert>.withVotes(responded: Set<String>, reported: Set<String>): List<SosAlert> = map { sos ->
        sos.copy(
            responderIds = if (sos.id in responded) (sos.responderIds + currentUserId).distinct() else sos.responderIds,
            falseReporterIds = if (sos.id in reported) (sos.falseReporterIds + currentUserId).distinct() else sos.falseReporterIds
        )
    }

    val sosAlertsState: StateFlow<UiState<List<SosAlert>>> = upstreamSosAlertsFlow
        .onEach { list -> notifyNewSosAlerts(list) }
        .map<List<SosAlert>, UiState<List<SosAlert>>> { list ->
            UiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE })
        }
        .catch { error ->
            Log.w(TAG, "Error observing SOS alerts", error)
            emit(UiState.Success(_localSosAlerts.value))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (isOfflineSession) UiState.Success(_localSosAlerts.value) else UiState.Loading
        )

    private fun notifyNewSosAlerts(list: List<SosAlert>) {
        if (!FeatureFlags.SOS_ENABLED) return
        val activeAlerts = list.filter { it.status == "ACTIVE" && !it.isExpiredAfter24Hours() && it.id.isNotBlank() }
        if (!sosInitialLoadDone) {
            activeAlerts.mapTo(seenSosAlertIds) { it.id }
            sosInitialLoadDone = true
            return
        }
        activeAlerts.forEach { alert ->
            if (seenSosAlertIds.add(alert.id) && alert.userId != currentUserId) {
                val distanceKm = alert.distanceFromKm(_userLatitude.value, _userLongitude.value)
                // 1 km proximity filter so distant users are not disturbed
                if (distanceKm <= SOS_NOTIFICATION_RADIUS_KM) {
                    RokkhonNotificationHelper.notifyOnce(
                        context = appContext,
                        key = "sos_${alert.id}",
                        title = "জরুরি SOS • ${"%.1f".format(distanceKm)} km দূরে",
                        body = "${alert.senderName} needs help near ${alert.district}. Open Rokkhon to respond."
                    )
                    showToast(
                        bn = "কাছাকাছি SOS (${"%.1f".format(distanceKm)} km): ${alert.senderName}",
                        en = "SOS nearby (${"%.1f".format(distanceKm)} km): ${alert.senderName}"
                    )
                }
            }
        }
    }

    /** Count of the user's own SOS alerts the community has disputed without evidence/responders. */
    val falseSosPenaltyCount: StateFlow<Int> = sosAlertsState
        .map { state ->
            (state as? UiState.Success)?.data.orEmpty().count {
                it.userId == currentUserId && it.isCommunityDisputed && !it.isShieldedByEvidenceOrResponders
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val notificationsState: StateFlow<List<RokkhonNotificationItem>> = combine(
        _customNotifications,
        sosAlertsState,
        bloodRequestsState,
        _readNotificationIds,
        donorNodesState
    ) { customList, sosUi, reqUi, readIds, _ ->
        val items = mutableListOf<RokkhonNotificationItem>()
        items.addAll(customList)

        val sosList = (sosUi as? UiState.Success)?.data.orEmpty()
        sosList.filter { alert ->
            alert.status == "ACTIVE" &&
                !alert.isExpiredAfter24Hours() &&
                alert.userId != currentUserId &&
                alert.distanceFromKm(_userLatitude.value, _userLongitude.value) <= SOS_NOTIFICATION_RADIUS_KM
        }
            .take(5)
            .forEach { alert ->
                val distKm = alert.distanceFromKm(_userLatitude.value, _userLongitude.value)
                items.add(
                    RokkhonNotificationItem(
                        id = "notif_sos_${alert.id}",
                        kind = NotificationKind.SOS_ALERT,
                        titleBn = "কাছাকাছি SOS: ${alert.senderName} (${"%.1f".format(distKm)} km)",
                        titleEn = "SOS nearby: ${alert.senderName} (${"%.1f".format(distKm)} km)",
                        bodyBn = "${alert.district} • যাচ্ছেন: ${alert.responderCount} জন • ভুয়া রিপোর্ট: ${alert.falseAlarmReports}",
                        bodyEn = "${alert.district} • Going: ${alert.responderCount} • False reports: ${alert.falseAlarmReports}",
                        timestampLabel = "${"%.1f".format(distKm)} km",
                        areaName = alert.district,
                        relatedSosId = alert.id
                    )
                )
            }

        val reqList = (reqUi as? UiState.Success)?.data.orEmpty()
        reqList.filter { req ->
            req.status == "OPEN" && req.userId != currentUserId && isCurrentUserMatchingDonorForAreaRequest(req)
        }
            .take(6)
            .forEach { req ->
                items.add(
                    RokkhonNotificationItem(
                        id = "notif_req_${req.id}",
                        kind = NotificationKind.BLOOD_REQUEST,
                        titleBn = "আপনার এলাকায় ${req.bloodGroup} রক্ত দরকার (${req.unitsNeeded} ব্যাগ)",
                        titleEn = "${req.bloodGroup} blood needed in your area (${req.unitsNeeded} bags)",
                        bodyBn = "রোগী: ${req.patientName} • ${req.hospitalName}, ${req.district}",
                        bodyEn = "Patient: ${req.patientName} • ${req.hospitalName}, ${req.district}",
                        timestampLabel = req.neededDateTime,
                        areaName = req.district,
                        relatedRequestId = req.id
                    )
                )
            }

        items.distinctBy { it.id }
            .filter { FeatureFlags.SOS_ENABLED || (it.kind != NotificationKind.SOS_ALERT && it.kind != NotificationKind.FALSE_SOS_WARNING) }
            .map { it.copy(isRead = it.isRead || it.id in readIds) }
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
            initialValue = 0
        )

    val bleBeacons: StateFlow<List<BleSosBeacon>> = bleMeshManager.nearbyBeacons
    val bleRelayedCount: StateFlow<Int> = bleMeshManager.relayedCount

    private val _guardNetworkEnabled = MutableStateFlow(GuardPrefs.isEnabled(appContext))
    /** Background "safety network": Bluetooth relay + 1 km SOS alerts while the app is closed. */
    val guardNetworkEnabled: StateFlow<Boolean> = _guardNetworkEnabled.asStateFlow()

    init {
        appPrefs.edit()
            .remove(KEY_ADMIN_LOC_OFF_USERS)
            .remove(KEY_ADMIN_GLOBAL_LOC_OFF)
            .remove(KEY_ADMIN_SUSPENDED_USERS)
            .remove(KEY_ADMIN_BANNED_DEVICES)
            .remove(KEY_ADMIN_REWARD_CHECKIN)
            .remove(KEY_ADMIN_REWARD_PLEDGE)
            .remove(KEY_ADMIN_REWARD_SOS)
            .apply()

        GuardPrefs.lastLocation(appContext)?.let { (lat, lng) ->
            if (lat != 0.0 && lng != 0.0) {
                _userLatitude.value = lat
                _userLongitude.value = lng
                _hasRealGpsFix.value = true
                bleMeshManager.updateOwnLocation(lat, lng)
            }
        }
        locationTracker?.seedInitialCoordinatesIfUnset(_userLatitude.value, _userLongitude.value)
        if (FeatureFlags.SOS_ENABLED) {
            MeshAlertNotifier.ensureStarted(appContext)
            bleMeshManager.startMeshScanning()
        }
        refreshGpsLocation()
        RokkhonGuardService.start(appContext)

        // Resume live location streaming if user already has an active SOS
        val existingActiveMySos = _localSosAlerts.value.firstOrNull {
            it.userId == currentUserId && it.status == "ACTIVE" && !it.isExpiredAfter24Hours()
        }
        if (existingActiveMySos != null) {
            _activeUserSosAlertId.value = existingActiveMySos.id
            _isLiveLocationSharingActive.value = true
            startContinuousLiveLocationSharing(existingActiveMySos.id)
        }

        viewModelScope.launch {
            // The background guard reads the donor profile to decide on Bluetooth blood alerts.
            combine(donorNodesState, _selectedDistrict) { state, district ->
                (state as? UiState.Success)?.data?.firstOrNull { it.userId == currentUserId && it.contactPhone.isNotBlank() }
                    ?.let { me ->
                        val area = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(me.district) } ?: district
                        GuardPrefs.DonorProfile(me.bloodGroup, BleSosMeshManager.areaHashOf(area.areaKey), me.isEligibleNow)
                    }
            }.collect { GuardPrefs.saveDonorProfile(appContext, it) }
        }

        if (repository != null) {
            if (!isOfflineSession) {
                viewModelScope.launch {
                    repository.registerDevice(currentDeviceId)
                        .onFailure { Log.w(TAG, "Device registration failed", it) }
                }
            }
            viewModelScope.launch {
                repository.observeAdminPolicy().collect { policy ->
                    _rewardCheckInPoints.value = policy.rewardCheckInPoints
                    _rewardBloodPledgePoints.value = policy.rewardBloodPledgePoints
                    _rewardSosRespondPoints.value = policy.rewardSosRespondPoints

                    val locDisabled = policy.globalLocationOff || (currentUserId in policy.locationOffUserIds)
                    val wasLocDisabled = _isLocationDisabledByAdmin.value
                    _isLocationDisabledByAdmin.value = locDisabled
                    if (locDisabled) {
                        liveLocationSharingJob?.cancel()
                        liveLocationSharingJob = null
                        locationTracker?.stopLiveUpdates()
                        _isLiveLocationSharingActive.value = false
                        _hasRealGpsFix.value = false
                    } else if (wasLocDisabled) {
                        refreshGpsLocation()
                    }

                    _isCurrentAccountSuspended.value = currentUserId in policy.suspendedUserIds
                    policy.suspendReasons[currentUserId]?.let { _currentAccountSuspendReason.value = it }
                    val deviceKey = currentDeviceId.uppercase()
                    _isCurrentDeviceBanned.value = deviceKey in policy.bannedDeviceIds
                    policy.banReasons[deviceKey]?.let { _currentDeviceBanReason.value = it }

                    policy.userPointsOverrides[currentUserId]?.let { pts ->
                        _guardianPoints.value = pts
                        prefs.edit().putInt(KEY_GUARDIAN_POINTS, pts).apply()
                    }
                    policy.userStreakOverrides[currentUserId]?.let { strk ->
                        _dailyStreakCount.value = strk
                        prefs.edit().putInt(KEY_STREAK_COUNT, strk).apply()
                    }
                    policy.userTrustOverrides[currentUserId]?.let { tr ->
                        _localDonors.value = _localDonors.value.map { d ->
                            if (d.userId == currentUserId) d.copy(trustScore = tr.toLong()) else d
                        }
                    }
                }
            }
        }
    }

    /** Called after the user grants Bluetooth / location permissions. */
    fun onPermissionsChanged() {
        if (FeatureFlags.SOS_ENABLED) bleMeshManager.startMeshScanning()
        refreshGpsLocation()
        RokkhonGuardService.start(appContext)
    }

    fun setGuardNetworkEnabled(enabled: Boolean) {
        if (enabled && !RokkhonGuardService.canRun(appContext)) {
            showToast("আগে ব্লুটুথ বা লোকেশনের অনুমতি দিন", "Allow Bluetooth or location first")
            return
        }
        GuardPrefs.setEnabled(appContext, enabled)
        _guardNetworkEnabled.value = enabled
        if (enabled) {
            RokkhonGuardService.start(appContext)
            showToast("সুরক্ষা নেটওয়ার্ক চালু", "Safety network on")
        } else {
            RokkhonGuardService.stop(appContext)
            showToast("সুরক্ষা নেটওয়ার্ক বন্ধ", "Safety network off")
        }
    }

    fun refreshGpsLocation() {
        if (_isLocationDisabledByAdmin.value) {
            locationTracker?.stopLiveUpdates()
            _isLiveLocationSharingActive.value = false
            _hasRealGpsFix.value = false
            return
        }
        locationTracker?.seedInitialCoordinatesIfUnset(_userLatitude.value, _userLongitude.value)
        locationTracker?.requestCurrentLocation { lat, lng ->
            updateGpsCoordinates(lat, lng, autoSelectArea = !prefs.contains(KEY_AREA_KEY))
        }
        locationTracker?.startLiveUpdates(
            intervalMillis = 1_000L,
            highAccuracy = true,
            minDistanceMeters = 0f,
            onBearing = { bearing ->
                if (!_isLocationDisabledByAdmin.value) {
                    _userBearing.value = bearing
                }
            },
            onLocation = { lat, lng ->
                if (!_isLocationDisabledByAdmin.value) {
                    updateGpsCoordinates(lat, lng, autoSelectArea = false)
                }
            }
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun todayKey(offsetDays: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun currentStreakFromPrefs(): Int {
        val last = prefs.getString(KEY_LAST_CHECKIN_DAY, "") ?: ""
        val stored = prefs.getInt(KEY_STREAK_COUNT, 0)
        // A streak survives only if the last check-in was today or yesterday.
        return if (last == todayKey() || last == todayKey(-1)) stored else 0
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

    private fun bn(): Boolean = _language.value == AppLanguage.BENGALI

    /** Shows the top banner and hides it automatically after a few seconds. */
    private fun showToast(bn: String, en: String) {
        _toastBanner.value = if (bn()) bn else en
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_DURATION_MS)
            _toastBanner.value = null
        }
    }

    private fun loadLocalOwnProfile(): DonorNode {
        val otpPhone = appPrefs.getString("otp_verified_phone", "")?.removePrefix("+88") ?: ""
        val otpName = appPrefs.getString("otp_verified_name", "") ?: ""
        val resolvedName = prefs.getString(KEY_OWN_NAME, null)?.takeIf { it.isNotBlank() }
            ?: otpName.takeIf { it.isNotBlank() }
            ?: currentUserName.takeIf { it.isNotBlank() && it != "Guest" }.orEmpty()
        val resolvedPhone = prefs.getString(KEY_OWN_PHONE, null)?.takeIf { it.isNotBlank() }
            ?: otpPhone
        return DonorNode(
            id = currentUserId,
            userId = currentUserId,
            displayName = resolvedName,
            bloodGroup = prefs.getString(KEY_OWN_BLOOD, "O+") ?: "O+",
            isAvailableDonor = prefs.getBoolean(KEY_OWN_AVAILABLE, true),
            district = prefs.getString(KEY_OWN_DISTRICT, null) ?: _selectedDistrict.value.nameEn,
            contactPhone = resolvedPhone,
            latitude = _selectedDistrict.value.latitude,
            longitude = _selectedDistrict.value.longitude,
            lastDonationDate = prefs.getString(KEY_OWN_LAST_DONATION, "") ?: ""
        )
    }

    private fun loadSavedOwnProfileOrNull(): DonorNode? {
        val savedName = prefs.getString(KEY_OWN_NAME, null)
        val savedPhone = prefs.getString(KEY_OWN_PHONE, null)
        if (savedName.isNullOrBlank() && savedPhone.isNullOrBlank()) return null
        return loadLocalOwnProfile()
    }

    private fun persistBloodRequestsToDisk(list: List<BloodRequest>) {
        try {
            val userItems = list.filter { it.userId == currentUserId || it.id.startsWith("req_") }.take(40)
            val arr = JSONArray()
            userItems.forEach { req ->
                arr.put(
                    JSONObject().apply {
                        put("id", req.id)
                        put("userId", req.userId)
                        put("requesterName", req.requesterName)
                        put("patientName", req.patientName)
                        put("bloodGroup", req.bloodGroup)
                        put("unitsNeeded", req.unitsNeeded)
                        put("hemoglobinLevel", req.hemoglobinLevel)
                        put("patientProblem", req.patientProblem)
                        put("hospitalName", req.hospitalName)
                        put("wardCabinNumber", req.wardCabinNumber)
                        put("neededDateTime", req.neededDateTime)
                        put("district", req.district)
                        put("contactPhone", req.contactPhone)
                        put("urgencyLevel", req.urgencyLevel)
                        put("latitude", req.latitude)
                        put("longitude", req.longitude)
                        put("status", req.status)
                        put("respondersCount", req.respondersCount)
                        put("createdAtSec", req.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                    }
                )
            }
            prefs.edit().putString(KEY_PERSISTED_BLOOD_REQUESTS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist blood requests", e)
        }
    }

    private fun loadPersistedBloodRequests(): List<BloodRequest> {
        val raw = prefs.getString(KEY_PERSISTED_BLOOD_REQUESTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val sec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
                    add(
                        BloodRequest(
                            id = obj.optString("id"),
                            userId = obj.optString("userId", currentUserId),
                            requesterName = obj.optString("requesterName"),
                            patientName = obj.optString("patientName"),
                            bloodGroup = obj.optString("bloodGroup", "O+"),
                            unitsNeeded = obj.optLong("unitsNeeded", 1L),
                            hemoglobinLevel = obj.optString("hemoglobinLevel"),
                            patientProblem = obj.optString("patientProblem"),
                            hospitalName = obj.optString("hospitalName"),
                            wardCabinNumber = obj.optString("wardCabinNumber"),
                            neededDateTime = obj.optString("neededDateTime"),
                            district = obj.optString("district"),
                            contactPhone = obj.optString("contactPhone"),
                            urgencyLevel = obj.optString("urgencyLevel", "CRITICAL"),
                            latitude = obj.optDouble("latitude", _selectedDistrict.value.latitude),
                            longitude = obj.optDouble("longitude", _selectedDistrict.value.longitude),
                            status = obj.optString("status", "OPEN"),
                            respondersCount = obj.optLong("respondersCount", 0L),
                            createdAt = Timestamp(sec, 0),
                            updatedAt = Timestamp(sec, 0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistSosAlertsToDisk(list: List<SosAlert>) {
        try {
            val userItems = list.filter { it.userId == currentUserId || it.id.startsWith("sos_") }.take(50)
            val arr = JSONArray()
            userItems.forEach { sos ->
                arr.put(
                    JSONObject().apply {
                        put("id", sos.id)
                        put("userId", sos.userId)
                        put("senderName", sos.senderName)
                        put("bloodGroup", sos.bloodGroup)
                        put("emergencyType", sos.emergencyType)
                        put("district", sos.district)
                        put("latitude", sos.latitude)
                        put("longitude", sos.longitude)
                        put("hasLiveCameraVerification", sos.hasLiveCameraVerification)
                        put("hasLiveAudioVerification", sos.hasLiveAudioVerification)
                        put("peakAudioDecibels", sos.peakAudioDecibels)
                        put("verificationNote", sos.verificationNote)
                        put("triggerMethod", sos.triggerMethod)
                        put("bleBeaconHash", sos.bleBeaconHash)
                        put("status", sos.status)
                        put("responderCount", sos.responderCount)
                        put("falseAlarmReports", sos.falseAlarmReports)
                        put("createdAtSec", sos.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                    }
                )
            }
            prefs.edit().putString(KEY_PERSISTED_SOS_ALERTS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist SOS alerts", e)
        }
    }

    private fun loadPersistedSosAlerts(): List<SosAlert> {
        val raw = prefs.getString(KEY_PERSISTED_SOS_ALERTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val sec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
                    add(
                        SosAlert(
                            id = obj.optString("id"),
                            userId = obj.optString("userId", currentUserId),
                            senderName = obj.optString("senderName"),
                            bloodGroup = obj.optString("bloodGroup", "O+"),
                            emergencyType = obj.optString("emergencyType", "MEDICAL"),
                            district = obj.optString("district"),
                            latitude = obj.optDouble("latitude", _selectedDistrict.value.latitude),
                            longitude = obj.optDouble("longitude", _selectedDistrict.value.longitude),
                            hasLiveCameraVerification = obj.optBoolean("hasLiveCameraVerification", false),
                            hasLiveAudioVerification = obj.optBoolean("hasLiveAudioVerification", false),
                            peakAudioDecibels = obj.optLong("peakAudioDecibels", 0L),
                            verificationNote = obj.optString("verificationNote"),
                            triggerMethod = obj.optString("triggerMethod", "ONE_TAP_SOS"),
                            bleBeaconHash = obj.optString("bleBeaconHash"),
                            status = obj.optString("status", "ACTIVE"),
                            responderCount = obj.optLong("responderCount", 0L),
                            falseAlarmReports = obj.optLong("falseAlarmReports", 0L),
                            createdAt = Timestamp(sec, 0),
                            updatedAt = Timestamp(sec, 0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadTrustedContacts(): List<TrustedContact> {
        val raw = prefs.getString(KEY_TRUSTED_CONTACTS, "") ?: ""
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size == 2 && parts[1].isNotBlank()) TrustedContact(parts[0], parts[1]) else null
        }
    }

    // ---------------------------------------------------------------- notifications & banners

    fun markNotificationRead(id: String) {
        val next = _readNotificationIds.value + id
        _readNotificationIds.value = next
        saveStringSet(KEY_READ_NOTIFICATION_IDS, next)
    }

    fun markAllNotificationsRead() {
        val allIds = notificationsState.value.map { it.id }.toSet() + _readNotificationIds.value
        _readNotificationIds.value = allIds
        saveStringSet(KEY_READ_NOTIFICATION_IDS, allIds)
        showToast("সব নোটিফিকেশন পড়া হয়েছে", "All notifications marked as read")
    }

    fun dismissFalseSosWarningBanner() {
        _falseSosWarningBanner.value = null
    }

    fun dismissToastBanner() {
        toastJob?.cancel()
        _toastBanner.value = null
    }

    // ---------------------------------------------------------------- daily check-in

    fun performDailySafeCheckIn() {
        if (_checkedInToday.value) {
            showToast(
                "আজকের চেক-ইন হয়ে গেছে • ${_dailyStreakCount.value} দিনের স্ট্রিক",
                "Already checked in today • ${_dailyStreakCount.value}-day streak"
            )
            return
        }
        val continues = prefs.getString(KEY_LAST_CHECKIN_DAY, "") == todayKey(-1)
        val nextStreak = if (continues) _dailyStreakCount.value + 1 else 1
        _dailyStreakCount.value = nextStreak
        _checkedInToday.value = true
        prefs.edit()
            .putInt(KEY_STREAK_COUNT, nextStreak)
            .putString(KEY_LAST_CHECKIN_DAY, todayKey())
            .apply()
        val pts = _rewardCheckInPoints.value
        addGuardianPoints(pts)
        showToast(
            "চেক-ইন সম্পন্ন! $nextStreak দিনের স্ট্রিক • +$pts পয়েন্ট",
            "Checked in! $nextStreak-day streak • +$pts points"
        )
    }

    // ---------------------------------------------------------------- settings & filters

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.BENGALI) AppLanguage.ENGLISH else AppLanguage.BENGALI
        _language.value = next
        appPrefs.edit().putString(KEY_LANGUAGE, next.code).apply()
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

    /** Manual area choice. Changes what is listed, never the user's real GPS position. */
    fun selectDistrict(district: BangladeshDistrict, isManual: Boolean = true) {
        _selectedDistrict.value = district
        _isAreaAutoDetected.value = !isManual
        _isAreaFilterEnabled.value = true
        prefs.edit().putString(KEY_AREA_KEY, district.areaKey).apply()
        if (!_hasRealGpsFix.value) {
            _userLatitude.value = district.latitude
            _userLongitude.value = district.longitude
        }
    }

    /** Picks the nearest listed area to the user's GPS position. */
    fun autoSelectAreaFromPermission(lat: Double? = null, lng: Double? = null, notifyUser: Boolean = false) {
        val effectiveLat = lat ?: _userLatitude.value
        val effectiveLng = lng ?: _userLongitude.value
        if (lat == null && !_hasRealGpsFix.value) {
            refreshGpsLocation()
            if (locationTracker?.hasPermission() != true) {
                showToast(
                    "লোকেশন পারমিশন দিন, তারপর আবার চেষ্টা করুন",
                    "Allow location access, then try again"
                )
                return
            }
        }
        if (effectiveLat !in 20.5..26.7 || effectiveLng !in 88.0..92.7) {
            if (notifyUser) {
                showToast("আপনার লোকেশন বাংলাদেশের বাইরে, এলাকা নিজে বেছে নিন", "You appear to be outside Bangladesh; pick an area manually")
            }
            return
        }

        val nearest = BANGLADESH_DISTRICTS.minByOrNull { dist ->
            calculateDistanceKm(effectiveLat, effectiveLng, dist.latitude, dist.longitude)
        } ?: return

        selectDistrict(nearest, isManual = false)
        if (notifyUser) {
            showToast("জিপিএস থেকে এলাকা: ${nearest.nameBn}", "Area from GPS: ${nearest.nameEn}")
        }
    }

    fun updateGpsCoordinates(lat: Double, lng: Double, autoSelectArea: Boolean = false) {
        if (_isLocationDisabledByAdmin.value) return
        val prevLat = _userLatitude.value
        val prevLng = _userLongitude.value
        if (_hasRealGpsFix.value && calculateDistanceKm(prevLat, prevLng, lat, lng) > 0.0003) {
            val dLng = Math.toRadians(lng - prevLng)
            val phi1 = Math.toRadians(prevLat)
            val phi2 = Math.toRadians(lat)
            val y = kotlin.math.sin(dLng) * kotlin.math.cos(phi2)
            val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
                kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLng)
            val moveBearing = ((Math.toDegrees(kotlin.math.atan2(y, x)).toFloat()) + 360f) % 360f
            _userBearing.value = moveBearing
        } else {
            locationTracker?.let { _userBearing.value = it.currentBearingDegrees }
        }

        _userLatitude.value = lat
        _userLongitude.value = lng
        _hasRealGpsFix.value = true
        bleMeshManager.updateOwnLocation(lat, lng)
        GuardPrefs.saveLastLocation(appContext, lat, lng)

        // Immediately move the user's active SOS pin with their live position
        if (_isLiveLocationSharingActive.value || _activeUserSosAlertId.value != null) {
            val activeId = _activeUserSosAlertId.value
            _localSosAlerts.value = _localSosAlerts.value.map { sos ->
                if (sos.status == "ACTIVE" && (sos.id == activeId || sos.userId == currentUserId)) {
                    sos.copy(latitude = lat, longitude = lng, updatedAt = Timestamp.now())
                } else {
                    sos
                }
            }
        }

        if (autoSelectArea) autoSelectAreaFromPermission(lat, lng, notifyUser = false)
    }

    // ---------------------------------------------------------------- votes & locks

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

    fun consumeSosToInspect() {
        _sosToInspect.value = null
    }

    private var lastSmsPromptAtMillis = 0L

    /** The SOS screen opens the SMS composer once per SOS; the alert id changes when it syncs, so use time. */
    fun shouldPromptTrustedSms(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSmsPromptAtMillis < 2 * 60_000L) return false
        lastSmsPromptAtMillis = now
        return true
    }

    // ---------------------------------------------------------------- trusted contacts

    fun saveTrustedContacts(contacts: List<TrustedContact>): Boolean {
        val cleaned = contacts
            .map { TrustedContact(it.name.trim().replace("\t", " ").replace("\n", " "), it.phone.trim()) }
            .filter { it.phone.isNotBlank() }
            .take(MAX_TRUSTED_CONTACTS)
        if (cleaned.any { !isValidBangladeshMobile(it.phone) }) {
            showToast("সঠিক মোবাইল নম্বর দিন (যেমন 01712345678)", "Enter valid mobile numbers (e.g. 01712345678)")
            return false
        }
        _trustedContacts.value = cleaned
        prefs.edit()
            .putString(KEY_TRUSTED_CONTACTS, cleaned.joinToString("\n") { "${it.name}\t${it.phone}" })
            .apply()
        showToast("বিশ্বস্ত কন্টাক্ট সেভ হয়েছে", "Trusted contacts saved")
        return true
    }

    // ---------------------------------------------------------------- donor profile

    fun saveDonorProfile(
        displayName: String,
        bloodGroup: String,
        isAvailableDonor: Boolean,
        district: String,
        contactPhone: String,
        lastDonationDate: String
    ): Boolean {
        if (displayName.isBlank()) {
            showToast("নাম লিখুন", "Please enter your name")
            return false
        }
        if (!isValidBangladeshMobile(contactPhone)) {
            showToast("সঠিক মোবাইল নম্বর দিন (যেমন 01712345678)", "Enter a valid mobile number (e.g. 01712345678)")
            return false
        }
        val existingNode = myDonorNode()
        val area = BANGLADESH_DISTRICTS.firstOrNull { it.matchesArea(district) } ?: _selectedDistrict.value
        // Donor pins sit at the area centre, never at the donor's exact home location.
        val updatedLocal = DonorNode(
            id = currentUserId,
            userId = currentUserId,
            displayName = displayName.trim(),
            bloodGroup = bloodGroup,
            isAvailableDonor = isAvailableDonor,
            district = district.trim(),
            contactPhone = contactPhone.trim(),
            latitude = area.latitude,
            longitude = area.longitude,
            lastDonationDate = lastDonationDate.trim(),
            trustScore = existingNode?.trustScore ?: 100L,
            verifiedSosCount = existingNode?.verifiedSosCount ?: 0L,
            createdAt = existingNode?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _localDonors.value = listOf(updatedLocal) + _localDonors.value.filterNot { it.userId == currentUserId }

        prefs.edit()
            .putString(KEY_OWN_NAME, updatedLocal.displayName)
            .putString(KEY_OWN_BLOOD, bloodGroup)
            .putBoolean(KEY_OWN_AVAILABLE, isAvailableDonor)
            .putString(KEY_OWN_DISTRICT, updatedLocal.district)
            .putString(KEY_OWN_PHONE, updatedLocal.contactPhone)
            .putString(KEY_OWN_LAST_DONATION, updatedLocal.lastDonationDate)
            .apply()

        if (isOfflineSession || repository == null) {
            showToast("ডোনার প্রোফাইল সফলভাবে সেভ হয়েছে", "Donor profile saved successfully")
            return true
        }

        viewModelScope.launch {
            val result = repository.saveOrUpdateDonorNode(
                displayName = updatedLocal.displayName,
                bloodGroup = bloodGroup,
                isAvailableDonor = isAvailableDonor,
                district = updatedLocal.district,
                contactPhone = updatedLocal.contactPhone,
                latitude = updatedLocal.latitude,
                longitude = updatedLocal.longitude,
                lastDonationDate = updatedLocal.lastDonationDate
            )
            if (result.isSuccess) {
                showToast("ডোনার প্রোফাইল সেভ হয়েছে", "Donor profile saved")
            } else {
                showToast("সেভ করা যায়নি, ইন্টারনেট চেক করুন", "Could not save. Check your connection.")
            }
        }
        return true
    }

    /**
     * "I donated today": stores today's date. The donor then shows as resting and comes back as
     * available by itself 90 days later (isEligibleNow is computed from the date), with nothing
     * to switch back on. No points for this, so it can't be tapped for rewards.
     */
    fun markDonatedToday() {
        val me = myDonorNode()
        if (me == null) {
            showToast("আগে ডোনার প্রোফাইল সেভ করুন", "Save your donor profile first")
            return
        }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (saveDonorProfile(me.displayName, me.bloodGroup, me.isAvailableDonor, me.district, me.contactPhone, today)) {
            showToast(
                "ধন্যবাদ! ৯০ দিন পর আপনি আবার নিজে থেকেই সক্রিয় হয়ে যাবেন",
                "Thank you! You'll be active again automatically in 90 days"
            )
        }
    }

    fun toggleDonorActiveStatus(currentNode: DonorNode?, newStatus: Boolean) {
        val existing = currentNode ?: myDonorNode()
        if (existing == null) {
            showToast("আগে ডোনার প্রোফাইল সেভ করুন", "Save your donor profile first")
            return
        }
        val updated = existing.copy(isAvailableDonor = newStatus)
        _localDonors.value = listOf(updated) + _localDonors.value.filterNot { it.userId == currentUserId }
        if (isOfflineSession) prefs.edit().putBoolean(KEY_OWN_AVAILABLE, newStatus).apply()

        showToast(
            if (newStatus) "আপনি এখন রক্তদাতা হিসেবে দৃশ্যমান" else "আপনি এখন ডোনার তালিকায় লুকানো",
            if (newStatus) "You are now visible as an available donor" else "You are hidden from donor lists"
        )

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch { repository.toggleDonorAvailability(newStatus) }
        }
    }

    // ---------------------------------------------------------------- blood requests

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
        if (matchedDistrict.areaKey != _selectedDistrict.value.areaKey) {
            // Show the area the request was posted to, but keep the user's real GPS untouched.
            selectDistrict(matchedDistrict, isManual = true)
        }

        val localId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        // Small stable offset so several requests at one hospital do not stack on one pin.
        val spread = (localId.hashCode() % 7) * 0.0012
        val pinLat = matchedDistrict.latitude + spread
        val pinLng = matchedDistrict.longitude - spread

        bleMeshManager.startBloodRequestBroadcast(
            patientName = patientName,
            bloodGroup = bloodGroup,
            unitsNeeded = unitsNeeded,
            districtAreaKey = matchedDistrict.areaKey,
            latitude = pinLat,
            longitude = pinLng
        )

        val newReq = BloodRequest(
            id = localId,
            userId = currentUserId,
            requesterName = currentUserName.ifBlank { "Rokkhon Member" },
            patientName = patientName.trim(),
            bloodGroup = bloodGroup,
            unitsNeeded = unitsNeeded.toLong(),
            hemoglobinLevel = hemoglobinLevel.trim(),
            patientProblem = patientProblem.trim(),
            hospitalName = hospitalName.trim(),
            wardCabinNumber = wardCabinNumber.trim(),
            neededDateTime = neededDateTime.trim(),
            district = district.trim(),
            contactPhone = contactPhone.trim(),
            urgencyLevel = urgencyLevel,
            latitude = pinLat,
            longitude = pinLng,
            status = "OPEN",
            respondersCount = 0L,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _localBloodRequests.value = listOf(newReq) + _localBloodRequests.value
        persistBloodRequestsToDisk(_localBloodRequests.value)
        _selectedBloodFilter.value = null
        _mapLayerFilter.value = MapLayerFilter.BLOOD_REQUESTS_ONLY
        _currentTab.value = RokkhonTab.RADAR_MAP

        val allDonors = (donorNodesState.value as? UiState.Success)?.data.orEmpty()
        val areaDonorsCount = allDonors.count {
            it.userId != currentUserId && it.isEligibleNow &&
                matchedDistrict.matchesArea(it.district) && it.bloodGroup == bloodGroup
        }
        showToast(
            if (areaDonorsCount > 0) {
                "আবেদন পোস্ট হয়েছে • ${matchedDistrict.nameBn}-এর $areaDonorsCount জন $bloodGroup ডোনারকে জানানো হচ্ছে"
            } else {
                "আবেদন পোস্ট হয়েছে • এই এলাকায় এখনো কোনো উপলব্ধ $bloodGroup ডোনার নেই, শেয়ার করুন"
            },
            if (areaDonorsCount > 0) {
                "Request posted • notifying $areaDonorsCount $bloodGroup donors in ${matchedDistrict.nameEn}"
            } else {
                "Request posted • no available $bloodGroup donors in this area yet, share it"
            }
        )

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch {
                val result = repository.createBloodRequest(
                    requesterName = currentUserName.ifBlank { "Rokkhon User" },
                    patientName = newReq.patientName,
                    bloodGroup = bloodGroup,
                    unitsNeeded = unitsNeeded,
                    hemoglobinLevel = newReq.hemoglobinLevel,
                    patientProblem = newReq.patientProblem,
                    hospitalName = newReq.hospitalName,
                    wardCabinNumber = newReq.wardCabinNumber,
                    neededDateTime = newReq.neededDateTime,
                    district = newReq.district,
                    contactPhone = newReq.contactPhone,
                    urgencyLevel = urgencyLevel,
                    latitude = pinLat,
                    longitude = pinLng
                )
                val createdId = result.getOrNull()
                if (createdId != null) {
                    _localBloodRequests.value = _localBloodRequests.value.map {
                        if (it.id == localId) it.copy(id = createdId) else it
                    }
                } else {
                    showToast("আবেদন সার্ভারে পাঠানো যায়নি, ইন্টারনেট চেক করুন", "Could not upload the request. Check your connection.")
                }
            }
        }
    }

    /** One "I can donate" per account, only for compatible donors, never on your own post. */
    fun pledgeToDonate(request: BloodRequest) {
        if (request.userId == currentUserId) {
            showToast("এটি আপনার নিজের আবেদন", "This is your own request")
            return
        }
        if (hasUserPledged(request)) {
            showToast("আপনি ইতিমধ্যে সাড়া দিয়েছেন", "You have already pledged for this request")
            return
        }
        val me = myDonorNode()
        if (me != null && !canDonateTo(me.bloodGroup, request.bloodGroup)) {
            showToast(
                "আপনার ${me.bloodGroup} রক্ত ${request.bloodGroup} রোগীকে দেওয়া যায় না • আবেদনটি শেয়ার করুন",
                "Your ${me.bloodGroup} blood is not compatible with ${request.bloodGroup} • please share the request"
            )
            return
        }

        val updatedPledgedIds = _pledgedRequestIds.value + request.id
        _pledgedRequestIds.value = updatedPledgedIds
        saveStringSet(KEY_PLEDGED_IDS, updatedPledgedIds)
        val pledgePts = _rewardBloodPledgePoints.value
        addGuardianPoints(pledgePts)

        _localBloodRequests.value = _localBloodRequests.value.map {
            if (it.id == request.id) it.copy(respondersCount = it.respondersCount + 1L) else it
        }
        if (_localBloodRequests.value.none { it.id == request.id }) {
            // Cloud-only request: keep an optimistic copy so the count updates immediately.
            _localBloodRequests.value = _localBloodRequests.value + request.copy(respondersCount = request.respondersCount + 1L)
        }

        prependCustomNotification(
            RokkhonNotificationItem(
                id = "notif_pledge_${request.id}",
                kind = NotificationKind.RESPONDER_UPDATE,
                titleBn = "রক্তদানের সাড়া নথিভুক্ত (+$pledgePts পয়েন্ট)",
                titleEn = "Donation pledge recorded (+$pledgePts pts)",
                bodyBn = "${request.patientName} (${request.bloodGroup}) • ${request.hospitalName} • যোগাযোগ: ${request.contactPhone}",
                bodyEn = "${request.patientName} (${request.bloodGroup}) • ${request.hospitalName} • Contact: ${request.contactPhone}",
                timestampLabel = if (bn()) "এইমাত্র" else "Just now",
                areaName = request.district,
                relatedRequestId = request.id
            )
        )

        showToast(
            "ধন্যবাদ! এখন রোগীর পরিবারকে কল করুন: ${request.contactPhone}",
            "Thank you! Now call the patient's family: ${request.contactPhone}"
        )

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch {
                if (repository.pledgeBloodResponse(request).isFailure) {
                    showToast("সাড়া সার্ভারে পাঠানো যায়নি", "Could not sync your pledge")
                }
            }
        }
    }

    fun fulfillOwnBloodRequest(requestId: String) {
        val all = (bloodRequestsState.value as? UiState.Success)?.data.orEmpty()
        val req = all.firstOrNull { it.id == requestId } ?: _localBloodRequests.value.firstOrNull { it.id == requestId }
        if (req == null || req.userId != currentUserId) {
            showToast("শুধু আবেদনকারী এটি বন্ধ করতে পারেন", "Only the requester can close this request")
            return
        }
        _localBloodRequests.value = if (_localBloodRequests.value.any { it.id == requestId }) {
            _localBloodRequests.value.map { if (it.id == requestId) it.copy(status = "FULFILLED") else it }
        } else {
            _localBloodRequests.value + req.copy(status = "FULFILLED")
        }
        persistBloodRequestsToDisk(_localBloodRequests.value)
        showToast("রক্ত ম্যানেজ হয়েছে • আবেদনটি তালিকা থেকে সরানো হলো", "Blood arranged • request closed")
        if (!isOfflineSession && repository != null) {
            viewModelScope.launch { repository.markBloodRequestFulfilled(requestId) }
        }
    }

    // ---------------------------------------------------------------- SOS

    /**
     * 3x power / volume press: starts a short cancel window (with live mic recording) instead of
     * sending immediately, so a pocket press can be undone before anyone is alerted.
     */
    fun startHardwareSosCountdown(triggerMethod: String) {
        if (_pendingHardwareSos.value != null) return
        hardwareCountdownJob?.cancel()
        audioVerifier.startLiveMonitoring(viewModelScope)
        hardwareCountdownJob = viewModelScope.launch {
            for (s in HARDWARE_SOS_CANCEL_SECONDS downTo 1) {
                _pendingHardwareSos.value = PendingHardwareSos(triggerMethod, s)
                delay(1000L)
            }
            _pendingHardwareSos.value = null
            dispatchSosWithCapturedEvidence(emergencyType = "ROAD_DANGER", triggerMethod = triggerMethod)
        }
    }

    fun cancelHardwareSosCountdown() {
        hardwareCountdownJob?.cancel()
        hardwareCountdownJob = null
        _pendingHardwareSos.value = null
        audioVerifier.stopLiveMonitoring()
        showToast("SOS বাতিল করা হয়েছে", "SOS cancelled")
    }

    fun sendHardwareSosNow() {
        val pending = _pendingHardwareSos.value ?: return
        hardwareCountdownJob?.cancel()
        _pendingHardwareSos.value = null
        dispatchSosWithCapturedEvidence(emergencyType = "ROAD_DANGER", triggerMethod = pending.triggerMethod)
    }

    private fun dispatchSosWithCapturedEvidence(emergencyType: String, triggerMethod: String) {
        val hasAudio = audioVerifier.hasCapturedAudio()
        val peak = audioVerifier.peakDecibels.value
        triggerVerifiedEmergencySos(
            bloodGroup = getCurrentUserBloodGroup() ?: "O+",
            emergencyType = emergencyType,
            district = _selectedDistrict.value.nameEn,
            hasLiveCamera = false,
            hasLiveAudio = hasAudio,
            peakDecibels = if (hasAudio) peak else 0,
            verificationNote = if (hasAudio) "Mic audio attached ($peak dB peak)" else "No audio evidence",
            triggerMethod = triggerMethod
        )
    }

    fun triggerVerifiedEmergencySos(
        bloodGroup: String,
        emergencyType: String,
        district: String,
        hasLiveCamera: Boolean,
        hasLiveAudio: Boolean,
        peakDecibels: Int,
        verificationNote: String,
        triggerMethod: String = "ONE_TAP_SOS"
    ) {
        audioVerifier.stopLiveMonitoring()
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

            val localAlertId = "sos_${UUID.randomUUID().toString().replace("-", "").take(16)}"
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
                peakAudioDecibels = peakDecibels.coerceIn(0, 140).toLong(),
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
            persistSosAlertsToDisk(_localSosAlerts.value)
            _activeUserSosAlertId.value = localAlertId
            _isLiveLocationSharingActive.value = true
            _currentTab.value = RokkhonTab.SOS_GUARDIAN

            val allDonors = (donorNodesState.value as? UiState.Success)?.data.orEmpty()
            val nearbyUsers = allDonors.count {
                it.userId != currentUserId &&
                    calculateDistanceKm(_userLatitude.value, _userLongitude.value, it.latitude, it.longitude) <= SOS_NOTIFICATION_RADIUS_KM
            }

            var finalAlertId = localAlertId
            var uploaded = isOfflineSession
            if (!isOfflineSession && repository != null) {
                val cloudId = repository.createVerifiedSosAlert(
                    senderName = currentUserName.ifBlank { "Rokkhon User" },
                    bloodGroup = bloodGroup,
                    emergencyType = emergencyType,
                    district = district,
                    latitude = _userLatitude.value,
                    longitude = _userLongitude.value,
                    hasLiveCameraVerification = hasLiveCamera,
                    hasLiveAudioVerification = hasLiveAudio,
                    peakAudioDecibels = peakDecibels,
                    verificationNote = verificationNote,
                    triggerMethod = triggerMethod,
                    bleBeaconHash = bleHash
                ).getOrNull()

                if (cloudId != null) {
                    uploaded = true
                    finalAlertId = cloudId
                    audioVerifier.moveClip(localAlertId, cloudId)
                    _activeUserSosAlertId.value = cloudId
                    _localSosAlerts.value = _localSosAlerts.value.map {
                        if (it.id == localAlertId) it.copy(id = cloudId) else it
                    }
                    persistSosAlertsToDisk(_localSosAlerts.value)
                }
            }

            startContinuousLiveLocationSharing(finalAlertId)

            showToast(
                if (uploaded) {
                    "SOS পাঠানো হয়েছে • ১ কি.মি.-এর মধ্যে $nearbyUsers জন ইউজার • বিশ্বস্ত কন্টাক্টদের SMS করুন"
                } else {
                    "ইন্টারনেট নেই: SOS শুধু ব্লুটুথে যাচ্ছে • ৯৯৯-এ কল ও SMS করুন"
                },
                if (uploaded) {
                    "SOS sent • $nearbyUsers users within 1 km • now SMS your trusted contacts"
                } else {
                    "No internet: SOS is on Bluetooth only • call 999 and send SMS"
                }
            )
        }
    }

    /** Pushes the real GPS position to the live SOS until the user marks themselves safe. */
    private fun startContinuousLiveLocationSharing(alertId: String) {
        liveLocationSharingJob?.cancel()
        refreshGpsLocation()
        liveLocationSharingJob = viewModelScope.launch {
            var lastSentLat = Double.NaN
            var lastSentLng = Double.NaN
            while (_isLiveLocationSharingActive.value) {
                val lat = _userLatitude.value
                val lng = _userLongitude.value
                val moved = lastSentLat.isNaN() ||
                    calculateDistanceKm(lastSentLat, lastSentLng, lat, lng) > 0.0003
                if (moved) {
                    lastSentLat = lat
                    lastSentLng = lng
                    _localSosAlerts.value = _localSosAlerts.value.map {
                        if ((it.id == alertId || it.userId == currentUserId) && it.status == "ACTIVE") {
                            it.copy(latitude = lat, longitude = lng, updatedAt = Timestamp.now())
                        } else {
                            it
                        }
                    }
                    persistSosAlertsToDisk(_localSosAlerts.value)
                    if (!isOfflineSession && repository != null) {
                        repository.updateOwnSosLiveLocation(alertId, lat, lng)
                    }
                }
                delay(LIVE_LOCATION_PUSH_MS)
            }
        }
    }

    fun respondToSos(alert: SosAlert) {
        if (alert.userId == currentUserId) return
        if (hasUserRespondedSos(alert)) {
            showToast("আপনি ইতিমধ্যে 'যাচ্ছি' মার্ক করেছেন", "You already marked yourself as going")
            return
        }

        val nextIds = _respondedSosIds.value + alert.id
        _respondedSosIds.value = nextIds
        saveStringSet(KEY_RESPONDED_SOS_IDS, nextIds)
        val sosPts = _rewardSosRespondPoints.value
        addGuardianPoints(sosPts)

        bumpLocalSos(alert) { it.copy(responderCount = it.responderCount + 1L) }

        prependCustomNotification(
            RokkhonNotificationItem(
                id = "notif_sos_resp_${alert.id}",
                kind = NotificationKind.RESPONDER_UPDATE,
                titleBn = "আপনি সাহায্যে যাচ্ছেন (+$sosPts পয়েন্ট)",
                titleEn = "You are going to help (+$sosPts pts)",
                bodyBn = "${alert.senderName} • ${alert.district} • নিজের নিরাপত্তা আগে, প্রয়োজনে ৯৯৯-এ কল করুন",
                bodyEn = "${alert.senderName} • ${alert.district} • Stay safe yourself; call 999 if needed",
                timestampLabel = if (bn()) "এইমাত্র" else "Just now",
                areaName = alert.district,
                relatedSosId = alert.id
            )
        )

        showToast("'যাচ্ছি' মার্ক হয়েছে • ডিরেকশন দেখতে 'পথ দেখুন' চাপুন", "Marked as going • tap Directions to navigate")

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch { repository.respondToSosAlert(alert) }
        }
    }

    private fun bumpLocalSos(alert: SosAlert, change: (SosAlert) -> SosAlert) {
        _localSosAlerts.value = if (_localSosAlerts.value.any { it.id == alert.id }) {
            _localSosAlerts.value.map { if (it.id == alert.id) change(it) else it }
        } else {
            _localSosAlerts.value + change(alert)
        }
        persistSosAlertsToDisk(_localSosAlerts.value)
    }

    /** Only the sender can mark an SOS safe; this stops BLE broadcast and live location. */
    fun resolveOwnSos(alertId: String) {
        val all = (sosAlertsState.value as? UiState.Success)?.data.orEmpty() + _localSosAlerts.value
        val target = all.firstOrNull { it.id == alertId }
            ?: all.firstOrNull { it.userId == currentUserId && it.status == "ACTIVE" }
        if (target == null || target.userId != currentUserId) {
            showToast(
                "শুধু যিনি SOS পাঠিয়েছেন তিনিই 'নিরাপদ' মার্ক করতে পারবেন",
                "Only the person who sent this SOS can mark it safe"
            )
            return
        }

        liveLocationSharingJob?.cancel()
        liveLocationSharingJob = null
        _isLiveLocationSharingActive.value = false
        _activeUserSosAlertId.value = null
        bleMeshManager.stopSosBroadcast()
        audioVerifier.stopPlayback()

        bumpLocalSos(target) { it.copy(status = "RESOLVED", updatedAt = Timestamp.now()) }

        showToast("আপনি নিরাপদ • SOS বন্ধ হয়েছে ও হিস্ট্রিতে রাখা হয়েছে", "You're safe • SOS turned off and kept in history")

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch { repository.resolveOwnSosAlert(target.id) }
        }
    }

    /**
     * "False" report, one per account. Reaching the threshold never deletes the SOS or punishes a
     * sender who has evidence or responders; the alert simply shows as disputed until it expires.
     */
    fun flagFalseAlarm(alert: SosAlert) {
        if (alert.userId == currentUserId) {
            showToast("নিজের SOS বন্ধ করতে 'আমি নিরাপদ' চাপুন", "Use 'I'm safe' to close your own SOS")
            return
        }
        if (hasUserReportedFalseSos(alert)) {
            showToast("এই SOS-এ আপনি আগেই রিপোর্ট করেছেন", "You already reported this SOS")
            return
        }

        val distanceKm = alert.distanceFromKm(_userLatitude.value, _userLongitude.value)
        if (!hasUserInspectedEvidence(alert.id) && distanceKm > SOS_NOTIFICATION_RADIUS_KM) {
            // Remote reporters must at least look at the details first.
            _sosToInspect.value = alert
            showToast(
                "আপনি ${"%.1f".format(distanceKm)} km দূরে • আগে বিস্তারিত দেখুন, তারপর আবার রিপোর্ট করুন",
                "You are ${"%.1f".format(distanceKm)} km away • review the details first, then report again"
            )
            return
        }

        val updatedFalseSet = _reportedFalseSosIds.value + alert.id
        _reportedFalseSosIds.value = updatedFalseSet
        saveStringSet(KEY_REPORTED_FALSE_SOS_IDS, updatedFalseSet)

        val nextCount = alert.falseAlarmReports + 1L
        bumpLocalSos(alert) { it.copy(falseAlarmReports = nextCount, updatedAt = Timestamp.now()) }

        if (nextCount >= FALSE_SOS_DISPUTE_THRESHOLD) {
            val shielded = alert.isShieldedByEvidenceOrResponders
            _falseSosWarningBanner.value = if (shielded) {
                if (bn()) {
                    "${alert.senderName}-এর SOS-এ $nextCount টি ভুয়া রিপোর্ট, কিন্তু প্রমাণ/রেসপন্ডার থাকায় প্রেরকের ট্রাস্ট কাটা হবে না। ২৪ ঘণ্টা পর অটো-রিমুভ হবে।"
                } else {
                    "$nextCount false reports on ${alert.senderName}'s SOS, but evidence/responders protect the sender. It expires after 24h."
                }
            } else {
                if (bn()) {
                    "${alert.senderName}-এর SOS এখন 'বিতর্কিত' ($nextCount টি ভুয়া রিপোর্ট, কোনো রেসপন্ডার নেই)। ২৪ ঘণ্টা পর অটো-রিমুভ হবে।"
                } else {
                    "${alert.senderName}'s SOS is now disputed ($nextCount false reports, no responders). It expires after 24h."
                }
            }
        }
        showToast("রিপোর্ট নথিভুক্ত হয়েছে (মোট $nextCount)", "Report recorded (total $nextCount)")

        if (!isOfflineSession && repository != null) {
            viewModelScope.launch { repository.reportFalseAlarm(alert) }
        }
    }

    private fun prependCustomNotification(item: RokkhonNotificationItem) {
        _customNotifications.value = listOf(item) + _customNotifications.value.filterNot { it.id == item.id }
    }

    override fun onCleared() {
        super.onCleared()
        liveLocationSharingJob?.cancel()
        hardwareCountdownJob?.cancel()
        locationTracker?.stopLiveUpdates()
        audioVerifier.stopLiveMonitoring()
        audioVerifier.stopPlayback()
        bleMeshManager.stopMeshScanning()
    }

    companion object {
        private const val TAG = "RokkhonViewModel"
        const val APP_PREFS = "rokkhon_app_prefs"
        const val MAX_TRUSTED_CONTACTS = 3
        const val HARDWARE_SOS_CANCEL_SECONDS = 5
        private const val TOAST_DURATION_MS = 4500L
        private const val LIVE_LOCATION_PUSH_MS = 2_000L
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_AREA_KEY = "selected_area_key"
        private const val KEY_PLEDGED_IDS = "pledged_request_ids"
        private const val KEY_RESPONDED_SOS_IDS = "responded_sos_ids"
        private const val KEY_REPORTED_FALSE_SOS_IDS = "reported_false_sos_ids"
        private const val KEY_INSPECTED_EVIDENCE_SOS_IDS = "inspected_evidence_sos_ids"
        private const val KEY_READ_NOTIFICATION_IDS = "read_notification_ids"
        private const val KEY_STREAK_COUNT = "daily_streak_count"
        private const val KEY_LAST_CHECKIN_DAY = "last_checkin_day"
        private const val KEY_GUARDIAN_POINTS = "guardian_points"
        private const val KEY_TRUSTED_CONTACTS = "trusted_contacts"
        private const val KEY_OWN_NAME = "demo_profile_name"
        private const val KEY_OWN_BLOOD = "demo_profile_blood"
        private const val KEY_OWN_AVAILABLE = "demo_profile_available"
        private const val KEY_OWN_DISTRICT = "demo_profile_district"
        private const val KEY_OWN_PHONE = "demo_profile_phone"
        private const val KEY_OWN_LAST_DONATION = "demo_profile_last_donation"
        private const val KEY_PERSISTED_BLOOD_REQUESTS = "persisted_blood_requests_json_v1"
        private const val KEY_PERSISTED_SOS_ALERTS = "persisted_sos_alerts_json_v1"
        private const val KEY_DEVICE_HW_ID = "rokkhon_device_hw_id_v1"
        private const val KEY_ADMIN_REWARD_CHECKIN = "admin_reward_checkin_pts"
        private const val KEY_ADMIN_REWARD_PLEDGE = "admin_reward_pledge_pts"
        private const val KEY_ADMIN_REWARD_SOS = "admin_reward_sos_pts"
        private const val KEY_ADMIN_LOC_OFF_USERS = "admin_location_off_user_ids"
        private const val KEY_ADMIN_GLOBAL_LOC_OFF = "admin_global_location_off"
        private const val KEY_ADMIN_SUSPENDED_USERS = "admin_suspended_user_ids"
        private const val KEY_ADMIN_BANNED_DEVICES = "admin_banned_device_ids"
    }
}
