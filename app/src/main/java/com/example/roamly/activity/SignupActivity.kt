package com.example.roamly.activity

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import android.content.Intent
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider

/**
 * Activity responsabile della registrazione utente.
 * Gestisce input email/password, validazione e invio dati a Supabase.
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var confirmPasswordField: TextInputEditText
    private lateinit var registerButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        emailField = findViewById(R.id.registerEmail)
        passwordField = findViewById(R.id.registerPassword)
        confirmPasswordField = findViewById(R.id.registerConfirmPassword)
        registerButton = findViewById(R.id.btnRegister)

        registerButton.setOnClickListener {
            if (validateForm()) {
                performSignup()
            }
        }
    }

    /**
     * Valida i campi del modulo di registrazione.
     *
     * @return `true` se tutti i campi sono validi, `false` altrimenti.
     */
    private fun validateForm(): Boolean {
        var valid = true

        val email = emailField.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.error = "Email non valida"
            valid = false
        } else {
            emailField.error = null
        }

        val pwd = passwordField.text.toString()
        if (pwd.length < 6) {
            passwordField.error = "La password deve contenere almeno 6 caratteri"
            valid = false
        } else {
            passwordField.error = null
        }

        val pwdConfirm = confirmPasswordField.text.toString()
        if (pwdConfirm != pwd) {
            confirmPasswordField.error = "Le password non corrispondono"
            valid = false
        } else {
            confirmPasswordField.error = null
        }

        return valid
    }

    /**
     * Esegue la registrazione dell'utente su Supabase.
     * In caso di utente già autenticato, effettua prima il logout.
     */
    private fun performSignup() {
        lifecycleScope.launch {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            try {
                // Se l'utente è già loggato, effettua il logout
                SupabaseClientProvider.auth.currentUserOrNull()?.let {
                    Log.d("Signup", "Utente già loggato (${it.id}), eseguo logout.")
                    SupabaseClientProvider.auth.signOut()
                }

                // Procedi con la registrazione
                val session = SupabaseClientProvider
                    .auth
                    .signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }

                val user = SupabaseClientProvider.auth.currentUserOrNull()

                if (user != null) {
                    Toast.makeText(
                        this@SignupActivity,
                        "Registrazione completata",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@SignupActivity,
                        "Registrazione ricevuta. Controlla la tua casella email",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("Signup", "Errore durante signup", e)
                Toast.makeText(
                    this@SignupActivity,
                    "Signup fallito: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}