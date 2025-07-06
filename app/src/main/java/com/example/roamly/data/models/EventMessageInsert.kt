package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class EventMessageInsert(
    val event_id: String,
    val sender_id: String,
    val message: String
)