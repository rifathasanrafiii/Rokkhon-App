package com.example

import android.Manifest
import android.content.Context
import android.os.Build
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ble.BleSosMeshManager
import com.example.data.hardware.HardwareSosDetector
import com.example.data.location.LocationTracker
import com.example.data.model.AppLanguage
import com.example.data.notification.RokkhonNotificationHelper
import com.example.data.repository.RokkhonRepository
import com.example.ui.components.HardwareSosCountdownOverlay
import com.example.ui.components.LiveCameraAudioSosDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DonorProfileScreen
import com.example.ui.screens.HomeMatchHubScreen
import com.example.ui.screens.PostBloodRequestDialog
import com.example.ui.screens.RadarMapHubScreen
import com.example.ui.screens.RokkhonSplashScreen
import com.example.ui.screens.RokkhonTutorialScreen
import com.example.ui.screens.RequestsScreen
import com.example.ui.screens.SosGuardianAndBleScreen
import com.example.ui.screens.signOutRokkhon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Rk
import com.example.ui.viewmodel.RokkhonTab
import com.example.ui.viewmodel.RokkhonViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
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

/** Without google-services.json Firebase is not initialised; the app then runs in demo mode instead of crashing. */
internal fun isFirebaseConfigured(context: Context): Boolean =
    try {
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (_: Exception) {
        false
    }

private const val KEY_TUTORIAL_DONE = "tutorial_done"
private const val KEY_GUEST_MODE = "guest_mode_chosen"

@Composable
fun RokkhonAppRoot(
    hardwareTriggerMethod: String? = null,
    recentPowerTaps: Int = 0,
    onConsumeHardwareTrigger: () -> Unit = {},
    onSimulatePowerTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences(RokkhonViewModel.APP_PREFS, Context.MODE_PRIVATE) }
    val firebaseReady = remember { isFirebaseConfigured(context) }
    val auth: FirebaseAuth? = remember { if (firebaseReady) Firebase.auth else null }
    // Remember the flow: creating a new flow on every recomposition re-registers the listener each time.
    val authFlow = remember(auth) { auth?.authStateFlow() }
    val noAuthFlow = remember { MutableStateFlow<FirebaseUser?>(null) }
    val currentUser by (authFlow ?: noAuthFlow).collectAsStateWithLifecycle(initialValue = auth?.currentUser)

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showTutorial by rememberSaveable { mutableStateOf(!appPrefs.getBoolean(KEY_TUTORIAL_DONE, false)) }
    // The old code started with "guest = true", so the sign-in screen was never shown.
    var isGuestChosen by rememberSaveable {
        mutableStateOf(!firebaseReady || appPrefs.getBoolean(KEY_GUEST_MODE, false))
    }
    val user = currentUser

    LaunchedEffect(hardwareTriggerMethod) {
        // An emergency press skips splash/tutorial/sign-in and goes straight to the SOS flow.
        if (hardwareTriggerMethod != null) {
            showSplash = false
            showTutorial = false
            if (user == null) isGuestChosen = true
        }
    }

    when {
        showSplash -> RokkhonSplashScreen(onSplashFinished = { showSplash = false })
        showTutorial -> RokkhonTutorialScreen(
            onFinishTutorial = {
                appPrefs.edit().putBoolean(KEY_TUTORIAL_DONE, true).apply()
                showTutorial = false
            }
        )
        user == null && !isGuestChosen -> AuthScreen(
            onAuthSuccess = {
                appPrefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
                isGuestChosen = false
            },
            onSkipSignIn = {
                appPrefs.edit().putBoolean(KEY_GUEST_MODE, true).apply()
                isGuestChosen = true
            }
        )
        else -> RokkhonAuthenticatedShell(
            currentUserId = user?.uid ?: "guest_rokkhon_bd",
            currentUserName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")
                ?: if (firebaseReady) "Guest" else "Demo User",
            isGuestSession = user == null,
            useCloud = firebaseReady && user != null,
            canSignIn = firebaseReady,
            hardwareTriggerMethod = hardwareTriggerMethod,
            recentPowerTaps = recentPowerTaps,
            onConsumeHardwareTrigger = onConsumeHardwareTrigger,
            onSimulatePowerTap = onSimulatePowerTap,
            onOpenTutorial = { showTutorial = true },
            onExitGuestOrSignOut = {
                appPrefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
                isGuestChosen = !firebaseReady
            }
        )
    }
}

private fun runtimePermissionsToAsk(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_ADVERTISE
        perms += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
    }
    return perms.toTypedArray()
}

@Composable
fun RokkhonAuthenticatedShell(
    currentUserId: String,
    currentUserName: String,
    isGuestSession: Boolean = false,
    useCloud: Boolean = false,
    canSignIn: Boolean = true,
    hardwareTriggerMethod: String? = null,
    recentPowerTaps: Int = 0,
    onConsumeHardwareTrigger: () -> Unit = {},
    onSimulatePowerTap: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    onExitGuestOrSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()

    val viewModel: RokkhonViewModel = viewModel(
        key = "${currentUserId}_${isGuestSession}_$useCloud",
        factory = viewModelFactory {
            initializer {
                val repository = if (useCloud) {
                    val databaseId = appContext.getString(R.string.firestore_database_id)
                    RokkhonRepository(FirebaseFirestore.getInstance(databaseId))
                } else {
                    null
                }
                RokkhonViewModel(
                    repository = repository,
                    bleMeshManager = BleSosMeshManager.get(appContext),
                    appContext = appContext,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    isGuestSession = isGuestSession,
                    locationTracker = LocationTracker(appContext)
                )
            }
        }
    )

    val language by viewModel.language.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val toastBanner by viewModel.toastBanner.collectAsStateWithLifecycle()
    val donorsState by viewModel.donorNodesState.collectAsStateWithLifecycle()
    val pendingHardwareSos by viewModel.pendingHardwareSos.collectAsStateWithLifecycle()
    val liveDb by viewModel.audioVerifier.currentDecibels.collectAsStateWithLifecycle()

    val myDonor = (donorsState as? UiState.Success)?.data?.firstOrNull { it.userId == currentUserId }

    var showSosVerificationDialog by remember { mutableStateOf(false) }
    var showPostBloodRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hardwareTriggerMethod) {
        if (hardwareTriggerMethod != null) {
            viewModel.startHardwareSosCountdown(hardwareTriggerMethod)
            onConsumeHardwareTrigger()
        }
    }

    // Ask once for location, Bluetooth and notification permissions, then restart BLE/GPS.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.onPermissionsChanged()
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(RokkhonViewModel.APP_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("startup_permissions_asked", false)) {
            prefs.edit().putBoolean("startup_permissions_asked", true).apply()
            permissionLauncher.launch(runtimePermissionsToAsk())
        }
    }

    if (currentTab != RokkhonTab.HOME_MATCH) {
        BackHandler {
            viewModel.selectTab(RokkhonTab.HOME_MATCH)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Rk.Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            RokkhonBottomBar(
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
                    onOpenBloodRequestDialog = { showPostBloodRequestDialog = true },
                    onOpenSosVerification = { showSosVerificationDialog = true }
                )
                RokkhonTab.RADAR_MAP -> RadarMapHubScreen(
                    viewModel = viewModel,
                    onOpenBloodRequestDialog = { showPostBloodRequestDialog = true }
                )
                RokkhonTab.SOS_GUARDIAN -> SosGuardianAndBleScreen(
                    viewModel = viewModel,
                    recentPowerTaps = recentPowerTaps,
                    onSimulatePowerTap = onSimulatePowerTap,
                    onOpenSosVerification = { showSosVerificationDialog = true }
                )
                RokkhonTab.REQUESTS -> RequestsScreen(
                    viewModel = viewModel,
                    onOpenBloodRequestDialog = { showPostBloodRequestDialog = true }
                )
                RokkhonTab.DONOR_PROFILE -> DonorProfileScreen(
                    viewModel = viewModel,
                    canSignIn = canSignIn,
                    onOpenTutorial = onOpenTutorial,
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

            // Top banner: neutral dark for info so it is not confused with an SOS alarm.
            AnimatedVisibility(
                visible = toastBanner != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = Color(0xFF231A1C),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toast_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toastBanner ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissToastBanner() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (language == AppLanguage.BENGALI) "বন্ধ করুন" else "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingHardwareSos?.let { pending ->
        HardwareSosCountdownOverlay(
            language = language,
            pending = pending,
            liveDecibels = liveDb,
            onCancel = { viewModel.cancelHardwareSosCountdown() },
            onSendNow = { viewModel.sendHardwareSosNow() }
        )
    }

    if (showSosVerificationDialog) {
        LiveCameraAudioSosDialog(
            language = language,
            userBloodGroup = myDonor?.bloodGroup ?: "O+",
            districtName = selectedDistrict.nameEn,
            triggerMethod = "ONE_TAP_SOS",
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
            defaultPhone = myDonor?.contactPhone.orEmpty(),
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

/**
 * Floating bottom bar (the original Rokkhon layout): Home, Find, centre "+" to post a blood
 * request, SOS, Profile. Requests open from Home ("সব দেখুন").
 */
@Composable
private fun RokkhonBottomBar(
    currentTab: RokkhonTab,
    language: AppLanguage,
    onSelectTab: (RokkhonTab) -> Unit,
    onPlusClick: () -> Unit
) {
    val isBn = language == AppLanguage.BENGALI
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(36.dp),
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Rk.Line),
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.HOME_MATCH || currentTab == RokkhonTab.REQUESTS,
                    icon = Icons.Outlined.Home,
                    selectedIcon = Icons.Filled.Home,
                    label = if (isBn) "হোম" else "Home",
                    onClick = { onSelectTab(RokkhonTab.HOME_MATCH) },
                    modifier = Modifier.weight(1f).testTag("nav_tab_home")
                )
                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.RADAR_MAP,
                    icon = Icons.Outlined.Map,
                    selectedIcon = Icons.Filled.Map,
                    label = if (isBn) "খুঁজুন" else "Find",
                    onClick = { onSelectTab(RokkhonTab.RADAR_MAP) },
                    modifier = Modifier.weight(1f).testTag("nav_tab_map")
                )
                // Centre "+" = post a blood request
                Box(modifier = Modifier.weight(0.95f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-8).dp)
                            .size(58.dp)
                            .shadow(10.dp, CircleShape, spotColor = Rk.Crimson)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFE5283F), Rk.Crimson, Rk.CrimsonDark)))
                            .border(3.dp, Color.White, CircleShape)
                            .clickable(role = Role.Button) { onPlusClick() }
                            .testTag("nav_center_plus_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (isBn) "রক্তের আবেদন করুন" else "Request blood",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.SOS_GUARDIAN,
                    icon = Icons.Outlined.Shield,
                    selectedIcon = Icons.Filled.Shield,
                    label = "SOS",
                    onClick = { onSelectTab(RokkhonTab.SOS_GUARDIAN) },
                    modifier = Modifier.weight(1f).testTag("nav_tab_sos")
                )
                RokkhonBottomNavItem(
                    selected = currentTab == RokkhonTab.DONOR_PROFILE,
                    icon = Icons.Outlined.Person,
                    selectedIcon = Icons.Filled.Person,
                    label = if (isBn) "প্রোফাইল" else "Profile",
                    onClick = { onSelectTab(RokkhonTab.DONOR_PROFILE) },
                    modifier = Modifier.weight(1f).testTag("nav_tab_profile")
                )
            }
        }
    }
}

@Composable
private fun RokkhonBottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (selected) Rk.Rose else Color.Transparent)
            .clickable(role = Role.Tab) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else icon,
            contentDescription = null,
            tint = if (selected) Rk.Crimson else Rk.Muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = if (selected) Rk.Crimson else Rk.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
