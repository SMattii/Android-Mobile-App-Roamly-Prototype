package com.example.roamly.data.models

data class UserWithAllInfo(
    val profile: Profile,
    val interests: List<Interest>,
    val languages: List<Language>
)
