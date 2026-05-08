package com.example.roamly.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roamly.R
import com.example.roamly.data.utils.SupabaseClientProvider
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthCallbackActivity : AppCompatActivity() {

    private var callbackHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth_callback)
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        val data = intent.data
        val callbackError = data?.getQueryParameter("error_description")
            ?: data?.getQueryParameter("error")

        if (callbackError != null) {
            Log.e("AuthCallback", "OAuth callback error: $callbackError")
            returnToLogin()
            return
        }

        SupabaseClientProvider.supabase.handleDeeplinks(intent) {
            callbackHandled = true
            lifecycleScope.launch {
                navigateAfterAuth()
            }
        }

        lifecycleScope.launch {
            delay(15_000L)
            if (!callbackHandled && !isFinishing) {
                Log.e("AuthCallback", "OAuth callback timed out or was not handled")
                returnToLogin()
            }
        }
    }

    private suspend fun navigateAfterAuth() {
        try {
            val nextIntent = AuthSessionNavigator.nextIntent(this)
            if (nextIntent == null) {
                returnToLogin()
                return
            }
            startActivity(nextIntent)
            finish()
        } catch (e: Exception) {
            Log.e("AuthCallback", "OAuth post-login routing failed", e)
            returnToLogin()
        }
    }

    private fun returnToLogin() {
        Toast.makeText(this, "Accesso con Google non riuscito", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
