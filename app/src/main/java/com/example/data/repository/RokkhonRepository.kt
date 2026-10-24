package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BloodRequest
import com.example.data.model.DonorNode
import com.example.data.model.SosAlert
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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

    suspend fun saveOrUpdateDonorNode(
        displayName: String,
        bloodGroup: String,
        isAvailableDonor: Boolean,
        district: String,
        contactPhone: String,
        latitude: Double,
        longitude: Double,
        lastDonationDate: String,
        trustScore: Long = 100L,
        verifiedSosCount: Long = 0L
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("donor_nodes").document(uid)
        val existingSnap = try {
            docRef.get().await()
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
                "trustScore" to trustScore.coerceIn(0L, 100L),
                "verifiedSosCount" to verifiedSosCount.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updateMap).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        } else {
            val createMap = mapOf(
                "userId" to uid,
                "displayName" to displayName.ifBlank { "Rokkhon Guardian" }.take(80),
                "bloodGroup" to bloodGroup,
                "isAvailableDonor" to isAvailableDonor,
                "district" to district.ifBlank { "Dhanmondi, Dhaka" }.take(80),
                "contactPhone" to contactPhone.ifBlank { "+8801700000000" }.take(24),
                "latitude" to latitude,
                "longitude" to longitude,
                "lastDonationDate" to lastDonationDate.ifBlank { "Ready to Donate" }.take(40),
                "trustScore" to trustScore.coerceIn(0L, 100L),
                "verifiedSosCount" to verifiedSosCount.coerceAtLeast(0L),
                "networkKey" to "BD_ROKKHON",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(createMap).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }
    }

    suspend fun toggleDonorAvailability(isAvailable: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("donor_nodes").document(uid)
        try {
            docRef.update(
                mapOf(
                    "isAvailableDonor" to isAvailable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
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
            "patientName" to patientName.ifBlank { "Emergency Patient" }.take(100),
            "bloodGroup" to bloodGroup,
            "unitsNeeded" to unitsNeeded.coerceIn(1, 10),
            "hemoglobinLevel" to hemoglobinLevel.ifBlank { "6.8 g/dL" }.take(40),
            "patientProblem" to patientProblem.ifBlank { "Emergency Surgery / Anemia" }.take(160),
            "hospitalName" to hospitalName.ifBlank { "Dhaka Medical College Hospital" }.take(120),
            "wardCabinNumber" to wardCabinNumber.ifBlank { "Emergency Ward" }.take(80),
            "neededDateTime" to neededDateTime.ifBlank { "আজ জরুরি (Within 2 Hours)" }.take(80),
            "district" to district.ifBlank { "Dhanmondi, Dhaka" }.take(80),
            "contactPhone" to contactPhone.ifBlank { "+8801700000000" }.take(24),
            "urgencyLevel" to urgencyLevel,
            "latitude" to latitude,
            "longitude" to longitude,
            "status" to "OPEN",
            "respondersCount" to 0,
            "networkKey" to "BD_ROKKHON",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            requestId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun pledgeBloodResponse(request: BloodRequest): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("blood_requests").document(request.id)
        try {
            docRef.update(
                mapOf(
                    "respondersCount" to (request.respondersCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun markBloodRequestFulfilled(requestId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("blood_requests").document(requestId)
        try {
            docRef.update(
                mapOf(
                    "status" to "FULFILLED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
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
        val uid = requireUserId()
        require(hasLiveCameraVerification || hasLiveAudioVerification) {
            "Silent 3s Camera or Audio Blackbox proof is required to prevent false alarms."
        }
        val alertId = "sos_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("sos_alerts").document(alertId)
        val payload = mapOf(
            "userId" to uid,
            "senderName" to senderName.ifBlank { "Rokkhon Citizen" }.take(80),
            "bloodGroup" to bloodGroup,
            "emergencyType" to emergencyType,
            "district" to district.ifBlank { "Dhaka" }.take(80),
            "latitude" to latitude,
            "longitude" to longitude,
            "hasLiveCameraVerification" to hasLiveCameraVerification,
            "hasLiveAudioVerification" to hasLiveAudioVerification,
            "peakAudioDecibels" to peakAudioDecibels.coerceIn(0, 140),
            "triggerMethod" to triggerMethod.ifBlank { "3X_POWER_BUTTON" }.take(40),
            "verificationNote" to verificationNote.ifBlank { "Auto 3s Silent Cam + Audio Blackbox Attached" }.take(300),
            "bleBeaconHash" to bleBeaconHash.ifBlank { "BLE-BD" }.take(64),
            "status" to "ACTIVE",
            "responderCount" to 0,
            "falseAlarmReports" to 0,
            "networkKey" to "BD_ROKKHON",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            alertId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun respondToSosAlert(alert: SosAlert): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alert.id)
        try {
            docRef.update(
                mapOf(
                    "responderCount" to (alert.responderCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateOwnSosLiveLocation(
        alertId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alertId)
        try {
            docRef.update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun resolveOwnSosAlert(alertId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alertId)
        try {
            docRef.update(
                mapOf(
                    "status" to "RESOLVED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun reportFalseAlarm(alert: SosAlert): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("sos_alerts").document(alert.id)
        val newReports = alert.falseAlarmReports + 1L
        try {
            docRef.update(
                mapOf(
                    "falseAlarmReports" to newReports,
                    "status" to alert.status.ifBlank { "ACTIVE" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}

