package com.example.data.sos

import java.security.SecureRandom

/** What one trusted person may do while you have an SOS running. Enforced by database.rules.json. */
data class SosPerms(
    val location: Boolean = true,
    val audio: Boolean = false,
    val alerts: Boolean = true,
    val history: Boolean = false
) {
    val any: Boolean get() = location || audio || alerts || history
    val anyLive: Boolean get() = location || audio || alerts
}

enum class GrantStatus(val wire: String) {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined");

    companion object {
        fun from(value: String?): GrantStatus = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

/** sosAccess/{ownerUid}/{viewerUid}: the owner trusts the viewer with [perms]. */
data class SosGrant(
    val ownerUid: String,
    val viewerUid: String,
    val ownerName: String,
    val viewerName: String,
    val status: GrantStatus,
    val perms: SosPerms,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Someone the user added by mobile number (sosContacts/{owner}/{phoneKey}). Every contact gets the
 * SOS text message; one whose number belongs to a Rokkhon account ([uid]) also gets the push, the
 * live map and, if allowed, the audio.
 */
data class SosContact(val key: String, val name: String, val phone: String, val uid: String?)

/** A Rokkhon account found by its exact verified email or phone number. */
data class SosAccount(val uid: String, val name: String)

data class SosListener(val state: String, val recording: Boolean, val ts: Long)

data class SosSession(
    val id: String,
    val ownerUid: String,
    val ownerName: String,
    val active: Boolean,
    val startedAt: Long,
    val endedAt: Long?,
    val audio: Boolean,
    val recordingAllowed: Boolean,
    val ownerOnline: Boolean,
    val locationOn: Boolean,
    val heartbeat: Long?,
    val seen: Map<String, Long>,
    val listeners: Map<String, SosListener>,
    val notify: Map<String, String>
)

/** One GPS fix as stored in liveLocations/{sessionId}/latest. */
data class LiveFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val ts: Long,
    val speedMps: Double?,
    val fromLastKnown: Boolean
)

data class SosHistoryItem(val sessionId: String, val startedAt: Long, val endedAt: Long?)

data class SosEvent(val type: String, val by: String, val name: String?, val ts: Long)

/** An SOS that someone who trusts you is running right now. */
data class WatchedSos(val ownerUid: String, val ownerName: String, val sessionId: String, val startedAt: Long)

object SosIds {
    private val random = SecureRandom()

    /** 128 random bits as 32 hex characters: not guessable, matches the rules' pattern. */
    fun newSessionId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isSessionId(value: String?): Boolean = value != null && Regex("^[a-f0-9]{32}$").matches(value)

    /** Same transformation the rules apply to auth.token.email. Null if it cannot be a database key. */
    fun emailKey(email: String?): String? {
        val e = email?.trim()?.lowercase() ?: return null
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e)) return null
        if (e.any { it in "#$[]/" }) return null
        return e.replace('.', ',')
    }

    /** E.164 digits without '+'. Accepts Bangladeshi local numbers like 01712345678. */
    fun phoneKey(phone: String?): String? {
        val raw = phone?.trim() ?: return null
        var digits = raw.filter { it.isDigit() }
        if (raw.startsWith("00")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("01")) digits = "88$digits"
        if (digits.length == 10 && digits.startsWith("1")) digits = "880$digits"
        return digits.takeIf { it.length in 8..15 }
    }

    /** Session id from https://rokkhon.com/live/{id}, or null. */
    fun sessionIdFromLink(link: String?): String? {
        val path = link?.trim()?.substringBefore('?')?.substringBefore('#')?.trimEnd('/') ?: return null
        val id = path.substringAfterLast("/live/", "")
        return id?.takeIf { isSessionId(it) }
    }
}
