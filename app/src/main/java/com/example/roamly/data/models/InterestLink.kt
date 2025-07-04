package com.example.roamly.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InterestLink(
    val profile_id: String,
    @SerialName("interests") val interest: Interest
)