package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LanguageJsonItem(
    val name: String,
    val code: String
)