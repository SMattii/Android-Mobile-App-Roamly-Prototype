package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LocationEntry(
    val user_id: String,
    val latitude: Double,
    val longitude: Double
)
