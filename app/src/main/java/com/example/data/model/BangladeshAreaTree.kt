package com.example.data.model

/** One district (জেলা) with the places you can pick inside it. */
data class AreaDistrict(
    val nameEn: String,
    val nameBn: String,
    val divisionEn: String,
    val divisionBn: String,
    /** The whole district (real coordinates; used for GPS matching). */
    val main: BangladeshDistrict,
    /** Well-known city areas kept from the curated list (e.g. Mirpur, Dhanmondi). */
    val popular: List<BangladeshDistrict>,
    val upazilas: List<BangladeshDistrict>
)

/** One division (বিভাগ) and its districts. */
data class AreaDivision(val nameEn: String, val nameBn: String, val districts: List<AreaDistrict>) {
    val upazilaCount: Int get() = districts.sumOf { it.upazilas.size }
}

/** Short name of an area without the trailing ", District" ("Bauphal, Patuakhali" → "Bauphal"). */
fun BangladeshDistrict.shortNameEn(): String = nameEn.substringBefore(",").trim()
fun BangladeshDistrict.shortNameBn(): String = nameBn.substringBefore(",").trim()

private fun districtKey(name: String): String =
    name.lowercase().filter { it.isLetter() }.replace("barisal", "barishal")

/**
 * Bangladesh as বিভাগ → জেলা → উপজেলা, built from the curated district list (real coordinates,
 * hospitals, popular city areas) and the 494 upazilas. Every curated entry and every upazila
 * appears exactly once.
 */
val BANGLADESH_AREA_TREE: List<AreaDivision> by lazy {
    // Upazilas carry their district as the last part of the English name.
    val upazilasByDistrict = BANGLADESH_UPAZILAS.groupBy { districtKey(it.nameEn.substringAfterLast(",")) }
    val curatedByDistrict = BANGLADESH_DISTRICTS.groupBy { d ->
        val tail = districtKey(d.nameEn.substringAfterLast(","))
        when {
            upazilasByDistrict.containsKey(tail) -> tail
            d.nameEn.contains("Dhaka") -> "dhaka" // "Jatrabari & Old Dhaka"
            else -> tail
        }
    }
    BANGLADESH_DIVISIONS.filter { it.first != "ALL" }.map { (divKey, names) ->
        val districts = BANGLADESH_DISTRICTS
            .filter { it.divisionEn == divKey }
            .map { d -> curatedByDistrict.entries.first { e -> d in e.value }.key }
            .distinct()
            .mapNotNull { key ->
                val curated = curatedByDistrict[key].orEmpty()
                val upazilas = upazilasByDistrict[key].orEmpty().sortedBy { it.shortNameEn() }
                // The whole-district entry: same key as the district ("chattogram", "dhaka_district"),
                // otherwise the one with a plain name, otherwise the first.
                val main = curated.firstOrNull { it.areaKey == key || it.areaKey == "${key}_district" }
                    ?: curated.firstOrNull { !it.nameEn.contains(",") }
                    ?: curated.firstOrNull()
                    ?: return@mapNotNull null
                val first = upazilas.firstOrNull()
                AreaDistrict(
                    nameEn = first?.nameEn?.substringAfterLast(",")?.trim()?.replace("Barisal", "Barishal") ?: main.shortNameEn(),
                    nameBn = main.nameBn.substringAfterLast(",").trim(),
                    divisionEn = names.first,
                    divisionBn = names.second,
                    main = main,
                    popular = curated.filter { it !== main },
                    upazilas = upazilas
                )
            }
        AreaDivision(names.first, names.second, districts)
    }
}

/** Where an area sits: its division and district, for breadcrumbs and search results. */
fun areaPath(area: BangladeshDistrict): Pair<AreaDivision, AreaDistrict>? {
    for (div in BANGLADESH_AREA_TREE) for (d in div.districts) {
        if (d.main.areaKey == area.areaKey || d.popular.any { it.areaKey == area.areaKey } || d.upazilas.any { it.areaKey == area.areaKey }) {
            return div to d
        }
    }
    return null
}
