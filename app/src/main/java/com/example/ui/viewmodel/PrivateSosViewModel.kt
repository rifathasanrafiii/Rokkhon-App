package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TrustedContact
import com.example.data.model.isValidBangladeshMobile
import com.example.data.sos.GrantStatus
import com.example.data.sos.Live
import com.example.data.sos.PrivateSosService
import com.example.data.sos.PushStatus
import com.example.data.sos.SmsStatus
import com.example.data.sos.SosAccount
import com.example.data.sos.SosDatabase
import com.example.data.sos.SosGrant
import com.example.data.sos.SosHistoryItem
import com.example.data.sos.SosMessagingService
import com.example.data.sos.SosNotifier
import com.example.data.sos.SosNotifyClient
import com.example.data.sos.SosPerms
import com.example.data.sos.SosRepository
import com.example.data.sos.SosRuntime
import com.example.data.sos.SosSession
import com.example.data.sos.SosSms
import com.example.data.sos.SosSmsText
import com.example.data.sos.TrustedSmsContacts
import com.example.data.sos.WatchedSos
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface AccountSearch {
    data object Idle : AccountSearch
    data object Searching : AccountSearch
    data class Found(val account: SosAccount, val existing: SosGrant?) : AccountSearch
    data object NotFound : AccountSearch
    data object Invalid : AccountSearch
    data object Self : AccountSearch
}

/**
 * Private SOS for the signed-in account: who may see your SOS, starting/ending it, and the SOS of
 * people who trust you. Separate from [RokkhonViewModel] so blood-donor features are untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivateSosViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication()
    private val settings = app.getSharedPreferences("rokkhon_private_sos_settings", Context.MODE_PRIVATE)

    val repo: SosRepository? = run {
        val ready = runCatching { FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false)
        if (!ready || FirebaseAuth.getInstance().currentUser == null) null
        else SosDatabase.get(app)?.let { SosRepository(it, FirebaseAuth.getInstance()) }
    }

    /** False when not signed in, or Realtime Database is not set up yet in the Firebase project. */
    val available: Boolean = repo != null
    val signedIn: Boolean = runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
    val myUid: String? = repo?.myUid

    val viewers: StateFlow<List<SosGrant>> = (repo?.observeMyViewers() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grantsToMe: StateFlow<List<SosGrant>> = (repo?.observeGrantsToMe() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watched: StateFlow<List<WatchedSos>> = grantsToMe
        .flatMapLatest { grants -> repo?.observeWatchedSos(grants) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val owner = SosRuntime.owner

    val activeSession: StateFlow<Live<SosSession>> = owner
        .map { it.sessionId }
        .flatMapLatest { sid -> if (sid == null || repo == null) flowOf(Live.Value(null)) else repo.observeSession(sid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Live.Loading)

    val myHistory: StateFlow<Live<List<SosHistoryItem>>> =
        (myUid?.let { uid -> repo?.observeHistory(uid) } ?: flowOf(Live.Value(emptyList())))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Live.Loading)

    fun historyOf(ownerUid: String): Flow<Live<List<SosHistoryItem>>> = repo?.observeHistory(ownerUid) ?: emptyFlow()

    private val _search = MutableStateFlow<AccountSearch>(AccountSearch.Idle)
    val search: StateFlow<AccountSearch> = _search.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A session still marked active on the server while nothing on this phone runs it (app was killed). */
    private val _stranded = MutableStateFlow<String?>(null)
    val stranded: StateFlow<String?> = _stranded.asStateFlow()

    private val _shareAudio = MutableStateFlow(settings.getBoolean(KEY_AUDIO, false))
    val shareAudio: StateFlow<Boolean> = _shareAudio.asStateFlow()

    private val _allowRecording = MutableStateFlow(settings.getBoolean(KEY_RECORDING, false))
    val allowRecording: StateFlow<Boolean> = _allowRecording.asStateFlow()

    /** Numbers that get an SMS from this phone when an SOS starts. Only on this device. */
    private val _smsContacts = MutableStateFlow(myUid?.let { TrustedSmsContacts.load(ctx, it) }.orEmpty())
    val smsContacts: StateFlow<List<TrustedContact>> = _smsContacts.asStateFlow()

    val hasSmsPermission: Boolean get() = SosSms.hasPermission(ctx)

    /** Saves up to three numbers; false (with a message) when one is not a valid mobile number. */
    fun saveSmsContacts(contacts: List<TrustedContact>): Boolean {
        val uid = myUid ?: return false
        val cleaned = contacts
            .map { TrustedContact(it.name.trim().replace("\t", " ").replace("\n", " "), it.phone.trim()) }
            .filter { it.phone.isNotBlank() }
            .distinctBy { SosSmsText.destination(it.phone) }
            .take(MAX_SMS_CONTACTS)
        if (cleaned.any { !isValidBangladeshMobile(it.phone) }) {
            say("সঠিক মোবাইল নম্বর দিন (যেমন 01712345678)")
            return false
        }
        TrustedSmsContacts.save(ctx, uid, cleaned)
        _smsContacts.value = cleaned
        say(if (cleaned.isEmpty()) "অটো SMS নম্বর মুছে ফেলা হয়েছে" else "অটো SMS নম্বর সেভ হয়েছে")
        return true
    }

    init {
        val r = repo
        if (r != null) {
            viewModelScope.launch {
                runCatching { r.publishLookup() }
                SosMessagingService.registerCurrentToken(ctx)
                SosNotifier.ensureChannels(ctx)
                val serverSid = runCatching { r.myActiveSessionId() }.getOrNull()
                if (serverSid != null && owner.value.sessionId == null) _stranded.value = serverSid
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    private fun say(text: String) { _message.value = text }

    // -------------------------------------------------------------- settings

    fun setShareAudioDefault(on: Boolean) {
        _shareAudio.value = on
        settings.edit().putBoolean(KEY_AUDIO, on).apply()
    }

    fun setAllowRecordingDefault(on: Boolean) {
        _allowRecording.value = on
        settings.edit().putBoolean(KEY_RECORDING, on).apply()
    }

    // -------------------------------------------------------------- trusted access

    fun clearSearch() { _search.value = AccountSearch.Idle }

    fun searchAccount(query: String) {
        val r = repo ?: return
        val q = query.trim()
        if (q.length < 6) { _search.value = AccountSearch.Invalid; return }
        _search.value = AccountSearch.Searching
        viewModelScope.launch {
            val found = runCatching { r.findAccount(q) }.getOrNull()
            _search.value = when {
                found == null -> AccountSearch.NotFound
                found.uid == r.myUid -> AccountSearch.Self
                else -> AccountSearch.Found(found, viewers.value.firstOrNull { it.viewerUid == found.uid })
            }
        }
    }

    fun invite(account: SosAccount, perms: SosPerms) = act("আমন্ত্রণ পাঠানো হয়েছে") {
        it.invite(account, perms)
        _search.value = AccountSearch.Idle
    }

    fun updatePerms(grant: SosGrant, perms: SosPerms) = act(null) { it.updatePerms(grant.viewerUid, perms) }

    fun revoke(grant: SosGrant) = act("${grant.viewerName}-এর অ্যাক্সেস বাতিল হয়েছে") {
        it.revoke(grant.viewerUid, owner.value.sessionId)
    }

    fun respond(grant: SosGrant, accept: Boolean) =
        act(if (accept) "${grant.ownerName}-এর SOS এখন আপনি পাবেন" else "আমন্ত্রণ ফিরিয়ে দেওয়া হয়েছে") {
            it.respond(grant.ownerUid, accept)
        }

    fun leave(grant: SosGrant) = act("${grant.ownerName}-এর তালিকা থেকে সরে এসেছেন") { it.leave(grant.ownerUid) }

    private fun act(success: String?, block: suspend (SosRepository) -> Unit) {
        val r = repo ?: return
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { block(r) }
            _busy.value = false
            if (result.isSuccess) success?.let(::say)
            else say("হয়নি — ইন্টারনেট দেখে আবার চেষ্টা করুন")
        }
    }

    // -------------------------------------------------------------- the SOS itself

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    val hasLocationPermission: Boolean get() =
        has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    val hasMicPermission: Boolean get() = has(Manifest.permission.RECORD_AUDIO)

    val trustedCount: Int get() = viewers.value.count { it.status == GrantStatus.ACCEPTED }

    /** Called after the 3-second hold and the permission prompt. */
    fun startSos() {
        val r = repo ?: return
        if (owner.value.active || _busy.value) return
        val audio = _shareAudio.value && hasMicPermission
        _busy.value = true
        SosRuntime.scope.launch {
            val sid = runCatching { r.startSession(audio, _allowRecording.value) }.getOrElse {
                _busy.value = false
                say("SOS শুরু করা যায়নি — ইন্টারনেট দেখে আবার চেষ্টা করুন, বা ৯৯৯-এ কল করুন")
                return@launch
            }
            SosRuntime.update { it.copy(sessionId = sid, startedAtMs = System.currentTimeMillis(), push = PushStatus.Sending) }
            if (hasLocationPermission) PrivateSosService.start(ctx, sid, audio)
            _busy.value = false
            sendSms(sid, update = false)
            val push = SosNotifyClient.notifyTrusted(ctx, sid)
            SosRuntime.update { if (it.sessionId == sid) it.copy(push = push) else it }
        }
    }

    /** Location permission arrived after the SOS started. */
    fun startSharingNow() {
        val sid = owner.value.sessionId ?: return
        if (hasLocationPermission) PrivateSosService.start(ctx, sid, _shareAudio.value && hasMicPermission, resume = true)
    }

    fun endSos() {
        val r = repo ?: return
        val sid = owner.value.sessionId ?: _stranded.value ?: return
        val toldBySms = (owner.value.sms as? SmsStatus.Sent)?.ok?.let { it > 0 } == true
        val smsContactsNow = _smsContacts.value
        SosRuntime.scope.launch {
            _busy.value = true
            val ok = runCatching { r.endSession(sid) }.isSuccess
            _busy.value = false
            PrivateSosService.stop(ctx)
            SosRuntime.reset()
            _stranded.value = null
            say(if (ok) "SOS বন্ধ হয়েছে। সবার কাছে লোকেশন পাঠানো বন্ধ।" else "সার্ভারে বন্ধ লেখা যায়নি; ইন্টারনেট ফিরলে আবার বন্ধ করুন")
            if (!ok) _stranded.value = sid
            // Tell trusted people it is over (best effort; their screens also see "ended" live).
            if (ok) SosNotifyClient.notifyTrusted(ctx, sid, event = "ended")
            // Whoever got the alarm text also hears that it is over.
            if (ok && toldBySms && SosSms.hasPermission(ctx)) SosSms.send(ctx, smsContactsNow, SosSmsText.safe(ownerName))
        }
    }

    fun resumeStranded() {
        val sid = _stranded.value ?: return
        _stranded.value = null
        SosRuntime.update { it.copy(sessionId = sid, startedAtMs = System.currentTimeMillis()) }
        if (hasLocationPermission) PrivateSosService.start(ctx, sid, _shareAudio.value && hasMicPermission, resume = true)
    }

    fun setLiveAudio(on: Boolean) {
        val r = repo ?: return
        val sid = owner.value.sessionId ?: return
        if (on && !hasMicPermission) return
        viewModelScope.launch {
            runCatching { r.setAudio(sid, on) }
                .onSuccess { PrivateSosService.setAudio(ctx, on) }
                .onFailure { say("মাইক্রোফোন বদলানো যায়নি") }
        }
    }

    fun setLocationSharing(on: Boolean) {
        if (owner.value.sessionId == null) return
        PrivateSosService.setLocation(ctx, on)
    }

    fun setSessionRecordingAllowed(on: Boolean) {
        val r = repo ?: return
        val sid = owner.value.sessionId ?: return
        viewModelScope.launch { runCatching { r.setRecordingAllowed(sid, on) } }
    }

    private val ownerName: String get() = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

    /**
     * Texts the saved numbers from this phone: the live link, plus a map link when a position is
     * known. [update] sends the newest position again while the SOS is still running.
     */
    private fun sendSms(sid: String, update: Boolean) {
        val contacts = _smsContacts.value
        if (contacts.isEmpty()) return
        fun setStatus(status: SmsStatus) = SosRuntime.update { if (it.sessionId == sid) it.copy(sms = status) else it }
        setStatus(SmsStatus.Sending)
        SosRuntime.scope.launch {
            if (!SosSms.hasTelephony(ctx)) return@launch setStatus(SmsStatus.NoTelephony)
            if (!SosSms.hasPermission(ctx)) return@launch setStatus(SmsStatus.NeedsPermission)
            // Go with a position the phone already has; otherwise give the GPS a few seconds.
            var where = SosRuntime.owner.value.lastFix?.let { it.lat to it.lng } ?: SosSms.lastKnownPosition(ctx)
            var waited = 0
            while (where == null && waited < 10) {
                delay(500)
                waited++
                where = SosRuntime.owner.value.lastFix?.let { it.lat to it.lng }
            }
            val body = SosSmsText.alert(
                ownerName,
                ctx.getString(com.example.R.string.sos_live_link_base) + sid,
                where?.let { com.example.data.model.mapsLink(it.first, it.second) },
                update
            )
            val out = SosSms.send(ctx, contacts, body)
            setStatus(SmsStatus.Sent(out.ok, out.failed))
        }
    }

    /** Text the saved numbers again with the newest position (also the way in after allowing SMS). */
    fun sendSmsNow() {
        val sid = owner.value.sessionId ?: return
        sendSms(sid, update = owner.value.sms is SmsStatus.Sent)
    }

    /** The text for the SMS-app fallback when this phone may not send SMS by itself. */
    fun smsFallbackBody(): String {
        val sid = owner.value.sessionId.orEmpty()
        val where = owner.value.lastFix?.let { it.lat to it.lng } ?: SosSms.lastKnownPosition(ctx)
        return SosSmsText.alert(
            ownerName,
            ctx.getString(com.example.R.string.sos_live_link_base) + sid,
            where?.let { com.example.data.model.mapsLink(it.first, it.second) },
            update = false
        )
    }

    fun resendAlert() {
        val sid = owner.value.sessionId ?: return
        SosRuntime.update { it.copy(push = PushStatus.Sending) }
        SosRuntime.scope.launch {
            val push = SosNotifyClient.notifyTrusted(ctx, sid)
            SosRuntime.update { if (it.sessionId == sid) it.copy(push = push) else it }
        }
    }

    fun deleteHistory(item: SosHistoryItem) = act("মুছে ফেলা হয়েছে") { it.deleteSessionHistory(item.sessionId) }

    companion object {
        const val MAX_SMS_CONTACTS = 3
        private const val KEY_AUDIO = "share_audio"
        private const val KEY_RECORDING = "allow_recording"
    }
}
