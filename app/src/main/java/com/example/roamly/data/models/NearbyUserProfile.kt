package com.example.roamly.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NearbyUserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val age: String? = null,
    val country: String? = null,
    val category: String? = null,
    val vibe: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val languages: List<String> = emptyList(),
    val interests: List<String> = emptyList()
)