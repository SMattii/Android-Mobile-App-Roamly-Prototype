package com.example.roamly.data.utils

import com.example.roamly.R
import io.github.jan.supabase.auth.providers.Facebook as SupabaseFacebook
import io.github.jan.supabase.auth.providers.Google as SupabaseGoogle
import io.github.jan.supabase.auth.providers.OAuthProvider

enum class SocialAuthProvider(
    val buttonTextRes: Int,
    val supabaseProvider: OAuthProvider,
    val scopes: List<String> = emptyList()
) {
    Google(
        buttonTextRes = R.string.auth_google_button,
        supabaseProvider = SupabaseGoogle,
        scopes = listOf("email", "profile")
    ),
    Facebook(
        buttonTextRes = R.string.auth_facebook_button,
        supabaseProvider = SupabaseFacebook,
        scopes = listOf("email", "public_profile")
    )
}

object SocialAuth {

    suspend fun signInWith(provider: SocialAuthProvider) {
        SupabaseClientProvider.auth.signInWith(provider.supabaseProvider) {
            scopes.addAll(provider.scopes)
        }
    }
}
