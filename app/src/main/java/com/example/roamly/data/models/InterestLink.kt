package com.example.roamly.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta il collegamento tra un profilo utente e un interesse,
 * tipicamente ottenuto tramite una `join` tra le tabelle `profile_interests` e `interests`.
 *
 * Utilizzato per recuperare gli interessi completi associati a un profilo.
 *
 * @property profile_id ID del profilo utente a cui è associato l'interesse.
 * @property interest Oggetto [Interest] associato al profilo.
 */
@Serializable
data class InterestLink(
    val profile_id: String,
    @SerialName("interests") val interest: Interest
)