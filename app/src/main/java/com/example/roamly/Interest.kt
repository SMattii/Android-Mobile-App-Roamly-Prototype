package com.example.roamly

import kotlinx.serialization.Serializable

@Serializable
data class Interest(
    val id: String,
    val name: String
)
