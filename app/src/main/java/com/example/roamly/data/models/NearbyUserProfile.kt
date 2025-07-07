package com.example.roamly.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un profilo utente visibile sulla mappa, tipicamente ottenuto
 * da una query Supabase che restituisce utenti nelle vicinanze con le informazioni principali.
 *
 * Include dati personali, vibe, immagine profilo, lingue e interessi.
 *
 * @property id ID univoco del profilo.
 * @property fullName Nome completo dell'utente.
 * @property age Età dell'utente.
 * @property country Codice del Paese dell'utente.
 * @property category Categoria utente (es. "traveler", "local").
 * @property vibe Vibe dell'utente (es. "chill", "party").
 * @property profileImageUrl URL dell'immagine profilo.
 * @property languages Elenco dei codici ISO 639-1 delle lingue parlate.
 * @property interests Elenco degli ID degli interessi selezionati.
 */
@Serializable
data class NearbyUserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val age: String? = null,
    val country: String? = null,
    val category: String? = null,
    val vibe: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val languages: List<String> = emptyList(),
    val interests: List<String> = emptyList()
)