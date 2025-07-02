package com.example.roamly.data.utils

import com.example.roamly.R
import com.example.roamly.data.models.Interest

object InterestProvider {

    private val interestNameToResId = mapOf(
        "Food and Drinks" to R.drawable.ic_food,
        "Nightlife" to R.drawable.ic_nightlife,
        "Culture" to R.drawable.ic_culture,
        "Nature" to R.drawable.ic_nature,
        "Sport" to R.drawable.ic_sport,
        "Networking" to R.drawable.ic_networking
    )

    fun getIconResIdFor(interestName: String): Int? {
        return interestNameToResId[interestName]
    }

    fun getAllAvailableInterests(): List<Interest> {
        return interestNameToResId.keys.mapIndexed { index, name ->
            Interest(id = (index + 1).toString(), name = name)
        }
    }
}