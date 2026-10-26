package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.sos.GrantStatus
import com.example.data.sos.ListenState
import com.example.data.sos.Live
import com.example.data.sos.LiveFix
import com.example.data.sos.SosDatabase
import com.example.data.sos.SosEvent
import com.example.data.sos.SosGrant
import com.example.data.sos.SosListenRuntime
import com.example.data.sos.SosRecordings
import com.example.data.sos.SosRepository
import com.example.data.sos.SosSession
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a trusted person can't see a session (or no longer can). */
enum class MonitorBlock { NONE, NOT_CONFIGURED, SIGNED_OUT, NO_ACCESS, REVOKED, NO_LOCATION_PERMISSION }

/** One SOS session as seen by a trusted person: live map, status, audio and local recordings. */
@OptIn(ExperimentalCoroutinesApi::class)
class SosMonitorViewModel(app: Application, val sessionId: String) : AndroidViewModel(app) {

    private val repo: SosRepository? = run {
        val ready = runCatching { FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false)
        if (!ready || FirebaseAuth.getInstance().currentUser == null) null
        else SosDatabase.get(app)?.let { SosRepository(it, FirebaseAuth.getInstance()) }
    }

    val firebaseReady = runCatching { FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false)
    val signedIn = firebaseReady && FirebaseAuth.getInstance().currentUser != null
    val myUid: String? = repo?.myUid

    val session: StateFlow<Live<SosSession>> = (repo?.observeSession(sessionId) ?: flowOf(Live.Denied))
        .stateIn(viewModelScope, SharingStarted.Eagerly, Live.Loading)

    private val ownerUid = session.map { (it as? Live.Value)?.value?.ownerUid }.distinctUntilChanged()

    /** My grant from the session owner, live: a revoke shows up here at once. */
    val grant: StateFlow<Live<SosGrant>> = ownerUid
        .flatMapLatest { uid -> if (uid == null || repo == null) flowOf(Live.Loading) else repo.observeMyGrantFrom(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Live.Loading)

    /** Re-subscribed whenever the session state or the grant changes, since both decide what the rules allow. */
    val fix: StateFlow<Live<LiveFix>> = combine(
        session.map { (it as? Live.Value)?.value?.active }.distinctUntilChanged(),
        grant.map { (it as? Live.Value)?.value?.let { g -> g.status to g.perms } }.distinctUntilChanged()
    ) { a, b -> a to b }
        .flatMapLatest { repo?.observeLatestFix(sessionId) ?: flowOf(Live.Denied) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Live.Loading)

    val events: StateFlow<Live<List<SosEvent>>> = (repo?.observeEvents(sessionId) ?: flowOf(Live.Denied))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Live.Loading)

    val online: StateFlow<Boolean> = (repo?.observeConnected() ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val listen: StateFlow<ListenState> = SosListenRuntime.state

    private val _recordings = MutableStateFlow(SosRecordings.list(app))
    val recordings: StateFlow<List<File>> = _recordings.asStateFlow()

    val block: StateFlow<MonitorBlock> = combine(session, grant) { s, g ->
        when {
            !firebaseReady || (signedIn && repo == null) -> MonitorBlock.NOT_CONFIGURED
            !signedIn -> MonitorBlock.SIGNED_OUT
            // The sender looking at their own session (e.g. from history).
            s is Live.Value && s.value?.ownerUid != null && s.value.ownerUid == myUid -> MonitorBlock.NONE
            s is Live.Denied -> MonitorBlock.NO_ACCESS
            g is Live.Value && g.value == null -> MonitorBlock.REVOKED
            g is Live.Value && g.value?.status != GrantStatus.ACCEPTED -> MonitorBlock.NO_ACCESS
            g is Live.Denied -> MonitorBlock.NO_ACCESS
            else -> MonitorBlock.NONE
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MonitorBlock.NONE)

    init {
        val r = repo
        if (r != null) {
            viewModelScope.launch {
                // "Opened" for the sender's delivery status, once the session is readable.
                val s = session.filterIsInstance<Live.Value<SosSession>>().first { it.value != null }.value!!
                if (s.active && s.ownerUid != r.myUid) {
                    r.markSeen(sessionId)
                    r.logEventAsync(sessionId, "viewer_opened")
                }
            }
            viewModelScope.launch {
                // Stop listening the moment access goes away or the session ends.
                block.collect { if (it == MonitorBlock.REVOKED || it == MonitorBlock.NO_ACCESS) stopListening() }
            }
            viewModelScope.launch {
                session.collect { s -> if (s is Live.Value && s.value?.active == false) stopListening() }
            }
            viewModelScope.launch {
                listen.map { it.lastSaved }.distinctUntilChanged().collect { refreshRecordings() }
            }
        }
    }

    fun startListening() {
        val r = repo ?: return
        SosListenRuntime.start(getApplication(), r, sessionId)
    }

    fun stopListening() {
        if (SosListenRuntime.state.value.sessionId == sessionId) SosListenRuntime.stop(getApplication())
    }

    fun setMuted(muted: Boolean) = SosListenRuntime.setMuted(muted)

    fun retryAudio() = SosListenRuntime.retry()

    fun startRecording(ownerName: String) = SosListenRuntime.startRecording(getApplication(), ownerName)

    fun stopRecording() = SosListenRuntime.stopRecording(getApplication())

    fun refreshRecordings() { _recordings.value = SosRecordings.list(getApplication()) }

    fun deleteRecording(file: File) {
        SosRecordings.delete(file)
        refreshRecordings()
    }

    fun saveRecording(file: File): Boolean = SosRecordings.saveToDownloads(getApplication(), file)
}
