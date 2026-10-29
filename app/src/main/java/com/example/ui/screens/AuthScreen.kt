package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.FeatureFlags
import com.example.KEY_OTP_VERIFIED_NAME
import com.example.KEY_OTP_VERIFIED_PHONE
import com.example.R
import com.example.data.model.isValidBangladeshMobile
import com.example.ui.icons.UIcons
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.LifelineBlush
import com.example.ui.theme.RadarDarkMaroon
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.RokkhonViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * `default_web_client_id` is generated by the google-services plugin from google-services.json.
 * Looking it up by name keeps the project compiling
 * when that file is not present, instead of failing the build.
 */
private fun googleWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return context.getString(resId).takeIf { it.isNotBlank() }
}

internal fun normalizeBangladeshPhoneE164(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("880") && digits.length == 13 -> "+$digits"
        digits.startsWith("01") && digits.length == 11 -> "+88$digits"
        else -> "+880${digits.takeLast(10)}"
    }
}

/**
 * Real SMS verification through Firebase Phone Auth. The old screen computed the code on the
 * phone itself and showed it on screen, so anyone could sign in as any number and those
 * "accounts" never reached the cloud. Firebase sends the SMS and creates a real user.
 */
internal fun sendFirebaseOtp(
    activity: Activity,
    e164Phone: String,
    resendToken: PhoneAuthProvider.ForceResendingToken?,
    callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
) {
    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(e164Phone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
    if (resendToken != null) builder.setForceResendingToken(resendToken)
    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

/** Bangla message for a failed phone verification. */
internal fun phoneAuthErrorMessage(error: Throwable): String = when {
    error is FirebaseAuthInvalidCredentialsException && error.errorCode == "ERROR_INVALID_VERIFICATION_CODE" ->
        "কোডটি ভুল হয়েছে। SMS-এ আসা ৬ সংখ্যার কোড আবার লিখুন।"
    error is FirebaseAuthInvalidCredentialsException -> "নম্বরটি সঠিক নয়। ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর দিন।"
    error is FirebaseTooManyRequestsException -> "অনেকবার চেষ্টা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
    error.message?.contains("OPERATION_NOT_ALLOWED") == true || error.message?.contains("not enabled") == true ->
        "Firebase-এ মোবাইল নম্বর লগইন চালু নেই। অ্যাডমিনকে Firebase Console → Authentication → Phone চালু করতে বলুন।"
    error is FirebaseNetworkException -> "ইন্টারনেট সংযোগ নেই। সংযোগ দিয়ে আবার চেষ্টা করুন।"
    else -> "কোড পাঠানো/যাচাই করা যায়নি। আবার চেষ্টা করুন।"
}

/** Signs in with a verified phone credential and keeps the entered name on the Firebase account. */
private suspend fun signInWithPhoneCredential(
    credential: PhoneAuthCredential,
    displayName: String,
    e164Phone: String,
    context: Context
) {
    val result = Firebase.auth.signInWithCredential(credential).await()
    val user = result.user
    if (user != null && displayName.isNotBlank() && user.displayName.isNullOrBlank()) {
        runCatching { user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await() }
    }
    // Remembered only to pre-fill the donor profile form; sign-in itself is the Firebase user.
    context.getSharedPreferences(RokkhonViewModel.APP_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_OTP_VERIFIED_PHONE, e164Phone)
        .putString(KEY_OTP_VERIFIED_NAME, displayName.ifBlank { e164Phone })
        .apply()
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    if (credentialManager == null) {
        onUnauthenticated()
        return
    }
    val clientId = googleWebClientId(context) ?: run {
        onUnauthenticated()
        return
    }

    scope.launch {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val activityContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (t: Throwable) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    if (credentialManager == null) {
        onAuthError("এই ডিভাইসে গুগল সাইন-ইন সার্ভিস সাপোর্ট করছে না। উপরে মোবাইল নম্বর OTP দিয়ে লগইন করুন।")
        return
    }
    val clientId = googleWebClientId(context) ?: run {
        onAuthError("গুগল সাইন-ইন এই বিল্ডে কনফিগার করা নেই। মোবাইল নম্বর OTP দিয়ে লগইন করুন।")
        return
    }

    scope.launch {
        val activityContext = context.findActivity() ?: context
        // Two ways to ask Google for the account: the "Sign in with Google" sheet, and the plain
        // account picker. Some phones (older Play services, some OEMs) fail the first one, so the
        // second is tried before giving up.
        val options = listOf(
            GetSignInWithGoogleOption.Builder(serverClientId = clientId).build(),
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
        )
        var lastError: Throwable? = null
        for (option in options) {
            val idToken = try {
                val result = credentialManager.getCredential(
                    activityContext,
                    GetCredentialRequest.Builder().addCredentialOption(option).build()
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    lastError = IllegalStateException("credential type ${credential.type}")
                    null
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
                return@launch
            } catch (t: Throwable) {
                Log.w("Auth", "Google account request failed (${option::class.java.simpleName})", t)
                lastError = t
                null
            } ?: continue

            // Google gave us the account; now Firebase has to accept it.
            try {
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                onAuthSuccess()
            } catch (t: Throwable) {
                Log.e("Auth", "Firebase rejected the Google credential", t)
                onAuthError(googleSignInErrorMessage(t))
            }
            return@launch
        }
        Log.e("Auth", "Google Sign-In failed", lastError)
        onAuthError(googleSignInErrorMessage(lastError))
    }
}

/**
 * Says why Google sign-in failed, in words the user (or whoever set up Firebase) can act on,
 * with the technical reason in brackets so it can be reported.
 */
private fun googleSignInErrorMessage(error: Throwable?): String {
    val raw = listOfNotNull(error?.javaClass?.simpleName, error?.message).joinToString(": ").take(160)
    val text = raw.lowercase()
    val reason = when {
        error is NoCredentialException ->
            "এই ফোনে কোনো গুগল অ্যাকাউন্ট পাওয়া যায়নি। ফোনের Settings → Accounts-এ গুগল অ্যাকাউন্ট যোগ করে আবার চেষ্টা করুন।"
        error is FirebaseNetworkException || text.contains("network") ->
            "ইন্টারনেট সংযোগ নেই। সংযোগ দিয়ে আবার চেষ্টা করুন।"
        text.contains("28444") || text.contains("developer console") || text.contains("[10]") || text.contains("unregistered") ->
            "অ্যাপের SHA-1 এই Firebase প্রজেক্টে যোগ করা নেই, তাই গুগল লগইন আটকে গেছে। Firebase Console → Project settings → এই Android অ্যাপে SHA-1 যোগ করে নতুন google-services.json দিন।"
        text.contains("operation_not_allowed") || text.contains("not enabled") ->
            "Firebase-এ Google লগইন চালু নেই। Firebase Console → Authentication → Sign-in method → Google চালু করুন।"
        text.contains("invalid_idp_response") || text.contains("audience") || text.contains("invalid id token") ->
            "Google অ্যাকাউন্টটি এই Firebase প্রজেক্টের সাথে মেলেনি (Web client ID ভুল)। নতুন google-services.json দিন।"
        text.contains("user_disabled") || text.contains("disabled") ->
            "এই অ্যাকাউন্টটি বন্ধ করা আছে।"
        text.contains("account_exists_with_different_credential") ->
            "এই ইমেইলে অন্যভাবে (যেমন মোবাইল নম্বর) অ্যাকাউন্ট আছে। সেভাবে লগইন করুন।"
        else -> "গুগল সাইন-ইন সম্পন্ন হয়নি। মোবাইল নম্বর OTP দিয়েও লগইন করতে পারেন।"
    }
    return if (raw.isBlank()) reason else "$reason\n($raw)"
}

fun signOutRokkhon(
    context: Context,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    val credentialManager = runCatching { CredentialManager.create(context) }.getOrNull()
    val appPrefs = context.getSharedPreferences(RokkhonViewModel.APP_PREFS, Context.MODE_PRIVATE)
    appPrefs.edit()
        .remove(KEY_OTP_VERIFIED_PHONE)
        .remove(KEY_OTP_VERIFIED_NAME)
        .apply()
    runCatching { Firebase.auth.signOut() }
    scope.launch {
        try {
            credentialManager?.clearCredentialState(ClearCredentialStateRequest())
        } catch (t: Throwable) {
            Log.e("Auth", "Failed to clear credential state", t)
        } finally {
            onSignOutComplete()
        }
    }
}

/**
 * Minimalist Luxury Login Screen:
 * - Pure smooth vertical gradient top hero with zero background shapes.
 * - Text placed higher with generous line height and breathing room.
 * - Uncluttered bottom white sheet with Mobile Number OTP login and Google Sign-In.
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkipSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { runCatching { CredentialManager.create(context) }.getOrNull() }
    val appPrefs = remember { context.getSharedPreferences(RokkhonViewModel.APP_PREFS, Context.MODE_PRIVATE) }

    var isLoading by remember { mutableStateOf(false) }
    var infoNotice by remember { mutableStateOf<String?>(null) }

    // Mobile Number & Dedicated OTP Screen states
    var userNameInput by rememberSaveable { mutableStateOf("") }
    var mobileInput by rememberSaveable { mutableStateOf("") }
    var showSeparateOtpScreen by rememberSaveable { mutableStateOf(false) }
    var activeE164Phone by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpBusy by remember { mutableStateOf(false) }
    var otpServerError by remember { mutableStateOf<String?>(null) }
    val firebaseReady = remember { runCatching { com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false) }

    fun finishPhoneSignIn(credential: PhoneAuthCredential) {
        otpBusy = true
        otpServerError = null
        coroutineScope.launch {
            try {
                signInWithPhoneCredential(credential, userNameInput.trim(), activeE164Phone, context)
                otpBusy = false
                onAuthSuccess()
            } catch (t: Throwable) {
                Log.w("AuthScreen", "Phone sign-in failed", t)
                otpBusy = false
                otpServerError = phoneAuthErrorMessage(t)
            }
        }
    }

    val phoneCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Android read the SMS by itself: sign in without typing the code.
                finishPhoneSignIn(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Log.w("AuthScreen", "Phone verification failed", e)
                isLoading = false
                otpBusy = false
                val message = phoneAuthErrorMessage(e)
                if (showSeparateOtpScreen) otpServerError = message else infoNotice = message
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                isLoading = false
                otpBusy = false
                otpServerError = null
                showSeparateOtpScreen = true
            }
        }
    }

    fun requestOtp(resend: Boolean) {
        val activity = context.findActivity()
        if (!firebaseReady || activity == null) {
            infoNotice = "এই বিল্ডে Firebase কনফিগার করা নেই (google-services.json নেই), তাই লগইন করা যাচ্ছে না।"
            return
        }
        if (resend) otpBusy = true else isLoading = true
        sendFirebaseOtp(activity, activeE164Phone, if (resend) resendToken else null, phoneCallbacks)
    }

    LaunchedEffect(Unit) {
        // Without google-services.json there is no FirebaseApp; calling Firebase.auth would crash.
        if (!firebaseReady) return@LaunchedEffect
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = coroutineScope
        )
    }

    if (showSeparateOtpScreen) {
        OtpVerificationScreen(
            userName = userNameInput.trim(),
            e164Phone = activeE164Phone,
            isVerifying = otpBusy,
            serverError = otpServerError,
            onBackToPhoneInput = {
                showSeparateOtpScreen = false
                infoNotice = null
                otpServerError = null
            },
            onResendOtp = { requestOtp(resend = true) },
            onSubmitCode = { code ->
                if (verificationId.isBlank()) {
                    otpServerError = "আগে কোড পাঠান।"
                } else {
                    finishPhoneSignIn(PhoneAuthProvider.getCredential(verificationId, code))
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Pure Gradient Hero Section (No background shapes, elevated text with spacious breathing room)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color.White
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            // Top Bar: Brand + Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.rokkhon_wordmark_black),
                    contentDescription = "রক্ষণ",
                    modifier = Modifier.height(28.dp)
                )

                Surface(
                    color = RokkhonPalette.Subtle,
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable { onSkipSignIn() }
                        .testTag("top_skip_sign_in_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (FeatureFlags.SOS_ENABLED) "শুধু SOS" else "এখন না",
                            style = MaterialTheme.typography.labelLarge,
                            color = RokkhonPalette.Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = UIcons.ArrowForward,
                            contentDescription = "Skip Sign-In",
                            tint = RokkhonPalette.Ink,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Elevated Hero Copy with generous vertical spacing
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 22.dp)
            ) {
                Text(
                    text = "প্রতিটি পদক্ষেপ\nএকটি জীবন বাঁচাতে পারে।",
                    fontSize = 28.sp,
                    color = RokkhonPalette.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 39.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "আপনার এলাকার রক্তদাতাদের সাথে যুক্ত হোন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RokkhonPalette.Muted,
                    letterSpacing = 0.2.sp,
                    lineHeight = 22.sp
                )
            }
        }

        // Bottom Rounded Clean White Sheet (Uncluttered, spacious layout)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E2E2))
                )

                Spacer(modifier = Modifier.height(22.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "মোবাইল নম্বর দিয়ে লগইন",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF000000)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "আপনার নাম ও ১১-ডিজিটের বাংলাদেশি মোবাইল নম্বর দিন",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5E5E5E),
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = { userNameInput = it.take(40) },
                    leadingIcon = {
                        Icon(UIcons.Person, contentDescription = null, tint = IosSystemRed)
                    },
                    label = { Text("আপনার নাম") },
                    placeholder = { Text("যেমন: Rifat Hasan") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = mobileInput,
                    onValueChange = {
                        mobileInput = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14)
                    },
                    leadingIcon = {
                        Icon(UIcons.Call, contentDescription = null, tint = IosSystemRed)
                    },
                    label = { Text("মোবাইল নম্বর (01XXXXXXXXX)") },
                    placeholder = { Text("017XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_phone_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (!isValidBangladeshMobile(mobileInput)) {
                            infoNotice = "সঠিক ১১ ডিজিটের বাংলাদেশি মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)।"
                            return@Button
                        }
                        infoNotice = null
                        activeE164Phone = normalizeBangladeshPhoneE164(mobileInput)
                        requestOtp(resend = false)
                    },
                    enabled = !isLoading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosSystemRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("send_otp_button")
                ) {
                    Icon(UIcons.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OTP পাঠান",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (infoNotice != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = LifelineBlush,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = infoNotice ?: "",
                            color = Color(0xFF1F1F1F),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Clean Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE8E8E8))
                    Text(
                        text = "   অথবা   ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8A8A8A),
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE8E8E8))
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Google Sign-In CTA Button
                Button(
                    onClick = {
                        isLoading = true
                        infoNotice = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                infoNotice = msg
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                isLoading = false
                                infoNotice = "গুগল সাইন-ইন বাতিল হয়েছে। উপরে মোবাইল নম্বর OTP দিয়ে লগইন করুন।"
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF000000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "গুগল দিয়ে সাইন-ইন করুন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Guest Access Button
                OutlinedButton(
                    onClick = onSkipSignIn,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFE2E2E2)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF333333)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("skip_sign_in_button")
                ) {
                    Text(
                        text = if (FeatureFlags.SOS_ENABLED) "লগইন ছাড়া শুধু জরুরি SOS" else "লগইন ছাড়া দেখুন",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Separate Full-Screen OTP Verification Screen:
 * - Pure smooth crimson-burgundy gradient header with zero background shapes.
 * - Text positioned higher (`Alignment.CenterStart`) with generous line-height and breathing room.
 * - Bottom White Sheet uses ONLY the 6 interactive square boxes as the main input.
 */
@Composable
fun OtpVerificationScreen(
    userName: String,
    e164Phone: String,
    isVerifying: Boolean,
    serverError: String?,
    onBackToPhoneInput: () -> Unit,
    onResendOtp: () -> Unit,
    onSubmitCode: (String) -> Unit
) {
    BackHandler {
        onBackToPhoneInput()
    }

    var otpText by rememberSaveable { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Pure Crimson-Burgundy Gradient Screen (Zero background shapes, elevated & airy text)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color.White,
                            RadarDarkMaroon
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            // Top Navigation Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = RokkhonPalette.Subtle,
                    shape = CircleShape,
                    modifier = Modifier
                        .clickable { onBackToPhoneInput() }
                        .testTag("otp_screen_back_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = UIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = RokkhonPalette.Ink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ফিরে যান",
                            style = MaterialTheme.typography.labelLarge,
                            color = RokkhonPalette.Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Image(
                    painter = painterResource(R.drawable.rokkhon_wordmark_black),
                    contentDescription = "রক্ষণ",
                    modifier = Modifier.height(24.dp)
                )
            }

            // Elevated Text on the Red Screen with generous spacing
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 20.dp)
            ) {
                Text(
                    text = if (userName.isNotBlank()) "$userName  •  $e164Phone" else e164Phone,
                    style = MaterialTheme.typography.labelLarge,
                    color = RokkhonPalette.Muted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ওটিপি কোড যাচাই করুন",
                    fontSize = 28.sp,
                    color = RokkhonPalette.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$e164Phone নম্বরে SMS-এ আসা ৬ সংখ্যার কোডটি নিচের বক্সে লিখুন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RokkhonPalette.Muted,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 22.sp
                )
            }
        }

        // Bottom Clean White Sheet: ONLY the 6 Square Boxes as the main UI/UX input
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E2E2))
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Main Interactive 6-Square-Box Input
                BasicTextField(
                    value = TextFieldValue(
                        text = otpText,
                        selection = TextRange(otpText.length)
                    ),
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { it.isDigit() }.take(6)
                        otpText = filtered
                        otpError = null
                        if (filtered.length == 6 && !isVerifying) {
                            onSubmitCode(filtered)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (otpText.length == 6) {
                                if (!isVerifying) onSubmitCode(otpText)
                            } else {
                                otpError = "অনুগ্রহ করে ৬টি ঘরেই কোড লিখুন।"
                            }
                        }
                    ),
                    cursorBrush = SolidColor(IosSystemRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("otp_code_input"),
                    decorationBox = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 6) {
                                val char = otpText.getOrNull(i)?.toString() ?: ""
                                val isFilled = char.isNotEmpty()
                                val isFocusedBox = otpText.length == i || (otpText.length == 6 && i == 5)

                                Box(
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                isFilled -> LifelineBlush
                                                isFocusedBox -> Color(0xFFFFFFFF)
                                                else -> Color(0xFFF6F6F6)
                                            }
                                        )
                                        .border(
                                            width = if (isFocusedBox || isFilled) 2.dp else 1.2.dp,
                                            color = when {
                                                isFocusedBox -> IosSystemRed
                                                isFilled -> IosSystemRed.copy(alpha = 0.75f)
                                                else -> Color(0xFFDADADA)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFilled) {
                                        Text(
                                            text = char,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF000000)
                                        )
                                    } else if (isFocusedBox) {
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(2.5.dp)
                                                .clip(CircleShape)
                                                .background(IosSystemRed)
                                        )
                                    }
                                }
                            }
                        }
                    }
                )

                val shownError = otpError ?: serverError
                if (shownError != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = shownError,
                        color = IosSystemRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (otpText.length != 6) {
                            otpError = "অনুগ্রহ করে ৬টি বক্সেই OTP কোড লিখুন।"
                            return@Button
                        }
                        onSubmitCode(otpText)
                    },
                    enabled = !isVerifying,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IosSystemGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("verify_otp_button")
                ) {
                    Icon(UIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OTP যাচাই ও লগইন সম্পন্ন করুন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            otpText = ""
                            otpError = null
                            onResendOtp()
                            focusRequester.requestFocus()
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("resend_otp_button")
                    ) {
                        Icon(
                            imageVector = UIcons.Refresh,
                            contentDescription = null,
                            tint = IosSystemRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "পুনরায় কোড পাঠান",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IosSystemRed
                        )
                    }

                    OutlinedButton(
                        onClick = onBackToPhoneInput,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("change_number_button")
                    ) {
                        Text(
                            text = "নম্বর পরিবর্তন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF000000)
                        )
                    }
                }
            }
        }
    }
}
