package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LanguageLink(
    val profile_id: String,
    val language_id: String
)