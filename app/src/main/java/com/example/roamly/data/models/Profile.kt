package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val full_name: String?,
    val first_name: String?,
    val last_name: String?,
    val profile_image_url: String? = null,
    val has_logged_before: Boolean = false,
    val age: String?,
    val country: String?,
    val category: String?,
    val vibe: String?,
    val visible: Boolean = true
)