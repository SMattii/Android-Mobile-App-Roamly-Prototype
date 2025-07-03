package com.example.roamly.data.models

data class Language(
    val name: String,
    val code: String, // Codice lingua ISO 639-1 (es. "en", "es", "it", "de")
    val flagResId: Int
) {
    override fun toString(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Language

        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }
}