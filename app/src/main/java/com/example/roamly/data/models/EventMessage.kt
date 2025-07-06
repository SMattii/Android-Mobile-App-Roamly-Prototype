package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class EventMessage(
    val id: String,
    val event_id: String,
    val sender_id: String,
    val message: String,
    val created_at: String
)