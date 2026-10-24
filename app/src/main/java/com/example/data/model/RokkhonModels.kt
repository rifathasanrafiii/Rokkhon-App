package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val code: String, val label: String) {
    BENGALI("bn", "বাংলা"),
    ENGLISH("en", "EN")
}

enum class BloodGroup(val label: String) {
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    O_POS("O+"),
    O_NEG("O-"),
    AB_POS("AB+"),
    AB_NEG("AB-");

    companion object {
        val ALL_LABELS = entries.map { it.label }
    }
}

data class BangladeshDistrict(
    val areaKey: String,
    val nameEn: String,
    val nameBn: String,
    val latitude: Double,
    val longitude: Double,
    val defaultHospitalEn: String,
    val defaultHospitalBn: String,
    val divisionEn: String = "Dhaka",
    val divisionBn: String = "ঢাকা"
) {
    fun matchesArea(locationText: String): Boolean {
        if (locationText.isBlank()) return false
        val normalized = locationText.lowercase()
        val key = areaKey.lowercase()
        val firstTokenEn = nameEn.substringBefore(",").trim().lowercase()
        val firstTokenBn = nameBn.substringBefore(",").trim()
        return normalized.contains(key) ||
            normalized.contains(firstTokenEn) ||
            (firstTokenBn.isNotEmpty() && locationText.contains(firstTokenBn)) ||
            (key == "dhanmondi" && (normalized.contains("kalabagan") || normalized.contains("lalmatia") || normalized.contains("panthapath"))) ||
            (key == "mirpur" && (normalized.contains("mirpur") || normalized.contains("pallabi") || normalized.contains("kazipara") || normalized.contains("shewrapara"))) ||
            (key == "uttara" && (normalized.contains("uttara") || normalized.contains("airport") || normalized.contains("tongi"))) ||
            (key == "gulshan" && (normalized.contains("gulshan") || normalized.contains("banani") || normalized.contains("baridhara") || normalized.contains("mohakhali")))
    }

    fun matchesSearch(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return nameEn.lowercase().contains(q) ||
            nameBn.contains(q) ||
            areaKey.lowercase().contains(q) ||
            divisionEn.lowercase().contains(q) ||
            divisionBn.contains(q) ||
            defaultHospitalEn.lowercase().contains(q)
    }
}

val BANGLADESH_DIVISIONS = listOf(
    "ALL" to ("All" to "সব বিভাগ"),
    "Dhaka" to ("Dhaka" to "ঢাকা"),
    "Chattogram" to ("Chattogram" to "চট্টগ্রাম"),
    "Rajshahi" to ("Rajshahi" to "রাজশাহী"),
    "Khulna" to ("Khulna" to "খুলনা"),
    "Sylhet" to ("Sylhet" to "সিলেট"),
    "Barishal" to ("Barishal" to "বরিশাল"),
    "Rangpur" to ("Rangpur" to "রংপুর"),
    "Mymensingh" to ("Mymensingh" to "ময়মনসিংহ")
)

/**
 * Complete Bangladesh Area Directory:
 * Major Metropolitan Thanas/Areas in Dhaka & Chattogram + All 64 Districts of Bangladesh across 8 Divisions.
 */
val BANGLADESH_DISTRICTS: List<BangladeshDistrict> = listOf(
    // --- Dhaka Metropolitan Thanas & Major Hubs ---
    BangladeshDistrict(
        areaKey = "mirpur",
        nameEn = "Mirpur 10, Dhaka",
        nameBn = "মিরপুর ১০, ঢাকা",
        latitude = 23.8069,
        longitude = 90.3687,
        defaultHospitalEn = "National Heart Foundation & Kidney Institute, Mirpur",
        defaultHospitalBn = "ন্যাশনাল হার্ট ফাউন্ডেশন হাসপাতাল, মিরপুর",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "dhanmondi",
        nameEn = "Dhanmondi, Dhaka",
        nameBn = "ধানমন্ডি, ঢাকা",
        latitude = 23.7461,
        longitude = 90.3742,
        defaultHospitalEn = "Square Hospital & LabAid, Dhanmondi",
        defaultHospitalBn = "স্কয়ার হাসপাতাল ও ল্যাবএইড, ধানমন্ডি",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "shahbagh",
        nameEn = "Shahbagh, Dhaka",
        nameBn = "শাহবাগ, ঢাকা",
        latitude = 23.7389,
        longitude = 90.3948,
        defaultHospitalEn = "Dhaka Medical College Hospital (DMCH) & BSMMU",
        defaultHospitalBn = "ঢাকা মেডিকেল কলেজ হাসপাতাল (ঢামেক)",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "uttara",
        nameEn = "Uttara Sector 7, Dhaka",
        nameBn = "উত্তরা সেক্টর ৭, ঢাকা",
        latitude = 23.8728,
        longitude = 90.3984,
        defaultHospitalEn = "Uttara Adhunik Medical College Hospital",
        defaultHospitalBn = "উত্তরা আধুনিক মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "bashundhara",
        nameEn = "Bashundhara, Dhaka",
        nameBn = "বসুন্ধরা, ঢাকা",
        latitude = 23.8191,
        longitude = 90.4526,
        defaultHospitalEn = "Evercare Hospital Dhaka",
        defaultHospitalBn = "এভারকেয়ার হাসপাতাল ঢাকা",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "mohammadpur",
        nameEn = "Mohammadpur, Dhaka",
        nameBn = "মোহাম্মদপুর, ঢাকা",
        latitude = 23.7662,
        longitude = 90.3589,
        defaultHospitalEn = "Shaheed Suhrawardy Medical College Hospital",
        defaultHospitalBn = "শহীদ সোহরাওয়ার্দী মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "gulshan",
        nameEn = "Gulshan & Banani, Dhaka",
        nameBn = "গুলশান ও বনানী, ঢাকা",
        latitude = 23.7925,
        longitude = 90.4078,
        defaultHospitalEn = "United Hospital Limited, Gulshan",
        defaultHospitalBn = "ইউনাইটেড হাসপাতাল, গুলশান",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "motijheel",
        nameEn = "Motijheel & Paltan, Dhaka",
        nameBn = "মতিঝিল ও পল্টন, ঢাকা",
        latitude = 23.7330,
        longitude = 90.4172,
        defaultHospitalEn = "Islami Bank Central Hospital, Kakrail",
        defaultHospitalBn = "ইসলামী ব্যাংক সেন্ট্রাল হাসপাতাল, কাকরাইল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "jatrabari",
        nameEn = "Jatrabari & Old Dhaka",
        nameBn = "যাত্রাবাড়ী ও পুরান ঢাকা",
        latitude = 23.7104,
        longitude = 90.4348,
        defaultHospitalEn = "Sir Salimullah Medical College (Mitford) Hospital",
        defaultHospitalBn = "স্যার সলিমুল্লাহ মেডিকেল কলেজ (মিটফোর্ড) হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "badda",
        nameEn = "Badda & Rampura, Dhaka",
        nameBn = "বাড্ডা ও রামপুরা, ঢাকা",
        latitude = 23.7805,
        longitude = 90.4267,
        defaultHospitalEn = "AMZ Hospital & Better Life Hospital, Badda",
        defaultHospitalBn = "এএমজেড হাসপাতাল, বাড্ডা",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "savar",
        nameEn = "Savar & Ashulia, Dhaka",
        nameBn = "সাভার ও আশুলিয়া, ঢাকা",
        latitude = 23.8583,
        longitude = 90.2667,
        defaultHospitalEn = "Enam Medical College & Hospital, Savar",
        defaultHospitalBn = "এনাম মেডিকেল কলেজ হাসপাতাল, সাভার",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),

    // --- Dhaka Division Districts (13 Districts) ---
    BangladeshDistrict(
        areaKey = "dhaka_district",
        nameEn = "Dhaka Sadar, Dhaka",
        nameBn = "ঢাকা সদর, ঢাকা",
        latitude = 23.7115,
        longitude = 90.4111,
        defaultHospitalEn = "Dhaka Medical College Hospital",
        defaultHospitalBn = "ঢাকা মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "gazipur",
        nameEn = "Gazipur",
        nameBn = "গাজীপুর",
        latitude = 23.9999,
        longitude = 90.4203,
        defaultHospitalEn = "Shaheed Tajuddin Ahmad Medical College Hospital, Gazipur",
        defaultHospitalBn = "শহীদ তাজউদ্দীন আহমদ মেডিকেল কলেজ হাসপাতাল, গাজীপুর",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "narayanganj",
        nameEn = "Narayanganj",
        nameBn = "নারায়ণগঞ্জ",
        latitude = 23.6238,
        longitude = 90.5000,
        defaultHospitalEn = "300 Bed Hospital Khanpur, Narayanganj",
        defaultHospitalBn = "৩০০ শয্যা হাসপাতাল খানপুর, নারায়ণগঞ্জ",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "tangail",
        nameEn = "Tangail",
        nameBn = "টাঙ্গাইল",
        latitude = 24.2513,
        longitude = 89.9167,
        defaultHospitalEn = "Sheikh Hasina Medical College Hospital, Tangail",
        defaultHospitalBn = "টাঙ্গাইল জেনারেল ও মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "narsingdi",
        nameEn = "Narsingdi",
        nameBn = "নরসিংদী",
        latitude = 23.9322,
        longitude = 90.7154,
        defaultHospitalEn = "Narsingdi 100 Bed District Hospital",
        defaultHospitalBn = "নরসিংদী জেলা হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "munshiganj",
        nameEn = "Munshiganj",
        nameBn = "মুন্সীগঞ্জ",
        latitude = 23.5422,
        longitude = 90.5305,
        defaultHospitalEn = "Munshiganj 250 Bed General Hospital",
        defaultHospitalBn = "মুন্সীগঞ্জ ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "manikganj",
        nameEn = "Manikganj",
        nameBn = "মানিকগঞ্জ",
        latitude = 23.8644,
        longitude = 90.0047,
        defaultHospitalEn = "Colonel Malek Medical College Hospital, Manikganj",
        defaultHospitalBn = "কর্নেল মালেক মেডিকেল কলেজ হাসপাতাল, মানিকগঞ্জ",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "kishoreganj",
        nameEn = "Kishoreganj",
        nameBn = "কিশোরগঞ্জ",
        latitude = 24.4449,
        longitude = 90.7766,
        defaultHospitalEn = "Shaheed Syed Nazrul Islam Medical College, Kishoreganj",
        defaultHospitalBn = "শহীদ সৈয়দ নজরুল ইসলাম মেডিকেল কলেজ, কিশোরগঞ্জ",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "faridpur",
        nameEn = "Faridpur",
        nameBn = "ফরিদপুর",
        latitude = 23.6070,
        longitude = 89.8429,
        defaultHospitalEn = "Faridpur Medical College Hospital",
        defaultHospitalBn = "ফরিদপুর মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "gopalganj",
        nameEn = "Gopalganj",
        nameBn = "গোপালগঞ্জ",
        latitude = 23.0051,
        longitude = 89.8266,
        defaultHospitalEn = "250 Bed General Hospital, Gopalganj",
        defaultHospitalBn = "গোপালগঞ্জ ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "madaripur",
        nameEn = "Madaripur",
        nameBn = "মাদারীপুর",
        latitude = 23.1641,
        longitude = 90.1897,
        defaultHospitalEn = "Madaripur 250 Bed District Hospital",
        defaultHospitalBn = "মাদারীপুর জেলা সদর হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "rajbari",
        nameEn = "Rajbari",
        nameBn = "রাজবাড়ী",
        latitude = 23.7574,
        longitude = 89.6445,
        defaultHospitalEn = "Rajbari Sadar Hospital",
        defaultHospitalBn = "রাজবাড়ী সদর হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),
    BangladeshDistrict(
        areaKey = "shariatpur",
        nameEn = "Shariatpur",
        nameBn = "শরীয়তপুর",
        latitude = 23.2423,
        longitude = 90.4348,
        defaultHospitalEn = "Shariatpur 100 Bed Sadar Hospital",
        defaultHospitalBn = "শরীয়তপুর সদর হাসপাতাল",
        divisionEn = "Dhaka",
        divisionBn = "ঢাকা"
    ),

    // --- Chattogram Division (11 Districts + Metro Areas) ---
    BangladeshDistrict(
        areaKey = "chattogram",
        nameEn = "Panchlaish, Chattogram",
        nameBn = "পাঁচলাইশ, চট্টগ্রাম",
        latitude = 22.3569,
        longitude = 91.8215,
        defaultHospitalEn = "Chittagong Medical College Hospital (CMCH)",
        defaultHospitalBn = "চট্টগ্রাম মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "agrabad",
        nameEn = "Agrabad & Halishahar, Chattogram",
        nameBn = "আগ্রাবাদ ও হালিশহর, চট্টগ্রাম",
        latitude = 22.3264,
        longitude = 91.8117,
        defaultHospitalEn = "Chattogram Maa-O-Shishu Hospital, Agrabad",
        defaultHospitalBn = "চট্টগ্রাম মা ও শিশু হাসপাতাল, আগ্রাবাদ",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "coxsbazar",
        nameEn = "Cox's Bazar",
        nameBn = "কক্সবাজার",
        latitude = 21.4272,
        longitude = 92.0058,
        defaultHospitalEn = "Cox's Bazar 250 Bed District Sadar Hospital",
        defaultHospitalBn = "কক্সবাজার ২৫০ শয্যা জেলা সদর হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "cumilla",
        nameEn = "Cumilla",
        nameBn = "কুমিল্লা",
        latitude = 23.4607,
        longitude = 91.1809,
        defaultHospitalEn = "Cumilla Medical College Hospital",
        defaultHospitalBn = "কুমিল্লা মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "noakhali",
        nameEn = "Noakhali",
        nameBn = "নোয়াখালী",
        latitude = 22.8696,
        longitude = 91.0994,
        defaultHospitalEn = "250 Bed General Hospital, Noakhali",
        defaultHospitalBn = "নোয়াখালী ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "feni",
        nameEn = "Feni",
        nameBn = "ফেনী",
        latitude = 23.0159,
        longitude = 91.3976,
        defaultHospitalEn = "Feni 250 Bed District Sadar Hospital",
        defaultHospitalBn = "ফেনী ২৫০ শয্যা জেলা সদর হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "brahmanbaria",
        nameEn = "Brahmanbaria",
        nameBn = "ব্রাহ্মণবাড়িয়া",
        latitude = 23.9571,
        longitude = 91.1119,
        defaultHospitalEn = "Brahmanbaria 250 Bed General Hospital",
        defaultHospitalBn = "ব্রাহ্মণবাড়িয়া ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "chandpur",
        nameEn = "Chandpur",
        nameBn = "চাঁদপুর",
        latitude = 23.2333,
        longitude = 90.6713,
        defaultHospitalEn = "Chandpur 250 Bed General Hospital",
        defaultHospitalBn = "চাঁদপুর ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "lakshmipur",
        nameEn = "Lakshmipur",
        nameBn = "লক্ষ্মীপুর",
        latitude = 22.9447,
        longitude = 90.8282,
        defaultHospitalEn = "Lakshmipur Sadar Hospital",
        defaultHospitalBn = "লক্ষ্মীপুর সদর হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "rangamati",
        nameEn = "Rangamati",
        nameBn = "রাঙ্গামাটি",
        latitude = 22.6533,
        longitude = 92.1789,
        defaultHospitalEn = "Rangamati General Hospital",
        defaultHospitalBn = "রাঙ্গামাটি জেনারেল হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "khagrachhari",
        nameEn = "Khagrachhari",
        nameBn = "খাগড়াছড়ি",
        latitude = 23.1193,
        longitude = 91.9847,
        defaultHospitalEn = "Khagrachhari District Sadar Hospital",
        defaultHospitalBn = "খাগড়াছড়ি জেলা সদর হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),
    BangladeshDistrict(
        areaKey = "bandarban",
        nameEn = "Bandarban",
        nameBn = "বান্দরবান",
        latitude = 22.1953,
        longitude = 92.2184,
        defaultHospitalEn = "Bandarban Sadar Hospital",
        defaultHospitalBn = "বান্দরবান সদর হাসপাতাল",
        divisionEn = "Chattogram",
        divisionBn = "চট্টগ্রাম"
    ),

    // --- Rajshahi Division (8 Districts) ---
    BangladeshDistrict(
        areaKey = "rajshahi",
        nameEn = "Boalia, Rajshahi",
        nameBn = "বোয়ালিয়া, রাজশাহী",
        latitude = 24.3745,
        longitude = 88.6042,
        defaultHospitalEn = "Rajshahi Medical College Hospital (RMCH)",
        defaultHospitalBn = "রাজশাহী মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "bogura",
        nameEn = "Bogura",
        nameBn = "বগুড়া",
        latitude = 24.8481,
        longitude = 89.3730,
        defaultHospitalEn = "Shaheed Ziaur Rahman Medical College Hospital, Bogura",
        defaultHospitalBn = "শহীদ জিয়াউর রহমান মেডিকেল কলেজ হাসপাতাল, বগুড়া",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "pabna",
        nameEn = "Pabna",
        nameBn = "পাবনা",
        latitude = 24.0063,
        longitude = 89.2372,
        defaultHospitalEn = "250 Bed General Hospital, Pabna",
        defaultHospitalBn = "পাবনা ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "sirajganj",
        nameEn = "Sirajganj",
        nameBn = "সিরাজগঞ্জ",
        latitude = 24.4534,
        longitude = 89.7007,
        defaultHospitalEn = "Shaheed M. Monsur Ali Medical College Hospital, Sirajganj",
        defaultHospitalBn = "শহীদ এম মনসুর আলী মেডিকেল কলেজ হাসপাতাল, সিরাজগঞ্জ",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "naogaon",
        nameEn = "Naogaon",
        nameBn = "নওগাঁ",
        latitude = 24.7936,
        longitude = 88.9318,
        defaultHospitalEn = "Naogaon 250 Bed General Hospital",
        defaultHospitalBn = "নওগাঁ ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "natore",
        nameEn = "Natore",
        nameBn = "নাটোর",
        latitude = 24.4206,
        longitude = 89.0006,
        defaultHospitalEn = "Natore Sadar Hospital",
        defaultHospitalBn = "নাটোর সদর হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "chapainawabganj",
        nameEn = "Chapainawabganj",
        nameBn = "চাঁপাইনবাবগঞ্জ",
        latitude = 24.5965,
        longitude = 88.2775,
        defaultHospitalEn = "250 Bed District Hospital, Chapainawabganj",
        defaultHospitalBn = "চাঁপাইনবাবগঞ্জ জেলা হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),
    BangladeshDistrict(
        areaKey = "joypurhat",
        nameEn = "Joypurhat",
        nameBn = "জয়পুরহাট",
        latitude = 25.0968,
        longitude = 89.0227,
        defaultHospitalEn = "Joypurhat 250 Bed District Hospital",
        defaultHospitalBn = "জয়পুরহাট জেলা হাসপাতাল",
        divisionEn = "Rajshahi",
        divisionBn = "রাজশাহী"
    ),

    // --- Khulna Division (10 Districts) ---
    BangladeshDistrict(
        areaKey = "khulna",
        nameEn = "Khulna Sadar, Khulna",
        nameBn = "খুলনা সদর, খুলনা",
        latitude = 22.8456,
        longitude = 89.5403,
        defaultHospitalEn = "Khulna Medical College Hospital (KMCH)",
        defaultHospitalBn = "খুলনা মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "jashore",
        nameEn = "Jashore",
        nameBn = "যশোর",
        latitude = 23.1664,
        longitude = 89.2081,
        defaultHospitalEn = "Jashore 250 Bed General Hospital",
        defaultHospitalBn = "যশোর ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "kushtia",
        nameEn = "Kushtia",
        nameBn = "কুষ্টিয়া",
        latitude = 23.9013,
        longitude = 89.1204,
        defaultHospitalEn = "Kushtia 250 Bed General Hospital",
        defaultHospitalBn = "কুষ্টিয়া ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "satkhira",
        nameEn = "Satkhira",
        nameBn = "সাতক্ষীরা",
        latitude = 22.7185,
        longitude = 89.0705,
        defaultHospitalEn = "Satkhira Medical College Hospital",
        defaultHospitalBn = "সাতক্ষীরা মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "bagerhat",
        nameEn = "Bagerhat",
        nameBn = "বাগেরহাট",
        latitude = 22.6516,
        longitude = 89.7859,
        defaultHospitalEn = "Bagerhat District Hospital",
        defaultHospitalBn = "বাগেরহাট জেলা সদর হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "jhenaidah",
        nameEn = "Jhenaidah",
        nameBn = "ঝিনাইদহ",
        latitude = 23.5450,
        longitude = 89.1726,
        defaultHospitalEn = "Jhenaidah 250 Bed Sadar Hospital",
        defaultHospitalBn = "ঝিনাইদহ সদর হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "magura",
        nameEn = "Magura",
        nameBn = "মাগুরা",
        latitude = 23.4873,
        longitude = 89.4199,
        defaultHospitalEn = "Magura 250 Bed Hospital",
        defaultHospitalBn = "মাগুরা ২৫০ শয্যা হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "narail",
        nameEn = "Narail",
        nameBn = "নড়াইল",
        latitude = 23.1725,
        longitude = 89.5127,
        defaultHospitalEn = "Narail Sadar Hospital",
        defaultHospitalBn = "নড়াইল সদর হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "chuadanga",
        nameEn = "Chuadanga",
        nameBn = "চুয়াডাঙ্গা",
        latitude = 23.6402,
        longitude = 88.8418,
        defaultHospitalEn = "Chuadanga Sadar Hospital",
        defaultHospitalBn = "চুয়াডাঙ্গা সদর হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),
    BangladeshDistrict(
        areaKey = "meherpur",
        nameEn = "Meherpur",
        nameBn = "মেহেরপুর",
        latitude = 23.7622,
        longitude = 88.6318,
        defaultHospitalEn = "Meherpur 250 Bed General Hospital",
        defaultHospitalBn = "মেহেরপুর ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Khulna",
        divisionBn = "খুলনা"
    ),

    // --- Sylhet Division (4 Districts) ---
    BangladeshDistrict(
        areaKey = "sylhet",
        nameEn = "Zindabazar, Sylhet",
        nameBn = "জিন্দাবাজার, সিলেট",
        latitude = 24.8949,
        longitude = 91.8687,
        defaultHospitalEn = "MAG Osmani Medical College Hospital, Sylhet",
        defaultHospitalBn = "এমএজি ওসমানী মেডিকেল কলেজ হাসপাতাল, সিলেট",
        divisionEn = "Sylhet",
        divisionBn = "সিলেট"
    ),
    BangladeshDistrict(
        areaKey = "moulvibazar",
        nameEn = "Moulvibazar",
        nameBn = "মৌলভীবাজার",
        latitude = 24.4829,
        longitude = 91.7774,
        defaultHospitalEn = "Moulvibazar 250 Bed Sadar Hospital",
        defaultHospitalBn = "মৌলভীবাজার ২৫০ শয্যা সদর হাসপাতাল",
        divisionEn = "Sylhet",
        divisionBn = "সিলেট"
    ),
    BangladeshDistrict(
        areaKey = "habiganj",
        nameEn = "Habiganj",
        nameBn = "হবিগঞ্জ",
        latitude = 24.3749,
        longitude = 91.4155,
        defaultHospitalEn = "Habiganj 250 Bed District Sadar Hospital",
        defaultHospitalBn = "হবিগঞ্জ ২৫০ শয্যা আধুনিক জেলা সদর হাসপাতাল",
        divisionEn = "Sylhet",
        divisionBn = "সিলেট"
    ),
    BangladeshDistrict(
        areaKey = "sunamganj",
        nameEn = "Sunamganj",
        nameBn = "সুনামগঞ্জ",
        latitude = 25.0658,
        longitude = 91.3950,
        defaultHospitalEn = "Sunamganj 250 Bed District Sadar Hospital",
        defaultHospitalBn = "সুনামগঞ্জ ২৫০ শয্যা জেলা সদর হাসপাতাল",
        divisionEn = "Sylhet",
        divisionBn = "সিলেট"
    ),

    // --- Barishal Division (6 Districts) ---
    BangladeshDistrict(
        areaKey = "barishal",
        nameEn = "Barishal Sadar, Barishal",
        nameBn = "বরিশাল সদর, বরিশাল",
        latitude = 22.7010,
        longitude = 90.3535,
        defaultHospitalEn = "Sher-e-Bangla Medical College Hospital (SBMCH), Barishal",
        defaultHospitalBn = "শের-ই-বাংলা মেডিকেল কলেজ হাসপাতাল, বরিশাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),
    BangladeshDistrict(
        areaKey = "bhola",
        nameEn = "Bhola",
        nameBn = "ভোলা",
        latitude = 22.6859,
        longitude = 90.6482,
        defaultHospitalEn = "Bhola 250 Bed General Hospital",
        defaultHospitalBn = "ভোলা ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),
    BangladeshDistrict(
        areaKey = "patuakhali",
        nameEn = "Patuakhali",
        nameBn = "পটুয়াখালী",
        latitude = 22.3596,
        longitude = 90.3298,
        defaultHospitalEn = "Patuakhali Medical College Hospital",
        defaultHospitalBn = "পটুয়াখালী মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),
    BangladeshDistrict(
        areaKey = "pirojpur",
        nameEn = "Pirojpur",
        nameBn = "পিরোজপুর",
        latitude = 22.5791,
        longitude = 89.9759,
        defaultHospitalEn = "Pirojpur District Hospital",
        defaultHospitalBn = "পিরোজপুর জেলা হাসপাতাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),
    BangladeshDistrict(
        areaKey = "jhalokati",
        nameEn = "Jhalokati",
        nameBn = "ঝালকাঠি",
        latitude = 22.6406,
        longitude = 90.1987,
        defaultHospitalEn = "Jhalokati Sadar Hospital",
        defaultHospitalBn = "ঝালকাঠি সদর হাসপাতাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),
    BangladeshDistrict(
        areaKey = "barguna",
        nameEn = "Barguna",
        nameBn = "বরগুনা",
        latitude = 22.1592,
        longitude = 90.1255,
        defaultHospitalEn = "Barguna 250 Bed General Hospital",
        defaultHospitalBn = "বরগুনা ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Barishal",
        divisionBn = "বরিশাল"
    ),

    // --- Rangpur Division (8 Districts) ---
    BangladeshDistrict(
        areaKey = "rangpur",
        nameEn = "Rangpur Sadar, Rangpur",
        nameBn = "রংপুর সদর, রংপুর",
        latitude = 25.7439,
        longitude = 89.2752,
        defaultHospitalEn = "Rangpur Medical College Hospital (RpMCH)",
        defaultHospitalBn = "রংপুর মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "dinajpur",
        nameEn = "Dinajpur",
        nameBn = "দিনাজপুর",
        latitude = 25.6279,
        longitude = 88.6332,
        defaultHospitalEn = "M Abdur Rahim Medical College Hospital, Dinajpur",
        defaultHospitalBn = "দিনাজপুর মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "kurigram",
        nameEn = "Kurigram",
        nameBn = "কুড়িগ্রাম",
        latitude = 25.8054,
        longitude = 89.6362,
        defaultHospitalEn = "Kurigram 250 Bed General Hospital",
        defaultHospitalBn = "কুড়িগ্রাম ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "gaibandha",
        nameEn = "Gaibandha",
        nameBn = "গাইবান্ধা",
        latitude = 25.3288,
        longitude = 89.5281,
        defaultHospitalEn = "Gaibandha District Hospital",
        defaultHospitalBn = "গাইবান্ধা জেলা হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "nilphamari",
        nameEn = "Nilphamari",
        nameBn = "নীলফামারী",
        latitude = 25.9318,
        longitude = 88.8560,
        defaultHospitalEn = "Nilphamari 250 Bed General Hospital",
        defaultHospitalBn = "নীলফামারী ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "lalmonirhat",
        nameEn = "Lalmonirhat",
        nameBn = "লালমনিরহাট",
        latitude = 25.9165,
        longitude = 89.4532,
        defaultHospitalEn = "Lalmonirhat 250 Bed Sadar Hospital",
        defaultHospitalBn = "লালমনিরহাট সদর হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "thakurgaon",
        nameEn = "Thakurgaon",
        nameBn = "ঠাকুরগাঁও",
        latitude = 26.0337,
        longitude = 88.4617,
        defaultHospitalEn = "Thakurgaon 250 Bed General Hospital",
        defaultHospitalBn = "ঠাকুরগাঁও ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),
    BangladeshDistrict(
        areaKey = "panchagarh",
        nameEn = "Panchagarh",
        nameBn = "পঞ্চগড়",
        latitude = 26.3411,
        longitude = 88.5542,
        defaultHospitalEn = "Panchagarh Modern Sadar Hospital",
        defaultHospitalBn = "পঞ্চগড় আধুনিক সদর হাসপাতাল",
        divisionEn = "Rangpur",
        divisionBn = "রংপুর"
    ),

    // --- Mymensingh Division (4 Districts) ---
    BangladeshDistrict(
        areaKey = "mymensingh",
        nameEn = "Mymensingh Sadar, Mymensingh",
        nameBn = "ময়মনসিংহ সদর, ময়মনসিংহ",
        latitude = 24.7471,
        longitude = 90.4203,
        defaultHospitalEn = "Mymensingh Medical College Hospital (MMCH)",
        defaultHospitalBn = "ময়মনসিংহ মেডিকেল কলেজ হাসপাতাল",
        divisionEn = "Mymensingh",
        divisionBn = "ময়মনসিংহ"
    ),
    BangladeshDistrict(
        areaKey = "jamalpur",
        nameEn = "Jamalpur",
        nameBn = "জামালপুর",
        latitude = 24.9375,
        longitude = 89.9378,
        defaultHospitalEn = "250 Bed General Hospital, Jamalpur",
        defaultHospitalBn = "জামালপুর ২৫০ শয্যা জেনারেল হাসপাতাল",
        divisionEn = "Mymensingh",
        divisionBn = "ময়মনসিংহ"
    ),
    BangladeshDistrict(
        areaKey = "netrokona",
        nameEn = "Netrokona",
        nameBn = "নেত্রকোনা",
        latitude = 24.8710,
        longitude = 90.7279,
        defaultHospitalEn = "Netrokona Modern Sadar Hospital",
        defaultHospitalBn = "নেত্রকোনা আধুনিক সদর হাসপাতাল",
        divisionEn = "Mymensingh",
        divisionBn = "ময়মনসিংহ"
    ),
    BangladeshDistrict(
        areaKey = "sherpur",
        nameEn = "Sherpur",
        nameBn = "শেরপুর",
        latitude = 25.0205,
        longitude = 90.0153,
        defaultHospitalEn = "Sherpur 250 Bed District Hospital",
        defaultHospitalBn = "শেরপুর ২৫০ শয্যা জেলা হাসপাতাল",
        divisionEn = "Mymensingh",
        divisionBn = "ময়মনসিংহ"
    )
)

enum class NotificationKind {
    SOS_ALERT,
    BLOOD_REQUEST,
    RESPONDER_UPDATE,
    FALSE_SOS_WARNING
}

data class RokkhonNotificationItem(
    val id: String,
    val kind: NotificationKind,
    val titleBn: String,
    val titleEn: String,
    val bodyBn: String,
    val bodyEn: String,
    val timestampLabel: String,
    val areaName: String,
    val relatedSosId: String? = null,
    val relatedRequestId: String? = null,
    val isRead: Boolean = false
)

data class RewardTier(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val subtitleEn: String,
    val requiredPoints: Int,
    val badgeLabel: String
)

val GUARDIAN_REWARD_TIERS = listOf(
    RewardTier(
        id = "tier_cbc",
        titleBn = "ফ্রি হিমোগ্লোবিন ও সিবিসি টেস্ট ভাউচার",
        titleEn = "Free Hemoglobin & CBC Test Voucher",
        subtitleBn = "নির্ধারিত ডায়াগনস্টিক সেন্টারে ফ্রি রক্ত পরীক্ষা",
        subtitleEn = "Complimentary blood work at partner diagnostic labs",
        requiredPoints = 300,
        badgeLabel = "300 PTS"
    ),
    RewardTier(
        id = "tier_priority_blood",
        titleBn = "প্রায়োরিটি ব্লাড ব্যাংক ও অ্যাম্বুলেন্স সাপোর্ট",
        titleEn = "Priority Blood Bank & Ambulance Support",
        subtitleBn = "পরিবারের জরুরি প্রয়োজনে অগ্রাধিকার সহায়তা",
        subtitleEn = "Priority family emergency blood & ambulance dispatch",
        requiredPoints = 500,
        badgeLabel = "500 PTS"
    ),
    RewardTier(
        id = "tier_guardian_crest",
        titleBn = "রক্ষণ ন্যাশনাল গার্ডিয়ান সার্টিফিকেট ও ব্যাজ",
        titleEn = "Rokkhon Verified Guardian Certificate",
        subtitleBn = "ভেরিফাইড রক্তদাতা ও রেসকিউ গার্ডিয়ান সম্মাননা",
        subtitleEn = "Official verified community responder recognition",
        requiredPoints = 1000,
        badgeLabel = "1000 PTS"
    )
)

@IgnoreExtraProperties
data class DonorNode(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bloodGroup: String = "O+",
    val isAvailableDonor: Boolean = true,
    val district: String = "Mirpur 10, Dhaka",
    val contactPhone: String = "+8801700000000",
    val latitude: Double = 23.8069,
    val longitude: Double = 90.3687,
    val lastDonationDate: String = "Available now",
    val trustScore: Long = 100L,
    val verifiedSosCount: Long = 0L,
    val networkKey: String = "BD_ROKKHON",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BloodRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val requesterName: String = "",
    val patientName: String = "",
    val bloodGroup: String = "O+",
    val unitsNeeded: Long = 1L,
    val hemoglobinLevel: String = "7.0 g/dL",
    val patientProblem: String = "Dengue / Surgery",
    val hospitalName: String = "",
    val wardCabinNumber: String = "Ward 4, Bed 12",
    val neededDateTime: String = "Immediate (Within 2 Hours)",
    val district: String = "Mirpur 10, Dhaka",
    val contactPhone: String = "",
    val urgencyLevel: String = "CRITICAL",
    val latitude: Double = 23.8069,
    val longitude: Double = 90.3687,
    val status: String = "OPEN",
    val respondersCount: Long = 0L,
    val networkKey: String = "BD_ROKKHON",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val responderIds: List<String> = emptyList()
)

const val FALSE_SOS_REMOVAL_THRESHOLD = 3L
const val FALSE_SOS_DISPUTE_THRESHOLD = 3L
const val SOS_NOTIFICATION_RADIUS_KM = 1.0
const val SOS_EXPIRY_DURATION_MILLIS = 24L * 60L * 60L * 1000L

/**
 * Calculates the real-world Haversine distance in kilometers between two GPS coordinates.
 */
fun calculateDistanceKm(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return (earthRadiusKm * c).coerceAtLeast(0.05)
}

@IgnoreExtraProperties
data class SosAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderName: String = "",
    val bloodGroup: String = "O+",
    val emergencyType: String = "ROAD_DANGER",
    val district: String = "Mirpur 10, Dhaka",
    val latitude: Double = 23.8069,
    val longitude: Double = 90.3687,
    val hasLiveCameraVerification: Boolean = true,
    val hasLiveAudioVerification: Boolean = true,
    val peakAudioDecibels: Long = 75L,
    val triggerMethod: String = "3X_POWER_BUTTON",
    val verificationNote: String = "Auto 5s Camera + Audio Evidence Attached",
    val bleBeaconHash: String = "BLE-ROKKHON",
    val status: String = "ACTIVE",
    val responderCount: Long = 0L,
    val falseAlarmReports: Long = 0L,
    val networkKey: String = "BD_ROKKHON",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val responderIds: List<String> = emptyList(),
    @get:Exclude val falseReporterIds: List<String> = emptyList()
) {
    @get:Exclude
    val createdAtMillis: Long
        get() = createdAt?.toDate()?.time ?: System.currentTimeMillis()

    @Exclude
    fun isExpiredAfter24Hours(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val createdMillis = createdAt?.toDate()?.time ?: return false
        return (nowMillis - createdMillis) >= SOS_EXPIRY_DURATION_MILLIS
    }

    @Exclude
    fun hoursRemainingUntilAutoRemove(nowMillis: Long = System.currentTimeMillis()): Int {
        val createdMillis = createdAt?.toDate()?.time ?: return 24
        val elapsedMillis = (nowMillis - createdMillis).coerceAtLeast(0L)
        val remainingMillis = (SOS_EXPIRY_DURATION_MILLIS - elapsedMillis).coerceAtLeast(0L)
        return ((remainingMillis + (60L * 60L * 1000L - 1L)) / (60L * 60L * 1000L)).toInt().coerceIn(1, 24)
    }

    @Exclude
    fun distanceFromKm(userLat: Double, userLng: Double): Double {
        return calculateDistanceKm(userLat, userLng, latitude, longitude)
    }

    /**
     * Protects real SOS victims from malicious/fake "False" reports:
     * If the SOS has verified 5-second camera + audio blackbox evidence (>= 68 dB)
     * OR at least 1 nearby responder (`responderCount > 0`) has confirmed "Nearby / যাচ্ছি",
     * the sender's Trust Score is 100% shielded from automatic penalties even if 3+ false reports are submitted.
     */
    @get:Exclude
    val isShieldedByEvidenceOrResponders: Boolean
        get() = responderCount > 0L ||
            (hasLiveCameraVerification && hasLiveAudioVerification && peakAudioDecibels >= 68L)

    @get:Exclude
    val isCommunityDisputed: Boolean
        get() = falseAlarmReports >= FALSE_SOS_DISPUTE_THRESHOLD
}

data class BleSosBeacon(
    val beaconId: String,
    val senderCode: String,
    val bloodGroup: String,
    val emergencyType: String,
    val latitude: Double,
    val longitude: Double,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val hasCameraAudioProof: Boolean,
    val timestampMillis: Long,
    val packetKind: String = "SOS_1KM",
    val districtAreaKey: String = "mirpur",
    val unitsNeeded: Int = 1
)

