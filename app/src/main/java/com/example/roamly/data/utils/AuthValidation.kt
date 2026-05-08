package com.example.roamly.data.utils

object AuthValidation {
    private const val PASSWORD_MIN_LENGTH = 8

    fun passwordValidationMessage(password: String): String? {
        return when {
            password.length < PASSWORD_MIN_LENGTH ->
                "La password deve contenere almeno $PASSWORD_MIN_LENGTH caratteri"
            !password.any { it.isUpperCase() } ->
                "La password deve contenere almeno una lettera maiuscola"
            !password.any { it.isLowerCase() } ->
                "La password deve contenere almeno una lettera minuscola"
            !password.any { it.isDigit() } ->
                "La password deve contenere almeno un numero"
            !password.any { !it.isLetterOrDigit() } ->
                "La password deve contenere almeno un simbolo"
            else -> null
        }
    }

    fun passwordsMatchMessage(password: String, confirmation: String): String? {
        return if (password == confirmation) null else "Le password non corrispondono"
    }
}
