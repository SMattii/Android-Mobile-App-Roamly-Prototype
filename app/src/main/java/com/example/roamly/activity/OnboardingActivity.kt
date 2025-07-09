package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.roamly.R
import kotlin.jvm.java

/**
 * Activity di onboarding che permette all'utente di scegliere tra login e registrazione.
 *
 * Configura la visualizzazione edge-to-edge, applica i giusti padding per le system bars e
 * gestisce la navigazione verso le activity di Login e Signup.
 */
class OnboardingActivity : AppCompatActivity() {

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
    }
}