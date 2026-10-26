package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangladeshAreaTreeTest {

    @Test
    fun treeHasEveryDivisionDistrictAndUpazilaOnce() {
        assertEquals(8, BANGLADESH_AREA_TREE.size)
        val districts = BANGLADESH_AREA_TREE.flatMap { it.districts }
        assertEquals(64, districts.size)
        assertEquals(64, districts.map { it.nameEn }.toSet().size)
        assertEquals(494, districts.sumOf { it.upazilas.size })
        // Every curated area (district centres and city areas) is reachable exactly once.
        val curated = districts.flatMap { listOf(it.main) + it.popular }.map { it.areaKey }
        assertEquals(BANGLADESH_DISTRICTS.size, curated.size)
        assertEquals(BANGLADESH_DISTRICTS.map { it.areaKey }.toSet(), curated.toSet())
        districts.forEach { assertTrue("${it.nameEn} has no upazilas", it.upazilas.isNotEmpty()) }
    }

    @Test
    fun dhakaDistrictKeepsCityAreasAndUpazilas() {
        val dhaka = BANGLADESH_AREA_TREE.first { it.nameEn == "Dhaka" }.districts.first { it.nameEn == "Dhaka" }
        assertEquals("dhaka_district", dhaka.main.areaKey)
        assertTrue(dhaka.popular.any { it.areaKey == "mirpur" })
        assertTrue(dhaka.popular.any { it.areaKey == "jatrabari" })
        assertTrue(dhaka.upazilas.any { it.shortNameEn() == "Savar" })
        assertEquals("ঢাকা", dhaka.nameBn)
    }

    @Test
    fun pathOfAnUpazilaIsItsDivisionAndDistrict() {
        val bauphal = BANGLADESH_UPAZILAS.first { it.nameEn.startsWith("Bauphal") }
        val (div, district) = assertNotNullPair(areaPath(bauphal))
        assertEquals("Barishal", div.nameEn)
        assertEquals("Patuakhali", district.nameEn)
        assertEquals("Barishal", BANGLADESH_AREA_TREE.first { it.districts.any { d -> d.nameEn == "Barishal" } }.nameEn)
    }

    private fun <A, B> assertNotNullPair(p: Pair<A, B>?): Pair<A, B> {
        assertNotNull(p)
        return p!!
    }
}
