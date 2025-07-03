package com.example.roamly.data.models

data class Country(
    val name: String,
    val code: String // deve essere in lowercase: "it", "us", "de", ecc.
) {
    override fun toString(): String {
        return name
    }
}

