package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.data.models.Profile
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * `MakeProfile4Activity` rappresenta l'ultima schermata dell'onboarding,
 * dove l'utente può scegliere tra una vibe "Chill" o "Party"
 * e impostare la visibilità del proprio profilo.
 *
 * Al salvataggio, il profilo viene aggiornato su Supabase
 * e l'utente viene reindirizzato alla `HomeActivity`.
 */
class MakeProfile4Activity : AppCompatActivity() {

    /** Switch per selezionare la vibe (Chill / Party) */
    private lateinit var vibeSwitch: MaterialSwitch

    /** Switch per impostare la visibilità del profilo */
    private lateinit var visibilitySwitch: MaterialSwitch

    /** Label associata alla vibe "Party" */
    private lateinit var partyText: TextView

    /** Label associata alla vibe "Chill" */
    private lateinit var chillText: TextView

    /** Label "No" per il campo visibilità */
    private lateinit var noText: TextView

    /** Label "Yes" per il campo visibilità */
    private lateinit var yesText: TextView

    /** Pulsante per completare la registrazione e accedere all'app */
    private lateinit var jumpInButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_profile4)

        // Inizializzazione elementi UI
        vibeSwitch = findViewById(R.id.vibeSwitch)
        visibilitySwitch = findViewById(R.id.visibilitySwitch)
        partyText = findViewById(R.id.partyText)
        chillText = findViewById(R.id.chillText)
        noText = findViewById(R.id.noText)
        yesText = findViewById(R.id.yesText)
        jumpInButton = findViewById(R.id.jumpInButton)

        // Cambia colori delle label al toggle del vibe switch
        vibeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                partyText.setTextColor(ContextCompat.getColor(this, R.color.gray))
                chillText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                partyText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                chillText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }

        // Cambia colori delle label al toggle del visibility switch
        visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                noText.setTextColor(ContextCompat.getColor(this, R.color.gray))
                yesText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                noText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                yesText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }

        // Azione al click su "Jump In"
        jumpInButton.setOnClickListener {
            lifecycleScope.launch {
                updateUserVibeAndVisibility()
            }
        }
    }

    /**
     * Aggiorna il profilo utente su Supabase con la vibe selezionata e la visibilità.
     * Se l'utente è autenticato, effettua una query per ottenere il profilo corrente,
     * lo aggiorna con i nuovi valori e salva il risultato.
     * In caso di successo, reindirizza alla `HomeActivity`.
     */
    private suspend fun updateUserVibeAndVisibility() {
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            Toast.makeText(this, "Utente non autenticato. Riprova il login.", Toast.LENGTH_LONG).show()
            return
        }

        val vibe = if (vibeSwitch.isChecked) "Chill" else "Party"
        val visible = visibilitySwitch.isChecked

        try {
            // Recupera il profilo corrente da Supabase
            val existingProfiles = SupabaseClientProvider.db.from("profiles")
                .select() {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<Profile>()

            val currentProfile = existingProfiles.firstOrNull()

            if (currentProfile == null) {
                Toast.makeText(this, "Profilo non trovato!", Toast.LENGTH_LONG).show()
                return
            }

            // Crea una nuova istanza aggiornata
            val updatedProfile = currentProfile.copy(
                has_logged_before = true,
                vibe = vibe,
                visible = visible
            )

            // Aggiorna il profilo con il nuovo oggetto
            val result = SupabaseClientProvider.db.from("profiles").update(updatedProfile) {
                filter {
                    eq("id", userId)
                }
            }

            Log.d("SupabaseUpdate", "Update result: $result")
            Toast.makeText(this, "Profilo aggiornato!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this@MakeProfile4Activity, HomeActivity::class.java)
            startActivity(intent)
            finish()

        } catch (e: Exception) {
            Log.e("SupabaseUpdate", "Errore aggiornamento profilo: ${e.localizedMessage}", e)
            Toast.makeText(this, "Errore aggiornamento profilo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

}