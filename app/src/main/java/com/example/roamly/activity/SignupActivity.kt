package com.example.roamly.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.data.utils.AuthValidation
import com.example.roamly.data.utils.SocialAuth
import com.example.roamly.data.utils.SocialAuthProvider
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var confirmPasswordField: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var googleSignupButton: MaterialButton
    private var isAuthInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        emailField = findViewById(R.id.registerEmail)
        passwordField = findViewById(R.id.registerPassword)
        confirmPasswordField = findViewById(R.id.registerConfirmPassword)
        registerButton = findViewById(R.id.btnRegister)
        googleSignupButton = findViewById(R.id.btnGoogleSignup)

        registerButton.setOnClickListener {
            if (!isAuthInProgress && validateForm()) {
                performSignup()
            }
        }

        googleSignupButton.setOnClickListener {
            if (!isAuthInProgress) {
                performSocialSignup(SocialAuthProvider.Google)
            }
        }
    }

    private fun validateForm(): Boolean {
        var valid = true

        val email = emailField.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.error = "Email non valida"
            valid = false
        } else {
            emailField.error = null
        }

        val password = passwordField.text.toString()
        val passwordError = AuthValidation.passwordValidationMessage(password)
        if (passwordError != null) {
            passwordField.error = passwordError
            valid = false
        } else {
            passwordField.error = null
        }

        val passwordConfirmation = confirmPasswordField.text.toString()
        val matchError = AuthValidation.passwordsMatchMessage(password, passwordConfirmation)
        if (matchError != null) {
            confirmPasswordField.error = matchError
            valid = false
        } else {
            confirmPasswordField.error = null
        }

        return valid
    }

    private fun performSignup() {
        lifecycleScope.launch {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            try {
                setAuthButtonsEnabled(false)

                SupabaseClientProvider.auth.currentUserOrNull()?.let {
                    Log.d("Signup", "Utente gia loggato (${it.id}), eseguo logout.")
                    SupabaseClientProvider.auth.signOut()
                }

                SupabaseClientProvider.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                SupabaseClientProvider.auth.currentUserOrNull()?.let {
                    SupabaseClientProvider.auth.signOut()
                }

                showVerificationDialog()
            } catch (e: Exception) {
                Log.e("Signup", "Errore durante signup", e)
                val message = if (isEmailAlreadyInUse(e)) {
                    "Email gia in uso"
                } else {
                    "Registrazione non riuscita. Riprova piu tardi"
                }
                Toast.makeText(this@SignupActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                setAuthButtonsEnabled(true)
            }
        }
    }

    private fun performSocialSignup(provider: SocialAuthProvider) {
        lifecycleScope.launch {
            try {
                setAuthButtonsEnabled(false)
                SocialAuth.signInWith(provider)
            } catch (e: Exception) {
                Log.e("Signup", "Social signup failed for ${provider.name}", e)
                Toast.makeText(this@SignupActivity, "Accesso con Google non riuscito", Toast.LENGTH_LONG).show()
            } finally {
                setAuthButtonsEnabled(true)
            }
        }
    }

    private fun showVerificationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verifica email")
            .setMessage("Ti abbiamo inviato una email di verifica. Conferma l'indirizzo, poi accedi dalla schermata di login.")
            .setPositiveButton("Vai al login") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setAuthButtonsEnabled(enabled: Boolean) {
        isAuthInProgress = !enabled
        registerButton.isEnabled = enabled
        googleSignupButton.isEnabled = enabled
    }

    private fun isEmailAlreadyInUse(error: Exception): Boolean {
        val message = error.message.orEmpty().lowercase()
        return listOf(
            "already registered",
            "already exists",
            "email_exists",
            "email already",
            "user already",
            "duplicate"
        ).any { it in message }
    }
}
