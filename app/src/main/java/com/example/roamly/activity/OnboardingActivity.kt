package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.data.utils.SocialAuth
import com.example.roamly.data.utils.SocialAuthProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlin.jvm.java

/**
 * Activity di onboarding che permette all'utente di scegliere tra login e registrazione.
 *
 * Configura la visualizzazione edge-to-edge, applica i giusti padding per le system bars e
 * gestisce la navigazione verso le activity di Login e Signup.
 */
class OnboardingActivity : AppCompatActivity() {

    private var isAuthInProgress = false

    /**
     * Callback invocato alla creazione dell'activity.
     *
     * @param savedInstanceState Bundle contenente lo stato precedentemente salvato, se presente.
     */
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogleOnboarding)

        // Navigazione alla LoginActivity
        btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Navigazione alla SignupActivity
        btnRegister.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnGoogle.setOnClickListener {
            if (!isAuthInProgress) {
                lifecycleScope.launch {
                    try {
                        setAuthButtonsEnabled(false, btnLogin, btnRegister, btnGoogle)
                        SocialAuth.signInWith(SocialAuthProvider.Google)
                    } catch (e: Exception) {
                        Log.e("Onboarding", "Google auth failed", e)
                        Toast.makeText(this@OnboardingActivity, "Accesso con Google non riuscito", Toast.LENGTH_LONG).show()
                    } finally {
                        setAuthButtonsEnabled(true, btnLogin, btnRegister, btnGoogle)
                    }
                }
            }
        }
    }

    private fun setAuthButtonsEnabled(enabled: Boolean, vararg buttons: Button) {
        isAuthInProgress = !enabled
        buttons.forEach { it.isEnabled = enabled }
    }
}
