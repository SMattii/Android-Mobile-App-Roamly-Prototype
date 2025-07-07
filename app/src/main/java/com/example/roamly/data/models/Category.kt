package com.example.roamly.data.models

/**
 * Modello dati che rappresenta la "vibe" selezionata dall'utente o associata a un evento,
 * ovvero lo stile o l'atmosfera (es. "chill" o "party").
 *
 * @property name Nome della vibe (es. "chill", "party").
 * @property iconResId ID della risorsa drawable associata all'icona della vibe.
 */
data class Category(
    val name: String,
    val iconResId: Int
) {
    /**
     * Restituisce solo il nome della vibe come stringa,
     * utile per la visualizzazione nei dropdown o nei componenti testuali.
     */
    override fun toString(): String {
        return name
    }
}