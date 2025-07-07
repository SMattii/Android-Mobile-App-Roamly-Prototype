package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta una posizione geografica associata a un utente.
 *
 * Utilizzato per salvare o aggiornare la posizione attuale di un utente
 * nella tabella `locations` su Supabase, ad esempio per mostrare utenti/eventi vicini.
 *
 * @property user_id ID dell'utente a cui appartiene la posizione.
 * @property latitude Latitudine della posizione corrente.
 * @property longitude Longitudine della posizione corrente.
 */
@Serializable
data class LocationEntry(
    val user_id: String,
    val latitude: Double,
    val longitude: Double
)
