package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Interest(
    val id: String,
    val name: String
)
