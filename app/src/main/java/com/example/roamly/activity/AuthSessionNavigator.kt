package com.example.roamly.activity

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.roamly.data.models.Profile
import com.example.roamly.data.utils.AuthSessionCache
import com.example.roamly.data.utils.SupabaseClientProvider

internal object AuthSessionNavigator {

    suspend fun nextIntent(context: Context, isFreshLogin: Boolean = true): Intent? {
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: return null
        val profile = loadOrCreateProfile(userId)
        AuthSessionCache.rememberProfile(context, profile)
        return intentForProfile(context, profile, isFreshLogin)
    }

    suspend fun startupIntent(context: Context): Intent? {
        val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id ?: run {
            AuthSessionCache.clear(context)
            return null
        }

        if (AuthSessionCache.isExpired(context, userId)) {
            runCatching { SupabaseClientProvider.auth.signOut() }
                .onFailure { Log.e("AuthSessionNavigator", "Expired session sign out failed", it) }
            AuthSessionCache.clear(context)
            return null
        }

        AuthSessionCache.cachedHasLoggedBefore(context, userId)?.let { hasLoggedBefore ->
            return intentForProfileState(context, hasLoggedBefore, isFreshLogin = false)
        }

        val profile = loadOrCreateProfile(userId)
        AuthSessionCache.rememberProfile(context, profile)
        return intentForProfile(context, profile, isFreshLogin = false)
    }

    private fun intentForProfile(context: Context, profile: Profile, isFreshLogin: Boolean): Intent {
        return if (!profile.has_logged_before) {
            Intent(context, MakeProfile1Activity::class.java)
        } else {
            Intent(context, HomeActivity::class.java).apply {
                putExtra("is_fresh_login", isFreshLogin)
            }
        }
    }

    private fun intentForProfileState(
        context: Context,
        hasLoggedBefore: Boolean,
        isFreshLogin: Boolean
    ): Intent {
        return if (!hasLoggedBefore) {
            Intent(context, MakeProfile1Activity::class.java)
        } else {
            Intent(context, HomeActivity::class.java).apply {
                putExtra("is_fresh_login", isFreshLogin)
            }
        }
    }

    private suspend fun loadOrCreateProfile(userId: String): Profile {
        val existingProfile = loadProfile(userId)
        if (existingProfile != null) return existingProfile

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

        return try {
            SupabaseClientProvider.db["profiles"].insert(newProfile)
            newProfile
        } catch (e: Exception) {
            Log.e("AuthSessionNavigator", "Profile creation failed", e)
            loadProfile(userId) ?: throw e
        }
    }

    private suspend fun loadProfile(userId: String): Profile? {
        return try {
            SupabaseClientProvider.db["profiles"]
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            Log.e("AuthSessionNavigator", "Profile load failed", e)
            null
        }
    }
}
