package com.example.roamly

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import android.util.Log

object LanguageProvider {

    private val languageCodeToCountryCodeMap: Map<String, String> = mapOf(
        "af" to "za", // Afrikaans -> South Africa
        "sq" to "al", // Shqip -> Albania
        "ar" to "sa", // العربية -> Saudi Arabia
        "hy" to "am", // Հայերեն -> Armenia
        "az" to "az", // Azərbaycanca -> Azerbaijan
        "bn" to "bd", // বাংলা -> Bangladesh
        "be" to "by", // Беларуская -> Belarus
        "bs" to "ba", // Bosanski -> Bosnia and Herzegovina
        "bg" to "bg", // Български -> Bulgaria
        "my" to "mm", // Myanmar -> Myanmar
        "km" to "kh", // ខ្មែរ -> Cambodia
        "zh" to "cn", // 中文 -> China
        "hr" to "hr", // Hrvatski -> Croatia
        "cs" to "cz", // Čeština -> Czech Republic
        "da" to "dk", // Dansk -> Denmark
        "nl" to "nl", // Nederlands -> Netherlands
        "en" to "gb", // English -> United Kingdom
        "et" to "ee", // Eesti -> Estonia
        "am" to "et", // አማርኛ -> Ethiopia
        "fi" to "fi", // Suomi -> Finland
        "fr" to "fr", // Français -> France
        "ka" to "ge", // ქართული -> Georgia
        "de" to "de", // Deutsch -> Germany
        "el" to "gr", // Ελληνικά -> Greece
        "gu" to "in", // ગુજરાતી -> India (Gujarat state)
        "he" to "il", // עברית -> Israel
        "hi" to "in", // हिन्दी -> India
        "hu" to "hu", // Magyar -> Hungary
        "is" to "is", // Íslenska -> Iceland
        "id" to "id", // Bahasa Indonesia -> Indonesia
        "fa" to "ir", // فارسی -> Iran
        "ga" to "ie", // Gaeilge -> Ireland
        "it" to "it", // Italiano -> Italy
        "ja" to "jp", // 日本語 -> Japan
        "kk" to "kz", // Қазақша -> Kazakhstan
        "ko" to "kr", // 한국어 -> South Korea
        "ky" to "kg", // Кыргызча -> Kyrgyzstan
        "lo" to "la", // ລາວ -> Laos
        "lv" to "lv", // Latviešu -> Latvia
        "lt" to "lt", // Lietuvių -> Lithuania
        "mk" to "mk", // Македонски -> North Macedonia
        "ms" to "my", // Bahasa Melayu -> Malaysia
        "mt" to "mt", // Malti -> Malta
        "mn" to "mn", // Монгол -> Mongolia
        "ne" to "np", // नेपाली -> Nepal
        "no" to "no", // Norsk -> Norway
        "ps" to "af", // پښتو -> Afghanistan
        "fil" to "ph", // Filipino -> Philippines
        "pl" to "pl", // Polski -> Poland
        "pt" to "pt", // Português -> Portugal
        "ro" to "ro", // Română -> Romania
        "ru" to "ru", // Русский -> Russia
        "sr" to "rs", // Српски -> Serbia
        "si" to "lk", // සිංහල -> Sri Lanka
        "sk" to "sk", // Slovenčina -> Slovakia
        "sl" to "si", // Slovenščina -> Slovenia
        "so" to "so", // Soomaaliga -> Somalia
        "es" to "es", // Español -> Spain
        "sw" to "ke", // Kiswahili -> Kenya
        "sv" to "se", // Svenska -> Sweden
        "tg" to "tj", // Тоҷикӣ -> Tajikistan
        "te" to "in", // తెలుగు -> India (Telangana/Andhra Pradesh)
        "th" to "th", // ไทย -> Thailand
        "tr" to "tr", // Türkçe -> Turkey
        "tk" to "tm", // Türkmençe -> Turkmenistan
        "uk" to "ua", // Українська -> Ukraine
        "ur" to "pk", // اردو -> Pakistan
        "uz" to "uz", // O'zbekcha -> Uzbekistan
        "vi" to "vn", // Tiếng Việt -> Vietnam
        "cy" to "gb"  // Cymraeg -> United Kingdom (Wales)
    )

    private fun getFlagResId(context: Context, countryCode: String): Int {
        val resourceName = countryCode.lowercase()
        return context.resources.getIdentifier(
            resourceName,
            "drawable",
            context.packageName
        )
    }

    fun loadLanguagesFromAssets(context: Context): List<Language> {
        val languagesList = mutableListOf<Language>()
        try {
            val jsonString = context.assets.open("languages.json")
                .bufferedReader()
                .use { it.readText() }

            val gson = Gson()
            val type = object : TypeToken<List<LanguageJsonItem>>() {}.type
            val rawLanguages = gson.fromJson<List<LanguageJsonItem>>(jsonString, type)

            for (rawItem in rawLanguages) {
                val countryCode = languageCodeToCountryCodeMap[rawItem.code]

                if (countryCode != null) {
                    val flagResId = getFlagResId(context, countryCode)
                    if (flagResId != 0) {
                        languagesList.add(Language(rawItem.name, rawItem.code, flagResId))
                    } else {
                        Log.w("LanguageProvider", "Flag drawable '${countryCode.lowercase()}.xml' not found for language code: '${rawItem.code}'. This language will be skipped.")
                    }
                } else {
                    Log.w("LanguageProvider", "No country code mapping found for language code: '${rawItem.code}'. This language will be skipped.")
                }
            }
        } catch (e: IOException) {
            Log.e("LanguageProvider", "Error reading languages.json: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e("LanguageProvider", "Error parsing languages.json: ${e.localizedMessage}")
        }

        return languagesList.sortedBy { it.name }
    }
}