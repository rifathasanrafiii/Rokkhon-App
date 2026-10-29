package com.example.data.sos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** A live read that can also be refused by the rules (no permission, or access revoked). */
sealed interface Live<out T> {
    data object Loading : Live<Nothing>
    data class Value<T>(val value: T?) : Live<T>
    data object Denied : Live<Nothing>
}

/**
 * Every read and write of the private SOS. Rules in database.rules.json decide what goes through;
 * this class never assumes a write succeeded without the server confirming it.
 */
class SosRepository(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val root: DatabaseReference get() = db.reference

    val myUid: String? get() = auth.currentUser?.uid

    private fun requireUid(): String = myUid ?: throw IllegalStateException("Sign in to use SOS")

    fun myName(): String {
        val u = auth.currentUser
        return (u?.displayName?.takeIf { it.isNotBlank() }
            ?: u?.email?.substringBefore('@')
            ?: u?.phoneNumber
            ?: "Rokkhon user").take(60)
    }

    // ---------------------------------------------------------------- account lookup

    /** Publish this account under its verified email and/or phone so trusted people can find it by exact match. */
    suspend fun publishLookup() {
        val u = auth.currentUser ?: return
        val entry = mapOf("uid" to u.uid, "name" to myName())
        val updates = mutableMapOf<String, Any>()
        if (u.email != null && u.isEmailVerifiedForRules()) {
            SosIds.emailKey(u.email)?.let { updates["sosLookup/email/$it"] = entry }
        }
        SosIds.phoneKey(u.phoneNumber)?.let { updates["sosLookup/phone/$it"] = entry }
        // One path at a time: if one is refused (say, an unverified email) the other still lands.
        for ((path, value) in updates) runCatching { root.child(path).setValue(value).await() }
    }

    // Google sign-in emails are verified; the rules re-check auth.token.email_verified anyway.
    private fun com.google.firebase.auth.FirebaseUser.isEmailVerifiedForRules(): Boolean =
        isEmailVerified || providerData.any { it.providerId == "google.com" }

    /** Exact email or phone number. Returns null when nobody uses Rokkhon with it. */
    suspend fun findAccount(query: String): SosAccount? {
        val path = if (query.contains('@')) {
            SosIds.emailKey(query)?.let { "sosLookup/email/$it" }
        } else {
            SosIds.phoneKey(query)?.let { "sosLookup/phone/$it" }
        } ?: return null
        val snap = root.child(path).get().await()
        val uid = snap.child("uid").getValue(String::class.java) ?: return null
        val name = snap.child("name").getValue(String::class.java) ?: "Rokkhon user"
        return SosAccount(uid, name)
    }

    // ---------------------------------------------------------------- trusted access (owner side)

    suspend fun updatePerms(viewerUid: String, perms: SosPerms) {
        val me = requireUid()
        root.child("sosAccess/$me/$viewerUid").updateChildren(
            mapOf(
                "location" to perms.location,
                "audio" to perms.audio,
                "alerts" to perms.alerts,
                "history" to perms.history,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    /** Removes the grant everywhere in one atomic write; the rules then refuse every read immediately. */
    suspend fun revoke(viewerUid: String, activeSessionId: String?) {
        val me = requireUid()
        val updates = mutableMapOf<String, Any?>(
            "sosAccess/$me/$viewerUid" to null,
            "sosAccessIndex/$viewerUid/$me" to null
        )
        if (activeSessionId != null) {
            updates["sosSignaling/$activeSessionId/$viewerUid"] = null
            updates["sosSessions/$activeSessionId/listeners/$viewerUid"] = null
        }
        root.updateChildren(updates).await()
    }

    fun observeMyViewers(): Flow<List<SosGrant>> = callbackFlow {
        val me = myUid
        if (me == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val ref = root.child("sosAccess/$me")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { parseGrant(me, it.key ?: return@mapNotNull null, it) }
                    .sortedBy { it.viewerName.lowercase() })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ---------------------------------------------------------------- contacts by mobile number

    fun observeContacts(): Flow<List<SosContact>> = callbackFlow {
        val me = myUid
        if (me == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val ref = root.child("sosContacts/$me")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { c ->
                    SosContact(
                        key = c.key ?: return@mapNotNull null,
                        name = c.child("name").getValue(String::class.java) ?: return@mapNotNull null,
                        phone = c.child("phone").getValue(String::class.java) ?: return@mapNotNull null,
                        uid = c.child("uid").getValue(String::class.java)
                    ) to (c.child("createdAt").getValue(Long::class.java) ?: 0L)
                }.sortedBy { it.second }.map { it.first })
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Adds someone by mobile number. If the number belongs to a Rokkhon account they are added
     * straight away (no invitation), with [perms]; either way the number gets the SOS text.
     */
    suspend fun addContact(name: String, phone: String, perms: SosPerms): SosContact {
        val me = requireUid()
        val key = SosIds.phoneKey(phone) ?: throw IllegalArgumentException("phone")
        val account = findAccount("+$key")?.takeIf { it.uid != me }
        val label = name.trim().take(60).ifBlank { account?.name ?: "+$key" }
        val contact = mutableMapOf<String, Any>("name" to label, "phone" to "+$key", "createdAt" to ServerValue.TIMESTAMP)
        val updates = mutableMapOf<String, Any?>()
        if (account != null) {
            contact["uid"] = account.uid
            updates.putAll(grantWrite(me, account.uid, label, perms))
        }
        updates["sosContacts/$me/$key"] = contact
        root.updateChildren(updates).await()
        return SosContact(key, label, "+$key", account?.uid)
    }

    /** A contact whose number later became a Rokkhon account is connected to it. */
    suspend fun connectContact(contact: SosContact, perms: SosPerms): Boolean {
        val me = requireUid()
        val account = findAccount(contact.phone)?.takeIf { it.uid != me } ?: return false
        val updates = grantWrite(me, account.uid, contact.name, perms).toMutableMap()
        updates["sosContacts/$me/${contact.key}/uid"] = account.uid
        root.updateChildren(updates).await()
        return true
    }

    private suspend fun grantWrite(me: String, viewer: String, viewerName: String, perms: SosPerms): Map<String, Any?> {
        val existing = root.child("sosAccess/$me/$viewer").get().await()
        val createdAt: Any = existing.child("createdAt").getValue(Long::class.java) ?: ServerValue.TIMESTAMP
        return mapOf(
            "sosAccess/$me/$viewer" to mapOf(
                "ownerName" to myName(),
                "viewerName" to viewerName.take(60).ifBlank { "Rokkhon user" },
                "status" to GrantStatus.ACCEPTED.wire,
                "location" to perms.location,
                "audio" to perms.audio,
                "alerts" to true,
                "history" to perms.history,
                "createdAt" to createdAt,
                "updatedAt" to ServerValue.TIMESTAMP
            ),
            "sosAccessIndex/$viewer/$me" to true
        )
    }

    /** Removes the number and, if it is an account, its access, in one write. */
    suspend fun removeContact(contact: SosContact, activeSessionId: String?) {
        val me = requireUid()
        val updates = mutableMapOf<String, Any?>("sosContacts/$me/${contact.key}" to null)
        contact.uid?.let { viewer ->
            updates["sosAccess/$me/$viewer"] = null
            updates["sosAccessIndex/$viewer/$me"] = null
            if (activeSessionId != null) {
                updates["sosSignaling/$activeSessionId/$viewer"] = null
                updates["sosSessions/$activeSessionId/listeners/$viewer"] = null
            }
        }
        root.updateChildren(updates).await()
    }

    /** People added before invitations were dropped: they count as added. */
    suspend fun acceptPendingGrants(viewers: List<SosGrant>) {
        val me = requireUid()
        viewers.filter { it.ownerUid == me && it.status == GrantStatus.PENDING }.forEach { g ->
            runCatching {
                root.child("sosAccess/$me/${g.viewerUid}").updateChildren(
                    mapOf("status" to GrantStatus.ACCEPTED.wire, "updatedAt" to ServerValue.TIMESTAMP)
                ).await()
            }
        }
    }

    // ---------------------------------------------------------------- trusted access (viewer side)

    /** Stop being someone's trusted contact. */
    suspend fun leave(ownerUid: String) {
        val me = requireUid()
        root.updateChildren(
            mapOf(
                "sosAccess/$ownerUid/$me" to null,
                "sosAccessIndex/$me/$ownerUid" to null
            )
        ).await()
    }

    /** Grants other people gave me (pending invitations included), kept live. */
    fun observeGrantsToMe(): Flow<List<SosGrant>> = callbackFlow {
        val me = myUid
        if (me == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val grants = linkedMapOf<String, SosGrant>()
        val children = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()
        fun emit() { trySend(grants.values.sortedBy { it.ownerName.lowercase() }) }

        val indexRef = root.child("sosAccessIndex/$me")
        val indexListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val owners = snapshot.children.mapNotNull { it.key }.toSet()
                (children.keys - owners).forEach { gone ->
                    children.remove(gone)?.let { (r, l) -> r.removeEventListener(l) }
                    grants.remove(gone)
                }
                (owners - children.keys).forEach { owner ->
                    val r = root.child("sosAccess/$owner/$me")
                    val l = object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            val g = if (s.exists()) parseGrant(owner, me, s) else null
                            if (g == null) grants.remove(owner) else grants[owner] = g
                            emit()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            grants.remove(owner); emit()
                        }
                    }
                    children[owner] = r to l
                    r.addValueEventListener(l)
                }
                if (owners.isEmpty()) emit()
            }
            override fun onCancelled(error: DatabaseError) { trySend(emptyList()) }
        }
        indexRef.addValueEventListener(indexListener)
        awaitClose {
            indexRef.removeEventListener(indexListener)
            children.values.forEach { (r, l) -> r.removeEventListener(l) }
        }
    }

    /** Active SOS sessions of people who trust me with alerts, location or audio. */
    fun observeWatchedSos(owners: List<SosGrant>): Flow<List<WatchedSos>> = callbackFlow {
        val live = linkedMapOf<String, WatchedSos>()
        val subs = owners.filter { it.status == GrantStatus.ACCEPTED && it.perms.anyLive }.map { g ->
            val r = root.child("sosActive/${g.ownerUid}")
            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val sid = s.child("sessionId").getValue(String::class.java)
                    if (sid == null) live.remove(g.ownerUid)
                    else live[g.ownerUid] = WatchedSos(g.ownerUid, g.ownerName, sid, s.child("startedAt").getValue(Long::class.java) ?: 0L)
                    trySend(live.values.toList())
                }
                override fun onCancelled(error: DatabaseError) {
                    live.remove(g.ownerUid); trySend(live.values.toList())
                }
            }
            r.addValueEventListener(l)
            r to l
        }
        if (subs.isEmpty()) trySend(emptyList())
        awaitClose { subs.forEach { (r, l) -> r.removeEventListener(l) } }
    }.distinctUntilChanged()

    // ---------------------------------------------------------------- sessions (owner)

    /**
     * Creates the session [sid] and the pointer viewers watch, atomically. The write is issued at
     * once (and queued while offline); the returned task completes when the server confirms it.
     */
    fun beginSession(sid: String, audio: Boolean, recordingAllowed: Boolean): com.google.android.gms.tasks.Task<Void> {
        val me = requireUid()
        return root.updateChildren(
            mapOf(
                "sosSessions/$sid" to mapOf(
                    "ownerUid" to me,
                    "ownerName" to myName(),
                    "status" to "active",
                    "startedAt" to ServerValue.TIMESTAMP,
                    "audio" to audio,
                    "recordingAllowed" to recordingAllowed,
                    "ownerOnline" to true,
                    "locationOn" to true
                ),
                "sosActive/$me" to mapOf("sessionId" to sid, "startedAt" to ServerValue.TIMESTAMP),
                "sosHistory/$me/$sid" to mapOf("startedAt" to ServerValue.TIMESTAMP)
            )
        )
    }

    suspend fun logStarted(sid: String) {
        runCatching { logEvent(sid, "started") }
    }

    suspend fun endSession(sid: String) {
        val me = requireUid()
        runCatching { logEvent(sid, "ended") }
        root.updateChildren(
            mapOf(
                "sosSessions/$sid/status" to "ended",
                "sosSessions/$sid/endedAt" to ServerValue.TIMESTAMP,
                "sosSessions/$sid/ownerOnline" to false,
                "sosSessions/$sid/listeners" to null,
                "sosActive/$me" to null,
                "sosHistory/$me/$sid/endedAt" to ServerValue.TIMESTAMP,
                "sosSignaling/$sid" to null
            )
        ).await()
    }

    suspend fun setAudio(sid: String, enabled: Boolean) {
        val updates = mutableMapOf<String, Any?>("sosSessions/$sid/audio" to enabled)
        if (!enabled) {
            updates["sosSignaling/$sid"] = null
            updates["sosSessions/$sid/listeners"] = null
        }
        root.updateChildren(updates).await()
        runCatching { logEvent(sid, if (enabled) "audio_on" else "audio_off") }
    }

    suspend fun setLocationOn(sid: String, on: Boolean) {
        root.child("sosSessions/$sid/locationOn").setValue(on).await()
    }

    /** The active session of this account, if one was left running (app killed, phone restarted). */
    suspend fun myActiveSessionId(): String? {
        val me = myUid ?: return null
        return root.child("sosActive/$me/sessionId").get().await().getValue(String::class.java)
    }

    fun publishFix(sid: String, fix: LiveFix) {
        val value = mutableMapOf<String, Any>(
            "lat" to fix.lat,
            "lng" to fix.lng,
            "acc" to fix.accuracyM,
            "ts" to fix.ts,
            "src" to if (fix.fromLastKnown) "last_known" else "gps"
        )
        fix.speedMps?.let { value["speed"] = it }
        root.child("liveLocations/$sid/latest").setValue(value)
    }

    /** Minimal history: one point a minute, only the newest [keep] are kept. */
    fun appendTrail(sid: String, fix: LiveFix, keep: Int = 30) {
        val trail = root.child("liveLocations/$sid/trail")
        trail.push().setValue(mapOf("lat" to fix.lat, "lng" to fix.lng, "acc" to fix.accuracyM, "ts" to fix.ts))
        trail.get().addOnSuccessListener { snap ->
            val extra = snap.childrenCount - keep
            if (extra > 0) snap.children.take(extra.toInt()).forEach { it.ref.removeValue() }
        }
    }

    fun heartbeat(sid: String) {
        root.child("sosSessions/$sid").updateChildren(mapOf("heartbeat" to ServerValue.TIMESTAMP, "ownerOnline" to true))
    }

    /** Marks the owner offline on the server if this phone loses its connection. */
    fun armOnlinePresence(sid: String) {
        root.child("sosSessions/$sid/ownerOnline").onDisconnect().setValue(false)
    }

    fun cancelOnlinePresence(sid: String) {
        root.child("sosSessions/$sid/ownerOnline").onDisconnect().cancel()
    }

    suspend fun deleteSessionHistory(sid: String) {
        val me = requireUid()
        root.updateChildren(
            mapOf(
                "sosEvents/$sid" to null,
                "liveLocations/$sid" to null,
                "sosSessions/$sid" to null,
                "sosHistory/$me/$sid" to null
            )
        ).await()
    }

    // ---------------------------------------------------------------- sessions (both sides)

    fun observeSession(sid: String): Flow<Live<SosSession>> = observe("sosSessions/$sid") { parseSession(sid, it) }

    fun observeLatestFix(sid: String): Flow<Live<LiveFix>> = observe("liveLocations/$sid/latest") { parseFix(it) }

    fun observeMyGrantFrom(ownerUid: String): Flow<Live<SosGrant>> {
        val me = myUid ?: ""
        return observe("sosAccess/$ownerUid/$me") { parseGrant(ownerUid, me, it) }
    }

    fun observeEvents(sid: String): Flow<Live<List<SosEvent>>> = observe("sosEvents/$sid") { snap ->
        snap.children.mapNotNull { e ->
            SosEvent(
                type = e.child("type").getValue(String::class.java) ?: return@mapNotNull null,
                by = e.child("by").getValue(String::class.java) ?: "",
                name = e.child("name").getValue(String::class.java),
                ts = e.child("ts").getValue(Long::class.java) ?: 0L
            )
        }.sortedBy { it.ts }
    }

    fun observeHistory(ownerUid: String): Flow<Live<List<SosHistoryItem>>> = observe("sosHistory/$ownerUid") { snap ->
        snap.children.mapNotNull { h ->
            SosHistoryItem(
                sessionId = h.key ?: return@mapNotNull null,
                startedAt = h.child("startedAt").getValue(Long::class.java) ?: 0L,
                endedAt = h.child("endedAt").getValue(Long::class.java)
            )
        }.sortedByDescending { it.startedAt }
    }

    fun observeConnected(): Flow<Boolean> = callbackFlow {
        val ref = db.getReference(".info/connected")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.getValue(Boolean::class.java) == true) }
            override fun onCancelled(error: DatabaseError) { trySend(false) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }.distinctUntilChanged()

    fun logEventAsync(sid: String, type: String) {
        val me = myUid ?: return
        root.child("sosEvents/$sid").push().setValue(eventValue(me, type))
    }

    suspend fun logEvent(sid: String, type: String) {
        val me = requireUid()
        root.child("sosEvents/$sid").push().setValue(eventValue(me, type)).await()
    }

    private fun eventValue(me: String, type: String) =
        mapOf("type" to type, "by" to me, "name" to myName(), "ts" to ServerValue.TIMESTAMP)

    // ---------------------------------------------------------------- viewer status

    fun markSeen(sid: String) {
        val me = myUid ?: return
        root.child("sosSessions/$sid/seen/$me").setValue(ServerValue.TIMESTAMP)
    }

    fun setListenerState(sid: String, state: String, recording: Boolean) {
        val me = myUid ?: return
        root.child("sosSessions/$sid/listeners/$me").setValue(
            mapOf("state" to state, "recording" to recording, "ts" to ServerValue.TIMESTAMP)
        )
    }

    fun clearListenerState(sid: String) {
        val me = myUid ?: return
        root.child("sosSessions/$sid/listeners/$me").removeValue()
    }

    // ---------------------------------------------------------------- push tokens

    suspend fun saveFcmToken(token: String) {
        val me = myUid ?: return
        val key = token.hashCode().toUInt().toString(16)
        root.child("fcmTokens/$me/$key").setValue(mapOf("token" to token, "ts" to ServerValue.TIMESTAMP)).await()
    }

    // ---------------------------------------------------------------- helpers

    fun ref(path: String): DatabaseReference = root.child(path)

    private fun <T> observe(path: String, parse: (DataSnapshot) -> T?): Flow<Live<T>> = callbackFlow {
        trySend(Live.Loading)
        val ref = root.child(path)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(Live.Value(if (snapshot.exists()) parse(snapshot) else null))
            }
            override fun onCancelled(error: DatabaseError) {
                // Permission denied: not allowed, or allowed until a moment ago (revoked / session ended).
                trySend(Live.Denied)
            }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    companion object {
        fun parseGrant(owner: String, viewer: String, s: DataSnapshot): SosGrant? {
            if (!s.exists()) return null
            return SosGrant(
                ownerUid = owner,
                viewerUid = viewer,
                ownerName = s.child("ownerName").getValue(String::class.java) ?: "Rokkhon user",
                viewerName = s.child("viewerName").getValue(String::class.java) ?: "Rokkhon user",
                status = GrantStatus.from(s.child("status").getValue(String::class.java)),
                perms = SosPerms(
                    location = s.child("location").getValue(Boolean::class.java) == true,
                    audio = s.child("audio").getValue(Boolean::class.java) == true,
                    alerts = s.child("alerts").getValue(Boolean::class.java) == true,
                    history = s.child("history").getValue(Boolean::class.java) == true
                ),
                createdAt = s.child("createdAt").getValue(Long::class.java) ?: 0L,
                updatedAt = s.child("updatedAt").getValue(Long::class.java) ?: 0L
            )
        }

        fun parseSession(sid: String, s: DataSnapshot): SosSession? {
            val owner = s.child("ownerUid").getValue(String::class.java) ?: return null
            return SosSession(
                id = sid,
                ownerUid = owner,
                ownerName = s.child("ownerName").getValue(String::class.java) ?: "Rokkhon user",
                active = s.child("status").getValue(String::class.java) == "active",
                startedAt = s.child("startedAt").getValue(Long::class.java) ?: 0L,
                endedAt = s.child("endedAt").getValue(Long::class.java),
                audio = s.child("audio").getValue(Boolean::class.java) == true,
                recordingAllowed = s.child("recordingAllowed").getValue(Boolean::class.java) == true,
                ownerOnline = s.child("ownerOnline").getValue(Boolean::class.java) == true,
                locationOn = s.child("locationOn").getValue(Boolean::class.java) != false,
                heartbeat = s.child("heartbeat").getValue(Long::class.java),
                seen = s.child("seen").children.associate { (it.key ?: "") to (it.getValue(Long::class.java) ?: 0L) },
                listeners = s.child("listeners").children.associate {
                    (it.key ?: "") to SosListener(
                        state = it.child("state").getValue(String::class.java) ?: "connecting",
                        recording = it.child("recording").getValue(Boolean::class.java) == true,
                        ts = it.child("ts").getValue(Long::class.java) ?: 0L
                    )
                },
                notify = s.child("notify").children.associate { (it.key ?: "") to (it.getValue(String::class.java) ?: "") }
            )
        }

        fun parseFix(s: DataSnapshot): LiveFix? {
            val lat = s.child("lat").getValue(Double::class.java) ?: return null
            val lng = s.child("lng").getValue(Double::class.java) ?: return null
            return LiveFix(
                lat = lat,
                lng = lng,
                accuracyM = s.child("acc").getValue(Double::class.java) ?: 0.0,
                ts = s.child("ts").getValue(Long::class.java) ?: 0L,
                speedMps = s.child("speed").getValue(Double::class.java),
                fromLastKnown = s.child("src").getValue(String::class.java) == "last_known"
            )
        }
    }
}
