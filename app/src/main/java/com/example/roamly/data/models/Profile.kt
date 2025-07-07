package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un profilo utente completo, così come salvato nella tabella `profiles` di Supabase.
 *
 * Include nome completo, immagine profilo, informazioni anagrafiche e preferenze (vibe, visibilità, ecc.).
 * Questo modello viene utilizzato per la serializzazione/deserializzazione automatica nei flussi di autenticazione
 * e nella gestione dei profili visibili sulla mappa o nei dettagli utente.
 *
 * @property id ID univoco dell'utente (fornito da Supabase Auth).
 * @property full_name Nome completo dell’utente.
 * @property first_name Nome dell’utente.
 * @property last_name Cognome dell’utente.
 * @property profile_image_url URL dell'immagine profilo.
 * @property has_logged_before Indica se l'utente ha già effettuato l’accesso almeno una volta.
 * @property age Età dell’utente.
 * @property country Codice del Paese associato all’utente.
 * @property category Categoria dell’utente (es. "traveler", "student").
 * @property vibe Vibe selezionata dall’utente (es. "chill", "party").
 * @property visible Indica se il profilo è attualmente visibile sulla mappa o nei risultati (default: `true`).
 */
@Serializable
data class Profile(
    val id: String,
    val full_name: String?,
    val first_name: String?,
    val last_name: String?,
    val profile_image_url: String? = null,
    val has_logged_before: Boolean = false,
    val age: String?,
    val country: String?,
    val category: String?,
    val vibe: String?,
    @kotlinx.serialization.EncodeDefault
    val visible: Boolean = true
)