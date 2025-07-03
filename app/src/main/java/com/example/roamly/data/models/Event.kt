package com.example.roamly.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String? = null,
    val profile_id: String,
    val latitude: Double,
    val longitude: Double,
    val event_type: String,
    val interests: List<String>,
    val languages: List<String>,
    val date: String,         // ISO 8601 (es: "2025-07-03")
    val time: String,         // "HH:mm"
    val min_age: Int? = null,
    val max_age: Int? = null,
    val max_participants: Int? = null,
    val vibe: String? = null,
    val created_at: String? = null,
    val visible_until: String? = null
)