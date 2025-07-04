package com.example.roamly.data.models

import android.content.Context
import android.util.Log
import com.example.roamly.R
import com.example.roamly.data.utils.LanguageProvider
import kotlinx.serialization.Serializable
import com.google.gson.annotations.SerializedName

@Serializable
data class Language(
    @SerializedName("code") val id: String,
    val name: String
) {
    fun getFlagResId(context: Context): Int {
        val countryCode = LanguageProvider.languageCodeToCountryCodeMap[id]
        if (countryCode == null) {
            Log.w("Language", "No country code mapping found for language code: '$id'")
            return R.drawable.ic_flag_default
        }

        val resId = context.resources.getIdentifier(
            countryCode.lowercase(), "drawable", context.packageName
        )
        return if (resId != 0) resId else R.drawable.ic_flag_default
    }
}
