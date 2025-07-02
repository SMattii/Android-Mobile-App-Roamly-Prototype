package com.example.roamly.data.models

import com.example.roamly.Interest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InterestLinkWithName(
    val profile_id: String,
    @SerialName("interests") val interest: Interest
)