package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ble.BleSosMeshManager
import com.example.data.hardware.HardwareSosDetector
import com.example.data.model.AppLanguage
import com.example.data.notification.RokkhonNotificationHelper
import com.example.data.repository.RokkhonRepository
import com.example.ui.components.LiveCameraAudioSosDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DonorProfileScreen
import com.example.ui.screens.HomeMatchHubScreen
import com.example.ui.screens.PostBloodRequestDialog
import com.example.ui.screens.RadarMapHubScreen
import com.example.ui.screens.RokkhonSplashScreen
import com.example.ui.screens.RokkhonTutorialScreen
import com.example.ui.screens.SosGuardianAndBleScreen
import com.example.ui.screens.signOutRokkhon
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {

    private lateinit var hardwareSosDetector: HardwareSosDetector
    private val _pendingHardwareSosMethod = MutableStateFlow<String?>(null)
    val pendingHardwareSosMethod: StateFlow<String?> = _pendingHardwareSosMethod.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RokkhonNotificationHelper.ensureChannel(this)

        hardwareSosDetector = HardwareSosDetector(
            context = this,
            onHardwareSosTriggered = { triggerMethod ->
                _pendingHardwareSosMethod.value = triggerMethod
            }
        )
        hardwareSosDetector.startListening()

        setContent {
            MyApplicationTheme {
                val hardwareTrigger by pendingHardwareSosMethod.collectAsStateWithLifecycle()
                val recentPowerTaps by hardwareSosDetector.recentPowerTapCount.collectAsStateWithLifecycle()

                RokkhonAppRoot(
                    hardwareTriggerMethod = hardwareTrigger,
                    recentPowerTaps = recentPowerTaps,
                    onConsumeHardwareTrigger = { _pendingHardwareSosMethod.value = null },
                    onSimulatePowerTap = { hardwareSosDetector.recordSimulatedPowerButtonTap() }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::hardwareSosDetector.isInitialized && hardwareSosDetector.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::hardwareSosDetector.isInitialized) {
            hardwareSosDetector.stopListening()
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun RokkhonAppRoot(
    auth: FirebaseAuth = Firebase.auth,
    hardwareTriggerMethod: String? = null,
    recentPowerTaps: Int = 0,
    onConsumeHardwareTrigger: () -> Unit = {},
    onSimulatePowerTap: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var showSplash by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    var isGuestSkipped by rememberSaveable { mutableStateOf(true) }
    val user = currentUser

    LaunchedEffect(hardwareTriggerMethod) {
        if (hardwareTriggerMethod != null) {
            showSplash = false
            showTutorial = false
            if (user == null && !isGuestSkipped) {
                isGuestSkipped = true
            }
        }
    }

    when {
        showSplash -> {
            RokkhonSplashScreen(
                onSplashFinished = { showSplash = false }
            )
        }
        showTutorial -> {
            RokkhonTutorialScreen(
                onFinishTutorial = { showTutorial = false }
            )
        }
        user == null && !isGuestSkipped -> {
            AuthScreen(
                onAuthSuccess = { isGuestSkipped = false },
                onSkipSignIn = { isGuestSkipped = true }
            )
        }
        else -> {
            RokkhonAuthenticatedShell(
                currentUserId = user?.uid ?: "guest_rokkhon_bd",
                currentUserName = user?.displayName
                    ?: user?.email?.substringBefore("@")
                    ?: "Rifat R.",
                isGuestSession = (user == null),
                hardwareTriggerMethod = hardwareTriggerMethod,
                recentPowerTaps = recentPowerTaps,
                onConsumeHardwareTrigger = onConsumeHardwareTrigger,
                onSimulatePowerTap = onSimulatePowerTap,
                onOpenTutorial = { showTutorial = true },
                onExitGuestOrSignOut = {
                    isGuestSkipped = false
                }
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun RokkhonAuthenticatedShell(
    currentUserId: String,
    currentUserName: String,
    isGuestSession: Boolean = false,
    hardwareTriggerMethod: String? = null,
    recentPowerTaps: Int = 0,
    onConsumeHardwareTrigger: () -> Unit = {},
    onSimulatePowerTap: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    onExitGuestOrSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: RokkhonViewModel = viewModel(
        key = "${currentUserId}_${isGuestSession}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RokkhonViewModel(
                    repository = RokkhonRepository(db),
                    bleMeshManager = BleSosMeshManager(app),
                    appContext = app,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    isGuestSession = isGuestSession
                )
            }
        }
    )

    val language by viewModel.language.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val toastBanner by viewModel.toastBanner.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()

    val myDonor = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == currentUserId }

    var showSosVerificationDialog by remember { mutableStateOf(false) }
    var activeSosTriggerMethod by remember { mutableStateOf("ONE_TAP_SOS") }
    var showPostBloodRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hardwareTriggerMethod) {
        if (hardwareTriggerMethod != null) {
            activeSosTriggerMethod = hardwareTriggerMethod
            val (autoCategory, autoNote) = viewModel.autoDetectEmergencyCategory(hardwareTriggerMethod)
            viewModel.audioVerifier.startLiveMonitoring(coroutineScope)
            viewModel.triggerVerifiedEmergencySos(
                bloodGroup = myDonor?.bloodGroup ?: "O+",
                emergencyType = autoCategory,
                district = selectedDistrict.nameEn,
                hasLiveCamera = true,
                hasLiveAudio = true,
                peakDecibels = 84,
                verificationNote = "$autoNote ($hardwareTriggerMethod)",
                triggerMethod = hardwareTriggerMethod
            )
            viewModel.selectTab(RokkhonTab.SOS_GUARDIAN)
            onConsumeHardwareTrigger()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null && loc.latitude in 20.5..26.7 && loc.longitude in 88.0..92.7) {
                            viewModel.autoSelectAreaFromPermission(loc.latitude, loc.longitude, notifyUser = true)
                        }
                    }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null && loc.latitude in 20.5..26.7 && loc.longitude in 88.0..92.7) {
                            viewModel.autoSelectAreaFromPermission(loc.latitude, loc.longitude, notifyUser = false)
                        }
                    }
            } catch (_: Exception) {
            }
        }
    }

    if (currentTab != RokkhonTab.HOME_MATCH) {
        BackHandler {
            viewModel.selectTab(RokkhonTab.HOME_MATCH)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFFAF8F8),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            RokkhonFloatingBottomBar(
                currentTab = currentTab,
                language = language,
                onSelectTab = { viewModel.selectTab(it) },
                onPlusClick = { showPostBloodRequestDialog = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                RokkhonTab.HOME_MATCH -> HomeMatchHubScreen(
                    viewModel = viewModel,
                    recentPowerTaps = recentPowerTaps,
                    onSimulatePowerTap = onSimulatePowerTap,
                    onOpenBloodRequestDialog = { showPostBloodRequestDialog = true },
                    onOpenSosVerification = {
                        activeSosTriggerMethod = "ONE_TAP_SOS"
                        showSosVerificationDialog = true
                    },
                    onOpenTutorial = onOpenTutorial
                )
                RokkhonTab.RADAR_MAP -> RadarMapHubScreen(
                    viewModel = viewModel,
                    recentPowerTaps = recentPowerTaps,
                    onSimulatePowerTap = onSimulatePowerTap,
                    onOpenSosVerification = {
                        activeSosTriggerMethod = "ONE_TAP_SOS"
                        showSosVerificationDialog = true
                    },
                    onOpenBloodRequestDialog = { showPostBloodRequestDialog = true }
                )
                RokkhonTab.SOS_GUARDIAN -> SosGuardianAndBleScreen(
                    viewModel = viewModel,
                    recentPowerTaps = recentPowerTaps,
                    onSimulatePowerTap = onSimulatePowerTap,
                    onOpenSosVerification = {
                        activeSosTriggerMethod = "ONE_TAP_SOS"
                        showSosVerificationDialog = true
                    }
                )
                RokkhonTab.DONOR_PROFILE -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    DonorProfileScreen(
                        viewModel = viewModel,
                        onSignOut = {
                            if (isGuestSession) {
                                onExitGuestOrSignOut()
                            } else {
                                signOutRokkhon(
                                    context = context,
                                    onSignOutComplete = onExitGuestOrSignOut,
                                    scope = coroutineScope
                                )
                            }
                        }
                    )
                }
            }

            // Floating Toast Notification Banner at Top
            AnimatedVisibility(
                visible = toastBanner != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = IosSystemRed,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toastBanner ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissToastBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSosVerificationDialog) {
        LiveCameraAudioSosDialog(
            language = language,
            userBloodGroup = myDonor?.bloodGroup ?: "O+",
            districtName = selectedDistrict.nameEn,
            triggerMethod = activeSosTriggerMethod,
            audioVerifier = viewModel.audioVerifier,
            onDismiss = { showSosVerificationDialog = false },
            onDispatchVerifiedSos = { emergencyType, hasCam, hasAudio, peakDb, note, triggerMethod ->
                showSosVerificationDialog = false
                viewModel.triggerVerifiedEmergencySos(
                    bloodGroup = myDonor?.bloodGroup ?: "O+",
                    emergencyType = emergencyType,
                    district = selectedDistrict.nameEn,
                    hasLiveCamera = hasCam,
                    hasLiveAudio = hasAudio,
                    peakDecibels = peakDb,
                    verificationNote = note,
                    triggerMethod = triggerMethod
                )
            }
        )
    }

    if (showPostBloodRequestDialog) {
        PostBloodRequestDialog(
            language = language,
            defaultDistrict = selectedDistrict.nameEn,
            defaultHospital = selectedDistrict.defaultHospitalEn,
            onDismiss = { showPostBloodRequestDialog = false },
            onSubmit = { patientName, bg, units, hb, problem, hospital, ward, neededTime, dist, phone, urgency ->
                showPostBloodRequestDialog = false
                viewModel.postUrgentBloodRequest(
                    patientName = patientName,
                    bloodGroup = bg,
                    unitsNeeded = units,
                    hemoglobinLevel = hb,
                    patientProblem = problem,
                    hospitalName = hospital,
                    wardCabinNumber = ward,
                    neededDateTime = neededTime,
                    district = dist,
                    contactPhone = phone,
                    urgencyLevel = urgency
                )
            }
        )
    }
}

@Composable
private fun RokkhonFloatingBottomBar(
    currentTab: RokkhonTab,
    language: AppLanguage,
    onSelectTab: (RokkhonTab) -> Unit,
    onPlusClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAF8F8))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(36.dp),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.HOME_MATCH,
                    icon = Icons.Default.Home,
                    label = if (language == AppLanguage.BENGALI) "হোম" else "Home",
                    onClick = { onSelectTab(RokkhonTab.HOME_MATCH) },
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("nav_tab_home")
                )

                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.RADAR_MAP,
                    icon = Icons.Default.Search,
                    label = if (language == AppLanguage.BENGALI) "খুঁজুন" else "Find",
                    onClick = { onSelectTab(RokkhonTab.RADAR_MAP) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_map")
                )

                // Center Crimson '+' Circle Action Button
                Box(
                    modifier = Modifier.weight(0.95f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0xFFC8102E),
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .offset(y = (-6).dp)
                            .size(52.dp)
                            .clickable { onPlusClick() }
                            .testTag("nav_center_plus_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Request",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.SOS_GUARDIAN,
                    icon = Icons.Default.ChatBubbleOutline,
                    label = "SOS + BLE",
                    onClick = { onSelectTab(RokkhonTab.SOS_GUARDIAN) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_sos")
                )

                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.DONOR_PROFILE,
                    icon = Icons.Default.PersonOutline,
                    label = if (language == AppLanguage.BENGALI) "প্রোফাইল" else "Profile",
                    onClick = { onSelectTab(RokkhonTab.DONOR_PROFILE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("nav_tab_profile")
                )
            }
        }
    }
}

@Composable
private fun RokkhonBottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRed = Color(0xFFB80F24)
    val inactiveGray = Color(0xFF6E6265)
    Surface(
        color = if (selected) Color(0xFFFBE9EB) else Color.Transparent,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeRed else inactiveGray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = if (selected) activeRed else inactiveGray,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

