package com.example.data.ble

import com.example.data.ble.BleSosMeshManager.Companion.MAX_HOPS
import com.example.data.ble.BleSosMeshManager.Companion.PACKET_KIND_BLOOD_AREA
import com.example.data.ble.BleSosMeshManager.Companion.PACKET_KIND_SOS_1KM
import com.example.data.ble.BleSosMeshManager.Companion.decodePayload
import com.example.data.ble.BleSosMeshManager.Companion.encodePayload
import com.example.data.ble.BleSosMeshManager.Companion.meshDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMeshRulesTest {

    @Test
    fun payload_roundTripsIdHopsAndLocation() {
        val bytes = encodePayload(
            isBlood = false, bloodGroup = "AB-", typeOrUnits = 2, verifiedOrAreaIdx = 1,
            lat = 23.8069f, lng = 90.3687f, packetId = 0x1234ABCD, hops = 3
        )
        // Fits a legacy BLE advertisement next to the flags and the 16-bit service UUID.
        assertEquals(16, bytes.size)
        val decoded = decodePayload(bytes)!!
        assertFalse(decoded.isBlood)
        assertEquals("AB-", decoded.bloodGroup)
        assertEquals(0x1234ABCD, decoded.packetId)
        assertEquals(3, decoded.hops)
        assertEquals(23.8069, decoded.latitude, 1e-4)
        assertEquals(90.3687, decoded.longitude, 1e-4)
    }

    @Test
    fun relayedCopy_keepsSamePacketId() {
        val original = encodePayload(true, "O+", 2, 17, 23.7f, 90.4f, packetId = 99, hops = 0)
        val relayed = original.copyOf().also { it[it.size - 1] = 1 }
        assertEquals(decodePayload(original)!!.packetId, decodePayload(relayed)!!.packetId)
        assertEquals(1, decodePayload(relayed)!!.hops)
    }

    @Test
    fun oldElevenBytePackets_stillDecode() {
        val legacy = encodePayload(false, "B+", 1, 0, 23.7f, 90.4f, packetId = 5, hops = 0).copyOf(11)
        val decoded = decodePayload(legacy)
        assertNotNull(decoded)
        assertEquals(0, decoded!!.hops)
    }

    @Test
    fun garbage_isRejected() {
        assertNull(decodePayload(ByteArray(5)))
        val badLat = encodePayload(false, "O+", 0, 0, Float.NaN, 90f, 1, 0)
        assertNull(decodePayload(badLat))
    }

    @Test
    fun sos_insideOneKm_alertsAndRelays() {
        val d = meshDecision(PACKET_KIND_SOS_1KM, distanceFromOriginKm = 0.8, hops = 4)
        assertTrue(d.alert)
        assertTrue(d.relay)
    }

    @Test
    fun sos_beyondOneKm_neitherAlertsNorRelays() {
        val d = meshDecision(PACKET_KIND_SOS_1KM, distanceFromOriginKm = 1.4, hops = 1)
        assertFalse(d.alert)
        assertFalse(d.relay)
    }

    @Test
    fun sos_stopsRelayingAtHopLimit() {
        val d = meshDecision(PACKET_KIND_SOS_1KM, distanceFromOriginKm = 0.3, hops = MAX_HOPS)
        assertTrue(d.alert)
        assertFalse(d.relay)
    }

    @Test
    fun sos_withoutOwnLocation_usesSmallHopLimit() {
        assertTrue(meshDecision(PACKET_KIND_SOS_1KM, null, hops = 0).alert)
        assertFalse(meshDecision(PACKET_KIND_SOS_1KM, null, hops = 5).alert)
    }

    @Test
    fun blood_relaysOnlyAFewHops() {
        assertTrue(meshDecision(PACKET_KIND_BLOOD_AREA, null, 0).relay)
        assertFalse(meshDecision(PACKET_KIND_BLOOD_AREA, null, 2).relay)
    }
}
