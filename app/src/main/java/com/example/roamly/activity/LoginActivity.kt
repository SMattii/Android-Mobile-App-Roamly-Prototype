package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.data.models.Profile
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlin.jvm.java


class LoginActivity : AppCompatActivity() {

    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var loginBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        emailField = findViewById(R.id.loginEmail)
        passwordField = findViewById(R.id.loginPassword)
        loginBtn = findViewById(R.id.loginBtn)

        loginBtn.setOnClickListener {
            if (validateForm()) {
                performLogin()
            }
        }
    }

    private fun validateForm(): Boolean {
        var valid = true
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.error = "Email non valida"
            valid = false
        } else {
            emailField.error = null
        }

        if (password.isEmpty()) {
            passwordField.error = "Inserisci la password"
            valid = false
        } else {
            passwordField.error = null
        }

        return valid
    }

// Modifica performLogin in LoginActivity

    private fun performLogin() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        lifecycleScope.launch {
            try {
                SupabaseClientProvider.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id

                if (userId != null) {
                    val profile = try {
                        SupabaseClientProvider.db["profiles"]
                            .select {
                                filter { eq("id", userId) }
                            }
                            .decodeSingleOrNull<Profile>()
                    } catch (e: Exception) {
                        null
                    }

                    if (profile == null) {
                        // Nessuna riga: crea nuovo profilo base
                        try {
                            val newProfile = Profile(
                                id = userId,
                                full_name = "",
                                first_name = "",
                                last_name = "",
                                profile_image_url = null,
                                has_logged_before = false,
                                age = null,
                                country = null,
                                category = null,
                                vibe = null,
                                visible = true
                            )

                            val result = SupabaseClientProvider.db["profiles"]
                                .insert(newProfile)

                            Log.d("Supabase", "Insert result: $result")
                        } catch (e: Exception) {
                            Log.e("Supabase", "Errore creazione profilo", e)
                            Toast.makeText(this@LoginActivity, "Errore creazione profilo", Toast.LENGTH_SHORT).show()
                        }

                        startActivity(Intent(this@LoginActivity, MakeProfile1Activity::class.java))
                        finish()

                    } else {
                        // ✅ Aggiungi flag per indicare che è un nuovo login
                        val intent = if (!profile.has_logged_before) {
                            Intent(this@LoginActivity, MakeProfile1Activity::class.java)
                        } else {
                            Intent(this@LoginActivity, HomeActivity::class.java).apply {
                                // Aggiungi flag per indicare che è un fresh login
                                putExtra("is_fresh_login", true)
                            }
                        }

                        startActivity(intent)
                        finish()
                    }

                } else {
                    Toast.makeText(this@LoginActivity, "Credenziali non valide", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Errore login: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

}