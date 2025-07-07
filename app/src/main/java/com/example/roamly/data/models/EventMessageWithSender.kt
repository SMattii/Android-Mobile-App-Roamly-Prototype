package com.example.roamly.data.models

/**
 * Modello dati che unisce un messaggio evento con il profilo completo del mittente.
 *
 * Utile per la visualizzazione nella chat evento, dove sono richieste informazioni
 * aggiuntive sull'utente (es. nome completo, immagine profilo).
 *
 * @property message Messaggio inviato nella chat dell'evento.
 * @property sender Profilo completo dell'utente che ha inviato il messaggio.
 */
data class EventMessageWithSender(
    val message: EventMessage,
    val sender: Profile
)