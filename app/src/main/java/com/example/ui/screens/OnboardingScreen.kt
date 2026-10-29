package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.isValidBangladeshMobile
import com.example.data.profile.UserProfile
import com.example.data.profile.UserProfileStore
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class OnboardStep { NAME, BLOOD, PHONE }

/**
 * After sign-in: name, blood group and a verified mobile number. Steps already answered (say a
 * phone sign-in, or a donor profile) are skipped. The number is verified with an SMS code and
 * added to the same account, so people can add this account to their SOS by number.
 */
@Composable
fun OnboardingScreen(initial: UserProfile, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = remember {
        buildList {
            if (initial.name.isBlank()) add(OnboardStep.NAME)
            if (initial.bloodGroup == null) add(OnboardStep.BLOOD)
            if (initial.phone == null) add(OnboardStep.PHONE)
        }.ifEmpty { listOf(OnboardStep.BLOOD) }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var blood by rememberSaveable { mutableStateOf(initial.bloodGroup) }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phoneFailed by rememberSaveable { mutableStateOf(false) }
    var resendIn by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val codeStep = step == OnboardStep.PHONE && verificationId != null

    fun finish(markDone: Boolean) {
        busy = true
        scope.launch {
            runCatching { UserProfileStore.save(context, name.trim(), blood ?: return@runCatching, markDone) }
            busy = false
            onDone()
        }
    }

    fun next() {
        error = null
        if (index < steps.lastIndex) index++ else finish(markDone = true)
    }

    fun linkNumber(credential: PhoneAuthCredential) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        busy = true
        scope.launch {
            try {
                user.linkWithCredential(credential).await()
                // A fresh token carries the number, which the SOS lookup rules check.
                runCatching { user.getIdToken(true).await() }
                busy = false
                next()
            } catch (e: Exception) {
                busy = false
                phoneFailed = true
                error = if (e is FirebaseAuthUserCollisionException) "এই নম্বর অন্য একটি অ্যাকাউন্টে আছে" else phoneAuthErrorMessage(e)
            }
        }
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = linkNumber(credential)
            override fun onVerificationFailed(e: FirebaseException) {
                busy = false
                phoneFailed = true
                error = phoneAuthErrorMessage(e)
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                busy = false
                verificationId = id
                resendToken = token
                resendIn = 60
            }
        }
    }

    fun sendCode(resend: Boolean) {
        val activity = context.findActivity() ?: return
        error = null
        busy = true
        sendFirebaseOtp(activity, normalizeBangladeshPhoneE164(phone), if (resend) resendToken else null, callbacks)
    }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1_000)
            resendIn--
        }
    }

    BackHandler(enabled = index > 0 || codeStep) {
        error = null
        if (codeStep) { verificationId = null; code = "" } else index--
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime))
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .testTag("onboarding")
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index > 0 || codeStep) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(RokkhonPalette.Subtle).springClick {
                        error = null
                        if (codeStep) { verificationId = null; code = "" } else index--
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(UIcons.ArrowBack, contentDescription = null, tint = RokkhonPalette.Ink, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.weight(1f))
            if (steps.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.width(if (i == index) 22.dp else 8.dp).height(8.dp).clip(CircleShape)
                            .background(if (i <= index) RokkhonPalette.Primary else RokkhonPalette.Divider))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                codeStep -> "কোড লিখুন"
                step == OnboardStep.NAME -> "আপনার নাম"
                step == OnboardStep.BLOOD -> "রক্তের গ্রুপ"
                else -> "মোবাইল নম্বর"
            },
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = RokkhonPalette.Ink
        )
        if (codeStep) {
            Text(normalizeBangladeshPhoneE164(phone), fontSize = 15.sp, color = RokkhonPalette.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))

        when {
            codeStep -> BigField(code, { code = it.filter(Char::isDigit).take(6) }, "••••••", KeyboardType.NumberPassword, "onboarding_code", center = true)
            step == OnboardStep.NAME -> BigField(name, { name = it.take(60) }, "নাম", KeyboardType.Text, "onboarding_name")
            step == OnboardStep.BLOOD -> BloodTiles(blood) { blood = it }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(60.dp).clip(RoundedCornerShape(16.dp)).background(RokkhonPalette.Subtle).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+88", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink) }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BigField(phone, { phone = it.filter(Char::isDigit).take(11) }, "01XXXXXXXXX", KeyboardType.Phone, "onboarding_phone")
                }
            }
        }

        if (codeStep) {
            Text(
                if (resendIn > 0) "আবার পাঠান · ${bnDigits(resendIn)}" else "আবার পাঠান",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = if (resendIn > 0) RokkhonPalette.Faint else RokkhonPalette.Ink,
                modifier = Modifier.padding(top = 14.dp).springClick(enabled = resendIn == 0 && !busy) { sendCode(resend = true) }.padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        error?.let {
            Text(it, fontSize = 13.5.sp, color = RokkhonPalette.Sos, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("onboarding_error"))
        }
        val ready = when {
            codeStep -> code.length == 6
            step == OnboardStep.NAME -> name.trim().length >= 2
            step == OnboardStep.BLOOD -> blood != null
            else -> isValidBangladeshMobile(phone)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (ready && !busy) RokkhonPalette.Primary else RokkhonPalette.Divider)
                .springClick(enabled = ready && !busy) {
                    when {
                        codeStep -> linkNumber(PhoneAuthProvider.getCredential(verificationId!!, code))
                        step == OnboardStep.PHONE -> sendCode(resend = false)
                        else -> next()
                    }
                }
                .testTag("onboarding_next"),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Text(
                when {
                    codeStep -> "যাচাই করুন"
                    step == OnboardStep.PHONE -> "কোড পাঠান"
                    index == steps.lastIndex -> "শুরু করুন"
                    else -> "পরবর্তী"
                },
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (ready) Color.White else RokkhonPalette.Muted
            )
        }
        // Only when the SMS code could not be sent or checked, so a Firebase problem never locks the app.
        if (step == OnboardStep.PHONE && phoneFailed && blood != null && name.isNotBlank()) {
            Text(
                "পরে করব", fontSize = 14.sp, color = RokkhonPalette.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).springClick(enabled = !busy) { finish(markDone = false) }.padding(8.dp).testTag("onboarding_later")
            )
        }
    }
}

@Composable
private fun BigField(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, testTag: String, center: Boolean = false) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(testTag) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(RokkhonPalette.Subtle).padding(horizontal = 18.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 18.sp, color = RokkhonPalette.Faint, letterSpacing = if (center) 6.sp else 0.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = if (center) 24.sp else 18.sp, color = RokkhonPalette.Ink, fontWeight = FontWeight.SemiBold,
                letterSpacing = if (center) 8.sp else 0.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start
            ),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(testTag)
        )
    }
}

@Composable
private fun BloodTiles(selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-").chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { bg ->
                    val sel = bg == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) RokkhonPalette.Primary else RokkhonPalette.Subtle)
                            .springClick(pressedScale = 0.94f) { onSelect(bg) }
                            .testTag("onboarding_blood_$bg"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(bg, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else RokkhonPalette.Ink)
                    }
                }
            }
        }
    }
}
