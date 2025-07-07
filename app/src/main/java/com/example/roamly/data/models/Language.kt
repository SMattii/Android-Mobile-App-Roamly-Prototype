package com.example.roamly.data.models

import android.content.Context
import android.util.Log
import com.example.roamly.R
import com.example.roamly.data.utils.LanguageProvider
import kotlinx.serialization.Serializable
import com.google.gson.annotations.SerializedName

/**
 * Modello dati che rappresenta una lingua selezionabile da un utente o associabile a un evento.
 *
 * Include un metodo per ottenere la risorsa drawable della bandiera in base al codice lingua.
 * Il codice lingua segue lo standard ISO 639-1 e viene mappato a un codice paese tramite [LanguageProvider].
 *
 * @property id Codice ISO 639-1 della lingua (es. "en", "it", "fr").
 * @property name Nome descrittivo della lingua (es. "English", "Italiano").
 */
@Serializable
data class Language(
    @SerializedName("code") val id: String,
    val name: String
) {
    /**
     * Restituisce l'ID della risorsa drawable della bandiera associata alla lingua.
     * Se non esiste una mappatura valida o la risorsa non viene trovata, restituisce una bandiera di default.
     *
     * @param context Contesto Android necessario per accedere alle risorse.
     * @return ID della risorsa drawable della bandiera (o fallback predefinito).
     */
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
