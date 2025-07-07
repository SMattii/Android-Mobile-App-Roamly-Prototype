package com.example.roamly.data.models

/**
 * Modello dati che rappresenta un Paese, utilizzato per identificare
 * la nazione associata a un profilo utente o altra entità.
 *
 * @property name Nome completo del Paese (es. "Italia", "Germany").
 * @property code Codice ISO 3166-1 alpha-2 in minuscolo (es. "it", "de").
 */
data class Country(
    val name: String,
    val code: String // deve essere in lowercase: "it", "us", "de", ecc.
) {
    /**
     * Restituisce solo il nome del Paese come stringa,
     * utile per la visualizzazione nei dropdown o nei componenti testuali.
     */
    override fun toString(): String {
        return name
    }
}