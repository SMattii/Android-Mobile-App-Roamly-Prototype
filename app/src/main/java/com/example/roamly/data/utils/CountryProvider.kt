package com.example.roamly.data.utils

import android.content.Context
import com.example.roamly.data.models.Country
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CountryProvider {
    fun loadCountriesFromAssets(context: Context): List<Country> {
        val json = context.assets.open("countries.json")
            .bufferedReader()
            .use { it.readText() }

        val gson = Gson()
        val type = object : TypeToken<List<Country>>() {}.type

        return gson.fromJson(json, type)
    }
}