package com.example.roamly.data.utils

import android.content.Context
import com.example.roamly.data.models.Country
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Utility per il caricamento della lista dei Paesi da un file JSON contenuto negli asset.
 *
 * Il file `countries.json` deve contenere una lista di oggetti compatibili con il modello [Country].
 */
object CountryProvider {
    /**
     * Carica la lista dei Paesi da un file JSON (`countries.json`) situato nella cartella assets.
     *
     * @param context Contesto Android utilizzato per accedere agli asset.
     * @return Lista di oggetti [Country] deserializzati dal JSON.
     */
    fun loadCountriesFromAssets(context: Context): List<Country> {
        val json = context.assets.open("countries.json")
            .bufferedReader()
            .use { it.readText() }

        val gson = Gson()
        val type = object : TypeToken<List<Country>>() {}.type

        return gson.fromJson(json, type)
    }
}