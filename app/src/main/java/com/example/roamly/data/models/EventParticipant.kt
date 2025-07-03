package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class EventParticipant(
    val event_id: String,
    val profile_id: String,
    val joined_at: String? = null // viene riempito da Supabase
)