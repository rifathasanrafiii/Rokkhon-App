package com.example.data.push

import com.example.data.model.BANGLADESH_AREA_TREE
import com.example.data.model.BANGLADESH_UPAZILAS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PushTopicsTest {

    @Test
    fun bloodGroupTopics() {
        assertEquals("bg_O_POS", PushTopics.bloodGroupTopic("O+"))
        assertEquals("bg_AB_NEG", PushTopics.bloodGroupTopic("ab-"))
        assertNull(PushTopics.bloodGroupTopic("X"))
    }

    @Test
    fun upazilasUseTheirDistrictTopic() {
        val bauphal = BANGLADESH_UPAZILAS.first { it.nameEn.startsWith("Bauphal") }
        assertEquals("dist_patuakhali", PushTopics.districtTopic(bauphal))
    }

    /** The admin panel's district list (web/admin/districts.js) must use the same topics as the app. */
    @Test
    fun adminPanelDistrictListMatchesTheApp() {
        val expected = buildString {
            append("// Generated from the app's area data; checked by PushTopicsTest. Do not edit by hand.\n")
            append("window.ROKKHON_DISTRICTS = [\n")
            BANGLADESH_AREA_TREE.forEach { div ->
                div.districts.forEach { d ->
                    append("  { topic: \"${PushTopics.districtTopic(d.main)}\", name: \"${d.nameEn}\", nameBn: \"${d.nameBn}\", division: \"${div.nameEn}\" },\n")
                }
            }
            append("];\n")
        }
        val file = listOf(File("../web/admin/districts.js"), File("web/admin/districts.js")).firstOrNull { it.parentFile?.exists() == true }
            ?: error("web/admin not found")
        if (System.getenv("ROKKHON_WRITE_DISTRICTS") != null) file.writeText(expected)
        assertEquals("Run with ROKKHON_WRITE_DISTRICTS=1 to regenerate web/admin/districts.js", expected, file.readText())
    }
}
