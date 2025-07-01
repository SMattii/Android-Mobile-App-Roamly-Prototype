package com.example.roamly

import kotlinx.serialization.Serializable

@Serializable
data class NearbyProfile(
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val profiles: ProfileImageData? = null
)

@Serializable
data class ProfileImageData(
    val profile_image_url: String? = null
)
