package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un interesse selezionabile dagli utenti
 * o associabile agli eventi (es. "nightlife", "culture", "nature").
 *
 * @property id Identificatore univoco dell'interesse.
 * @property name Nome descrittivo dell'interesse.
 */
@Serializable
data class Interest(
    val id: String,
    val name: String
)
