package com.example.roamly.data.models

data class ProfileComplete(
    val profile: Profile,
    val selectedLanguages: List<Language>,
    val selectedInterests: List<Interest>
)