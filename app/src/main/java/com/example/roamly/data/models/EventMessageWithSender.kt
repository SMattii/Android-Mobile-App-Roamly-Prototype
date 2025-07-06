package com.example.roamly.data.models

data class EventMessageWithSender(
    val message: EventMessage,
    val sender: Profile
)