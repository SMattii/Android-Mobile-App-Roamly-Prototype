package com.example.roamly.data.models

/**
 * Modello dati che rappresenta un profilo utente completo di dettagli,
 * inclusi oggetti `Language` e `Interest` associati al profilo.
 *
 * Utilizzato per visualizzare o modificare un profilo con tutte le informazioni già risolte,
 * come nella schermata di modifica profilo o durante la registrazione.
 *
 * @property profile Dati principali del profilo utente.
 * @property selectedLanguages Elenco degli oggetti lingua associati al profilo.
 * @property selectedInterests Elenco degli oggetti interesse associati al profilo.
 */
data class ProfileComplete(
    val profile: Profile,
    val selectedLanguages: List<Language>,
    val selectedInterests: List<Interest>
)