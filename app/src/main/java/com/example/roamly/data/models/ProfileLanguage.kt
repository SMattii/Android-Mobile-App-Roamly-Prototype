package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ProfileLanguage(
    val profile_id: String,
    val language_id: String // Questo sarà il codice della lingua (es. "en", "it")
)