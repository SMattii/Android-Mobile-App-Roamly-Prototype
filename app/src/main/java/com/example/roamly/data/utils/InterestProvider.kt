package com.example.roamly.data.utils

import com.example.roamly.R
import com.example.roamly.data.models.Interest

/**
 * Utility per la gestione statica degli interessi disponibili nell'app.
 *
 * Fornisce le icone associate a ciascun interesse e un elenco completo degli interessi supportati.
 */
object InterestProvider {

    private val interestNameToResId = mapOf(
        "Food and Drinks" to R.drawable.ic_food,
        "Nightlife" to R.drawable.ic_nightlife,
        "Culture" to R.drawable.ic_culture,
        "Nature" to R.drawable.ic_nature,
        "Sport" to R.drawable.ic_sport,
        "Networking" to R.drawable.ic_networking
    )

    /**
     * Restituisce l'ID della risorsa drawable associata a un interesse dato il suo nome.
     *
     * @param interestName Nome dell’interesse (es. "Sport", "Nature").
     * @return ID della risorsa drawable, oppure `null` se non trovato.
     */
    fun getIconResIdFor(interestName: String): Int? {
        return interestNameToResId[interestName]
    }

    /**
     * Restituisce la lista completa degli interessi supportati.
     *
     * @return Lista di oggetti [Interest] disponibili nell'app.
     */
    fun getAllAvailableInterests(): List<Interest> {
        return interestNameToResId.keys.mapIndexed { index, name ->
            Interest(id = (index + 1).toString(), name = name)
        }
    }
}