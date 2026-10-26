package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SosAlert
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RokkhonRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeDonorNodes(): Flow<List<DonorNode>> {
        val path = "donor_nodes"
        return db.collection(path)
            .whereEqualTo("networkKey", "BD_ROKKHON")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DonorNode::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeBloodRequests(): Flow<List<BloodRequest>> {
        val path = "blood_requests"
        return db.collection(path)
            .whereEqualTo("networkKey", "BD_ROKKHON")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BloodRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeSosAlerts(): Flow<List<SosAlert>> {
        val path = "sos_alerts"
        return db.collection(path)
            .whereEqualTo("networkKey", "BD_ROKKHON")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SosAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    data class AdminPolicySnapshot(
        val rewardCheckInPoints: Int = 25,
        val rewardBloodPledgePoints: Int = 100,
        val rewardSosRespondPoints: Int = 50,
        val globalLocationOff: Boolean = false,
        val locationOffUserIds: Set<String> = emptySet(),
        val suspendedUserIds: Set<String> = emptySet(),
        val bannedDeviceIds: Set<String> = emptySet(),
        val userPointsOverrides: Map<String, Int> = emptyMap(),
        val userStreakOverrides: Map<String, Int> = emptyMap(),
        val userTrustOverrides: Map<String, Int> = emptyMap(),
        val suspendReasons: Map<String, String> = emptyMap(),
        val banReasons: Map<String, String> = emptyMap()
    )

    /** Publishes this phone's hardware ID so the admin web can ban the real device. */
    suspend fun registerDevice(deviceId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("device_registry").document(uid).set(
            mapOf(
                "userId" to uid,
                "deviceId" to deviceId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
        (get(field) as? Map<*, *>)?.mapNotNull { (k, v) ->
            val key = k?.toString() ?: return@mapNotNull null
            val text = (v as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            key to text
        }?.toMap().orEmpty()

    fun observeAdminPolicy(): Flow<AdminPolicySnapshot> {
        return db.collection("admin_controls")
            .document("global_policy")
            .snapshots()
            .map { doc ->
                if (!doc.exists()) {
                    AdminPolicySnapshot()
                } else {
                    val locOffList = (doc.get("locationOffUserIds") as? List<*>)?.mapNotNull { it?.toString() }?.toSet().orEmpty()
                    val suspList = (doc.get("suspendedUserIds") as? List<*>)?.mapNotNull { it?.toString() }?.toSet().orEmpty()
                    val banList = (doc.get("bannedDeviceIds") as? List<*>)?.mapNotNull { it?.toString()?.uppercase() }?.toSet().orEmpty()
                    val ptsMap = (doc.get("userPointsOverrides") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val num = (v as? Number)?.toInt() ?: return@mapNotNull null
                        key to num
                    }?.toMap().orEmpty()
                    val streakMap = (doc.get("userStreakOverrides") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val num = (v as? Number)?.toInt() ?: return@mapNotNull null
                        key to num
                    }?.toMap().orEmpty()
                    val trustMap = (doc.get("userTrustOverrides") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        val key = k?.toString() ?: return@mapNotNull null
                        val num = (v as? Number)?.toInt() ?: return@mapNotNull null
                        key to num
                    }?.toMap().orEmpty()

                    AdminPolicySnapshot(
                        rewardCheckInPoints = (doc.getLong("rewardCheckInPoints")?.toInt() ?: 25).coerceIn(5, 1000),
                        rewardBloodPledgePoints = (doc.getLong("rewardBloodPledgePoints")?.toInt() ?: 100).coerceIn(10, 2000),
                        rewardSosRespondPoints = (doc.getLong("rewardSosRespondPoints")?.toInt() ?: 50).coerceIn(10, 2000),
                        globalLocationOff = doc.getBoolean("globalLocationOff") ?: false,
                        locationOffUserIds = locOffList,
                        suspendedUserIds = suspList,
                        bannedDeviceIds = banList,
                        userPointsOverrides = ptsMap,
                        userStreakOverrides = streakMap,
                        userTrustOverrides = trustMap,
                        suspendReasons = doc.stringMap("suspendReasons"),
                        banReasons = doc.stringMap("banReasons").mapKeys { it.key.uppercase() }
                    )
                }
            }
            .catch { emit(AdminPolicySnapshot()) }
    }

    /**
     * Creates the donor document on first save, afterwards only updates the fields the user owns.
     * Trust score and verified-SOS count are never written by the client after creation.
     */
    suspend fun saveOrUpdateDonorNode(
        displayName: String,
        bloodGroup: String,
        isAvailableDonor: Boolean,
        district: String,
        contactPhone: String,
        latitude: Double,
        longitude: Double,
        lastDonationDate: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("donor_nodes").document(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (existingSnap != null && existingSnap.exists()) {
            val updateMap = mapOf(
                "displayName" to displayName.take(80),
                "bloodGroup" to bloodGroup,
                "isAvailableDonor" to isAvailableDonor,
                "district" to district.take(80),
                "contactPhone" to contactPhone.take(24),
                "latitude" to latitude,
                "longitude" to longitude,
                "lastDonationDate" to lastDonationDate.take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            write(docRef, OperationType.UPDATE) { docRef.update(updateMap).await() }
        } else {
            val createMap = mapOf(
                "userId" to uid,
                "displayName" to displayName.ifBlank { "Rokkhon Donor" }.take(80),
                "bloodGroup" to bloodGroup,
                "isAvailableDonor" to isAvailableDonor,
                "district" to district.take(80),
                "contactPhone" to contactPhone.take(24),
                "latitude" to latitude,
                "longitude" to longitude,
                "lastDonationDate" to lastDonationDate.take(40),
                "trustScore" to 100,
                "verifiedSosCount" to 0,
                "networkKey" to "BD_ROKKHON",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            write(docRef, OperationType.CREATE) { docRef.set(createMap).await() }
        }
    }

    suspend fun toggleDonorAvailability(isAvailable: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("donor_nodes").document(uid)
        write(docRef, OperationType.UPDATE) {
            docRef.update(
                mapOf(
                    "isAvailableDonor" to isAvailable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun createBloodRequest(
        requesterName: String,
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
        urgencyLevel: String,
        latitude: Double,
        longitude: Double
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val requestId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("blood_requests").document(requestId)
        val payload = mapOf(
            "userId" to uid,
            "requesterName" to requesterName.ifBlank { "Rokkhon Member" }.take(80),
            "patientName" to patientName.ifBlank { "Patient" }.take(100),
            "bloodGroup" to bloodGroup,
            "unitsNeeded" to unitsNeeded.coerceIn(1, 10),
            "hemoglobinLevel" to hemoglobinLevel.ifBlank { "-" }.take(40),
            "patientProblem" to patientProblem.ifBlank { "-" }.take(160),
            "hospitalName" to hospitalName.take(120),
            "wardCabinNumber" to wardCabinNumber.ifBlank { "-" }.take(80),
            "neededDateTime" to neededDateTime.ifBlank { "-" }.take(80),
            "district" to district.take(80),
            "contactPhone" to contactPhone.take(24),
            "urgencyLevel" to urgencyLevel,
            "latitude" to latitude,
            "longitude" to longitude,
            "status" to "OPEN",
            "respondersCount" to 0,
            "networkKey" to "BD_ROKKHON",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        write(docRef, OperationType.CREATE) { docRef.set(payload).await() }
        requestId
    }

    /**
     * One pledge per account is enforced on the server: the pledge marker document
     * `blood_requests/{id}/pledges/{uid}` and the counter increment are committed together,
     * and the security rules reject the batch if the marker already exists.
     */
    suspend fun pledgeBloodResponse(request: BloodRequest): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("blood_requests").document(request.id)
        val markerRef = docRef.collection("pledges").document(uid)
        write(docRef, OperationType.UPDATE) {
            db.batch()
                .set(markerRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                .update(
                    docRef,
                    mapOf(
                        "respondersCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .commit()
                .await()
        }
    }

    suspend fun markBloodRequestFulfilled(requestId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("blood_requests").document(requestId)
        write(docRef, OperationType.UPDATE) {
            docRef.update(
                mapOf(
                    "status" to "FULFILLED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun createVerifiedSosAlert(
        senderName: String,
        bloodGroup: String,
        emergencyType: String,
        district: String,
        latitude: Double,
        longitude: Double,
        hasLiveCameraVerification: Boolean,
        hasLiveAudioVerification: Boolean,
        peakAudioDecibels: Int,
        triggerMethod: String,
        verificationNote: String,
        bleBeaconHash: String
    ): Result<String> = runCatching {
        // An SOS is never blocked for missing evidence: a victim without mic/camera permission
        // still needs help. Evidence flags only change how the alert is labelled.
        val uid = requireUserId()
        val alertId = "sos_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("sos_alerts").document(alertId)
        val payload = mapOf(
            "userId" to uid,
            "senderName" to senderName.ifBlank { "Rokkhon Citizen" }.take(80),
            "bloodGroup" to bloodGroup,
            "emergencyType" to emergencyType,
            "district" to district.ifBlank { "Bangladesh" }.take(80),
            "latitude" to latitude,
            "longitude" to longitude,
            "hasLiveCameraVerification" to hasLiveCameraVerification,
            "hasLiveAudioVerification" to hasLiveAudioVerification,
            "peakAudioDecibels" to peakAudioDecibels.coerceIn(0, 140),
            "triggerMethod" to triggerMethod.ifBlank { "ONE_TAP_SOS" }.take(40),
            "verificationNote" to verificationNote.ifBlank { "SOS" }.take(300),
            "bleBeaconHash" to bleBeaconHash.ifBlank { "BLE-BD" }.take(64),
            "status" to "ACTIVE",
            "responderCount" to 0,
            "falseAlarmReports" to 0,
            "networkKey" to "BD_ROKKHON",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        write(docRef, OperationType.CREATE) { docRef.set(payload).await() }
        alertId
    }

    suspend fun respondToSosAlert(alert: SosAlert): Result<Unit> =
        voteOnSos(alert, markerCollection = "responders", counterField = "responderCount")

    suspend fun reportFalseAlarm(alert: SosAlert): Result<Unit> =
        voteOnSos(alert, markerCollection = "false_reports", counterField = "falseAlarmReports")

    private suspend fun voteOnSos(
        alert: SosAlert,
        markerCollection: String,
        counterField: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("sos_alerts").document(alert.id)
        val markerRef = docRef.collection(markerCollection).document(uid)
        write(docRef, OperationType.UPDATE) {
            db.batch()
                .set(markerRef, mapOf("userId" to uid, "createdAt" to FieldValue.serverTimestamp()))
                .update(
                    docRef,
                    mapOf(
                        counterField to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .commit()
                .await()
        }
    }

    suspend fun updateOwnSosLiveLocation(
        alertId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alertId)
        write(docRef, OperationType.UPDATE) {
            docRef.update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun resolveOwnSosAlert(alertId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alertId)
        write(docRef, OperationType.UPDATE) {
            docRef.update(
                mapOf(
                    "status" to "RESOLVED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend inline fun write(
        docRef: DocumentReference,
        operation: OperationType,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operation, docRef.path)
            throw e
        }
    }
}
