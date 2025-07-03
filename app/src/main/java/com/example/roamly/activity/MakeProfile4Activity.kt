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

class MakeProfile4Activity : AppCompatActivity() {

    private lateinit var vibeSwitch: MaterialSwitch
    private lateinit var visibilitySwitch: MaterialSwitch
    private lateinit var partyText: TextView
    private lateinit var chillText: TextView
    private lateinit var noText: TextView
    private lateinit var yesText: TextView
    private lateinit var jumpInButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_make_profile4)

        // Collega gli elementi del layout
        vibeSwitch = findViewById(R.id.vibeSwitch)
        visibilitySwitch = findViewById(R.id.visibilitySwitch)
        partyText = findViewById(R.id.partyText)
        chillText = findViewById(R.id.chillText)
        noText = findViewById(R.id.noText)
        yesText = findViewById(R.id.yesText)
        jumpInButton = findViewById(R.id.jumpInButton)

        // Cambia il colore dei testi quando si preme lo switch Vibe
        vibeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                partyText.setTextColor(ContextCompat.getColor(this, R.color.gray))
                chillText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                partyText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                chillText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }

        // Cambia il colore dei testi quando si preme lo switch Visibility
        visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                noText.setTextColor(ContextCompat.getColor(this, R.color.gray))
                yesText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                noText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                yesText.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
        }

        // Quando si preme "Jump In"
        jumpInButton.setOnClickListener {
            lifecycleScope.launch {
                updateUserVibeAndVisibility()
            }
        }
    }

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