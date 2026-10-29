package com.example.data.profile

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** What onboarding collects. [phone] is the number Firebase verified for the account, if any. */
data class UserProfile(val name: String, val bloodGroup: String?, val phone: String?) {
    val complete: Boolean get() = name.isNotBlank() && bloodGroup != null && phone != null
}

/**
 * The onboarding profile: Firestore user_profiles/{uid} (private, see firestore.rules), the Firebase
 * account's display name and verified phone, and a local "done" flag so it is not asked again.
 */
object UserProfileStore {
    private const val PREFS = "rokkhon_app_prefs"
    private fun doneKey(uid: String) = "onboarding_done_$uid"

    private fun db(context: Context) =
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))

    fun isDone(context: Context, uid: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(doneKey(uid), false)

    private fun markDone(context: Context, uid: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(doneKey(uid), true).apply()

    /**
     * Everything already known about the signed-in account: the saved profile, or else the donor
     * profile. Missing parts come back null/blank; a slow network just means fewer answers.
     */
    suspend fun load(context: Context): UserProfile? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        val saved = withTimeoutOrNull(5_000) { runCatching { db(context).collection("user_profiles").document(user.uid).get().await() }.getOrNull() }
        val donor = if (saved?.getString("bloodGroup") == null) {
            withTimeoutOrNull(5_000) { runCatching { db(context).collection("donor_nodes").document(user.uid).get().await() }.getOrNull() }
        } else null
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: saved?.getString("name")
            ?: donor?.getString("displayName")
            ?: ""
        val blood = saved?.getString("bloodGroup") ?: donor?.getString("bloodGroup")
        val profile = UserProfile(name, blood, user.phoneNumber?.takeIf { it.isNotBlank() })
        if (profile.complete && saved != null) markDone(context, user.uid)
        return profile
    }

    /**
     * Saves the onboarding answers. [markDone] false (the number step was put off) asks again on
     * the next launch. A refused or offline cloud write does not hold the user up.
     */
    suspend fun save(context: Context, name: String, bloodGroup: String, markDone: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.displayName != name) {
            runCatching {
                user.updateProfile(com.google.firebase.auth.userProfileChangeRequest { displayName = name }).await()
            }
        }
        val data = mutableMapOf<String, Any>("name" to name, "bloodGroup" to bloodGroup, "updatedAt" to FieldValue.serverTimestamp())
        user.phoneNumber?.takeIf { it.isNotBlank() }?.let { data["phone"] = it }
        com.example.ui.viewmodel.RokkhonViewModel.rememberOnboarding(context, user.uid, name, bloodGroup, user.phoneNumber)
        if (markDone) markDone(context, user.uid)
        withTimeoutOrNull(8_000) { runCatching { db(context).collection("user_profiles").document(user.uid).set(data).await() } }
    }
}
