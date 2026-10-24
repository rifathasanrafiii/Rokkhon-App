package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RokkhonRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveDonorNode_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = RokkhonRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveOrUpdateDonorNode(
                displayName = "Nadia Islam",
                bloodGroup = "B+",
                isAvailableDonor = true,
                district = "Dhanmondi, Dhaka",
                contactPhone = "+8801712345678",
                latitude = 23.7461,
                longitude = 90.3742,
                lastDonationDate = "2026-05-10"
            )
        }
        assertTrue("Expected donor node save to succeed", saveResult.isSuccess)

        val donors = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDonorNodes().first { list -> list.any { it.userId == uid } }
        }
        val myDonor = donors.first { it.userId == uid }
        assertEquals("B+", myDonor.bloodGroup)
        assertTrue(myDonor.isAvailableDonor)
    }

    @Test
    fun createBloodRequest_andVerifiedSosAlert_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = RokkhonRepository(firestore)

        val reqResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBloodRequest(
                requesterName = "Nadia Islam",
                patientName = "Kamal Hossain",
                bloodGroup = "O-",
                unitsNeeded = 2,
                hemoglobinLevel = "6.5 g/dL",
                patientProblem = "Dengue Shock Syndrome",
                hospitalName = "Dhaka Medical College Hospital",
                wardCabinNumber = "Ward 14, Bed 9",
                neededDateTime = "আজ রাত ১০টার মধ্যে",
                district = "Shahbagh, Dhaka",
                contactPhone = "+8801712345678",
                urgencyLevel = "CRITICAL",
                latitude = 23.7389,
                longitude = 90.3948
            )
        }
        assertTrue(reqResult.isSuccess)
        val requestId = reqResult.getOrThrow()

        val sosResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVerifiedSosAlert(
                senderName = "Nadia Islam",
                bloodGroup = "B+",
                emergencyType = "ROAD_DANGER",
                district = "Mirpur 10, Dhaka",
                latitude = 23.8069,
                longitude = 90.3687,
                hasLiveCameraVerification = true,
                hasLiveAudioVerification = true,
                peakAudioDecibels = 82,
                triggerMethod = "3X_POWER_BUTTON",
                verificationNote = "Auto 3s Silent Cam + Audio Blackbox Attached",
                bleBeaconHash = "BLE-BD-77"
            )
        }
        assertTrue(sosResult.isSuccess)
        val alertId = sosResult.getOrThrow()

        val requests = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBloodRequests().first { list -> list.any { it.id == requestId } }
        }
        assertTrue(requests.any { it.id == requestId && it.hemoglobinLevel == "6.5 g/dL" })

        val alerts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSosAlerts().first { list -> list.any { it.id == alertId } }
        }
        assertTrue(alerts.any { it.id == alertId && it.triggerMethod == "3X_POWER_BUTTON" })
    }

    @Test
    fun crossUserDonorNodeUpdate_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = RokkhonRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveOrUpdateDonorNode(
                displayName = "Alice",
                bloodGroup = "A+",
                isAvailableDonor = true,
                district = "Uttara, Dhaka",
                contactPhone = "+8801711111111",
                latitude = 23.8728,
                longitude = 90.3984,
                lastDonationDate = "Ready"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        var caughtException: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("donor_nodes").document(aliceUid)
                    .update(
                        mapOf(
                            "isAvailableDonor" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            caughtException = e
        }
        assertNotNull("Expected cross-user update to fail", caughtException)
        val fsEx = caughtException?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeDonorNodes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = RokkhonRepository(firestore)

        var caughtException: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDonorNodes().first()
            }
        } catch (e: Exception) {
            caughtException = e
        }
        assertNotNull("Expected unauthenticated query to fail", caughtException)
        val fsEx = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rokkhon@test.com"
        const val BOB_EMAIL = "bob_rokkhon@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
