package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roamly.data.models.Interest
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlin.jvm.java // Questo import non è necessario e può essere rimosso, dato che usi ::class.java

/**
 * Activity per la selezione degli interessi dell'utente.
 * Carica tutti gli interessi dal database, popola una mappa nome→id,
 * e salva gli interessi selezionati associandoli al profilo.
 */
class MakeProfile3Activity : AppCompatActivity() {

    /**
     * Pulsante per procedere al prossimo step del profilo.
     */
    private lateinit var nextButton: MaterialButton

    /**
     * Mappa dinamica da nome dell'interesse (key) a ID (value),
     * popolata da loadAllInterests().
     */
    private val interestNameToId = mutableMapOf<String, String>()

    /**
     * Inizializza l'Activity, setta il layout e avvia il caricamento
     * degli interessi dal database.
     * @param savedInstanceState Stato salvato precedentemente, se presente.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_profile3)

        nextButton = findViewById(R.id.nextButton3)

        // Carica tutti gli interessi disponibili
        lifecycleScope.launch {
            loadAllInterests()
        }

        // Listener per il pulsante "Next": salva gli interessi selezionati
        nextButton.setOnClickListener {
            lifecycleScope.launch {
                saveSelectedInterests()
            }
        }
    }

    /**
     * Recupera dal database Supabase la lista di tutti gli interessi
     * e popola la mappa interestNameToId.
     * Gestisce eventuali errori mostrando un Toast e loggando l'eccezione.
     */
    private suspend fun loadAllInterests() {
        try {
            val interests = SupabaseClientProvider.db.from("interests")
                .select()
                .decodeList<Interest>()

            Log.d("LoadInterests", "Interests fetched from DB: $interests") // Log 1: Interessi grezzi dal DB

            interests.forEach {
                interestNameToId[it.name] = it.id
            }
            Log.d("LoadInterests", "Mapped interestNameToId: $interestNameToId") // Log 2: Mappa popolata

        } catch (e: Exception) {
            Log.e("SupabaseLoad", "Errore caricamento interessi: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore caricamento interessi", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Salva nel database gli interessi selezionati dall'utente.
     * - Recupera l'ID utente autenticato.
     * - Colleziona le checkbox selezionate.
     * - Converte i nomi in ID tramite interestNameToId.
     * - Inserisce le associazioni nel database o mostra un messaggio se nessun
     *   interesse è stato selezionato.
     * In caso di errore, logga l'eccezione e mostra un Toast.
     */
    private suspend fun saveSelectedInterests() {
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato.", Toast.LENGTH_LONG).show()
            Log.e("SupabaseSave", "User not authenticated - userId is null.") // Log A: Utente non autenticato
            return
        }
        Log.d("SupabaseSave", "Authenticated User ID: $userId") // Log B: ID utente autenticato

        try {
            val checkboxPairs = listOf(
                Pair("Food and Drinks", findViewById<CheckBox>(R.id.interestFood)),
                Pair("Nightlife", findViewById<CheckBox>(R.id.interestNightlife)),
                Pair("Culture", findViewById<CheckBox>(R.id.interestCulture)),
                Pair("Nature", findViewById<CheckBox>(R.id.interestNature)),
                Pair("Sport", findViewById<CheckBox>(R.id.interestSport)),
                Pair("Networking", findViewById<CheckBox>(R.id.interestNetworking))
            )
            Log.d("SupabaseSave", "All Checkbox Pairs: $checkboxPairs") // Log C: Tutte le checkbox (pair name, view)

            // Filtra solo le checkbox selezionate
            val checkedPairs = checkboxPairs.filter { it.second.isChecked }
            Log.d("SupabaseSave", "Checked Checkbox Pairs: $checkedPairs") // Log D: Solo le checkbox selezionate

            // Ottieni gli ID dalla mappa e logga eventuali missing key
            val selectedNames = checkedPairs.mapNotNull {
                val interestId = interestNameToId[it.first]
                if (interestId == null) {
                    Log.w("SupabaseSave", "Interest ID not found for name: ${it.first}") // Log E: ID interesse non trovato
                }
                interestId
            }
            Log.d("SupabaseSave", "Selected Interest IDs (from map): $selectedNames") // Log F: ID degli interessi selezionati

            if (selectedNames.isNotEmpty()) {
                val entriesToInsert = selectedNames.map { interestId ->
                    mapOf(
                        "profile_id" to userId,
                        "interest_id" to interestId
                    )
                }
                Log.d("SupabaseSave", "Final Entries to Insert: $entriesToInsert") // Log G: Le entries pronte per l'inserimento

                val result = SupabaseClientProvider.db.from("profile_interests").insert(entriesToInsert)
                Log.d("SupabaseSave", "Insert operation result: $result") // Log H: Il risultato dell'operazione di insert

                Toast.makeText(this, "Interessi salvati!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MakeProfile4Activity::class.java))
                finish()
            } else {
                Log.d("SupabaseSave", "No interests selected for saving.") // Log I: Nessun interesse selezionato
                Toast.makeText(this, "Nessun interesse selezionato.", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("SupabaseSave", "Errore salvataggio interessi: ${e.message}", e) // Log J: Dettagli dell'errore
            Toast.makeText(this, "Errore: ${e.message ?: "Errore sconosciuto"}", Toast.LENGTH_LONG).show()
        }
    }
}