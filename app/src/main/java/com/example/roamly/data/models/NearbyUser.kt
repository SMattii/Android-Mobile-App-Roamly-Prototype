package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un utente nelle vicinanze.
 *
 * Include la posizione dell'utente e opzionalmente l'immagine del profilo,
 * tramite join con la tabella `profiles`.
 *
 * @property user_id ID dell'utente trovato nelle vicinanze.
 * @property latitude Latitudine della posizione dell'utente.
 * @property longitude Longitudine della posizione dell'utente.
 * @property profiles Dati relativi all'immagine del profilo.
 */
@Serializable
data class NearbyUser(
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val profiles: ProfileImageData? = null
)

/**
 * Modello dati che rappresenta solo l'URL dell'immagine profilo di un utente.
 *
 * Utilizzato quando si effettua una join parziale sulla tabella `profiles`
 * per ridurre i dati trasferiti (ad es. solo immagine profilo).
 *
 * @property profile_image_url URL pubblico o firmato dell'immagine profilo (può essere null).
 */
@Serializable
data class ProfileImageData(
    val profile_image_url: String? = null
)
