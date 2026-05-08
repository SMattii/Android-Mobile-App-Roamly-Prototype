package com.example.roamly.activity

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var loginBtn: MaterialButton
    private lateinit var loginProgress: CircularProgressIndicator
    private var isLoginInProgress = false

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
        loginProgress = findViewById(R.id.loginProgress)

        loginBtn.setOnClickListener {
            if (!isLoginInProgress && validateForm()) {
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

    private fun performLogin() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()

        setLoginLoading(true)

        lifecycleScope.launch {
            try {
                SupabaseClientProvider.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                navigateAfterAuthenticated()
            } catch (e: Exception) {
                Log.e("Login", "Login failed", e)
                val message = if (e.message.orEmpty().contains("email not confirmed", ignoreCase = true)) {
                    "Conferma la tua email prima di accedere"
                } else {
                    "Credenziali errate"
                }
                Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                if (!isFinishing) {
                    setLoginLoading(false)
                }
            }
        }
    }

    private suspend fun navigateAfterAuthenticated() {
        val nextIntent = AuthSessionNavigator.nextIntent(this)
        if (nextIntent == null) {
            Toast.makeText(this, "Credenziali errate", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(nextIntent)
        finish()
    }

    private fun setLoginLoading(isLoading: Boolean) {
        isLoginInProgress = isLoading
        loginBtn.isClickable = !isLoading
        loginBtn.isFocusable = !isLoading
        loginProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
