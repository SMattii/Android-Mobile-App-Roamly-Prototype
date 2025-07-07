package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta l'associazione tra un profilo utente e una lingua parlata.
 *
 * Utilizzato per salvare o recuperare i collegamenti tra la tabella `profiles` e `languages`
 * tramite la tabella intermedia `profile_languages`.
 *
 * @property profile_id ID del profilo utente.
 * @property language_id Codice ISO 639-1 della lingua parlata (es. "en", "it").
 */
@Serializable
data class LanguageLink(
    val profile_id: String,
    val language_id: String
)